package com.nubiaphone.dialer.ui.dialpad

import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.nubiaphone.dialer.data.CallLogStore
import com.nubiaphone.dialer.data.ContactsStore
import com.nubiaphone.dialer.data.formatPhone
import com.nubiaphone.dialer.data.mapDigitRange
import com.nubiaphone.dialer.ui.components.ContactAvatar
import com.nubiaphone.dialer.ui.components.MyOsFab
import com.nubiaphone.dialer.ui.components.MyOsMenuItem

private val Keys =
    listOf(
        '1' to "", '2' to "ABC", '3' to "DEF",
        '4' to "GHI", '5' to "JKL", '6' to "MNO",
        '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
        '*' to "", '0' to "+", '#' to "",
    )

private val Tones =
    mapOf(
        '0' to ToneGenerator.TONE_DTMF_0, '1' to ToneGenerator.TONE_DTMF_1, '2' to ToneGenerator.TONE_DTMF_2,
        '3' to ToneGenerator.TONE_DTMF_3, '4' to ToneGenerator.TONE_DTMF_4, '5' to ToneGenerator.TONE_DTMF_5,
        '6' to ToneGenerator.TONE_DTMF_6, '7' to ToneGenerator.TONE_DTMF_7, '8' to ToneGenerator.TONE_DTMF_8,
        '9' to ToneGenerator.TONE_DTMF_9, '*' to ToneGenerator.TONE_DTMF_S, '#' to ToneGenerator.TONE_DTMF_P,
    )

/*
 * Dial pad panel (TASARIM.md → "Telefon uygulaması bileşenleri"):
 *   panel   Saat bottom panel: 24dp top corners (mfvc_*Radius_dialog), #232323 at night / white by day
 *   keys    MyOS lock-screen PIN: 32dp digits, 66.5dp rows, 30dp side inset, ≤298dp wide
 *   letters not measurable on MyOS: 11sp secondary under the digit
 *   call    64dp accent circle (mfvc_floating_button_size), white icon, accent glow
 * T9 / number suggestions sit inside the same panel (not a floating card above it).
 */
private val PanelShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val PanelDark = Color(0xFF232323)
private val KeyRow = 66.5.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadPanel(
    initialNumber: String = "",
    onClose: () -> Unit,
    onCall: (String) -> Unit,
    onVoicemail: () -> Unit,
    onAddContact: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    var number by rememberSaveable(initialNumber) { mutableStateOf(initialNumber) }
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val view = LocalView.current
    val tones = rememberDtmf(context)
    val contacts by ContactsStore.contacts.collectAsState()
    val days by CallLogStore.days.collectAsState()
    val matches = remember(number, contacts, days) { matchDialed(number, contacts.orEmpty(), days.orEmpty()) }

    fun press(c: Char) {
        number += c
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        tones.play(c)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(if (dark) PanelDark else Color.White)
            // Swallow taps so they do not reach the scrim behind the panel.
            .pointerInput(Unit) { detectTapGestures { } }
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (matches.isNotEmpty()) {
            Suggestions(
                matches = matches,
                onPick = { onCall(it) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
        }
        // Full-bleed number strip: no top spacer — ripple meets the panel's rounded top edge.
        NumberLine(
            number = number,
            onBackspace = {
                number = number.dropLast(1)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            },
            onClear = { number = "" },
            onPaste = { pasted -> number = pasted },
        )
        Column(
            Modifier
                .widthIn(max = 298.dp + 60.dp)
                .padding(horizontal = 30.dp),
        ) {
            // Chinese-ROM style shortcuts under a typed number.
            Row(
                Modifier.fillMaxWidth().height(32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (number.length >= 3) {
                    Shortcut("Kişilere ekle") { onAddContact(number) }
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
                    Shortcut("Mesaj gönder") { onMessage(number) }
                }
            }

            Keys.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (digit, letters) ->
                        Key(
                            digit = digit,
                            letters = letters,
                            voicemail = digit == '1',
                            onClick = { press(digit) },
                            onLongClick =
                                when (digit) {
                                    '0' -> ({ number += "+" })
                                    '1' -> if (number.isEmpty()) onVoicemail else null
                                    else -> null
                                },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Tuş takımını kapat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    MyOsFab(
                        icon = Icons.Filled.Call,
                        contentDescription = "Ara",
                        onClick = { onCall(number) },
                        size = 64.dp,
                        iconSize = 28.dp,
                        enabled = number.isNotEmpty(),
                    )
                }
                Box(Modifier.weight(1f))
            }
        }
    }
}

/** Typed number, grouped like a Turkish number while it is only digits; shrinks when long. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberLine(
    number: String,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onPaste: (String) -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val shown = remember(number) { if (number.all { it.isDigit() || it == '+' } && number.length > 3) formatPhone(number) else number }
    val size =
        when {
            shown.length > 17 -> 22.sp
            shown.length > 13 -> 26.sp
            else -> 32.sp
        }
    val numberInteraction = remember { MutableInteractionSource() }
    // Tall enough that, with no suggestions above, the strip fills into the panel top curve.
    Box(Modifier.fillMaxWidth().height(72.dp)) {
        // Edge-to-edge ripple across the dial panel width (clipped by PanelShape).
        Box(
            Modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = numberInteraction,
                    indication = ripple(bounded = true),
                    onClick = {},
                    onLongClick = { menu = true },
                ),
        )
        Text(
            text = shown,
            fontSize = size,
            lineHeight = size * 1.2f,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    enabled = number.isNotEmpty(),
                    onClick = onBackspace,
                    onLongClick = onClear,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (number.isNotEmpty()) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "Sil",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        // Anchor for the floating Yapıştır / Kopyala chip (centered under the number).
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp)) {
            DialpadClipboardPopup(
                expanded = menu,
                onDismiss = { menu = false },
                items =
                    if (!menu) {
                        emptyList()
                    } else {
                        buildList {
                            clipboardNumber(context)?.let { pasted ->
                                add(MyOsMenuItem("Yapıştır", Icons.Outlined.ContentPaste) { onPaste(pasted) })
                            }
                            if (number.isNotEmpty()) {
                                add(
                                    MyOsMenuItem("Kopyala", Icons.Outlined.ContentCopy) {
                                        com.nubiaphone.dialer.data.PhoneActions.copy(context, number)
                                    },
                                )
                            }
                        }
                    },
                dark = isSystemInDarkTheme(),
            )
        }
    }
}

/**
 * Floating clipboard actions under the number: each action is icon-over-label (alt-üst),
 * not a wide list row. Stays a popup bubble over the keypad.
 */
@Composable
private fun DialpadClipboardPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<MyOsMenuItem>,
    dark: Boolean,
) {
    if (!expanded || items.isEmpty()) return
    val bg = if (dark) Color(0xFF232323) else Color.White
    val fg = if (dark) Color.White.copy(alpha = 0.9f) else Color(0xFF1A1A1A)
    val shape = RoundedCornerShape(16.dp)
    val position =
        remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                    val y = anchorBounds.bottom
                    return IntOffset(
                        x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                        y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
                    )
                }
            }
        }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Row(
            Modifier
                .padding(8.dp)
                .then(
                    if (dark) {
                        Modifier
                    } else {
                        Modifier.shadow(12.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                    },
                )
                .clip(shape)
                .background(bg)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            items.forEach { item ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onDismiss()
                            item.onClick()
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (item.icon != null) {
                        Icon(item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(item.label, style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
        }
    }
}

/** Dialable characters from the clipboard, or null when it holds no number. */
private fun clipboardNumber(context: Context): String? {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: return null
    val dialable = text.filter { it.isDigit() || it in "+*#" }
    return dialable.takeIf { it.length >= 2 }
}

@Composable
private fun Shortcut(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(
    digit: Char,
    letters: String,
    voicemail: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .height(KeyRow)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = digit.toString(),
            fontSize = 32.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(Modifier.height(13.dp), contentAlignment = Alignment.Center) {
            if (voicemail) {
                Icon(
                    Icons.Outlined.Voicemail,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Text(
                    text = letters,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Up to four matches at the top of the dial panel. Tap calls the number. */
@Composable
private fun Suggestions(
    matches: List<DialMatch>,
    onPick: (String) -> Unit,
    modifier: Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth()) {
        matches.forEachIndexed { i, m ->
            key(m.number) {
                val shownNumber = formatPhone(m.number)
                val numberHighlight = mapDigitRange(m.number, shownNumber, m.numberRange)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onPick(m.number) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(photoUri = m.photoUri, name = m.name)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text =
                                if (m.name != null) {
                                    mark(m.name, m.nameRange, accent)
                                } else {
                                    mark(shownNumber, numberHighlight, accent)
                                },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (m.name != null) {
                            Text(
                                text = mark(shownNumber, numberHighlight, accent),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (i != matches.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    )
                }
            }
        }
    }
}

private fun mark(text: String, range: IntRange?, accent: Color): AnnotatedString {
    if (range == null || range.first < 0 || range.last >= text.length) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, range.first))
        withStyle(SpanStyle(color = accent)) { append(text.substring(range.first, range.last + 1)) }
        append(text.substring(range.last + 1))
    }
}

/** DTMF feedback that follows the system "Tuşlama sesleri" switch and silent/vibrate mode. */
private class Dtmf(private val context: Context) {
    private var generator: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 80) }.getOrNull()

    fun play(c: Char) {
        val tone = Tones[c] ?: return
        val enabled = Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1
        val audio = context.getSystemService(AudioManager::class.java)
        if (!enabled || audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        generator?.startTone(tone, 150)
    }

    fun release() {
        generator?.release()
        generator = null
    }
}

@Composable
private fun rememberDtmf(context: Context): Dtmf {
    val dtmf = remember { Dtmf(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { dtmf.release() } }
    return dtmf
}
