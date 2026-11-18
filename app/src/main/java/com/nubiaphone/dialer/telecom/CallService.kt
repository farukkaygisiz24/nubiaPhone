package com.nubiaphone.dialer.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.nubiaphone.dialer.ui.incall.InCallActivity

/**
 * Telecom binds this while nubiaPhone is the default phone app and a call exists. Ringing
 * stays with Telecom (no IN_CALL_SERVICE_RINGING in the manifest), so a crash here can never
 * leave the phone silent.
 *
 * Incoming calls are shown through the full-screen notification ([CallNotifier]); outgoing
 * calls open the in-call screen directly (a system-bound InCallService may start activities).
 */
class CallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        CallRegistry.service = this
        CallNotifier.attach(this)
    }

    override fun onDestroy() {
        if (CallRegistry.service === this) CallRegistry.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallRegistry.add(this, call)
        val phase = CallRegistry.phaseOf(call)
        val showNow =
            phase == CallPhase.Dialing ||
                phase == CallPhase.SelectAccount ||
                // Incoming normally comes up through the full-screen notification; without it
                // (notifications or full-screen intents not allowed) open the screen directly.
                (phase == CallPhase.Incoming && !CallNotifier.canAlert(this))
        if (showNow) startActivity(InCallActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onCallRemoved(call: Call) {
        CallRegistry.remove(call)
    }

    @Deprecated("Still delivered on all supported versions")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallRegistry.updateAudio(audioState)
    }
}
