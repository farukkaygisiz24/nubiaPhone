package com.nubiaphone.dialer.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.nubiaphone.dialer.ui.incall.InCallActivity

/**
 * Dev / adb fake incoming call that follows the same lock policy as a real ring:
 * locked or screen-off → full [InCallActivity]; unlocked → CallStyle notification + ringtone
 * (via [PreviewRingService] foreground service — required by Android for CallStyle without FSI).
 */
object IncomingPreview {
    const val CH = "incoming_preview"
    const val NOTIF_ID = 4202

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
            PreviewRingService.start(app)
        }
    }

    fun dismiss(context: Context) {
        stopRingFromService()
        runCatching { PreviewRingService.stop(context.applicationContext) }
    }

    fun startRingFromService(context: Context) {
        stopRingFromService()
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

    fun stopRingFromService() {
        runCatching { tone?.stop() }
        tone = null
        vibrator?.cancel()
        vibrator = null
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
}
