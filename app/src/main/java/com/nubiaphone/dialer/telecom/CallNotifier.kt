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
 * Unlocked + screen on → full in-call UI (activity / full-screen intent).
 * Keyguard locked (or screen off) → heads-up / lock-screen notification only.
 * Ongoing: name + running timer + "Kapat". Ringing sound stays with Telecom.
 */
object CallNotifier {
    private const val CH_INCOMING = "incoming_call"
    private const val CH_ONGOING = "ongoing_call"
    private const val NOTIF_ID = 4201

    fun attach(context: Context) {
        val app = context.applicationContext
        createChannels(app)
        CallRegistry.onChanged = { ctx, calls -> update(ctx, calls) }
    }

    /**
     * Show the full incoming call screen only when the user is already in the phone
     * (screen interactive and keyguard not locked). Otherwise stay on the notification.
     */
    fun shouldShowIncomingUi(context: Context): Boolean {
        val km = context.getSystemService(KeyguardManager::class.java)
        if (km?.isKeyguardLocked == true) return false
        val pm = context.getSystemService(PowerManager::class.java)
        if (pm?.isInteractive == false) return false
        return true
    }

    /** True when an incoming call can use a full-screen intent (permission + unlocked). */
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

    private fun broadcast(context: Context, action: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(InCallActivity.EXTRA_CALL_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun incoming(context: Context, call: CallUi): android.app.Notification {
        val open = openScreen(context, call.id)
        val builder =
            NotificationCompat.Builder(context, CH_INCOMING)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setContentTitle(call.title)
                .setContentText("Gelen arama")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        person(call),
                        broadcast(context, CallActionReceiver.DECLINE, call.id),
                        openScreen(context, call.id, answer = true),
                    ),
                )
        // Full-screen takeover only while unlocked; on the lock screen stay as a notification.
        if (canAlert(context)) {
            builder.setFullScreenIntent(open, true)
        }
        return builder.build()
    }

    private fun ongoing(context: Context, call: CallUi): android.app.Notification {
        val text =
            when (call.phase) {
                CallPhase.Dialing -> "Aranıyor…"
                CallPhase.Holding -> "Beklemede"
                else -> "Süren arama"
            }
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
            .addAction(0, "Kapat", broadcast(context, CallActionReceiver.HANG_UP, call.id))
            .build()
    }
}
