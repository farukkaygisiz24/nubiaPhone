package com.nubiaphone.dialer.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

private val TR = Locale("tr", "TR")

/** One row of the contact list. */
data class ContactSummary(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
    /** Every phone number of the contact (for search and "Ara" from favourites). */
    val numbers: List<String>,
) {
    /** Section letter in the Turkish alphabet; digits and symbols go under "#". */
    val section: String
        get() {
            val c = name.trim().firstOrNull() ?: return "#"
            return if (c.isLetter()) c.toString().uppercase(TR) else "#"
        }
}

data class PhoneItem(
    val dataId: Long?,
    val number: String,
    val type: Int,
    val label: String?,
)

data class EmailItem(
    val dataId: Long?,
    val address: String,
    val type: Int,
    val label: String?,
)

data class ContactDetail(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val givenName: String,
    val familyName: String,
    val photoUri: String?,
    val starred: Boolean,
    val phones: List<PhoneItem>,
    val emails: List<EmailItem>,
    /** Raw contact our editor writes to (the first writable one). */
    val rawContactId: Long?,
    val nameDataId: Long?,
    val accountLabel: String?,
)

/** Where a new contact is saved: a sync account seen on the phone, or the device itself. */
data class SaveTarget(
    val accountType: String?,
    val accountName: String?,
) {
    val label: String
        get() =
            when {
                accountType == null -> "Telefon"
                accountType == "com.google" -> "Google · $accountName"
                else -> accountName ?: accountType
            }
}

/**
 * Process-wide view of the phone book (ContactsContract). Read-only list with live reload;
 * writes (save, star, delete) are explicit calls the UI makes after the user asks for them.
 */
object ContactsStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _contacts = MutableStateFlow<List<ContactSummary>?>(null)

    /** Sorted with the Turkish collator; null until the first load finishes. */
    val contacts: StateFlow<List<ContactSummary>?> = _contacts.asStateFlow()

    private var observing = false
    private var pending: Job? = null

    fun canRead(context: Context): Boolean = granted(context, Manifest.permission.READ_CONTACTS)

    fun canWrite(context: Context): Boolean = granted(context, Manifest.permission.WRITE_CONTACTS)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun refresh(context: Context) {
        val app = context.applicationContext
        observe(app)
        pending?.cancel()
        pending =
            scope.launch {
                _contacts.value = if (canRead(app)) runCatching { query(app) }.getOrDefault(emptyList()) else emptyList()
            }
    }

    fun byId(id: Long): ContactSummary? = _contacts.value?.firstOrNull { it.id == id }

    private fun observe(app: Context) {
        if (observing) return
        observing = true
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    pending?.cancel()
                    pending =
                        scope.launch {
                            delay(300)
                            refresh(app)
                        }
                }
            }
        runCatching {
            app.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        }
    }

    private fun query(context: Context): List<ContactSummary> {
        val resolver = context.contentResolver
        val numbers = HashMap<Long, MutableList<String>>()
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.NUMBER),
            null,
            null,
            "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone.IS_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(1) ?: continue
                val list = numbers.getOrPut(c.getLong(0)) { ArrayList() }
                if (list.none { numberKey(it) == numberKey(n) }) list += n
            }
        }
        val out = ArrayList<ContactSummary>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED,
            ),
            "${ContactsContract.Contacts.IN_VISIBLE_GROUP}=1",
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                out +=
                    ContactSummary(
                        id = id,
                        lookupKey = c.getString(1).orEmpty(),
                        name = name,
                        photoUri = c.getString(3),
                        starred = c.getInt(4) == 1,
                        numbers = numbers[id].orEmpty(),
                    )
            }
        }
        val collator = Collator.getInstance(TR)
        // "#" first (like the launcher's index), then letters in Turkish order (Ç after C, Ş after S…).
        return out.sortedWith(compareBy<ContactSummary> { it.section != "#" }.thenComparator { a, b -> collator.compare(a.name, b.name) })
    }

    /** Full card for the detail and edit screens. Runs on the caller's (IO) thread. */
    fun detail(context: Context, contactId: Long): ContactDetail? {
        if (!canRead(context)) return null
        val resolver = context.contentResolver
        val base =
            resolver.query(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                arrayOf(
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_URI,
                    ContactsContract.Contacts.STARRED,
                ),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) arrayOf(c.getString(0), c.getString(1), c.getString(2), c.getInt(3).toString()) else null
            } ?: return null

        var rawId: Long? = null
        var account: String? = null
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME,
            ),
            "${ContactsContract.RawContacts.CONTACT_ID}=? AND ${ContactsContract.RawContacts.DELETED}=0",
            arrayOf(contactId.toString()),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                rawId = c.getLong(0)
                account = SaveTarget(c.getString(1), c.getString(2)).label
            }
        }

        var given = ""
        var family = ""
        var nameId: Long? = null
        val phones = ArrayList<PhoneItem>()
        val emails = ArrayList<EmailItem>()
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
                ContactsContract.Data.DATA3,
                ContactsContract.Data.RAW_CONTACT_ID,
            ),
            "${ContactsContract.Data.CONTACT_ID}=?",
            arrayOf(contactId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                when (c.getString(1)) {
                    StructuredName.CONTENT_ITEM_TYPE ->
                        if (nameId == null || c.getLong(5) == rawId) {
                            nameId = id
                            // DATA2 = given, DATA3 = family.
                            given = c.getString(3).orEmpty()
                            family = c.getString(4).orEmpty()
                        }
                    Phone.CONTENT_ITEM_TYPE -> {
                        val n = c.getString(2) ?: continue
                        if (phones.none { numberKey(it.number) == numberKey(n) }) {
                            phones += PhoneItem(id, n, c.getInt(3), c.getString(4))
                        }
                    }
                    Email.CONTENT_ITEM_TYPE -> {
                        val a = c.getString(2) ?: continue
                        if (emails.none { it.address.equals(a, ignoreCase = true) }) {
                            emails += EmailItem(id, a, c.getInt(3), c.getString(4))
                        }
                    }
                }
            }
        }
        return ContactDetail(
            id = contactId,
            lookupKey = base[0].orEmpty(),
            name = base[1] ?: listOf(given, family).filter { it.isNotBlank() }.joinToString(" "),
            givenName = given,
            familyName = family,
            photoUri = base[2],
            starred = base[3] == "1",
            phones = phones,
            emails = emails,
            rawContactId = rawId,
            nameDataId = nameId,
            accountLabel = account,
        )
    }

    /** Accounts that already hold contacts, most used first, plus the device itself. */
    fun saveTargets(context: Context): List<SaveTarget> {
        val counts = LinkedHashMap<SaveTarget, Int>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.ACCOUNT_NAME),
                "${ContactsContract.RawContacts.DELETED}=0",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(0)
                    // SIM and other read-only/system stores are not offered.
                    if (type != null && (type.contains("sim", ignoreCase = true) || type.contains("whatsapp"))) continue
                    val t = SaveTarget(type, c.getString(1))
                    counts[t] = (counts[t] ?: 0) + 1
                }
            }
        }
        val sorted = counts.entries.sortedByDescending { it.value }.map { it.key }.toMutableList()
        if (sorted.none { it.accountType == null }) sorted += SaveTarget(null, null)
        return sorted
    }

    /** Edit-form state → provider. New contact when [existing] is null. Returns the contact id. */
    fun save(
        context: Context,
        existing: ContactDetail?,
        target: SaveTarget,
        givenName: String,
        familyName: String,
        phones: List<PhoneItem>,
        emails: List<EmailItem>,
    ): Long? {
        val ops = ArrayList<ContentProviderOperation>()
        val rawId = existing?.rawContactId
        // Back-reference index of the new raw contact (only used when inserting).
        val rawRef = 0

        fun insertData(): ContentProviderOperation.Builder =
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).let {
                if (rawId != null) it.withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId) else it.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawRef)
            }

        if (existing == null || rawId == null) {
            ops +=
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, target.accountType)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, target.accountName)
                    .build()
        }
        val effectiveRaw = if (existing == null) null else rawId

        // Name
        if (existing?.nameDataId != null && effectiveRaw != null) {
            ops +=
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection("${ContactsContract.Data._ID}=?", arrayOf(existing.nameDataId.toString()))
                    .withValue(StructuredName.GIVEN_NAME, givenName)
                    .withValue(StructuredName.FAMILY_NAME, familyName)
                    .withValue(StructuredName.DISPLAY_NAME, null)
                    .build()
        } else {
            ops +=
                insertData()
                    .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.GIVEN_NAME, givenName)
                    .withValue(StructuredName.FAMILY_NAME, familyName)
                    .build()
        }

        // Phones: delete removed rows, update kept ones, insert new ones.
        val keptPhoneIds = phones.mapNotNull { it.dataId }.toSet()
        existing?.phones?.filter { it.dataId != null && it.dataId !in keptPhoneIds }?.forEach {
            ops += ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data._ID}=?", arrayOf(it.dataId.toString())).build()
        }
        phones.filter { it.number.isNotBlank() }.forEach { p ->
            ops +=
                if (p.dataId != null && effectiveRaw != null) {
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection("${ContactsContract.Data._ID}=?", arrayOf(p.dataId.toString()))
                        .withValue(Phone.NUMBER, p.number.trim())
                        .withValue(Phone.TYPE, p.type)
                        .withValue(Phone.LABEL, p.label)
                        .build()
                } else {
                    insertData()
                        .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.NUMBER, p.number.trim())
                        .withValue(Phone.TYPE, p.type)
                        .withValue(Phone.LABEL, p.label)
                        .build()
                }
        }

        // E-mails, same rules.
        val keptEmailIds = emails.mapNotNull { it.dataId }.toSet()
        existing?.emails?.filter { it.dataId != null && it.dataId !in keptEmailIds }?.forEach {
            ops += ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data._ID}=?", arrayOf(it.dataId.toString())).build()
        }
        emails.filter { it.address.isNotBlank() }.forEach { e ->
            ops +=
                if (e.dataId != null && effectiveRaw != null) {
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection("${ContactsContract.Data._ID}=?", arrayOf(e.dataId.toString()))
                        .withValue(Email.ADDRESS, e.address.trim())
                        .withValue(Email.TYPE, e.type)
                        .build()
                } else {
                    insertData()
                        .withValue(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                        .withValue(Email.ADDRESS, e.address.trim())
                        .withValue(Email.TYPE, e.type)
                        .build()
                }
        }

        val results =
            runCatching { context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops) }.getOrNull()
                ?: return null
        if (existing != null && rawId != null) return existing.id
        val newRaw = results.firstOrNull()?.uri?.let { ContentUris.parseId(it) } ?: return null
        return context.contentResolver.query(
            ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, newRaw),
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            null,
            null,
            null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }

    fun setStarred(context: Context, contactId: Long, starred: Boolean): Boolean =
        runCatching {
            context.contentResolver.update(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                android.content.ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) },
                null,
                null,
            ) > 0
        }.getOrDefault(false)

    /** Irreversible from this app's point of view. Always confirmed in the UI first. */
    fun delete(context: Context, contactId: Long): Boolean =
        runCatching {
            context.contentResolver.delete(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                null,
                null,
            ) > 0
        }.getOrDefault(false)

    fun vcardUri(lookupKey: String): Uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)
}
