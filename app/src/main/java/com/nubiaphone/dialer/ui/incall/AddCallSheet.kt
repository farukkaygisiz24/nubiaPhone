package com.nubiaphone.dialer.ui.incall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nubiaphone.dialer.data.CallLogStore
import com.nubiaphone.dialer.data.ContactsStore
import com.nubiaphone.dialer.data.formatPhone
import com.nubiaphone.dialer.data.numberKey
import com.nubiaphone.dialer.ui.components.ContactAvatar
import com.nubiaphone.dialer.ui.components.MyOsCard
import com.nubiaphone.dialer.ui.components.MyOsSearchField
import com.nubiaphone.dialer.ui.components.myOsAccentFill
import java.util.Locale

private val TR = Locale("tr", "TR")

private data class Pick(val name: String?, val number: String, val photoUri: String?)

/**
 * "Arama ekle" modal, like nubiaSMS's new-message picker: a full-height panel sliding up over
 * the call with "×" + title, a search field (name or number), "Ara: <number>" for a typed
 * number, recent people when the field is empty, matching contacts while typing.
 * Picking someone calls [onPick] with the number.
 */
@Composable
fun BoxScope.AddCallSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(visible) {
        if (visible) {
            ContactsStore.refresh(context)
            CallLogStore.refresh(context)
        }
    }
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
        enter = slideInVertically(tween(320)) { it },
        exit = slideOutVertically(tween(250)) { it },
    ) {
        var query by remember { mutableStateOf("") }
        val contacts by ContactsStore.contacts.collectAsState()
        val days by CallLogStore.days.collectAsState()
        val dark = isSystemInDarkTheme()

        val recent =
            remember(days) {
                val seen = HashSet<String>()
                days.orEmpty().asSequence().flatMap { it.groups }
                    .filter { !it.isHidden && seen.add(numberKey(it.number)) }
                    .take(6)
                    .map { Pick(it.contact?.name, it.number, it.contact?.photoUri) }
                    .toList()
            }
        val matches =
            remember(contacts, query) {
                val q = query.trim()
                val digits = q.filter { it.isDigit() }
                if (q.isEmpty()) {
                    emptyList()
                } else {
                    contacts.orEmpty().filter { c ->
                        c.numbers.isNotEmpty() &&
                            (c.name.lowercase(TR).contains(q.lowercase(TR)) ||
                                (digits.length >= 2 && c.numbers.any { n -> n.filter { it.isDigit() }.contains(digits) }))
                    }.take(30).map { Pick(it.name, it.numbers.first(), it.photoUri) }
                }
            }
        val typedNumber = query.trim().takeIf { q -> q.count { it.isDigit() } >= 3 && q.all { it.isDigit() || it in "+ ()-" } }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(if (dark) Color(0xFF1B1B1B) else MaterialTheme.colorScheme.background)
                .imePadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("Kişi ekle", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            MyOsSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Ad ya da numara",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                "Mevcut görüşme beklemeye alınır, sonra seçtiğiniz kişi aranır.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (typedNumber != null) {
                    item(key = "typed") {
                        MyOsCard {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onPick(typedNumber) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(myOsAccentFill()), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Ara: ${formatPhone(typedNumber)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
                val list = if (query.isBlank()) recent else matches
                if (list.isNotEmpty()) {
                    item(key = "label") {
                        Text(
                            if (query.isBlank()) "Son aramalar" else "Kişiler",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp),
                        )
                    }
                    itemsIndexed(list, key = { _, p -> "p-${p.number}-${p.name}" }) { i, p ->
                        PickRow(p, isFirst = i == 0, isLast = i == list.lastIndex) { onPick(p.number) }
                    }
                } else if (query.isNotBlank() && typedNumber == null) {
                    item(key = "none") {
                        MyOsCard {
                            Text(
                                "Sonuç yok",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(
    p: Pick,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val top = if (isFirst) 16.dp else 0.dp
    val bottom = if (isLast) 16.dp else 0.dp
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(top, top, bottom, bottom))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(photoUri = p.photoUri, name = p.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    p.name ?: formatPhone(p.number),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (p.name != null) {
                    Text(formatPhone(p.number), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Filled.Call, contentDescription = "Ara", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
