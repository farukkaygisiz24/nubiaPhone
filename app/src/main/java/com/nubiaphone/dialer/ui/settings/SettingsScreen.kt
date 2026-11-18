package com.nubiaphone.dialer.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nubiaphone.dialer.BuildConfig
import com.nubiaphone.dialer.data.BlockedNumbers
import com.nubiaphone.dialer.data.formatPhone
import com.nubiaphone.dialer.telecom.DefaultDialer
import com.nubiaphone.dialer.ui.components.MyOsCard
import com.nubiaphone.dialer.ui.components.MyOsSnackbarHost
import com.nubiaphone.dialer.ui.components.UndoDuration
import com.nubiaphone.dialer.ui.components.NubiaPhoneTopBar
import com.nubiaphone.dialer.ui.components.myOsBounce
import com.nubiaphone.dialer.ui.components.myOsSearchContainer
import com.nubiaphone.dialer.ui.theme.MyOsCardShape
import com.nubiaphone.dialer.ui.theme.MyOsSearchShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ayarlar-style settings: card groups of 56/64dp rows with a chevron. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onBlocked: () -> Unit,
) {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(DefaultDialer.isDefault(context)) }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = DefaultDialer.isDefault(context) }
    LifecycleResumeEffect(Unit) {
        isDefault = DefaultDialer.isDefault(context)
        onPauseOrDispose { }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            NubiaPhoneTopBar(title = "Ayarlar", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).myOsBounce(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    MyOsCard {
                        SettingRow(
                            "Varsayılan telefon uygulaması",
                            if (isDefault) "Telefon (bu uygulama)" else "Başka bir uygulama · Değiştirmek için dokunun",
                        ) {
                            if (isDefault) {
                                open(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                            } else {
                                DefaultDialer.requestIntent(context)?.let { role.launch(it) }
                            }
                        }
                    }
                }
                item {
                    MyOsCard {
                        SettingRow(
                            "Engellenen numaralar",
                            if (isDefault) null else "Varsayılan telefon uygulaması olunca kullanılabilir",
                            onClick = onBlocked,
                        )
                        Line()
                        SettingRow("Arama hesapları ve SIM", null) {
                            open(context, Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
                        }
                        Line()
                        SettingRow("Operatör arama ayarları", "Yönlendirme, bekletme, arayan kimliği") {
                            open(context, Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS))
                        }
                        Line()
                        SettingRow("Ses ve titreşim", "Zil sesi, tuşlama sesleri") {
                            open(context, Intent(Settings.ACTION_SOUND_SETTINGS))
                        }
                    }
                }
                item {
                    MyOsCard {
                        SettingRow("Hakkında", "Telefon ${BuildConfig.VERSION_NAME}", chevron = false) {}
                    }
                }
            }
        }
    }
}

/** System block list; editable only while this is the default phone app. */
@Composable
fun BlockedNumbersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var version by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    val available by produceState(false, version) { value = withContext(Dispatchers.IO) { BlockedNumbers.available(context) } }
    val list by produceState(emptyList<BlockedNumbers.Entry>(), version) {
        value = withContext(Dispatchers.IO) { BlockedNumbers.list(context) }
    }

    fun unblock(number: String) {
        scope.launch {
            withContext(Dispatchers.IO) { BlockedNumbers.unblock(context, number) }
            version++
            val r = snackbar.showSnackbar("Engel kaldırıldı", actionLabel = "Geri al", duration = UndoDuration)
            if (r == SnackbarResult.ActionPerformed) {
                withContext(Dispatchers.IO) { BlockedNumbers.block(context, number) }
                version++
            }
        }
    }

    Scaffold(snackbarHost = { MyOsSnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            NubiaPhoneTopBar(
                title = "Engellenen numaralar",
                onBack = onBack,
                actions = {
                    if (available) {
                        IconButton(onClick = { adding = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Numara engelle", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                },
            )
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).myOsBounce(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            ) {
                item {
                    Text(
                        if (available) {
                            "Bu numaralardan gelen aramalar ve mesajlar size ulaşmaz."
                        } else {
                            "Engel listesini yalnızca varsayılan telefon uygulaması görebilir. Ayarlar'dan Telefon'u varsayılan yapın."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                    )
                }
                if (available) {
                    item {
                        MyOsCard {
                            if (list.isEmpty()) {
                                Text(
                                    "Engellenen numara yok. Sağ üstteki + ile ekleyin.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )
                            }
                            list.forEachIndexed { i, e ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        formatPhone(e.number),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { unblock(e.number) }) {
                                        Text("Engeli kaldır", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                    }
                                }
                                if (i != list.lastIndex) Line()
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        var number by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Numara engelle") },
            text = {
                BasicTextField(
                    value = number,
                    onValueChange = { number = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .then(Modifier.padding(0.dp)),
                    decorationBox = { inner ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .background(myOsSearchContainer(), MyOsSearchShape)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (number.isEmpty()) {
                                Text("Telefon numarası", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = number.any { it.isDigit() },
                    onClick = {
                        adding = false
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { BlockedNumbers.block(context, number.trim()) }
                            version++
                            if (!ok) snackbar.showSnackbar("Engellenemedi")
                        }
                    },
                ) { Text("Engelle") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("İptal") } },
            shape = MyOsCardShape,
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
}

@Composable
private fun Line() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle == null) 56.dp else 64.dp)
            .clickable(enabled = chevron, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Spacer(Modifier.size(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
