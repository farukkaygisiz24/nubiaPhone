package com.nubiaphone.dialer.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubiaphone.dialer.data.ContactSummary
import com.nubiaphone.dialer.ui.common.callSwipeActions
import com.nubiaphone.dialer.ui.components.ContactAvatar
import com.nubiaphone.dialer.ui.components.SwipeableRow
import com.nubiaphone.dialer.ui.components.myOsAccentFill
import com.nubiaphone.dialer.ui.components.myOsAccentInk
import com.nubiaphone.dialer.ui.components.myOsBounce
import com.nubiaphone.dialer.ui.theme.MyOsCardShape
import com.nubiaphone.dialer.ui.theme.MyOsCorner
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/** Launcher › Tüm uygulamalar index (TASARIM.md › Harf dizini). */
private val IndexLetters =
    listOf(
        "#", "A", "B", "C", "Ç", "D", "E", "F", "G", "Ğ", "H", "I", "İ", "J", "K", "L", "M",
        "N", "O", "Ö", "P", "R", "S", "Ş", "T", "U", "Ü", "V", "Y", "Z",
    )
private val IndexPitch = 19.dp
private val IndexWidth = 20.dp

/**
 * Contact list with letter labels, one card group per letter, and the MyOS A–Z index on the
 * right edge. The index column's centre sits 10dp from the screen edge; this pane is laid out
 * inside the home screen's 16dp gutter, so the index is shifted out into it.
 */
@Composable
fun ContactsPane(
    contacts: List<ContactSummary>,
    onOpen: (ContactSummary) -> Unit,
    modifier: Modifier = Modifier,
    onCall: (String) -> Unit = {},
    onWhatsApp: (String) -> Unit = {},
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sections = remember(contacts) { contacts.groupBy { it.section } }
    // LazyColumn index of each letter label.
    val sectionIndex =
        remember(sections) {
            var i = 0
            sections.mapValues { (_, list) -> i.also { i += 1 + list.size } }
        }
    var active by remember { mutableStateOf<String?>(null) }

    Box(modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().padding(end = 8.dp).myOsBounce(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            sections.forEach { (letter, list) ->
                item(key = "sec-$letter", contentType = "label") {
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
                itemsIndexed(list, key = { _, c -> "c-${c.id}" }, contentType = { _, _ -> "contact" }) { i, c ->
                    SwipeableContact(c, isFirst = i == 0, isLast = i == list.lastIndex, onCall = onCall, onWhatsApp = onWhatsApp) {
                        ContactRow(c, isFirst = i == 0, isLast = i == list.lastIndex, onClick = { onOpen(c) })
                    }
                }
            }
        }

        if (sections.size > 1) {
            AlphabetIndex(
                active = active,
                onSelect = { letter ->
                    active = letter
                    if (letter != null) {
                        val target = nearestSection(letter, sections.keys)
                        sectionIndex[target]?.let { scope.launch { state.scrollToItem(it) } }
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = 16.dp),
            )
        }

        // Big letter while the finger is on the index.
        active?.let { letter ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(MyOsCardShape)
                    .background(myOsAccentFill()),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }
    }
}

/** The letter's own section, else the next one in Turkish order, else the last. */
private fun nearestSection(letter: String, available: Set<String>): String {
    if (letter in available) return letter
    val collator = Collator.getInstance(Locale("tr", "TR"))
    val letters = available.filter { it != "#" }.sortedWith(collator)
    if (letter == "#") return available.firstOrNull() ?: letter
    return letters.firstOrNull { collator.compare(it, letter) > 0 } ?: letters.lastOrNull() ?: letter
}

@Composable
private fun AlphabetIndex(
    active: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxHeight().width(IndexWidth)) {
        val pitch = minOf(IndexPitch, maxHeight / IndexLetters.size)
        val total = pitch * IndexLetters.size
        val pitchPx = with(density) { pitch.toPx() }
        Column(
            Modifier
                .align(Alignment.Center)
                .width(IndexWidth)
                .height(total)
                .pointerInput(pitchPx) {
                    fun letterAt(y: Float): String =
                        IndexLetters[(y / pitchPx).toInt().coerceIn(0, IndexLetters.lastIndex)]
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var last = letterAt(down.position.y)
                        onSelect(last)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val now = letterAt(change.position.y)
                            if (now != last) {
                                last = now
                                onSelect(now)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            change.consume()
                        }
                        onSelect(null)
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val accent = myOsAccentInk()
            IndexLetters.forEach { l ->
                Box(Modifier.height(pitch), contentAlignment = Alignment.Center) {
                    Text(
                        text = l,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (l == active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Single-line 56dp contact row (TASARIM.md › Liste: 52–56dp), divider from 64dp. */
@Composable
fun ContactRow(
    contact: ContactSummary,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    title: AnnotatedString? = null,
    subtitle: String? = null,
) {
    val top = if (isFirst) MyOsCorner else 0.dp
    val bottom = if (isLast) MyOsCorner else 0.dp
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(top, top, bottom, bottom))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (subtitle == null) 56.dp else 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(photoUri = contact.photoUri, name = contact.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title ?: AnnotatedString(contact.name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
        }
    }
}

/** Right: WhatsApp chat · Left: call, both on the contact's first number. Off when it has none. */
@Composable
fun SwipeableContact(
    contact: ContactSummary,
    isFirst: Boolean,
    isLast: Boolean,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val number = contact.numbers.firstOrNull()
    val top = if (isFirst) MyOsCorner else 0.dp
    val bottom = if (isLast) MyOsCorner else 0.dp
    val (start, end) = callSwipeActions(onWhatsApp = { number?.let(onWhatsApp) }, onCall = { number?.let(onCall) })
    SwipeableRow(
        shape = RoundedCornerShape(top, top, bottom, bottom),
        enabled = number != null,
        start = start,
        end = end,
        content = content,
    )
}
