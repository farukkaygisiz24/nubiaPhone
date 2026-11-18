package com.nubiaphone.dialer.telecom

import android.content.BroadcastReceiver
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.nubiaphone.dialer.ui.incall.InCallActivity
import com.nubiaphone.dialer.ui.incall.IncomingHeadsUpActivity

/**
 * Telecom binds this while nubiaPhone is the default phone app and a call exists. Ringing
 * stays with Telecom (no IN_CALL_SERVICE_RINGING in the manifest), so a crash here can never
 * leave the phone silent.
 *
 * Incoming: full UI when locked/screen-off; notification only while unlocked.
 * Volume keys while ringing silence the ringer even when only the notification is showing.
 */
class CallService : InCallService() {
    private var volumeReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        CallRegistry.service = this
        CallNotifier.attach(this)
        volumeReceiver =
            VolumeSilence.register(this) {
                if (CallRegistry.calls.value.any { it.phase == CallPhase.Incoming }) {
                    CallNotifier.silence(this)
                }
            }
    }

    override fun onDestroy() {
        VolumeSilence.unregister(this, volumeReceiver)
        volumeReceiver = null
        if (CallRegistry.service === this) CallRegistry.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallRegistry.add(this, call)
        val phase = CallRegistry.phaseOf(call)
        when {
            phase == CallPhase.Dialing || phase == CallPhase.SelectAccount ->
                startActivity(InCallActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            phase == CallPhase.Incoming && CallNotifier.shouldShowIncomingUi(this) ->
                startActivity(InCallActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            phase == CallPhase.Incoming -> {
                // Unlocked: shade notification + swipe-up banner (HUN swipe-up alone won't silence).
                val ui = CallRegistry.calls.value.firstOrNull { it.phase == CallPhase.Incoming }
                if (ui != null) {
                    startActivity(
                        IncomingHeadsUpActivity.intentLive(
                            this,
                            title = ui.title,
                            subtitle = "Gelen arama · yukarı kaydır: sessiz",
                            photo = ui.photoUri,
                            callId = ui.id,
                        ),
                    )
                }
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        CallRegistry.remove(call)
    }

    @Deprecated("Still delivered on all supported versions")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallRegistry.updateAudio(audioState)
    }
}
