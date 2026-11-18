package com.nubiaphone.dialer.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nubiaphone.dialer.ui.incall.InCallActivity

/** Actions from call / preview notifications. Not exported. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SILENCE -> CallNotifier.silence(context)
            PREVIEW_SILENCE -> IncomingPreview.silence(context)
            PREVIEW_DECLINE -> IncomingPreview.dismiss(context)
            PREVIEW_ANSWER -> IncomingPreview.answer(context)
            ANSWER -> {
                // Unlocked: answer in place — no full-screen takeover.
                val id = intent.getIntExtra(InCallActivity.EXTRA_CALL_ID, -1)
                if (id >= 0) CallRegistry.answer(id)
            }
            DECLINE -> {
                val id = intent.getIntExtra(InCallActivity.EXTRA_CALL_ID, -1)
                if (id >= 0) CallRegistry.reject(id)
            }
            HANG_UP -> {
                val id = intent.getIntExtra(InCallActivity.EXTRA_CALL_ID, -1)
                if (id >= 0) CallRegistry.hangUp(id)
            }
            TOGGLE_MUTE -> {
                val muted = !CallRegistry.audio.value.muted
                CallRegistry.setMuted(muted)
            }
            TOGGLE_SPEAKER -> {
                val speaker = CallRegistry.audio.value.route == android.telecom.CallAudioState.ROUTE_SPEAKER
                CallRegistry.setRoute(
                    if (speaker) android.telecom.CallAudioState.ROUTE_EARPIECE
                    else android.telecom.CallAudioState.ROUTE_SPEAKER,
                )
            }
            PREVIEW_TOGGLE_MUTE -> IncomingPreview.toggleMute(context)
            PREVIEW_TOGGLE_SPEAKER -> IncomingPreview.toggleSpeaker(context)
        }
    }

    companion object {
        const val ANSWER = "com.nubiaphone.dialer.ANSWER_BG"
        const val DECLINE = "com.nubiaphone.dialer.DECLINE"
        const val HANG_UP = "com.nubiaphone.dialer.HANG_UP"
        const val SILENCE = "com.nubiaphone.dialer.SILENCE"
        const val TOGGLE_MUTE = "com.nubiaphone.dialer.TOGGLE_MUTE"
        const val TOGGLE_SPEAKER = "com.nubiaphone.dialer.TOGGLE_SPEAKER"
        const val PREVIEW_SILENCE = "com.nubiaphone.dialer.PREVIEW_SILENCE"
        const val PREVIEW_DECLINE = "com.nubiaphone.dialer.PREVIEW_DECLINE"
        const val PREVIEW_ANSWER = "com.nubiaphone.dialer.PREVIEW_ANSWER"
        const val PREVIEW_TOGGLE_MUTE = "com.nubiaphone.dialer.PREVIEW_TOGGLE_MUTE"
        const val PREVIEW_TOGGLE_SPEAKER = "com.nubiaphone.dialer.PREVIEW_TOGGLE_SPEAKER"
    }
}
