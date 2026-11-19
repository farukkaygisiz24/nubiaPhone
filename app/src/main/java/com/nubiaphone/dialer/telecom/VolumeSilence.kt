package com.nubiaphone.dialer.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * While ringing, a real STREAM_RING volume **decrease** silences the ringtone — the call
 * notification stays. Ignores Telecom/OEM volume writes at ring start (grace + decrease filter).
 *
 * Nubia sometimes skips VOLUME_CHANGED; optional ring-volume Settings URI covers that without
 * watching all of Settings.System.
 */
class VolumeSilence private constructor(
    private val app: Context,
    private val onSilence: () -> Unit,
) {
    private var armed = true
    private val armedAtElapsed = SystemClock.elapsedRealtime()
    private val main = Handler(Looper.getMainLooper())
    private var knownRingVolume: Int? = readRingVolume()

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION) return
                val stream = intent.getIntExtra(EXTRA_STREAM, -1)
                val current = intent.getIntExtra(EXTRA_VOLUME, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
                val previous =
                    if (intent.hasExtra(EXTRA_PREV)) intent.getIntExtra(EXTRA_PREV, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
                    else null
                maybeFire(stream, current, previous)
            }
        }

    private val ringUris: List<Uri> =
        listOf("volume_ring", "volume_ring_speaker").map { Settings.System.getUriFor(it) }

    private val observer =
        object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) = onChange(selfChange, null)

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                val current = readRingVolume() ?: return
                maybeFire(VolumeSilencePolicy.STREAM_RING, current, knownRingVolume)
            }
        }

    private fun maybeFire(stream: Int?, current: Int?, previous: Int?) {
        val allow =
            VolumeSilencePolicy.shouldSilence(stream, current, previous, knownRingVolume)
        // Track ring level even during grace so post-grace comparisons stay honest.
        if (stream == VolumeSilencePolicy.STREAM_RING && current != null) {
            knownRingVolume = current
        }
        if (!armed) return
        if (!VolumeSilencePolicy.pastGrace(SystemClock.elapsedRealtime(), armedAtElapsed)) return
        if (!allow) return
        fire()
    }

    private fun fire() {
        if (!armed) return
        armed = false
        main.post {
            runCatching { onSilence() }
            main.postDelayed({ armed = true }, 800)
        }
    }

    private fun readRingVolume(): Int? =
        runCatching {
            val am = app.getSystemService(AudioManager::class.java) ?: return null
            am.getStreamVolume(AudioManager.STREAM_RING)
        }.getOrNull()

    fun register() {
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Ring volume URIs only — never Settings.System.CONTENT_URI (false silence on any setting).
        ringUris.forEach { uri ->
            runCatching { app.contentResolver.registerContentObserver(uri, false, observer) }
        }
    }

    fun unregister() {
        runCatching { app.unregisterReceiver(receiver) }
        runCatching { app.contentResolver.unregisterContentObserver(observer) }
        main.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val EXTRA_VOLUME = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        private const val EXTRA_PREV = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"

        fun start(context: Context, onSilence: () -> Unit): VolumeSilence =
            VolumeSilence(context.applicationContext, onSilence).also { it.register() }
    }
}
