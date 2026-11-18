package com.nubiaphone.dialer.ui.incall

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nubiaphone.dialer.MainActivity
import com.nubiaphone.dialer.data.PhoneActions
import com.nubiaphone.dialer.telecom.AudioUi
import com.nubiaphone.dialer.telecom.CallPhase
import com.nubiaphone.dialer.telecom.CallRegistry
import com.nubiaphone.dialer.telecom.CallUi
import com.nubiaphone.dialer.telecom.Participant
import com.nubiaphone.dialer.ui.theme.NubiaPhoneTheme
import kotlinx.coroutines.delay

/**
 * Full-screen call UI. Shown over the lock screen and turns the screen on for a ringing call.
 * Live mode reads [CallRegistry]; preview mode ([EXTRA_PREVIEW]) draws fake calls so the
 * layout can be checked without a real call.
 */
class InCallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show over the keyguard even on OEM skins that ignore only one of these APIs.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )
        enableEdgeToEdge()
        handleAnswer(intent)
        val preview = intent.getStringExtra(EXTRA_PREVIEW)
        setContent {
            NubiaPhoneTheme {
                if (preview != null) Preview(preview) else Live()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAnswer(intent)
    }

    private fun handleAnswer(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) {
            val id = intent.getIntExtra(EXTRA_CALL_ID, -1)
            if (id >= 0) CallRegistry.answer(id)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val ringing = CallRegistry.calls.value.any { it.phase == CallPhase.Incoming }
        if (ringing && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            CallRegistry.silenceRinger(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    /** Screen off when held to the ear (earpiece route during a live call). */
    private fun setProximity(on: Boolean) {
        if (on) {
            if (proximity?.isHeld == true) return
            val pm = getSystemService(PowerManager::class.java) ?: return
            if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "nubiaphone:proximity").apply { acquire(4 * 60 * 60 * 1000L) }
        } else {
            proximity?.let { if (it.isHeld) it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
            proximity = null
        }
    }

    @Composable
    private fun Live() {
        val calls by CallRegistry.calls.collectAsState()
        val audio by CallRegistry.audio.collectAsState()
        val primary =
            calls.firstOrNull { it.phase == CallPhase.Incoming }
                ?: calls.firstOrNull { it.phase == CallPhase.Active || it.phase == CallPhase.Dialing || it.phase == CallPhase.SelectAccount }
                ?: calls.firstOrNull { it.phase == CallPhase.Holding }
                ?: calls.firstOrNull()
        val other = calls.firstOrNull { it.id != primary?.id && it.phase != CallPhase.Ended }

        LaunchedEffect(calls.isEmpty()) {
            if (calls.isEmpty()) {
                delay(300)
                if (CallRegistry.calls.value.isEmpty()) finish()
            }
        }
        val earpiece =
            primary != null &&
                (primary.phase == CallPhase.Active || primary.phase == CallPhase.Dialing) &&
                audio.route == CallAudioState.ROUTE_EARPIECE
        DisposableEffect(earpiece) {
            setProximity(earpiece)
            onDispose { }
        }
        val handles = remember(primary?.phase) { if (primary?.phase == CallPhase.SelectAccount) phoneAccounts() else emptyList() }
        primary ?: return
        InCallScreen(
            primary = primary,
            other = other,
            audio = audio,
            accounts = handles.map { it.second },
            actions =
                InCallActions(
                    answer = CallRegistry::answer,
                    decline = { CallRegistry.reject(it) },
                    quickReply = { call, text -> CallRegistry.reject(call.id, text) },
                    silence = { CallRegistry.silenceRinger(this) },
                    replyWithMessage = { call ->
                        CallRegistry.reject(call.id)
                        afterUnlock { PhoneActions.message(this, call.number) }
                    },
                    hangUp = CallRegistry::hangUp,
                    setMuted = { CallRegistry.setMuted(it) },
                    setRoute = { CallRegistry.setRoute(it) },
                    hold = CallRegistry::hold,
                    swap = CallRegistry::swap,
                    addCall = { number ->
                        // Hold the current call first (Telecom would too), then dial the new one.
                        primary.takeIf { it.phase == CallPhase.Active && it.canHold }?.let { CallRegistry.hold(it.id, true) }
                        PhoneActions.call(this, number)
                    },
                    dtmf = CallRegistry::dtmf,
                    message = { call -> afterUnlock { PhoneActions.message(this, call.number) } },
                    selectAccount = { id, index -> handles.getOrNull(index)?.let { CallRegistry.selectAccount(id, it.first) } },
                    merge = CallRegistry::merge,
                    split = { CallRegistry.split(it) },
                    hangUpParticipant = { CallRegistry.hangUpParticipant(it) },
                ),
        )
    }

    /** Runs [action] once the keyguard is gone (asks for unlock when the phone is locked). */
    private fun afterUnlock(action: () -> Unit) {
        val km = getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isKeyguardLocked || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            action()
            return
        }
        km.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = action()
            },
        )
    }

    @Suppress("MissingPermission")
    private fun phoneAccounts(): List<Pair<PhoneAccountHandle, String>> {
        val tm = getSystemService(TelecomManager::class.java) ?: return emptyList()
        return runCatching {
            tm.callCapablePhoneAccounts.map { h -> h to (tm.getPhoneAccount(h)?.label?.toString() ?: "SIM") }
        }.getOrDefault(emptyList())
    }

    /**
     * Fake calls for checking the screens without a real call. Kinds: "incoming", "active",
     * "dialing", "two", "conference". Nothing is dialled or sent; the add-call modal, merge,
     * swap, Özel and Çıkar all play out on these fakes.
     */
    @Composable
    private fun Preview(kind: String) {
        data class Fake(val id: Int, val name: String, val number: String, val phase: CallPhase, val connect: Long)
        val now = System.currentTimeMillis()
        val first = Fake(1, "Önizleme Kişi", "+905321234567", CallPhase.Active, now - 83_000)
        var fakes by remember {
            mutableStateOf(
                when (kind) {
                    "incoming" -> listOf(first.copy(phase = CallPhase.Incoming, connect = 0))
                    "dialing" -> listOf(first.copy(phase = CallPhase.Dialing, connect = 0))
                    "two" -> listOf(first, Fake(2, "İkinci Kişi", "+905339876543", CallPhase.Holding, now - 40_000))
                    "conference" ->
                        listOf(
                            first,
                            Fake(2, "İkinci Kişi", "+905339876543", CallPhase.Active, now - 40_000),
                            Fake(3, "Üçüncü Kişi", "+905441112233", CallPhase.Active, now - 20_000),
                        )
                    else -> listOf(first)
                },
            )
        }
        var conf by remember { mutableStateOf(if (kind == "conference") setOf(1, 2, 3) else emptySet<Int>()) }
        var muted by remember { mutableStateOf(false) }
        var route by remember { mutableStateOf(CallAudioState.ROUTE_EARPIECE) }
        var nextId by remember { mutableStateOf(10) }
        var ringOn by remember { mutableStateOf(kind == "incoming") }

        // Preview incoming: default ringtone + vibrate until silence / answer / decline.
        DisposableEffect(ringOn) {
            if (!ringOn) return@DisposableEffect onDispose { }
            val tone =
                runCatching {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    RingtoneManager.getRingtone(this@InCallActivity, uri)?.also { r ->
                        r.audioAttributes =
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) r.isLooping = true
                        r.play()
                    }
                }.getOrNull()
            val vibrator = startPreviewVibrate()
            onDispose {
                runCatching { tone?.stop() }
                vibrator?.cancel()
            }
        }

        // A fake outgoing call connects after ~2 s.
        LaunchedEffect(fakes.any { it.phase == CallPhase.Dialing }) {
            if (fakes.any { it.phase == CallPhase.Dialing } && kind != "dialing") {
                delay(2000)
                fakes = fakes.map { if (it.phase == CallPhase.Dialing) it.copy(phase = CallPhase.Active, connect = System.currentTimeMillis()) else it }
            }
        }
        LaunchedEffect(fakes.isEmpty()) { if (fakes.isEmpty()) finish() }

        fun ui(f: Fake) =
            CallUi(
                id = f.id, phase = f.phase, number = f.number, hidden = false, name = f.name, photoUri = null,
                detail = "Mobil · ${com.nubiaphone.dialer.data.formatPhone(f.number)}",
                connectTimeMillis = f.connect, canHold = true, canMerge = true,
            )
        val inConf = fakes.filter { it.id in conf }
        val loose = fakes.filter { it.id !in conf }
        val confUi =
            if (inConf.size >= 2) {
                CallUi(
                    id = CONF_ID, phase = if (inConf.all { it.phase == CallPhase.Holding }) CallPhase.Holding else CallPhase.Active,
                    number = "", hidden = false, name = null, photoUri = null, detail = null,
                    connectTimeMillis = inConf.minOf { it.connect }, canHold = true, canMerge = loose.isNotEmpty(),
                    isConference = true,
                    participants = inConf.map { Participant(it.id, it.name, null, canSplit = true, canHangUp = true) },
                )
            } else {
                null
            }
        val tops = listOfNotNull(confUi) + loose.map(::ui)
        val primary =
            tops.firstOrNull { it.phase == CallPhase.Incoming }
                ?: tops.firstOrNull { it.phase == CallPhase.Active || it.phase == CallPhase.Dialing }
                ?: tops.firstOrNull() ?: return
        val other = tops.firstOrNull { it.id != primary.id }

        fun setPhase(id: Int, phase: CallPhase) {
            fakes = fakes.map { if (it.id == id || (id == CONF_ID && it.id in conf)) it.copy(phase = phase) else it }
        }
        InCallScreen(
            primary = primary,
            other = other,
            audio = AudioUi(muted, route, CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER),
            accounts = emptyList(),
            actions =
                InCallActions(
                    answer = {
                        ringOn = false
                        setPhase(it, CallPhase.Active)
                        fakes = fakes.map { f -> if (f.id == it) f.copy(connect = System.currentTimeMillis()) else f }
                    },
                    decline = {
                        ringOn = false
                        finish()
                    },
                    replyWithMessage = {
                        ringOn = false
                        finish()
                    },
                    quickReply = { _, _ ->
                        ringOn = false
                        finish()
                    },
                    silence = { ringOn = false },
                    hangUp = { id ->
                        ringOn = false
                        // Ending the current call leaves the held one on screen ("Devam" to resume).
                        fakes = if (id == CONF_ID) fakes.filterNot { it.id in conf } else fakes.filterNot { it.id == id }
                        if (id == CONF_ID) conf = emptySet()
                    },
                    setMuted = { muted = it },
                    setRoute = { route = it },
                    hold = { id, on -> setPhase(id, if (on) CallPhase.Holding else CallPhase.Active) },
                    swap = {
                        val activeIds = tops.filter { it.phase == CallPhase.Active }.map { it.id }
                        val heldIds = tops.filter { it.phase == CallPhase.Holding }.map { it.id }
                        activeIds.forEach { setPhase(it, CallPhase.Holding) }
                        heldIds.forEach { setPhase(it, CallPhase.Active) }
                    },
                    addCall = { number ->
                        tops.filter { it.phase == CallPhase.Active }.forEach { setPhase(it.id, CallPhase.Holding) }
                        val name = com.nubiaphone.dialer.data.ContactsStore.contacts.value
                            ?.firstOrNull { c -> c.numbers.any { com.nubiaphone.dialer.data.numberKey(it) == com.nubiaphone.dialer.data.numberKey(number) } }?.name
                        fakes = fakes + Fake(nextId, name ?: com.nubiaphone.dialer.data.formatPhone(number), number, CallPhase.Dialing, 0)
                        nextId++
                    },
                    dtmf = { _, _ -> },
                    message = {},
                    selectAccount = { _, _ -> },
                    merge = {
                        conf = fakes.map { it.id }.toSet()
                        fakes = fakes.map { it.copy(phase = CallPhase.Active) }
                    },
                    split = { id ->
                        // Private with one person; the rest of the conference waits.
                        fakes = fakes.map { if (it.id == id) it.copy(phase = CallPhase.Active) else if (it.id in conf) it.copy(phase = CallPhase.Holding) else it }
                        conf = conf - id
                        if (conf.size < 2) conf = emptySet()
                    },
                    hangUpParticipant = { id ->
                        fakes = fakes.filterNot { it.id == id }
                        conf = conf - id
                        if (conf.size < 2) conf = emptySet()
                    },
                ),
        )
    }

    /** Ringtone-style vibrate pattern for the incoming preview. */
    private fun startPreviewVibrate(): Vibrator? {
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            } ?: return null
        if (!vibrator.hasVibrator()) return null
        val pattern = longArrayOf(0, 1000, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
        return vibrator
    }

    companion object {
        const val ACTION_ANSWER = "com.nubiaphone.dialer.ANSWER"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_PREVIEW = "preview"
        private const val CONF_ID = -1

        fun intent(context: Context): Intent =
            Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
