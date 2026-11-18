package com.nubiaphone.dialer.ui.common

import android.provider.CallLog
import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TR = Locale("tr", "TR")

fun callTypeIcon(type: Int): ImageVector =
    when (type) {
        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Filled.CallMissed
        CallLog.Calls.BLOCKED_TYPE -> Icons.Filled.Block
        CallLog.Calls.VOICEMAIL_TYPE -> Icons.Filled.Voicemail
        else -> Icons.AutoMirrored.Filled.CallReceived
    }

fun callTypeLabel(type: Int): String =
    when (type) {
        CallLog.Calls.OUTGOING_TYPE -> "Giden arama"
        CallLog.Calls.MISSED_TYPE -> "Cevapsız arama"
        CallLog.Calls.REJECTED_TYPE -> "Reddedilen arama"
        CallLog.Calls.BLOCKED_TYPE -> "Engellenen arama"
        CallLog.Calls.VOICEMAIL_TYPE -> "Sesli mesaj"
        else -> "Gelen arama"
    }

/** Missed, rejected and blocked calls are drawn in the danger colour. */
fun isMissedLike(type: Int): Boolean =
    type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE || type == CallLog.Calls.BLOCKED_TYPE

/** Section header: "Bugün", "Dün", "12 Kasım", other years "12 Kasım 2025". */
fun dayHeader(dayStart: Long): String {
    if (DateUtils.isToday(dayStart)) return "Bugün"
    if (DateUtils.isToday(dayStart + DateUtils.DAY_IN_MILLIS)) return "Dün"
    val then = Calendar.getInstance().apply { timeInMillis = dayStart }
    val now = Calendar.getInstance()
    val pattern = if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "d MMMM" else "d MMMM yyyy"
    return SimpleDateFormat(pattern, TR).format(Date(dayStart))
}

/** Mixed-day lists: today → "17:41", otherwise "12.11.2026" (TASARIM.md › Liste). */
fun listTime(millis: Long): String =
    if (DateUtils.isToday(millis)) timeOfDay(millis) else DateFormat.getDateInstance(DateFormat.SHORT, TR).format(Date(millis))

fun timeOfDay(millis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT, TR).format(Date(millis))

/** Detail screen: "14 Kasım 2026, 17:41". */
fun longDateTime(millis: Long): String = SimpleDateFormat("d MMMM yyyy, HH:mm", TR).format(Date(millis))

/** "45 sn", "2 dk 13 sn", "1 sa 4 dk". */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "$h sa $m dk"
        m > 0 -> "$m dk $s sn"
        else -> "$s sn"
    }
}
