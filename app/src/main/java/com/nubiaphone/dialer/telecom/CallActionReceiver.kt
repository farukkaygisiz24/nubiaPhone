package com.nubiaphone.dialer.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nubiaphone.dialer.ui.incall.InCallActivity

/** "Reddet" and "Kapat" from the call notification. Not exported. "Cevapla" opens the screen directly. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(InCallActivity.EXTRA_CALL_ID, -1)
        if (id < 0) return
        when (intent.action) {
            DECLINE -> CallRegistry.reject(id)
            HANG_UP -> CallRegistry.hangUp(id)
        }
    }

    companion object {
        const val DECLINE = "com.nubiaphone.dialer.DECLINE"
        const val HANG_UP = "com.nubiaphone.dialer.HANG_UP"
    }
}
