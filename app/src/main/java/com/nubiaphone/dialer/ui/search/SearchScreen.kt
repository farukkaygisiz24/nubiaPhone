package com.nubiaphone.dialer.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nubiaphone.dialer.data.CallGroup
import com.nubiaphone.dialer.data.CallLogStore
import com.nubiaphone.dialer.data.ContactsStore
import com.nubiaphone.dialer.data.formatPhone
import com.nubiaphone.dialer.ui.contacts.ContactRow
import com.nubiaphone.dialer.ui.navigation.LocalContactNav
import com.nubiaphone.dialer.data.numberKey
import com.nubiaphone.dialer.ui.components.MyOsCard
import com.nubiaphone.dialer.ui.components.MyOsSearchField
import com.nubiaphone.dialer.ui.components.MyOsSnackbarHost
import com.nubiaphone.dialer.ui.components.myOsBounce
import com.nubiaphone.dialer.ui.recents.CallRow
import com.nubiaphone.dialer.ui.recents.rememberCallController
import java.util.Locale

private val TR = Locale("tr", "TR")

/**
 * Shared search page (same top layout as nubiaSMS: thin "<" + accent-tinted field).
 * Searches contacts (name, any number) and the call log (latest call per number).
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenCall: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val days by CallLogStore.days.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val calls = rememberCallController(snackbar, onInfo = { onOpenCall(it.key) })

    val results =
        remember(days, query) {
            val q = query.trim()
            if (q.isEmpty()) {
                emptyList()
            } else {
                val digits = q.filter { it.isDigit() }
                val seen = HashSet<String>()
                days.orEmpty().asSequence().flatMap { it.groups }
                    .filter { !it.isHidden && seen.add(numberKey(it.number)) }
                    .filter { g ->
                        g.title.lowercase(TR).contains(q.lowercase(TR)) ||
                            (digits.length >= 2 && g.number.filter { it.isDigit() }.contains(digits))
                    }.toList()
            }
        }
    val people by ContactsStore.contacts.collectAsState()
    val contactNav = LocalContactNav.current
    val contactResults =
        remember(people, query) {
            val q = query.trim()
            val digits = q.filter { it.isDigit() }
            if (q.isEmpty()) {
                emptyList()
            } else {
                people.orEmpty().filter { c ->
                    c.name.lowercase(TR).contains(q.lowercase(TR)) ||
                        (digits.length >= 2 && c.numbers.any { n -> n.filter { it.isDigit() }.contains(digits) })
                }
            }
        }
    val accent = MaterialTheme.colorScheme.primary

    Scaffold(
        snackbarHost = { MyOsSnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(end = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Geri",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                MyOsSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Kişi, numara ya da arama",
                    modifier = Modifier.focusRequester(focus),
                )
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(start = 16.dp).myOsBounce(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                when {
                    query.isBlank() -> Unit
                    results.isEmpty() && contactResults.isEmpty() ->
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
                    else -> {
                        if (contactResults.isNotEmpty()) {
                            item(key = "l-contacts") { SectionLabel("Kişiler") }
                            itemsIndexed(contactResults, key = { _, c -> "c-${c.id}" }) { index, c ->
                                ContactRow(
                                    c,
                                    isFirst = index == 0,
                                    isLast = index == contactResults.lastIndex,
                                    onClick = { contactNav.open(c.id) },
                                    title = highlightText(c.name, query.trim(), accent),
                                    subtitle = c.numbers.firstOrNull()?.let { formatPhone(it) },
                                )
                            }
                        }
                        if (results.isNotEmpty()) {
                            item(key = "l-calls") { SectionLabel("Son aramalar") }
                            itemsIndexed(results, key = { _, g -> g.key }) { index, group ->
                                CallRow(
                                    group = group,
                                    isFirst = index == 0,
                                    isLast = index == results.lastIndex,
                                    actions = calls.actions,
                                    titleOverride = highlight(group, query.trim(), accent),
                                    showDate = true,
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp),
    )
}

private fun highlight(group: CallGroup, q: String, accent: Color): AnnotatedString = highlightText(group.title, q, accent)

/** Title with the matched part in the accent colour (TASARIM.md › Arama). */
private fun highlightText(title: String, q: String, accent: Color): AnnotatedString {
    val start = title.lowercase(TR).indexOf(q.lowercase(TR))
    if (q.isEmpty() || start < 0) return AnnotatedString(title)
    return buildAnnotatedString {
        append(title.substring(0, start))
        withStyle(SpanStyle(color = accent)) { append(title.substring(start, start + q.length)) }
        append(title.substring(start + q.length))
    }
}
