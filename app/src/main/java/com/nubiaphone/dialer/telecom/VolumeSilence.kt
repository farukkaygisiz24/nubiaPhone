package com.nubiaphone.dialer.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * While ringing, volume-key presses should silence the ringtone only — the call notification
 * stays in the shade. Uses both VOLUME_CHANGED and a Settings observer because Nubia/MyOS
 * often skips or delays the broadcast when only a heads-up is showing.
 */
class VolumeSilence private constructor(
    private val app: Context,
    private val onSilence: () -> Unit,
) {
    private var armed = true
    private val main = Handler(Looper.getMainLooper())

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == ACTION) fire()
            }
        }

    private val observer =
        object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) = fire()
        }

    private fun fire() {
        if (!armed) return
        armed = false
        main.post {
            runCatching { onSilence() }
            // Re-arm after a beat so a later ring can use volume again if needed.
            main.postDelayed({ armed = true }, 800)
        }
    }

    fun register() {
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        runCatching {
            app.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        }
    }

    fun unregister() {
        runCatching { app.unregisterReceiver(receiver) }
        runCatching { app.contentResolver.unregisterContentObserver(observer) }
        main.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val ACTION = "android.media.VOLUME_CHANGED_ACTION"

        fun start(context: Context, onSilence: () -> Unit): VolumeSilence =
            VolumeSilence(context.applicationContext, onSilence).also { it.register() }
    }
}
