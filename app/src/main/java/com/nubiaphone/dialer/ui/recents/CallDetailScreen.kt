package com.nubiaphone.dialer.ui.recents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Block
import com.nubiaphone.dialer.data.BlockedNumbers
import com.nubiaphone.dialer.data.CallEntry
import com.nubiaphone.dialer.data.CallGroup
import com.nubiaphone.dialer.data.CallLogStore
import com.nubiaphone.dialer.data.formatPhone
import com.nubiaphone.dialer.ui.common.callTypeIcon
import com.nubiaphone.dialer.ui.common.callTypeLabel
import com.nubiaphone.dialer.ui.common.formatDuration
import com.nubiaphone.dialer.ui.common.isMissedLike
import com.nubiaphone.dialer.ui.common.longDateTime
import com.nubiaphone.dialer.ui.components.MyOsCard
import com.nubiaphone.dialer.ui.components.MyOsMenuItem
import com.nubiaphone.dialer.ui.components.MyOsOverflowMenu
import com.nubiaphone.dialer.ui.components.MyOsSnackbarHost
import com.nubiaphone.dialer.ui.components.NubiaPhoneTopBar
import com.nubiaphone.dialer.ui.components.myOsBounce
import com.nubiaphone.dialer.ui.components.myOsDanger

/**
 * Call details for one number (opened with the row's (i)): back bar with name + number,
 * an action card (Ara · Mesaj · Kişi) and every logged call with that number.
 */
@Composable
fun CallDetailScreen(
    groupKey: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val days by CallLogStore.days.collectAsState()
    // Re-resolve after every reload so a deleted group closes the page.
    val group = remember(days, groupKey) { CallLogStore.group(groupKey) }
    val snackbar = remember { SnackbarHostState() }
    val calls = rememberCallController(snackbar, onInfo = {}, onDeleted = onBack)

    if (group == null) {
        // Deleted or not loaded yet; nothing to show.
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            NubiaPhoneTopBar(title = "Arama ayrıntıları", onBack = onBack)
        }
        return
    }
    val history: List<CallEntry> =
        remember(group, days) { if (group.isHidden) group.calls else CallLogStore.callsFor(group.number) }
    // The whole number's history: delete / actions act on all of it, not only today's run.
    val all = remember(group, history) { group.copy(calls = history) }
    val subtitle =
        when {
            group.isHidden -> null
            group.contact != null -> listOfNotNull(group.contact.numberLabel, formatPhone(group.number)).joinToString(" · ")
            else -> group.latest.geocoded?.takeIf { it.isNotBlank() }
        }

    Scaffold(
        snackbarHost = { MyOsSnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NubiaPhoneTopBar(
                title = all.title,
                subtitle = subtitle,
                onBack = onBack,
                actions = {
                    MyOsOverflowMenu(
                        dark = isSystemInDarkTheme(),
                        items =
                            buildList {
                                if (!group.isHidden) {
                                    add(MyOsMenuItem("Numarayı kopyala", Icons.Outlined.ContentCopy) { calls.actions.onCopy(all) })
                                }
                                if (!group.isHidden && BlockedNumbers.available(context)) {
                                    if (BlockedNumbers.isBlocked(context, group.number)) {
                                        add(MyOsMenuItem("Engeli kaldır", Icons.Outlined.Block) { calls.actions.onBlock(all, false) })
                                    } else {
                                        add(MyOsMenuItem("Numarayı engelle", Icons.Outlined.Block, destructive = true) { calls.actions.onBlock(all, true) })
                                    }
                                }
                                add(
                                    MyOsMenuItem("Geçmişten sil", Icons.Outlined.DeleteOutline, destructive = true) {
                                        calls.actions.onDelete(all)
                                    },
                                )
                            },
                    )
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).myOsBounce(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            ) {
                if (!group.isHidden) {
                    item(key = "actions") {
                        MyOsCard {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                DetailAction("Ara", Icons.Outlined.Call, Modifier.weight(1f)) { calls.call(group.number) }
                                DetailAction("Mesaj", Icons.AutoMirrored.Outlined.Message, Modifier.weight(1f)) {
                                    calls.actions.onMessage(all)
                                }
                                if (group.contact != null) {
                                    DetailAction("Kişi", Icons.Outlined.PersonOutline, Modifier.weight(1f)) {
                                        calls.actions.onContact(all)
                                    }
                                } else {
                                    DetailAction("Kişi ekle", Icons.Outlined.PersonAddAlt, Modifier.weight(1f)) {
                                        calls.actions.onContact(all)
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "label") {
                    Text(
                        text = "Arama geçmişi",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                item(key = "history") {
                    MyOsCard {
                        history.forEachIndexed { index, call ->
                            HistoryRow(call)
                            if (index != history.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 34.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    calls.dialogs()
}

/** Icon over label, like the MyOS selection action bar. */
@Composable
private fun DetailAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
internal fun HistoryRow(call: CallEntry) {
    val missed = isMissedLike(call.type)
    val tint = if (missed) myOsDanger() else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(callTypeIcon(call.type), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                callTypeLabel(call.type),
                style = MaterialTheme.typography.bodyLarge,
                color = if (missed) tint else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                longDateTime(call.dateMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Missed/rejected rows carry the ring time on this ROM; only talk time is shown.
        if (call.durationSec > 0 && !missed) {
            Text(
                formatDuration(call.durationSec),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
