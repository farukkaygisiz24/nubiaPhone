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
            DECLINE -> {
                val id = intent.getIntExtra(InCallActivity.EXTRA_CALL_ID, -1)
                if (id >= 0) CallRegistry.reject(id)
            }
            HANG_UP -> {
                val id = intent.getIntExtra(InCallActivity.EXTRA_CALL_ID, -1)
                if (id >= 0) CallRegistry.hangUp(id)
            }
        }
    }

    companion object {
        const val DECLINE = "com.nubiaphone.dialer.DECLINE"
        const val HANG_UP = "com.nubiaphone.dialer.HANG_UP"
        const val SILENCE = "com.nubiaphone.dialer.SILENCE"
        const val PREVIEW_SILENCE = "com.nubiaphone.dialer.PREVIEW_SILENCE"
        const val PREVIEW_DECLINE = "com.nubiaphone.dialer.PREVIEW_DECLINE"
    }
}
