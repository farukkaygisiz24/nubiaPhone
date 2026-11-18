package com.nubiaphone.dialer.ui.recents

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Block
import androidx.compose.ui.platform.LocalContext
import com.nubiaphone.dialer.data.BlockedNumbers
import com.nubiaphone.dialer.data.CallDay
import com.nubiaphone.dialer.data.CallGroup
import com.nubiaphone.dialer.ui.common.callSwipeActions
import com.nubiaphone.dialer.ui.common.callTypeIcon
import com.nubiaphone.dialer.ui.components.SwipeableRow
import com.nubiaphone.dialer.ui.common.dayHeader
import com.nubiaphone.dialer.ui.common.isMissedLike
import com.nubiaphone.dialer.ui.common.listTime
import com.nubiaphone.dialer.ui.common.timeOfDay
import com.nubiaphone.dialer.ui.components.ContactAvatar
import com.nubiaphone.dialer.ui.components.MyOsMenuItem
import com.nubiaphone.dialer.ui.components.MyOsPopupMenu
import com.nubiaphone.dialer.ui.components.myOsDanger
import com.nubiaphone.dialer.ui.theme.MyOsCorner

/** What a call row can ask the screen to do. */
class CallRowActions(
    val onCall: (CallGroup) -> Unit,
    val onInfo: (CallGroup) -> Unit,
    val onMessage: (CallGroup) -> Unit,
    val onCopy: (CallGroup) -> Unit,
    val onContact: (CallGroup) -> Unit,
    val onDelete: (CallGroup) -> Unit,
    /** Block (true) or unblock (false) the number. Only offered while the block list is usable. */
    val onBlock: (CallGroup, Boolean) -> Unit = { _, _ -> },
    val onWhatsApp: (CallGroup) -> Unit = {},
)

/** Day-grouped call log: a grey day label, then the day's calls in one MyOS card group. */
fun LazyListScope.recentsItems(
    days: List<CallDay>,
    actions: CallRowActions,
    /** Non-null while selecting: keys of the selected rows. */
    selection: Set<String>? = null,
    onToggle: (CallGroup) -> Unit = {},
) {
    days.forEach { day ->
        item(key = "day-${day.dayStart}", contentType = "header") {
            Text(
                text = dayHeader(day.dayStart),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp),
            )
        }
        itemsIndexed(day.groups, key = { _, g -> g.key }, contentType = { _, _ -> "call" }) { index, group ->
            val isFirst = index == 0
            val isLast = index == day.groups.lastIndex
            val top = if (isFirst) MyOsCorner else 0.dp
            val bottom = if (isLast) MyOsCorner else 0.dp
            // Right: WhatsApp chat · Left: call (not for hidden numbers or while selecting).
            val (start, end) = callSwipeActions(onWhatsApp = { actions.onWhatsApp(group) }, onCall = { actions.onCall(group) })
            SwipeableRow(
                shape = RoundedCornerShape(top, top, bottom, bottom),
                enabled = selection == null && !group.isHidden,
                start = start,
                end = end,
            ) {
                CallRow(
                    group = group,
                    isFirst = isFirst,
                    isLast = isLast,
                    actions = actions,
                    selected = selection?.contains(group.key),
                    onToggle = { onToggle(group) },
                )
            }
        }
    }
}

/**
 * One call log row (TASARIM.md › Liste): 64dp two-line row inside the day's card group,
 * 36dp avatar, divider from 64dp. Title = name (count), red for missed calls. Second line =
 * call type glyph + number label / location. Right: time and an (i) for the call details.
 * Tap calls back, long press opens the MyOS popup menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallRow(
    group: CallGroup,
    isFirst: Boolean,
    isLast: Boolean,
    actions: CallRowActions,
    titleOverride: AnnotatedString? = null,
    /** Search results mix days: show a short date for anything not from today. */
    showDate: Boolean = false,
    /** null = normal row; true/false = selection mode (tap toggles, avatar shows a check). */
    selected: Boolean? = null,
    onToggle: () -> Unit = {},
) {
    val top = if (isFirst) MyOsCorner else 0.dp
    val bottom = if (isLast) MyOsCorner else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    val danger = myOsDanger()
    val missed = isMissedLike(group.latest.type)
    var menu by remember { mutableStateOf(false) }

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .then(if (selected == true) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) else Modifier)
                .combinedClickable(
                    onClick = {
                        when {
                            selected != null -> onToggle()
                            !group.isHidden -> actions.onCall(group)
                        }
                    },
                    onLongClick = { if (selected == null) menu = true else onToggle() },
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected == true) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Seçili", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                } else {
                    ContactAvatar(photoUri = group.contact?.photoUri, name = group.contact?.name)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val title = titleOverride ?: AnnotatedString(group.title)
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (missed) danger else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (group.calls.size > 1) {
                            Text(
                                text = " (${group.calls.size})",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (missed) danger else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = callTypeIcon(group.latest.type),
                            contentDescription = null,
                            tint = if (missed) danger else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = subtitle(group),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (showDate) listTime(group.latest.dateMillis) else timeOfDay(group.latest.dateMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = selected == null) { actions.onInfo(group) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Arama ayrıntıları",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
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

        // Block state is read when the menu opens (provider call, default phone app only).
        val context = LocalContext.current
        val blockState =
            remember(menu) {
                if (!menu || group.isHidden || !BlockedNumbers.available(context)) null else BlockedNumbers.isBlocked(context, group.number)
            }
        MyOsPopupMenu(
            expanded = menu,
            onDismiss = { menu = false },
            items = menuItems(group, actions, blockState),
            dark = isSystemInDarkTheme(),
            alignEnd = true,
        )
    }
}

private fun menuItems(group: CallGroup, actions: CallRowActions, blocked: Boolean?): List<MyOsMenuItem> =
    buildList {
        if (!group.isHidden) {
            add(MyOsMenuItem("Mesaj gönder", Icons.AutoMirrored.Outlined.Message) { actions.onMessage(group) })
            add(MyOsMenuItem("Numarayı kopyala", Icons.Outlined.ContentCopy) { actions.onCopy(group) })
            if (group.contact != null) {
                add(MyOsMenuItem("Kişiyi aç", Icons.Outlined.PersonOutline) { actions.onContact(group) })
            } else {
                add(MyOsMenuItem("Kişi ekle", Icons.Outlined.PersonAddAlt) { actions.onContact(group) })
            }
        }
        add(MyOsMenuItem("Ayrıntılar", Icons.Outlined.Info) { actions.onInfo(group) })
        if (blocked == false) add(MyOsMenuItem("Numarayı engelle", Icons.Outlined.Block, destructive = true) { actions.onBlock(group, true) })
        if (blocked == true) add(MyOsMenuItem("Engeli kaldır", Icons.Outlined.Block) { actions.onBlock(group, false) })
        add(MyOsMenuItem("Geçmişten sil", Icons.Outlined.DeleteOutline, destructive = true) { actions.onDelete(group) })
    }

/** "Mobil", the place the number belongs to, or the formatted number under a contact name. */
private fun subtitle(group: CallGroup): String {
    if (group.isHidden) return "Numara gösterilmiyor"
    group.contact?.numberLabel?.let { return it }
    val geo = group.latest.geocoded
    return if (!geo.isNullOrBlank()) geo else "Bilinmeyen numara"
}
