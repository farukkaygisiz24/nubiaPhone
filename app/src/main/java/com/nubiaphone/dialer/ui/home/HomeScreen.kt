package com.nubiaphone.dialer.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nubiaphone.dialer.telecom.DefaultDialer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.lazy.itemsIndexed
import com.nubiaphone.dialer.DevHooks
import com.nubiaphone.dialer.data.CallLogStore
import com.nubiaphone.dialer.data.ContactsStore
import com.nubiaphone.dialer.data.PhoneActions
import com.nubiaphone.dialer.data.formatPhone
import com.nubiaphone.dialer.ui.contacts.ContactRow
import com.nubiaphone.dialer.ui.contacts.SwipeableContact
import com.nubiaphone.dialer.ui.contacts.ContactsPane
import com.nubiaphone.dialer.ui.navigation.LocalContactNav
import com.nubiaphone.dialer.ui.recents.recentsItems
import com.nubiaphone.dialer.ui.recents.rememberCallController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.DeleteOutline
import com.nubiaphone.dialer.ui.components.MyOsAction
import com.nubiaphone.dialer.ui.components.MyOsActionBar
import com.nubiaphone.dialer.ui.components.MyOsCard
import com.nubiaphone.dialer.ui.components.MyOsSelectionHeader
import com.nubiaphone.dialer.ui.components.MyOsFab
import com.nubiaphone.dialer.ui.components.MyOsMenuItem
import com.nubiaphone.dialer.ui.components.MyOsOverflowMenu
import com.nubiaphone.dialer.ui.components.MyOsSearchBar
import com.nubiaphone.dialer.ui.components.MyOsSnackbarHost
import com.nubiaphone.dialer.ui.components.UndoDuration
import com.nubiaphone.dialer.ui.components.MyOsTab
import com.nubiaphone.dialer.ui.components.MyOsTabBar
import com.nubiaphone.dialer.ui.components.myOsBounce
import com.nubiaphone.dialer.ui.dialpad.DialpadPanel
import kotlinx.coroutines.launch

private enum class HomeTab(val title: String, val search: String, val empty: String) {
    Recents("Son aramalar", "Aramalarda arayın", "Arama geçmişi yok. Tuş takımından bir numara arayın."),
    Contacts("Kişiler", "Kişilerde arayın", "Kişi yok. Sağ üstteki ⋮ menüsünden \"Kişi ekle\"yi seçin."),
    Favorites("Favoriler", "Favorilerde arayın", "Favori yok. Bir kişiyi açıp yıldıza dokunun."),
}

private val Tabs =
    listOf(
        MyOsTab("Son aramalar", Icons.Outlined.AccessTime, Icons.Filled.AccessTime),
        MyOsTab("Kişiler", Icons.Outlined.PersonOutline, Icons.Filled.Person),
        MyOsTab("Favoriler", Icons.Outlined.StarOutline, Icons.Filled.Star),
    )

@Composable
fun HomeScreen(
    dialRequest: String?,
    onDialConsumed: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenCall: (String) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var dialpadOpen by rememberSaveable { mutableStateOf(false) }
    val tab = HomeTab.entries[tabIndex]
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        DevHooks.snackbar.collect { text ->
            if (text != null) {
                DevHooks.snackbar.value = null
                launch { snackbar.showSnackbar(text, actionLabel = "Geri al", duration = UndoDuration) }
            }
        }
    }
    BackHandler(enabled = dialpadOpen) { dialpadOpen = false }
    var selection by remember { mutableStateOf<Set<String>?>(null) }
    BackHandler(enabled = selection != null) { selection = null }
    LaunchedEffect(tabIndex) { selection = null }

    // DIAL / tel: from another app: open the keypad with that number.
    var dialSeed by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(dialRequest) {
        if (dialRequest != null) {
            dialSeed = dialRequest
            dialpadOpen = true
            onDialConsumed()
        }
    }
    var isDefault by remember { mutableStateOf(DefaultDialer.isDefault(context)) }
    val roleRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isDefault = DefaultDialer.isDefault(context)
        }

    val days by CallLogStore.days.collectAsState()
    var hasCallLog by remember { mutableStateOf(CallLogStore.hasPermission(context)) }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasCallLog = CallLogStore.hasPermission(context)
            CallLogStore.refresh(context)
        }
    LaunchedEffect(Unit) {
        // Ask for whatever is still missing (later phases added permissions to the list).
        val missing = StartupPermissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
        CallLogStore.refresh(context)
    }
    // Back from a call or from Kişiler: pick up permission changes and new calls.
    LifecycleResumeEffect(Unit) {
        isDefault = DefaultDialer.isDefault(context)
        hasCallLog = CallLogStore.hasPermission(context)
        CallLogStore.refresh(context)
        onPauseOrDispose { }
    }
    val calls = rememberCallController(snackbar, onInfo = { onOpenCall(it.key) })
    val contactNav = LocalContactNav.current
    val contacts by ContactsStore.contacts.collectAsState()
    LifecycleResumeEffect(Unit) {
        ContactsStore.refresh(context)
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            floatingActionButton = {
                if (!dialpadOpen && selection == null) {
                    // Saat: 48dp accent circle, centred, 28dp above the tab bar (16 + 12).
                    // Never moves for a snackbar (user request): the bar simply covers it.
                    MyOsFab(
                        icon = Icons.Filled.Dialpad,
                        contentDescription = "Tuş takımı",
                        onClick = { dialpadOpen = true },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            },
            floatingActionButtonPosition = FabPosition.Center,
            bottomBar = {
                val sel = selection
                if (sel != null) {
                    MyOsActionBar(
                        actions =
                            listOf(
                                MyOsAction("Sil", Icons.Outlined.DeleteOutline, destructive = true) {
                                    val groups = days.orEmpty().flatMap { it.groups }.filter { it.key in sel }
                                    calls.deleteMany(groups)
                                },
                            ),
                        enabled = sel.isNotEmpty(),
                    )
                } else {
                    MyOsTabBar(tabs = Tabs, selected = tabIndex, onSelect = { tabIndex = it })
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                val sel = selection
                if (sel != null) {
                    val allKeys = days.orEmpty().flatMap { d -> d.groups.map { it.key } }
                    Spacer(Modifier.height(8.dp))
                    MyOsSelectionHeader(
                        count = sel.size,
                        allSelected = allKeys.isNotEmpty() && sel.size == allKeys.size,
                        onClose = { selection = null },
                        onToggleAll = { selection = if (sel.size == allKeys.size) emptySet() else allKeys.toSet() },
                    )
                    Spacer(Modifier.height(12.dp))
                } else {
                // Pinned like nubiaSMS / Ayarlar: title + ⋮ and search never scroll.
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        MyOsOverflowMenu(
                            dark = dark,
                            items =
                                buildList {
                                    if (tab == HomeTab.Contacts) {
                                        add(MyOsMenuItem("Kişi ekle", Icons.Outlined.PersonAddAlt) { contactNav.add(null) })
                                    }
                                    if (tab == HomeTab.Recents && days.orEmpty().isNotEmpty()) {
                                        add(MyOsMenuItem("Seç", Icons.Outlined.Checklist) { selection = emptySet() })
                                    }
                                    add(MyOsMenuItem("Ayarlar", Icons.Outlined.Settings, onClick = onSettings))
                                },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    MyOsSearchBar(placeholder = tab.search, onClick = onSearch)
                    Spacer(Modifier.height(16.dp))
                }
                val list = contacts.orEmpty()
                if (tab == HomeTab.Contacts && ContactsStore.canRead(context) && list.isNotEmpty()) {
                    ContactsPane(
                        contacts = list,
                        onOpen = { contactNav.open(it.id) },
                        modifier = Modifier.fillMaxSize(),
                        onCall = { calls.call(it) },
                        onWhatsApp = { calls.whatsapp(it) },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().myOsBounce(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        val favorites = list.filter { it.starred }
                        when {
                            tab == HomeTab.Recents && !hasCallLog ->
                                item(key = "perm") {
                                    EmptyCard("Arama geçmişini görmek için izin verin.", action = "İzin ver") {
                                        permissions.launch(StartupPermissions)
                                    }
                                }
                            tab != HomeTab.Recents && !ContactsStore.canRead(context) ->
                                item(key = "perm-c") {
                                    EmptyCard("Kişileri görmek için izin verin.", action = "İzin ver") {
                                        permissions.launch(StartupPermissions)
                                    }
                                }
                            tab == HomeTab.Recents && days == null -> Unit
                            tab == HomeTab.Recents && days.orEmpty().isNotEmpty() -> {
                                if (!isDefault) {
                                    item(key = "default-banner") {
                                        DefaultBanner {
                                            DefaultDialer.requestIntent(context)?.let { roleRequest.launch(it) }
                                        }
                                    }
                                }
                                recentsItems(
                                    days.orEmpty(),
                                    calls.actions,
                                    selection = selection,
                                    onToggle = { g ->
                                        selection = selection?.let { if (g.key in it) it - g.key else it + g.key }
                                    },
                                )
                            }
                            tab != HomeTab.Recents && contacts == null -> Unit
                            tab == HomeTab.Favorites && favorites.isNotEmpty() ->
                                itemsIndexed(favorites, key = { _, c -> "f-${c.id}" }) { i, c ->
                                    SwipeableContact(
                                        c,
                                        isFirst = i == 0,
                                        isLast = i == favorites.lastIndex,
                                        onCall = { calls.call(it) },
                                        onWhatsApp = { calls.whatsapp(it) },
                                    ) {
                                        ContactRow(
                                            c,
                                            isFirst = i == 0,
                                            isLast = i == favorites.lastIndex,
                                            onClick = { contactNav.open(c.id) },
                                            subtitle = c.numbers.firstOrNull()?.let { formatPhone(it) },
                                        )
                                    }
                                }
                            else -> item(key = "empty-${tab.name}") { EmptyCard(tab.empty) }
                        }
                    }
                }
            }
        }

        // Saat's bottom panel darkens the page behind it; tapping the dimmed page closes it.
        AnimatedVisibility(visible = dialpadOpen, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { dialpadOpen = false },
            )
        }
        AnimatedVisibility(
            visible = dialpadOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300)) { it },
            exit = slideOutVertically(tween(250)) { it },
        ) {
            DialpadPanel(
                initialNumber = dialSeed,
                onClose = {
                    dialpadOpen = false
                    dialSeed = ""
                },
                onCall = { calls.call(it) },
                onVoicemail = calls.voicemail,
                onAddContact = { contactNav.add(it) },
                onMessage = { PhoneActions.message(context, it) },
            )
        }
        // Always at the very bottom of the screen at one fixed height (16dp above the system
        // navigation bar), drawn over the tab bar and FAB; nothing else moves.
        MyOsSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
    calls.dialogs()
}

private val StartupPermissions =
    buildList {
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.WRITE_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

/** Like nubiaSMS's default-app card: first list item, scrolls with the list. */
@Composable
private fun DefaultBanner(onClick: () -> Unit) {
    MyOsCard(Modifier.padding(top = 4.dp)) {
        Text(
            "Gelen aramaları bu uygulamada görmek için Telefon'u varsayılan telefon uygulaması yapın.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
        TextButton(onClick = onClick, modifier = Modifier.padding(bottom = 4.dp)) {
            Text("Varsayılan yap", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EmptyCard(
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    MyOsCard {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = if (action == null) 16.dp else 4.dp),
        )
        if (action != null) {
            TextButton(onClick = onAction, modifier = Modifier.padding(bottom = 4.dp)) {
                Text(action, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }
    }
}
