package com.nubiaphone.dialer.telecom

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import com.nubiaphone.dialer.data.CallLogStore
import com.nubiaphone.dialer.data.formatPhone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CallPhase { Incoming, Dialing, SelectAccount, Active, Holding, Ended }

/** What the in-call screen and notifications need from a Telecom [Call]. */
data class CallUi(
    val id: Int,
    val phase: CallPhase,
    val number: String,
    val hidden: Boolean,
    val name: String?,
    val photoUri: String?,
    /** "Mobil", the number's place, or the formatted number under a contact name. */
    val detail: String?,
    /** Wall-clock time the call connected, 0 before that. */
    val connectTimeMillis: Long,
    val canHold: Boolean,
    val endedReason: String? = null,
    /** Two calls can be joined (Telecom "merge" or a conferenceable call is available). */
    val canMerge: Boolean = false,
    /** This is a conference; [participants] are its legs. */
    val isConference: Boolean = false,
    val participants: List<Participant> = emptyList(),
) {
    val title: String
        get() =
            when {
                isConference -> "Konferans araması"
                else -> name ?: if (hidden) "Gizli numara" else formatPhone(number)
            }
}

/** One leg of a conference call. */
data class Participant(
    val id: Int,
    val title: String,
    val photoUri: String?,
    /** "Özel": take this person out to a private call (the rest go on hold). */
    val canSplit: Boolean,
    /** "Çıkar": hang up just this person. */
    val canHangUp: Boolean,
)

data class AudioUi(
    val muted: Boolean = false,
    val route: Int = CallAudioState.ROUTE_EARPIECE,
    val supportedRoutes: Int = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER,
)

/**
 * Process-wide list of live calls, fed by [CallService] (Telecom binds it while this app is
 * the default phone app). UI and notifications read [calls]/[audio] and act through the
 * functions here, never on [Call] objects directly.
 */
object CallRegistry {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val live = LinkedHashMap<Int, Call>()
    private val ids = HashMap<Call, Int>()
    private val contacts = HashMap<Int, Triple<String?, String?, String?>>()
    private val ended = HashMap<Int, CallUi>()
    private var nextId = 1

    private val _calls = MutableStateFlow<List<CallUi>>(emptyList())
    val calls: StateFlow<List<CallUi>> = _calls.asStateFlow()

    private val _audio = MutableStateFlow(AudioUi())
    val audio: StateFlow<AudioUi> = _audio.asStateFlow()

    @Volatile internal var service: CallService? = null

    /** Called whenever the list changes, so [CallNotifier] can follow. */
    internal var onChanged: ((Context, List<CallUi>) -> Unit)? = null
    private var appContext: Context? = null

    private val callback =
        object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = publish()

            override fun onDetailsChanged(call: Call, details: Call.Details) = publish()

            override fun onChildrenChanged(call: Call, children: MutableList<Call>) = publish()

            override fun onParentChanged(call: Call, parent: Call?) = publish()

            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = publish()
        }

    fun add(context: Context, call: Call): Int {
        appContext = context.applicationContext
        val id = nextId++
        live[id] = call
        ids[call] = id
        call.registerCallback(callback, main)
        publish()
        lookupContact(context.applicationContext, id, call)
        return id
    }

    fun remove(call: Call) {
        val id = ids.remove(call) ?: return
        call.unregisterCallback(callback)
        live.remove(id)
        // Keep "Arama sona erdi" on screen briefly before the call disappears.
        snapshot(id, call)?.let { ended[id] = it.copy(phase = CallPhase.Ended) }
        publish()
        main.postDelayed({
            ended.remove(id)
            contacts.remove(id)
            publish()
        }, 1500)
    }

    internal fun updateAudio(state: CallAudioState?) {
        state ?: return
        _audio.value = AudioUi(state.isMuted, state.route, state.supportedRouteMask)
        // Refresh the ongoing notification actions (Sessiz / Hoparlör labels).
        appContext?.let { ctx -> onChanged?.invoke(ctx, _calls.value) }
    }

    fun answer(id: Int) = live[id]?.answer(VideoProfile.STATE_AUDIO_ONLY)

    /**
     * Decline ("Meşgul"). With [message], Telecom sends it as an SMS through the default SMS
     * app's respond-via-message service (nubiaSMS provides one); without, nothing is sent.
     */
    fun reject(id: Int, message: String? = null) = live[id]?.reject(message != null, message)

    fun hangUp(id: Int) {
        val call = live[id] ?: return
        if (phaseOf(call) == CallPhase.Incoming) call.reject(false, null) else call.disconnect()
    }

    fun hold(id: Int, on: Boolean) {
        val call = live[id] ?: return
        if (on) call.hold() else call.unhold()
    }

    /** Two calls: put the active one on hold and resume the other. */
    fun swap() {
        val holding = live.values.firstOrNull { phaseOf(it) == CallPhase.Holding } ?: return
        holding.unhold()
    }

    /**
     * "Birleştir": join the two calls. Carriers that merge on their side expose
     * CAPABILITY_MERGE_CONFERENCE; otherwise the held call is offered as conferenceable.
     */
    fun merge(id: Int) {
        val call = live[id] ?: return
        if (call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
            call.mergeConference()
        } else {
            call.conferenceableCalls.firstOrNull()?.let { call.conference(it) }
        }
    }

    /** "Özel": talk to one participant privately; the others are held. */
    fun split(participantId: Int) = live[participantId]?.splitFromConference()

    /** "Çıkar": hang up one participant, the conference continues. */
    fun hangUpParticipant(participantId: Int) = live[participantId]?.disconnect()

    fun dtmf(id: Int, c: Char) {
        val call = live[id] ?: return
        call.playDtmfTone(c)
        main.postDelayed({ call.stopDtmfTone() }, 180)
    }

    fun selectAccount(id: Int, handle: PhoneAccountHandle) = live[id]?.phoneAccountSelected(handle, false)

    fun setMuted(muted: Boolean) = service?.setMuted(muted)

    fun setRoute(route: Int) = service?.setAudioRoute(route)

    /** Volume key while ringing. Only allowed for the default phone app. */
    fun silenceRinger(context: Context) {
        runCatching { context.getSystemService(TelecomManager::class.java)?.silenceRinger() }
    }

    private fun publish() {
        val list = ArrayList<CallUi>()
        // Conference legs are shown inside their conference, not as calls of their own.
        live.forEach { (id, call) -> if (call.parent == null) snapshot(id, call)?.let { list += it } }
        list += ended.values
        _calls.value = list
        appContext?.let { ctx -> onChanged?.invoke(ctx, list) }
    }

    private fun snapshot(id: Int, call: Call): CallUi? {
        val d = call.details ?: return null
        val number = d.handle?.schemeSpecificPart.orEmpty()
        val hidden = d.handlePresentation != TelecomManager.PRESENTATION_ALLOWED || number.isBlank()
        val (name, photo, detail) = contacts[id] ?: Triple(null, null, null)
        return CallUi(
            id = id,
            phase = phaseOf(call),
            number = number,
            hidden = hidden,
            name = name ?: d.callerDisplayName?.takeIf { it.isNotBlank() },
            photoUri = photo,
            detail = detail,
            connectTimeMillis = d.connectTimeMillis.coerceAtLeast(0),
            canHold = d.can(Call.Details.CAPABILITY_HOLD) || d.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            endedReason = d.disconnectCause?.label?.toString()?.takeIf { it.isNotBlank() },
            canMerge = d.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) || call.conferenceableCalls.isNotEmpty(),
            isConference = d.hasProperty(Call.Details.PROPERTY_CONFERENCE),
            participants = call.children.mapNotNull { child -> participant(child) },
        )
    }

    private fun participant(child: Call): Participant? {
        val id = ids[child] ?: return null
        val d = child.details ?: return null
        val number = d.handle?.schemeSpecificPart.orEmpty()
        val (name, photo, _) = contacts[id] ?: Triple(null, null, null)
        return Participant(
            id = id,
            title = name ?: d.callerDisplayName?.takeIf { it.isNotBlank() } ?: if (number.isBlank()) "Gizli numara" else formatPhone(number),
            photoUri = photo,
            canSplit = d.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE),
            canHangUp = d.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE),
        )
    }

    private fun lookupContact(context: Context, id: Int, call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: return
        scope.launch {
            val info = withContext(Dispatchers.IO) { CallLogStore.contactFor(context, number) }
            contacts[id] =
                if (info != null) {
                    Triple(info.name, info.photoUri, listOfNotNull(info.numberLabel, formatPhone(number)).joinToString(" · "))
                } else {
                    Triple(null, null, null)
                }
            if (live.containsKey(id)) publish()
        }
    }

    @Suppress("DEPRECATION")
    internal fun phaseOf(call: Call): CallPhase {
        val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state else call.state
        return when (state) {
            Call.STATE_RINGING, Call.STATE_NEW -> CallPhase.Incoming
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL -> CallPhase.Dialing
            Call.STATE_SELECT_PHONE_ACCOUNT -> CallPhase.SelectAccount
            Call.STATE_ACTIVE -> CallPhase.Active
            Call.STATE_HOLDING -> CallPhase.Holding
            else -> CallPhase.Ended
        }
    }
}
