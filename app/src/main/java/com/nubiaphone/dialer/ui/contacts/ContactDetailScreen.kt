package com.nubiaphone.dialer.ui.contacts

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nubiaphone.dialer.data.CallLogStore
import com.nubiaphone.dialer.data.ContactDetail
import com.nubiaphone.dialer.data.ContactsStore
import com.nubiaphone.dialer.data.PhoneActions
import com.nubiaphone.dialer.data.formatPhone
import com.nubiaphone.dialer.ui.components.ContactAvatar
import com.nubiaphone.dialer.ui.components.MyOsCard
import com.nubiaphone.dialer.ui.components.MyOsMenuItem
import com.nubiaphone.dialer.ui.components.MyOsOverflowMenu
import com.nubiaphone.dialer.ui.components.MyOsSnackbarHost
import com.nubiaphone.dialer.ui.components.NubiaPhoneTopBar
import com.nubiaphone.dialer.ui.components.myOsBounce
import com.nubiaphone.dialer.ui.components.myOsDanger
import com.nubiaphone.dialer.ui.components.rememberMyOsBounce
import com.nubiaphone.dialer.ui.recents.HistoryRow
import com.nubiaphone.dialer.ui.recents.rememberCallController
import com.nubiaphone.dialer.ui.theme.MyOsCardShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** TASARIM.md › Renk: star. */
private val StarYellow = Color(0xFFF5B400)

/**
 * Contact card: avatar + name, action card (Ara · Mesaj · E-posta), numbers, e-mails and the
 * latest calls with this contact. Star and ⋮ (Düzenle, Paylaş, Sil) in the top bar.
 * Writes (star, delete) need WRITE_CONTACTS and are asked for when used.
 */
@Composable
fun ContactDetailScreen(
    contactId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val calls = rememberCallController(snackbar, onInfo = {})
    // Reload whenever the phone book changes (e.g. after editing).
    val list by ContactsStore.contacts.collectAsState()
    val detail by produceState<ContactDetail?>(null, contactId, list) {
        value = withContext(Dispatchers.IO) { ContactsStore.detail(context, contactId) }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    val writePermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingWrite
            pendingWrite = null
            if (granted) action?.invoke() else scope.launch { snackbar.showSnackbar("Kişileri değiştirmek için izin gerekli") }
        }

    fun withWrite(action: () -> Unit) {
        if (ContactsStore.canWrite(context)) {
            action()
        } else {
            pendingWrite = action
            writePermission.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    val c = detail
    Scaffold(
        snackbarHost = { MyOsSnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            NubiaPhoneTopBar(
                title = "",
                onBack = onBack,
                actions = {
                    if (c != null) {
                        IconButton(onClick = {
                            withWrite {
                                scope.launch(Dispatchers.IO) { ContactsStore.setStarred(context, c.id, !c.starred) }
                            }
                        }) {
                            Icon(
                                if (c.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (c.starred) "Favorilerden çıkar" else "Favorilere ekle",
                                tint = if (c.starred) StarYellow else MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        MyOsOverflowMenu(
                            dark = isSystemInDarkTheme(),
                            items =
                                listOf(
                                    MyOsMenuItem("Düzenle", Icons.Outlined.Edit) { onEdit(c.id) },
                                    MyOsMenuItem("Paylaş", Icons.Outlined.Share) {
                                        val send =
                                            Intent(Intent.ACTION_SEND)
                                                .setType(ContactsContract.Contacts.CONTENT_VCARD_TYPE)
                                                .putExtra(Intent.EXTRA_STREAM, ContactsStore.vcardUri(c.lookupKey))
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        context.startActivity(Intent.createChooser(send, "Kişiyi paylaş"))
                                    },
                                    MyOsMenuItem("Sil", Icons.Outlined.DeleteOutline, destructive = true) { confirmDelete = true },
                                ),
                        )
                    }
                },
            )
            if (c == null) return@Column
            val history =
                remember(c) {
                    c.phones.flatMap { CallLogStore.callsFor(it.number) }.distinctBy { it.id }.sortedByDescending { it.dateMillis }.take(5)
                }
            val bounce = rememberMyOsBounce()
            // Pull-down grows the pinned header (avatar stays fully visible — never clipped mid-circle).
            val pull = (bounce.offset.coerceAtLeast(0f) / bounce.maxDragPx).coerceIn(0f, 1f)
            val avatarSize = 72.dp + 40.dp * pull
            val nameSize = (21f + 6f * pull).sp
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ContactAvatar(photoUri = c.photoUri, name = c.name, size = avatarSize)
                Spacer(Modifier.height(12.dp))
                Text(
                    c.name,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = nameSize, lineHeight = nameSize * 1.25f),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                c.accountLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .myOsBounce(bounce, translateTop = false),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                val primary = c.phones.firstOrNull()?.number
                if (primary != null || c.emails.isNotEmpty()) {
                    item(key = "actions") {
                        MyOsCard {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                if (primary != null) {
                                    Action("Ara", Icons.Outlined.Call, Modifier.weight(1f)) { calls.call(primary) }
                                    Action("Mesaj", Icons.AutoMirrored.Outlined.Message, Modifier.weight(1f)) {
                                        PhoneActions.message(context, primary)
                                    }
                                }
                                c.emails.firstOrNull()?.let { e ->
                                    Action("E-posta", Icons.Outlined.Email, Modifier.weight(1f)) { mail(context, e.address) }
                                }
                            }
                        }
                    }
                }
                if (c.phones.isNotEmpty()) {
                    item(key = "phones") {
                        Label("Telefon")
                        MyOsCard {
                            c.phones.forEachIndexed { i, p ->
                                val label =
                                    ContactsContract.CommonDataKinds.Phone
                                        .getTypeLabel(context.resources, p.type, p.label)
                                        .toString()
                                InfoRow(
                                    primary = formatPhone(p.number),
                                    secondary = label,
                                    onClick = { calls.call(p.number) },
                                    trailing = Icons.AutoMirrored.Outlined.Message,
                                    trailingDescription = "Mesaj gönder",
                                    onTrailing = { PhoneActions.message(context, p.number) },
                                )
                                if (i != c.phones.lastIndex) Divider()
                            }
                        }
                    }
                }
                if (c.emails.isNotEmpty()) {
                    item(key = "emails") {
                        Label("E-posta")
                        MyOsCard {
                            c.emails.forEachIndexed { i, e ->
                                val label =
                                    ContactsContract.CommonDataKinds.Email
                                        .getTypeLabel(context.resources, e.type, e.label)
                                        .toString()
                                InfoRow(primary = e.address, secondary = label, onClick = { mail(context, e.address) })
                                if (i != c.emails.lastIndex) Divider()
                            }
                        }
                    }
                }
                if (history.isNotEmpty()) {
                    item(key = "history") {
                        Label("Son aramalar")
                        MyOsCard {
                            history.forEachIndexed { i, call ->
                                HistoryRow(call)
                                if (i != history.lastIndex) Divider(start = 34.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && c != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Kişi silinsin mi?") },
            text = { Text("${c.name} rehberden ve eşitlenen hesaptan silinecek. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    withWrite {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { ContactsStore.delete(context, c.id) }
                            if (ok) onBack() else snackbar.showSnackbar("Silinemedi")
                        }
                    }
                }) { Text("Sil", color = myOsDanger()) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("İptal") } },
            shape = MyOsCardShape,
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
    calls.dialogs()
}

private fun mail(context: android.content.Context, address: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Divider(start: androidx.compose.ui.unit.Dp = 0.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = start),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

@Composable
private fun Action(
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** 64dp two-line row inside a card: value over its grey type, optional trailing icon. */
@Composable
private fun InfoRow(
    primary: String,
    secondary: String,
    onClick: () -> Unit,
    trailing: ImageVector? = null,
    trailingDescription: String? = null,
    onTrailing: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                primary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                secondary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Box(
                Modifier.size(40.dp).clickable(onClick = onTrailing),
                contentAlignment = Alignment.Center,
            ) {
                Icon(trailing, trailingDescription, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
