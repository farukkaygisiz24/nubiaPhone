package com.nubiaphone.dialer.telecom

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.nubiaphone.dialer.R
import com.nubiaphone.dialer.ui.incall.InCallActivity

/**
 * Call notifications. Incoming: CallStyle with Reddet / Cevapla.
 * Keyguard locked (or screen off) → full in-call UI (activity / full-screen intent).
 * Unlocked + screen on → heads-up notification only (no takeover).
 * Volume key or swipe-dismiss → silence ringtone; notification stays so the call can still be answered.
 * Ongoing: name + running timer + "Kapat". Ringing sound stays with Telecom.
 */
object CallNotifier {
    private const val CH_INCOMING = "incoming_call"
    private const val CH_ONGOING = "ongoing_call"
    private const val NOTIF_ID = 4201

    /** User silenced via volume / notification swipe; keep a quiet notification. */
    @Volatile
    var ringerSilenced: Boolean = false
        private set

    fun attach(context: Context) {
        val app = context.applicationContext
        createChannels(app)
        CallRegistry.onChanged = { ctx, calls ->
            if (calls.none { it.phase == CallPhase.Incoming }) ringerSilenced = false
            update(ctx, calls)
        }
    }

    fun silence(context: Context) {
        if (ringerSilenced) return
        ringerSilenced = true
        CallRegistry.silenceRinger(context)
        appUpdate(context)
    }

    private fun appUpdate(context: Context) {
        val app = context.applicationContext
        update(app, CallRegistry.calls.value)
    }

    /**
     * Full incoming UI when the phone is locked or the screen is off — so a ring wakes into
     * the slide-to-answer screen. While unlocked and using the phone, stay on a notification.
     */
    fun shouldShowIncomingUi(context: Context): Boolean {
        val km = context.getSystemService(KeyguardManager::class.java)
        if (km?.isKeyguardLocked == true) return true
        val pm = context.getSystemService(PowerManager::class.java)
        if (pm?.isInteractive == false) return true
        return false
    }

    /** True when an incoming call may post a full-screen intent (permission + locked/off). */
    fun canAlert(context: Context): Boolean {
        if (!shouldShowIncomingUi(context)) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm?.canUseFullScreenIntent() != true) return false
        }
        return true
    }

    private fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_INCOMING, "Gelen aramalar", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, "Süren arama", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun update(context: Context, calls: List<CallUi>) {
        val nm = NotificationManagerCompat.from(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        val incoming = calls.firstOrNull { it.phase == CallPhase.Incoming }
        val current =
            calls.firstOrNull { it.phase == CallPhase.Active || it.phase == CallPhase.Dialing }
                ?: calls.firstOrNull { it.phase == CallPhase.Holding }
        val notification =
            when {
                incoming != null -> incoming(context, incoming)
                current != null -> ongoing(context, current)
                else -> null
            }
        try {
            if (notification == null) nm.cancel(NOTIF_ID) else nm.notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
            // CallStyle without FSI/FGS can be rejected on some builds; fall back below.
            if (incoming != null) {
                runCatching { nm.notify(NOTIF_ID, incomingFallback(context, incoming)) }
            }
        }
    }

    private fun person(call: CallUi) =
        Person.Builder().setName(call.title).setImportant(true).build()

    private fun openScreen(context: Context, id: Int, answer: Boolean = false): PendingIntent {
        val intent = InCallActivity.intent(context).apply {
            if (answer) {
                action = InCallActivity.ACTION_ANSWER
                putExtra(InCallActivity.EXTRA_CALL_ID, id)
            }
        }
        return PendingIntent.getActivity(
            context,
            if (answer) 2 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcast(context: Context, action: String, id: Int = -1): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(InCallActivity.EXTRA_CALL_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun incoming(context: Context, call: CallUi): android.app.Notification {
        val open = openScreen(context, call.id)
        val silenced = ringerSilenced
        val builder =
            NotificationCompat.Builder(context, CH_INCOMING)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setContentTitle(call.title)
                .setContentText(if (silenced) "Gelen arama · sessiz" else "Gelen arama")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                // Stay in the shade; silence is volume-only (not reject / not dismiss).
                .setOngoing(true)
                .setOnlyAlertOnce(silenced)
                .setSilent(silenced)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        person(call),
                        broadcast(context, CallActionReceiver.DECLINE, call.id),
                        // Locked → open in-call UI; unlocked → answer in notification only.
                        answerAction(context, call.id),
                    ),
                )
        if (canAlert(context) && !silenced) {
            builder.setFullScreenIntent(open, true)
        }
        return builder.build()
    }

    /** Unlocked: answer without stealing focus. Locked: open the full in-call screen. */
    private fun answerAction(context: Context, id: Int): PendingIntent =
        if (shouldShowIncomingUi(context)) {
            openScreen(context, id, answer = true)
        } else {
            broadcast(context, CallActionReceiver.ANSWER, id)
        }

    private fun incomingFallback(context: Context, call: CallUi): android.app.Notification {
        val silenced = ringerSilenced
        return NotificationCompat.Builder(context, CH_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(call.title)
            .setContentText(if (silenced) "Gelen arama · sessiz" else "Gelen arama")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(silenced)
            .setContentIntent(openScreen(context, call.id))
            .addAction(0, "Reddet", broadcast(context, CallActionReceiver.DECLINE, call.id))
            .addAction(0, "Cevapla", answerAction(context, call.id))
            .build()
    }

    private fun ongoing(context: Context, call: CallUi): android.app.Notification {
        val text =
            when (call.phase) {
                CallPhase.Dialing -> "Aranıyor…"
                CallPhase.Holding -> "Beklemede"
                else -> "Süren arama"
            }
        val audio = CallRegistry.audio.value
        val speaker = audio.route == android.telecom.CallAudioState.ROUTE_SPEAKER
        // Plain actions (not CallStyle): OEM CallStyle often hides extra buttons.
        return NotificationCompat.Builder(context, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(call.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openScreen(context, call.id))
            .apply {
                if (call.connectTimeMillis > 0) {
                    setUsesChronometer(true)
                    setWhen(call.connectTimeMillis)
                    setShowWhen(true)
                }
            }
            .addAction(
                0,
                if (speaker) "Kulaklık" else "Hoparlöre al",
                broadcast(context, CallActionReceiver.TOGGLE_SPEAKER, call.id),
            )
            .addAction(
                0,
                if (audio.muted) "Mikrofonu aç" else "Mikrofon sessiz",
                broadcast(context, CallActionReceiver.TOGGLE_MUTE, call.id),
            )
            .addAction(0, "Kapat", broadcast(context, CallActionReceiver.HANG_UP, call.id))
            .build()
    }
}
