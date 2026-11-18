package com.nubiaphone.dialer.ui.recents

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SnackbarResult
import com.nubiaphone.dialer.data.BlockedNumbers
import com.nubiaphone.dialer.data.CallGroup
import com.nubiaphone.dialer.data.CallLogStore
import com.nubiaphone.dialer.data.PhoneActions
import com.nubiaphone.dialer.ui.components.UndoDuration
import com.nubiaphone.dialer.ui.components.myOsDanger
import com.nubiaphone.dialer.ui.navigation.LocalContactNav
import com.nubiaphone.dialer.ui.theme.MyOsCardShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared behaviour behind call rows (home list, search, details): calling with the CALL_PHONE
 * prompt when needed, messaging via nubiaSMS, and the always-confirmed, irreversible delete.
 * Put [Dialogs] once in the screen that uses it.
 */
class CallController internal constructor(
    val actions: CallRowActions,
    val call: (String) -> Unit,
    /** Long press on 1. Same CALL_PHONE prompt as [call]. */
    val voicemail: () -> Unit,
    /** Opens the WhatsApp chat for a number, or says WhatsApp is not installed. */
    val whatsapp: (String) -> Unit,
    /** Ask, then delete every call in [groups] (selection mode). */
    val deleteMany: (List<CallGroup>) -> Unit,
    val dialogs: @Composable () -> Unit,
)

@Composable
fun rememberCallController(
    snackbar: SnackbarHostState,
    onInfo: (CallGroup) -> Unit,
    onDeleted: () -> Unit = {},
): CallController {
    val context = LocalContext.current
    val contactNav = LocalContactNav.current
    val scope = rememberCoroutineScope()
    var pendingNumber by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<List<CallGroup>?>(null) }

    val callPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val number = pendingNumber
            pendingNumber = null
            if (granted && number != null) {
                PhoneActions.call(context, number)
            } else if (!granted) {
                scope.launch { snackbar.showSnackbar("Arama yapmak için telefon izni gerekli") }
            }
        }

    fun call(number: String) {
        if (number.isBlank()) return
        if (PhoneActions.canCall(context)) {
            PhoneActions.call(context, number)
        } else {
            pendingNumber = number
            callPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    fun whatsapp(number: String) {
        if (number.isBlank()) return
        if (!PhoneActions.whatsapp(context, number)) {
            scope.launch { snackbar.showSnackbar("WhatsApp yüklü değil") }
        }
    }

    fun voicemail() {
        if (PhoneActions.canCall(context)) {
            PhoneActions.voicemail(context)
        } else {
            scope.launch { snackbar.showSnackbar("Sesli mesaj için telefon izni gerekli") }
        }
    }

    val actions =
        CallRowActions(
            onCall = { call(it.number) },
            onInfo = onInfo,
            onMessage = { PhoneActions.message(context, it.number) },
            onCopy = {
                PhoneActions.copy(context, it.number)
                // Android 13+ shows its own "copied" confirmation.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    scope.launch { snackbar.showSnackbar("Numara kopyalandı") }
                }
            },
            onContact = { group ->
                val contact = group.contact
                if (contact != null) contactNav.open(contact.contactId) else contactNav.add(group.number)
            },
            onDelete = { confirmDelete = listOf(it) },
            onWhatsApp = { whatsapp(it.number) },
            onBlock = { group, block ->
                scope.launch {
                    val n = group.number
                    val ok = withContext(Dispatchers.IO) { if (block) BlockedNumbers.block(context, n) else BlockedNumbers.unblock(context, n) }
                    if (!ok) {
                        snackbar.showSnackbar(if (block) "Engellenemedi" else "Engel kaldırılamadı")
                        return@launch
                    }
                    val r = snackbar.showSnackbar(if (block) "Numara engellendi" else "Engel kaldırıldı", actionLabel = "Geri al", duration = UndoDuration)
                    if (r == SnackbarResult.ActionPerformed) {
                        withContext(Dispatchers.IO) { if (block) BlockedNumbers.unblock(context, n) else BlockedNumbers.block(context, n) }
                    }
                }
            },
        )

    val dialogs: @Composable () -> Unit = {
        confirmDelete?.let { groups ->
            val n = groups.sumOf { it.calls.size }
            val single = groups.singleOrNull()
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text("Geçmişten silinsin mi?") },
                text = {
                    Text(
                        (if (single != null) "${single.title} ile $n arama" else "$n arama") +
                            " arama geçmişinden silinecek. Bu işlem geri alınamaz.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = null
                        scope.launch {
                            val ok =
                                withContext(Dispatchers.IO) {
                                    PhoneActions.deleteCalls(context, groups.flatMap { g -> g.calls.map { it.id } })
                                }
                            if (ok) {
                                CallLogStore.refresh(context)
                                onDeleted()
                            }
                            snackbar.showSnackbar(if (ok) "Silindi" else "Silinemedi: arama geçmişi izni gerekli")
                        }
                    }) { Text("Sil", color = myOsDanger()) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = null }) { Text("İptal") }
                },
                shape = MyOsCardShape,
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }
    }

    return CallController(actions, ::call, ::voicemail, ::whatsapp, { confirmDelete = it }, dialogs)
}
