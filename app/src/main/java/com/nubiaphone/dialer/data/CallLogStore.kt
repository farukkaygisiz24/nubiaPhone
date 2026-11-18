package com.nubiaphone.dialer.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
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
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/** One row of the system call log. */
data class CallEntry(
    val id: Long,
    val number: String,
    val type: Int,
    val dateMillis: Long,
    val durationSec: Long,
    val cachedName: String?,
    val geocoded: String?,
    /** CallLog.Calls.NUMBER_PRESENTATION: private / unknown / payphone numbers have no callable number. */
    val presentation: Int,
    val phoneAccountId: String?,
) {
    val isHidden: Boolean
        get() = presentation != CallLog.Calls.PRESENTATION_ALLOWED || number.isBlank()
}

data class ContactInfo(
    val contactId: Long,
    val name: String,
    val lookupUri: Uri?,
    val photoUri: String?,
    /** "Mobil", "Ev", … in the system language. */
    val numberLabel: String?,
)

/**
 * Consecutive calls with the same number on the same day, newest first — one list row.
 * [calls] is never empty.
 */
data class CallGroup(
    val key: String,
    val calls: List<CallEntry>,
    val contact: ContactInfo?,
) {
    val latest: CallEntry get() = calls.first()
    val number: String get() = latest.number
    val isHidden: Boolean get() = latest.isHidden
    val isMissed: Boolean get() = latest.type == CallLog.Calls.MISSED_TYPE

    val title: String
        get() =
            when {
                isHidden -> "Gizli numara"
                contact != null -> contact.name
                !latest.cachedName.isNullOrBlank() -> latest.cachedName.orEmpty()
                else -> formatPhone(number)
            }
}

/** Calls of one day under a "Bugün" / "Dün" / date header. */
data class CallDay(
    val dayStart: Long,
    val groups: List<CallGroup>,
)

/**
 * Process-wide, read-only view of the call log. Loads off the main thread, reloads itself when
 * the call log or the phone book changes, and resolves names/photos through PhoneLookup (the
 * call log's own cached name goes stale when a contact is renamed).
 */
object CallLogStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _days = MutableStateFlow<List<CallDay>?>(null)

    /** null until the first load finishes. */
    val days: StateFlow<List<CallDay>?> = _days.asStateFlow()

    @Volatile private var entries: List<CallEntry> = emptyList()
    private val contacts = ConcurrentHashMap<String, Optional>()
    private var observing = false
    private var pending: Job? = null

    private class Optional(val value: ContactInfo?)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    /** Start (or re-run) loading; safe to call on every resume and after a permission grant. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        observe(app)
        pending?.cancel()
        pending =
            scope.launch {
                if (!hasPermission(app)) {
                    entries = emptyList()
                    _days.value = emptyList()
                    return@launch
                }
                val loaded = runCatching { query(app) }.getOrDefault(emptyList())
                entries = loaded
                _days.value = groupByDay(loaded) { lookup(app, it) }
            }
    }

    fun group(key: String): CallGroup? =
        _days.value?.firstNotNullOfOrNull { day -> day.groups.firstOrNull { it.key == key } }

    /** All loaded calls with the same number as [number], newest first. */
    fun callsFor(number: String): List<CallEntry> {
        val key = numberKey(number)
        return entries.filter { !it.isHidden && numberKey(it.number) == key }
    }

    fun contactFor(context: Context, number: String): ContactInfo? = lookup(context.applicationContext, number)

    private fun observe(app: Context) {
        if (observing) return
        observing = true
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    contacts.clear()
                    // Coalesce bursts (a call ending writes several rows / contact syncs).
                    pending?.cancel()
                    pending =
                        scope.launch {
                            delay(300)
                            refresh(app)
                        }
                }
            }
        runCatching {
            app.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
            app.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        }
    }

    private fun query(context: Context): List<CallEntry> {
        val uri =
            CallLog.Calls.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "1000")
                .build()
        val projection =
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.GEOCODED_LOCATION,
                CallLog.Calls.NUMBER_PRESENTATION,
                CallLog.Calls.PHONE_ACCOUNT_ID,
            )
        val out = ArrayList<CallEntry>()
        context.contentResolver.query(uri, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            while (c.moveToNext()) {
                out +=
                    CallEntry(
                        id = c.getLong(0),
                        number = c.getString(1).orEmpty(),
                        type = c.getInt(2),
                        dateMillis = c.getLong(3),
                        durationSec = c.getLong(4),
                        cachedName = c.getString(5),
                        geocoded = c.getString(6),
                        presentation = c.getInt(7),
                        phoneAccountId = c.getString(8),
                    )
            }
        }
        return out
    }

    private fun lookup(context: Context, number: String): ContactInfo? {
        if (number.isBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val key = numberKey(number)
        contacts[key]?.let { return it.value }
        val info =
            runCatching {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup._ID,
                        ContactsContract.PhoneLookup.LOOKUP_KEY,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                        ContactsContract.PhoneLookup.TYPE,
                        ContactsContract.PhoneLookup.LABEL,
                    ),
                    null,
                    null,
                    null,
                )?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    val name = c.getString(0) ?: return@use null
                    ContactInfo(
                        contactId = c.getLong(1),
                        name = name,
                        lookupUri = ContactsContract.Contacts.getLookupUri(c.getLong(1), c.getString(2)),
                        photoUri = c.getString(3),
                        numberLabel =
                            if (c.isNull(4)) {
                                null
                            } else {
                                ContactsContract.CommonDataKinds.Phone
                                    .getTypeLabel(context.resources, c.getInt(4), c.getString(5))
                                    ?.toString()
                            },
                    )
                }
            }.getOrNull()
        contacts[key] = Optional(info)
        return info
    }
}

/** Comparison key for phone numbers: digits only, last 10 (so +90 532… == 0532…). */
fun numberKey(number: String): String {
    val digits = PhoneNumberUtils.normalizeNumber(number).filter { it.isDigit() }
    return if (digits.length > 10) digits.takeLast(10) else digits
}

fun formatPhone(number: String): String =
    PhoneNumberUtils.formatNumber(number, "TR") ?: number

private fun startOfDay(millis: Long): Long =
    Calendar.getInstance().run {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

internal fun groupByDay(
    entries: List<CallEntry>,
    lookup: (String) -> ContactInfo?,
): List<CallDay> {
    val days = ArrayList<CallDay>()
    var dayStart = Long.MIN_VALUE
    var dayGroups = ArrayList<CallGroup>()
    var run = ArrayList<CallEntry>()

    fun closeRun() {
        if (run.isEmpty()) return
        val first = run.first()
        val contact = if (first.isHidden) null else lookup(first.number)
        dayGroups += CallGroup(key = "${first.id}", calls = run, contact = contact)
        run = ArrayList()
    }

    fun closeDay() {
        closeRun()
        if (dayGroups.isNotEmpty()) days += CallDay(dayStart, dayGroups)
        dayGroups = ArrayList()
    }

    for (e in entries) {
        val day = startOfDay(e.dateMillis)
        if (day != dayStart) {
            closeDay()
            dayStart = day
        }
        val prev = run.lastOrNull()
        val same =
            prev != null &&
                prev.isHidden == e.isHidden &&
                (e.isHidden || numberKey(prev.number) == numberKey(e.number))
        if (!same) closeRun()
        run += e
    }
    closeDay()
    return days
}
