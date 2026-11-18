package com.nubiaphone.dialer.ui.incall

import android.telecom.CallAudioState
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.SwapCalls
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubiaphone.dialer.telecom.AudioUi
import com.nubiaphone.dialer.telecom.CallPhase
import com.nubiaphone.dialer.telecom.CallUi
import com.nubiaphone.dialer.telecom.Participant
import com.nubiaphone.dialer.ui.components.ContactAvatar
import com.nubiaphone.dialer.ui.components.MyOsCard
import com.nubiaphone.dialer.ui.components.MyOsMenuItem
import com.nubiaphone.dialer.ui.components.MyOsPopupMenu
import com.nubiaphone.dialer.ui.components.myOsAccentFill
import com.nubiaphone.dialer.ui.components.myOsDanger
import kotlinx.coroutines.delay

/** What the in-call screen can ask for. Implemented by [InCallActivity] (live) and its preview. */
class InCallActions(
    val answer: (Int) -> Unit,
    /** "Meşgul": decline; the caller hears busy / goes to voicemail. */
    val decline: (Int) -> Unit,
    /** "Özel mesaj yaz…": decline and open the SMS app with the number. */
    val replyWithMessage: (CallUi) -> Unit,
    /** A preset quick reply: decline and send [String] as SMS (system "respond via message"). */
    val quickReply: (CallUi, String) -> Unit,
    /** Stop the ringtone, keep the call ringing on screen. */
    val silence: () -> Unit,
    val hangUp: (Int) -> Unit,
    val setMuted: (Boolean) -> Unit,
    val setRoute: (Int) -> Unit,
    val hold: (Int, Boolean) -> Unit,
    val swap: () -> Unit,
    /** "Arama ekle": hold the current call, then call [String] (picked in the modal). */
    val addCall: (String) -> Unit,
    val dtmf: (Int, Char) -> Unit,
    val message: (CallUi) -> Unit,
    val selectAccount: (Int, Int) -> Unit,
    /** "Birleştir": join the active and the held call into a conference. */
    val merge: (Int) -> Unit = {},
    /** Conference: talk privately with one participant. */
    val split: (Int) -> Unit = {},
    /** Conference: hang up one participant. */
    val hangUpParticipant: (Int) -> Unit = {},
)

private val ButtonSize = 64.dp

/*
 * In-call screen. MyOS has no measurable call UI on the Global ROM (TASARIM.md), so it is
 * built from the measured parts: page colour, 64dp round buttons (mfvc_floating_button_size),
 * card-coloured idle buttons and accent-filled active ones (Saat FAB tone), danger red for
 * ending, 12sp labels, Medium never Bold.
 */
@Composable
fun InCallScreen(
    primary: CallUi,
    other: CallUi?,
    audio: AudioUi,
    accounts: List<String>,
    actions: InCallActions,
) {
    var keypad by rememberSaveable { mutableStateOf(false) }
    var dtmf by rememberSaveable(primary.id) { mutableStateOf("") }
    var replies by rememberSaveable(primary.id) { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var silenced by rememberSaveable(primary.id) { mutableStateOf(false) }
    val density = LocalDensity.current
    val silenceThresholdPx = with(density) { 56.dp.toPx() }

    fun silenceNow() {
        if (silenced) return
        silenced = true
        actions.silence()
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            // Incoming: swipe up anywhere (except the answer slider) to mute the ringtone.
            .then(
                if (primary.phase == CallPhase.Incoming && !silenced) {
                    Modifier.pointerInput(primary.id) {
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragged = 0f },
                            onVerticalDrag = { change, amount ->
                                dragged += amount
                                if (dragged <= -silenceThresholdPx) {
                                    change.consume()
                                    silenceNow()
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (other != null && other.phase != CallPhase.Ended) {
            Spacer(Modifier.height(8.dp))
            OtherCallStrip(other, onSwap = actions.swap)
        }
        // Conference: the people are managed right here at the top (Özel / Çıkar).
        if (primary.isConference && primary.participants.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ConferenceCard(primary.participants, onSplit = actions.split, onHangUp = actions.hangUpParticipant)
        }
        Spacer(Modifier.height(if (keypad || primary.isConference) 24.dp else 56.dp))
        if (!keypad) {
            if (primary.isConference) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Groups, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(52.dp))
                }
            } else {
                ContactAvatar(photoUri = primary.photoUri, name = primary.name, size = 96.dp)
            }
            Spacer(Modifier.height(20.dp))
        }
        Text(
            primary.title,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        (if (primary.isConference) primary.participants.joinToString(", ") { it.title }.ifBlank { null } else primary.detail)?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        StatusLine(primary)
        if (dtmf.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(dtmf.takeLast(20), fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
        }

        Spacer(Modifier.weight(1f))

        when (primary.phase) {
            CallPhase.Incoming ->
                IncomingControls(
                    call = primary,
                    actions = actions,
                    silenced = silenced,
                    onSilence = { silenceNow() },
                    onReplies = { replies = true },
                )
            CallPhase.SelectAccount -> AccountChooser(accounts) { actions.selectAccount(primary.id, it) }
            CallPhase.Ended -> Spacer(Modifier.height(ButtonSize + 48.dp))
            else -> {
                if (keypad) {
                    InCallKeypad { c ->
                        dtmf += c
                        actions.dtmf(primary.id, c)
                    }
                } else {
                    ControlGrid(primary, other, audio, actions, onKeypad = { keypad = true }, onAdd = { adding = true })
                }
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (keypad) {
                            Text(
                                "Gizle",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { keypad = false }.padding(12.dp),
                            )
                        }
                    }
                    RoundButton(Icons.Filled.CallEnd, "Kapat", fill = myOsDanger(), tint = Color.White) { actions.hangUp(primary.id) }
                    Box(Modifier.weight(1f))
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
    AddCallSheet(
        visible = adding,
        onDismiss = { adding = false },
        onPick = { number ->
            adding = false
            actions.addCall(number)
        },
    )
    if (primary.phase == CallPhase.Incoming) {
        QuickReplies(
            visible = replies,
            onDismiss = { replies = false },
            onPick = { text ->
                replies = false
                actions.quickReply(primary, text)
            },
            onCustom = {
                replies = false
                actions.replyWithMessage(primary)
            },
        )
    }
    }
}

@Composable
private fun StatusLine(call: CallUi) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.connectTimeMillis, call.phase) {
        while (call.phase == CallPhase.Active && call.connectTimeMillis > 0) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val text =
        when (call.phase) {
            CallPhase.Incoming -> "Gelen arama"
            CallPhase.Dialing -> "Aranıyor…"
            CallPhase.SelectAccount -> "SIM seçin"
            CallPhase.Holding -> "Beklemede"
            CallPhase.Ended -> call.endedReason ?: "Arama sona erdi"
            CallPhase.Active -> if (call.connectTimeMillis > 0) elapsed(now - call.connectTimeMillis) else "Bağlandı"
        }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (call.phase == CallPhase.Active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun elapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun OtherCallStrip(other: CallUi, onSwap: () -> Unit) {
    MyOsCard {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(other.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(
                    if (other.phase == CallPhase.Holding) "Beklemede" else "Süren arama",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSwap).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.SwapCalls, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Değiştir", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Ringing: small round actions (Mesaj · Sessiz · Meşgul) above an iOS-style slide-to-answer.
 * "Meşgul" declines (busy / voicemail); "Mesaj" opens the quick replies.
 */
@Composable
private fun IncomingControls(
    call: CallUi,
    actions: InCallActions,
    silenced: Boolean,
    onSilence: () -> Unit,
    onReplies: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().widthIn(max = 420.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Control("Mesaj", Icons.AutoMirrored.Outlined.Message, on = false, enabled = !call.hidden, onClick = onReplies)
        Control(if (silenced) "Sessiz" else "Sessize al", Icons.AutoMirrored.Outlined.VolumeOff, on = silenced, onClick = onSilence)
        Labeled("Meşgul", Modifier.width(88.dp)) {
            RoundButton(Icons.Filled.CallEnd, "Meşgul", fill = myOsDanger(), tint = Color.White) { actions.decline(call.id) }
        }
    }
    Spacer(Modifier.height(36.dp))
    SlideToAnswer(onAnswer = { actions.answer(call.id) }, modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 8.dp))
    Spacer(Modifier.height(48.dp))
}

private val QuickReplyTexts =
    listOf(
        "Şu an konuşamıyorum.",
        "Seni birazdan arayacağım.",
        "Yoldayım, sonra ararım.",
        "Toplantıdayım, mesaj atar mısın?",
    )

/** Bottom panel like the keypad panel: 24dp top corners, dimmed page behind. */
@Composable
private fun BoxScope.QuickReplies(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCustom: () -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(300)) { it },
        exit = slideOutVertically(tween(250)) { it },
    ) {
        val dark = isSystemInDarkTheme()
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(if (dark) Color(0xFF232323) else Color.White)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
        ) {
            Text(
                "Mesajla yanıtla",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
            Text(
                "Arama reddedilir, seçtiğiniz mesaj SMS olarak gönderilir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
            QuickReplyTexts.forEach { text ->
                ReplyRow(text) { onPick(text) }
            }
            ReplyRow("Özel mesaj yaz…", accent = true, onClick = onCustom)
        }
    }
}

/** Conference participants at the top of the call screen: avatar, name, "Özel", red "Çıkar". */
@Composable
private fun ConferenceCard(
    participants: List<Participant>,
    onSplit: (Int) -> Unit,
    onHangUp: (Int) -> Unit,
) {
    MyOsCard {
        participants.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(photoUri = p.photoUri, name = p.title)
                Spacer(Modifier.width(12.dp))
                Text(
                    p.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (p.canSplit) {
                    Text(
                        "Özel",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSplit(p.id) }.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
                if (p.canHangUp) {
                    Text(
                        "Çıkar",
                        color = myOsDanger(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onHangUp(p.id) }.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
            }
            if (i != participants.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 48.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
            }
        }
    }
}

@Composable
private fun ReplyRow(
    text: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (accent) FontWeight.Medium else FontWeight.Normal,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .heightIn(min = 52.dp)
                .padding(horizontal = 8.dp, vertical = 15.dp),
    )
}

@Composable
private fun AccountChooser(accounts: List<String>, onPick: (Int) -> Unit) {
    MyOsCard(Modifier.widthIn(max = 420.dp)) {
        accounts.forEachIndexed { i, label ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onPick(i) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.SimCard, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    Spacer(Modifier.height(56.dp))
}

@Composable
private fun ControlGrid(
    call: CallUi,
    other: CallUi?,
    audio: AudioUi,
    actions: InCallActions,
    onKeypad: () -> Unit,
    onAdd: () -> Unit,
) {
    val bluetooth = audio.supportedRoutes and CallAudioState.ROUTE_BLUETOOTH != 0
    var routeMenu by remember { mutableStateOf(false) }
    val routeLabel =
        when (audio.route) {
            CallAudioState.ROUTE_BLUETOOTH -> "Bluetooth"
            CallAudioState.ROUTE_WIRED_HEADSET -> "Kulaklık"
            else -> "Hoparlör"
        }
    val active = call.phase == CallPhase.Active || call.phase == CallPhase.Holding
    Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Control("Sessiz", Icons.Outlined.MicOff, on = audio.muted) { actions.setMuted(!audio.muted) }
            Control("Tuş takımı", Icons.Outlined.Dialpad, on = false, onClick = onKeypad)
            Box {
                Control(
                    routeLabel,
                    if (audio.route == CallAudioState.ROUTE_BLUETOOTH) Icons.Outlined.Bluetooth else Icons.AutoMirrored.Outlined.VolumeUp,
                    on = audio.route == CallAudioState.ROUTE_SPEAKER || audio.route == CallAudioState.ROUTE_BLUETOOTH,
                ) {
                    if (bluetooth) {
                        routeMenu = true
                    } else {
                        actions.setRoute(
                            if (audio.route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER,
                        )
                    }
                }
                MyOsPopupMenu(
                    expanded = routeMenu,
                    onDismiss = { routeMenu = false },
                    items =
                        buildList {
                            add(MyOsMenuItem("Telefon") { actions.setRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE) })
                            add(MyOsMenuItem("Hoparlör") { actions.setRoute(CallAudioState.ROUTE_SPEAKER) })
                            add(MyOsMenuItem("Bluetooth") { actions.setRoute(CallAudioState.ROUTE_BLUETOOTH) })
                        },
                    dark = isSystemInDarkTheme(),
                    alignEnd = true,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Control(
                if (call.phase == CallPhase.Holding) "Devam" else "Beklet",
                Icons.Outlined.Pause,
                on = call.phase == CallPhase.Holding,
                enabled = active && call.canHold && other == null,
            ) { actions.hold(call.id, call.phase != CallPhase.Holding) }
            if (other != null && other.phase != CallPhase.Ended) {
                // Second call up: "Arama ekle" becomes "Birleştir".
                Control("Birleştir", Icons.AutoMirrored.Outlined.CallMerge, on = false, enabled = active && call.canMerge) { actions.merge(call.id) }
            } else {
                // Only while connected: the current call is held first, then the new one dials.
                Control("Arama ekle", Icons.Outlined.Add, on = false, enabled = call.phase == CallPhase.Active, onClick = onAdd)
            }
            Control("Mesaj", Icons.AutoMirrored.Outlined.Message, on = false, enabled = !call.hidden && !call.isConference) { actions.message(call) }
        }
    }
}

@Composable
private fun Control(
    label: String,
    icon: ImageVector,
    on: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Labeled(label, Modifier.width(88.dp).alpha(if (enabled) 1f else 0.35f)) {
        Box(
            Modifier
                .size(ButtonSize)
                .clip(CircleShape)
                .background(if (on) myOsAccentFill() else MaterialTheme.colorScheme.surface)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (on) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun Labeled(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    fill: Color,
    tint: Color,
    glow: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(ButtonSize)
            .then(if (glow) Modifier.shadow(10.dp, CircleShape, ambientColor = fill, spotColor = fill) else Modifier)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(28.dp))
    }
}

private val DtmfKeys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#')

/** Compact DTMF pad shown in place of the control grid. */
@Composable
private fun InCallKeypad(onKey: (Char) -> Unit) {
    Column(Modifier.widthIn(max = 320.dp)) {
        DtmfKeys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { c ->
                    Box(
                        Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(16.dp)).clickable { onKey(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(c.toString(), fontSize = 30.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}
