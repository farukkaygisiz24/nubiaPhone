package com.nubiaphone.dialer.ui.contacts

import android.Manifest
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nubiaphone.dialer.data.ContactDetail
import com.nubiaphone.dialer.data.ContactsStore
import com.nubiaphone.dialer.data.EmailItem
import com.nubiaphone.dialer.data.PhoneItem
import com.nubiaphone.dialer.data.SaveTarget
import com.nubiaphone.dialer.ui.components.MyOsCard
import com.nubiaphone.dialer.ui.components.MyOsMenuItem
import com.nubiaphone.dialer.ui.components.MyOsPopupMenu
import com.nubiaphone.dialer.ui.components.MyOsSnackbarHost
import com.nubiaphone.dialer.ui.components.myOsBounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PhoneTypes =
    listOf(
        Phone.TYPE_MOBILE to "Mobil",
        Phone.TYPE_HOME to "Ev",
        Phone.TYPE_WORK to "İş",
        Phone.TYPE_OTHER to "Diğer",
    )
private val EmailTypes =
    listOf(
        Email.TYPE_HOME to "Kişisel",
        Email.TYPE_WORK to "İş",
        Email.TYPE_OTHER to "Diğer",
    )

private fun phoneTypeName(type: Int, label: String?) =
    PhoneTypes.firstOrNull { it.first == type }?.second ?: label ?: "Diğer"

private fun emailTypeName(type: Int, label: String?) =
    EmailTypes.firstOrNull { it.first == type }?.second ?: label ?: "Diğer"

/**
 * New / edit contact. Name, phones (with type), e-mails, and for a new contact the account it
 * is saved to. Only these fields are written; anything else on the contact is left untouched.
 * [contactId] null = new contact, prefilled with [number] when it comes from the call log.
 */
@Composable
fun ContactEditScreen(
    contactId: Long?,
    number: String?,
    onClose: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val isNew = contactId == null

    var loaded by remember { mutableStateOf(isNew) }
    var existing by remember { mutableStateOf<ContactDetail?>(null) }
    var given by remember { mutableStateOf("") }
    var family by remember { mutableStateOf("") }
    val phones = remember { mutableStateListOf<PhoneItem>() }
    val emails = remember { mutableStateListOf<EmailItem>() }
    var targets by remember { mutableStateOf<List<SaveTarget>>(emptyList()) }
    var target by remember { mutableStateOf<SaveTarget?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(contactId) {
        if (contactId != null) {
            val d = withContext(Dispatchers.IO) { ContactsStore.detail(context, contactId) }
            existing = d
            if (d != null) {
                given = d.givenName.ifBlank { if (d.familyName.isBlank()) d.name else "" }
                family = d.familyName
                phones.addAll(d.phones)
                emails.addAll(d.emails)
            }
            loaded = true
        } else {
            phones.add(PhoneItem(null, number.orEmpty(), Phone.TYPE_MOBILE, null))
            targets = withContext(Dispatchers.IO) { ContactsStore.saveTargets(context) }
            target = targets.firstOrNull()
        }
    }

    fun doSave() {
        if (saving) return
        saving = true
        scope.launch {
            val id =
                withContext(Dispatchers.IO) {
                    ContactsStore.save(
                        context,
                        existing,
                        target ?: SaveTarget(null, null),
                        given.trim(),
                        family.trim(),
                        phones.toList(),
                        emails.toList(),
                    )
                }
            saving = false
            if (id != null) {
                ContactsStore.refresh(context)
                onSaved(id)
            } else {
                snackbar.showSnackbar("Kaydedilemedi")
            }
        }
    }

    val writePermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) doSave() else scope.launch { snackbar.showSnackbar("Kişi kaydetmek için izin gerekli") }
        }
    val canSave =
        loaded && !saving && (given.isNotBlank() || family.isNotBlank() || phones.any { it.number.isNotBlank() })

    Scaffold(
        snackbarHost = { MyOsSnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            // "×" · title · "Kaydet" (accent text button), like the selection header.
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Vazgeç", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    if (isNew) "Yeni kişi" else "Kişiyi düzenle",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = canSave,
                    onClick = {
                        if (ContactsStore.canWrite(context)) doSave() else writePermission.launch(Manifest.permission.WRITE_CONTACTS)
                    },
                ) {
                    Text(
                        "Kaydet",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (canSave) 1f else 0.35f),
                    )
                }
            }
            if (!loaded) return@Column
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).myOsBounce(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            ) {
                item(key = "name") {
                    MyOsCard {
                        Field(given, { given = it }, "Ad")
                        Line()
                        Field(family, { family = it }, "Soyad")
                    }
                }
                item(key = "phones") {
                    Label("Telefon")
                    MyOsCard {
                        phones.forEachIndexed { i, p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TypeChip(
                                    text = phoneTypeName(p.type, p.label),
                                    options = PhoneTypes,
                                    onPick = { phones[i] = p.copy(type = it, label = null) },
                                )
                                Field(
                                    p.number,
                                    { phones[i] = p.copy(number = it) },
                                    "Telefon numarası",
                                    keyboard = KeyboardType.Phone,
                                    modifier = Modifier.weight(1f),
                                )
                                RemoveButton { phones.removeAt(i) }
                            }
                            Line()
                        }
                        AddRow("Telefon ekle") { phones.add(PhoneItem(null, "", Phone.TYPE_MOBILE, null)) }
                    }
                }
                item(key = "emails") {
                    Label("E-posta")
                    MyOsCard {
                        emails.forEachIndexed { i, e ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TypeChip(
                                    text = emailTypeName(e.type, e.label),
                                    options = EmailTypes,
                                    onPick = { emails[i] = e.copy(type = it, label = null) },
                                )
                                Field(
                                    e.address,
                                    { emails[i] = e.copy(address = it) },
                                    "E-posta adresi",
                                    keyboard = KeyboardType.Email,
                                    modifier = Modifier.weight(1f),
                                )
                                RemoveButton { emails.removeAt(i) }
                            }
                            Line()
                        }
                        AddRow("E-posta ekle") { emails.add(EmailItem(null, "", Email.TYPE_HOME, null)) }
                    }
                }
                if (isNew && targets.size > 1) {
                    item(key = "target") {
                        Label("Kaydedilecek yer")
                        MyOsCard {
                            var open by remember { mutableStateOf(false) }
                            Box {
                                Row(
                                    Modifier.fillMaxWidth().height(52.dp).clickable { open = true },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        target?.label.orEmpty(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                MyOsPopupMenu(
                                    expanded = open,
                                    onDismiss = { open = false },
                                    items = targets.map { t -> MyOsMenuItem(t.label) { target = t } },
                                    dark = isSystemInDarkTheme(),
                                    alignEnd = true,
                                )
                            }
                        }
                    }
                }
                existing?.accountLabel?.let { account ->
                    item(key = "account") {
                        Text(
                            "Kaydedildiği yer: $account",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 16.dp),
                        )
                    }
                }
            }
        }
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
private fun Line() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

/** 52dp text row inside a card; grey placeholder like the search field. */
@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.height(52.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = style.copy(color = if (isSystemInDarkTheme()) placeholderDark else placeholderLight))
                }
                inner()
            }
        },
    )
}

private val placeholderDark = androidx.compose.ui.graphics.Color(0xFF888888)
private val placeholderLight = androidx.compose.ui.graphics.Color(0xFF9A9A9A)

@Composable
private fun TypeChip(
    text: String,
    options: List<Pair<Int, String>>,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.width(76.dp).height(52.dp).clickable { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        MyOsPopupMenu(
            expanded = open,
            onDismiss = { open = false },
            items = options.map { (type, name) -> MyOsMenuItem(name) { onPick(type) } },
            dark = isSystemInDarkTheme(),
            alignEnd = false,
        )
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = "Kaldır", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AddRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}
