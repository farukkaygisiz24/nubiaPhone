package com.nubiaphone.dialer.telecom

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.nubiaphone.dialer.R
import com.nubiaphone.dialer.ui.incall.InCallActivity

/**
 * Dev / adb fake incoming call that follows the same lock policy as a real ring:
 * locked or screen-off → full [InCallActivity]; unlocked → CallStyle notification + ringtone.
 */
object IncomingPreview {
    private const val CH = "incoming_preview"
    private const val NOTIF_ID = 4202

    @Volatile private var tone: Ringtone? = null
    @Volatile private var vibrator: Vibrator? = null

    fun launch(context: Context, kind: String) {
        val app = context.applicationContext
        if (kind != "incoming") {
            app.startActivity(
                InCallActivity.intent(app).putExtra(InCallActivity.EXTRA_PREVIEW, kind),
            )
            return
        }
        createChannel(app)
        if (CallNotifier.shouldShowIncomingUi(app)) {
            app.startActivity(
                InCallActivity.intent(app).putExtra(InCallActivity.EXTRA_PREVIEW, "incoming"),
            )
        } else {
            startRing(app)
            postNotification(app)
        }
    }

    fun dismiss(context: Context) {
        stopRing()
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIF_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH, "Sahte gelen arama", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun postNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val person = Person.Builder().setName("Önizleme Kişi").setImportant(true).build()
        val open =
            PendingIntent.getActivity(
                context,
                11,
                InCallActivity.intent(context)
                    .putExtra(InCallActivity.EXTRA_PREVIEW, "incoming")
                    .putExtra(InCallActivity.EXTRA_PREVIEW_SILENT, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val answer =
            PendingIntent.getActivity(
                context,
                12,
                InCallActivity.intent(context)
                    .putExtra(InCallActivity.EXTRA_PREVIEW, "active")
                    .putExtra(InCallActivity.EXTRA_PREVIEW_SILENT, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val decline =
            PendingIntent.getBroadcast(
                context,
                13,
                Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.PREVIEW_DECLINE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(context, CH)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setContentTitle("Önizleme Kişi")
                .setContentText("Gelen arama")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .setDeleteIntent(decline)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
                .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun startRing(context: Context) {
        stopRing()
        tone =
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                RingtoneManager.getRingtone(context, uri)?.also { r ->
                    r.audioAttributes =
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) r.isLooping = true
                    r.play()
                }
            }.getOrNull()
        vibrator =
            runCatching {
                val v =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        context.getSystemService(Vibrator::class.java)
                    } ?: return@runCatching null
                if (!v.hasVibrator()) return@runCatching null
                val pattern = longArrayOf(0, 1000, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, 0)
                }
                v
            }.getOrNull()
    }

    private fun stopRing() {
        runCatching { tone?.stop() }
        tone = null
        vibrator?.cancel()
        vibrator = null
    }
}
