package com.nubiaphone.dialer.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat

/**
 * While a call is ringing, any volume-key change silences the ringtone — works when only the
 * heads-up notification is showing (no in-call activity focused).
 */
object VolumeSilence {
    private const val ACTION = "android.media.VOLUME_CHANGED_ACTION"
    private const val EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"

    fun register(context: Context, onVolume: () -> Unit): BroadcastReceiver {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != ACTION) return
                    val stream = intent.getIntExtra(EXTRA_STREAM, -1)
                    // Ring / notification / music / voice — OEMs route volume keys differently.
                    if (stream != AudioManager.STREAM_RING &&
                        stream != AudioManager.STREAM_NOTIFICATION &&
                        stream != AudioManager.STREAM_MUSIC &&
                        stream != AudioManager.STREAM_VOICE_CALL &&
                        stream != -1
                    ) {
                        return
                    }
                    onVolume()
                }
            }
        val filter = IntentFilter(ACTION)
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return receiver
    }

    fun unregister(context: Context, receiver: BroadcastReceiver?) {
        if (receiver == null) return
        runCatching { context.applicationContext.unregisterReceiver(receiver) }
    }
}
