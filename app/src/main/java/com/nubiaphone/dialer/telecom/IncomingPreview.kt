package com.nubiaphone.dialer.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
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

    @Volatile var silenced: Boolean = false
        private set

    /** Answered from the notification without opening the full in-call UI. */
    @Volatile var answered: Boolean = false
        private set

    @Volatile private var tone: Ringtone? = null
    @Volatile private var vibrator: Vibrator? = null
    @Volatile private var connectedAt: Long = 0L

    fun launch(context: Context, kind: String) {
        val app = context.applicationContext
        silenced = false
        answered = false
        connectedAt = 0L
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

    fun silence(context: Context) {
        if (silenced || answered) return
        silenced = true
        stopRingFromService(context)
        PreviewRingService.refresh(context.applicationContext)
    }

    /** Answer in the shade — no full-screen UI (unlocked workflow). */
    fun answer(context: Context) {
        if (answered) return
        answered = true
        silenced = true
        connectedAt = System.currentTimeMillis()
        stopRingFromService(context)
        PreviewRingService.refresh(context.applicationContext)
    }

    fun connectedAtMillis(): Long = connectedAt

    fun dismiss(context: Context) {
        silenced = false
        answered = false
        connectedAt = 0L
        stopRingFromService(context)
        runCatching { PreviewRingService.stop(context.applicationContext) }
    }

    fun startRingFromService(context: Context) {
        if (silenced) return
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

    fun stopRingFromService(context: Context? = null) {
        runCatching { tone?.stop() }
        tone = null
        vibrator?.cancel()
        vibrator = null
        // Belt-and-suspenders: some OEMs keep a system ringtone cue after Ringtone.stop().
        context?.getSystemService(AudioManager::class.java)?.let { am ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
                    am.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                }
            }
        }
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
