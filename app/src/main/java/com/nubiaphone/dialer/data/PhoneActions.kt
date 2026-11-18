package com.nubiaphone.dialer.data

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/** Package of the sibling SMS app; "Mesaj gönder" prefers it when it is installed. */
private const val NUBIA_SMS = "com.nubiasms.messages"

object PhoneActions {
    fun canCall(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Places a real call through Telecom. Callers must have CALL_PHONE (see [canCall]);
     * the UI asks for it first. Until nubiaPhone is the default dialer (phase 4) the
     * in-call screen is the system one.
     */
    fun call(context: Context, number: String) {
        val tm = context.getSystemService(TelecomManager::class.java) ?: return
        try {
            tm.placeCall(Uri.fromParts("tel", number, null), null)
        } catch (_: SecurityException) {
        }
    }

    /** Calls the SIM's voicemail number (long press on 1). Needs CALL_PHONE. */
    fun voicemail(context: Context) {
        val tm = context.getSystemService(TelecomManager::class.java) ?: return
        try {
            tm.placeCall(Uri.fromParts("voicemail", "", null), null)
        } catch (_: SecurityException) {
        }
    }

    fun message(context: Context, number: String) {
        val base = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
        val toNubia = Intent(base).setPackage(NUBIA_SMS)
        val intent = if (toNubia.resolveActivity(context.packageManager) != null) toNubia else base
        start(context, intent)
    }

    /**
     * Opens the WhatsApp chat with [number] (WhatsApp, then WhatsApp Business). Returns false
     * when neither is installed. Nothing is sent: WhatsApp only opens the conversation.
     */
    fun whatsapp(context: Context, number: String): Boolean {
        val phone = internationalDigits(number) ?: return false
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone")
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
            if (intent.resolveActivity(context.packageManager) != null) {
                start(context, intent)
                return true
            }
        }
        return false
    }

    /** "0532 123 45 67" / "+90 532…" / "0090…" → "905321234567" (Turkey assumed for local numbers). */
    internal fun internationalDigits(number: String): String? {
        val digits = number.filter { it.isDigit() }
        if (digits.length < 7) return null
        return when {
            number.trim().startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            digits.startsWith("0") -> "90" + digits.drop(1)
            digits.length == 10 -> "90$digits"
            else -> digits
        }
    }

    fun addContact(context: Context, number: String) {
        start(
            context,
            Intent(ContactsContract.Intents.Insert.ACTION)
                .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                .putExtra(ContactsContract.Intents.Insert.PHONE, number),
        )
    }

    /** Opens the contact card (system Kişiler until phase 2 brings our own). */
    fun openContact(context: Context, lookupUri: Uri) {
        start(context, Intent(Intent.ACTION_VIEW, lookupUri))
    }

    fun copy(context: Context, number: String) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Numara", number))
    }

    /** Irreversible: removes the given call log rows. Needs WRITE_CALL_LOG. Always confirm first. */
    fun deleteCalls(context: Context, ids: List<Long>): Boolean {
        if (ids.isEmpty()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return runCatching {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} IN (${ids.joinToString(",")})",
                null,
            ) > 0
        }.getOrDefault(false)
    }

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
}
