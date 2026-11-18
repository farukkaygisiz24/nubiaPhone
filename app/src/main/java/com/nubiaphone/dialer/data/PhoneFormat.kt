package com.nubiaphone.dialer.data

import android.telephony.PhoneNumberUtils

/**
 * Display form for Turkish numbers: `0 (532) 123 45 67`.
 * Compact `+90532…` / `90532…` / `532…` become the same national shape.
 */
fun formatPhone(number: String): String {
    val trimmed = number.trim()
    if (trimmed.isEmpty()) return trimmed
    val tr = formatTrDisplay(trimmed)
    if (tr.contains('(')) return tr
    PhoneNumberUtils.formatNumber(trimmed, "TR")?.takeIf { looksFormatted(it) }?.let { return it }
    val national = toTrNationalDigits(trimmed) ?: return trimmed
    return PhoneNumberUtils.formatNumber(national, "TR")?.takeIf { looksFormatted(it) } ?: tr
}

/** Pure TR display helper (also used by JVM unit tests). */
fun formatTrDisplay(number: String): String {
    val national = toTrNationalDigits(number) ?: return number.trim()
    // 0 + 3-digit NDC + 7 subscriber → 0 (XXX) XXX XX XX
    if (national.length != 11 || !national.startsWith('0')) return number.trim()
    return buildString(17) {
        append("0 (")
        append(national, 1, 4)
        append(") ")
        append(national, 4, 7)
        append(' ')
        append(national, 7, 9)
        append(' ')
        append(national, 9, 11)
    }
}

/**
 * Maps a character [range] on [raw] onto the same digits in [formatted]
 * (so T9 / digit highlights survive spacing and `+90` → `0 (…)`)
 */
fun mapDigitRange(raw: String, formatted: String, range: IntRange?): IntRange? {
    if (range == null) return null
    val rawDigitIdx = raw.indices.filter { raw[it].isDigit() }
    val fmtDigitIdx = formatted.indices.filter { formatted[it].isDigit() }
    if (rawDigitIdx.isEmpty() || fmtDigitIdx.isEmpty()) return null
    val firstDigit = rawDigitIdx.indexOfFirst { it >= range.first }
    val lastDigit = rawDigitIdx.indexOfLast { it <= range.last }
    if (firstDigit < 0 || lastDigit < 0 || lastDigit >= fmtDigitIdx.size || firstDigit >= fmtDigitIdx.size) return null
    // Align by digit content when raw has a country code the formatted national form dropped.
    val rawDigits = rawDigitIdx.map { raw[it] }.joinToString("")
    val fmtDigits = fmtDigitIdx.map { formatted[it] }.joinToString("")
    val rawSlice = raw.substring(range).filter { it.isDigit() }
    if (rawSlice.isEmpty()) return null
    // Prefer matching the highlighted digit run inside the formatted digit string.
    val at = fmtDigits.indexOf(rawSlice)
    if (at >= 0) {
        return fmtDigitIdx[at]..fmtDigitIdx[at + rawSlice.length - 1]
    }
    // Fallback: drop leading 90 from raw digits and retry.
    val stripped =
        when {
            rawDigits.startsWith("90") && rawDigits.length >= rawSlice.length + 2 ->
                rawDigits.drop(2)
            else -> rawDigits
        }
    val sliceInStripped =
        if (stripped !== rawDigits) {
            val startInRawDigits = rawDigitIdx.indexOfFirst { it >= range.first }
            val startInStripped = (startInRawDigits - 2).coerceAtLeast(0)
            stripped.drop(startInStripped).take(rawSlice.length)
        } else {
            rawSlice
        }
    val at2 = fmtDigits.indexOf(sliceInStripped)
    if (at2 >= 0 && sliceInStripped.isNotEmpty()) {
        return fmtDigitIdx[at2]..fmtDigitIdx[at2 + sliceInStripped.length - 1]
    }
    return fmtDigitIdx[firstDigit.coerceIn(0, fmtDigitIdx.lastIndex)]..
        fmtDigitIdx[lastDigit.coerceIn(0, fmtDigitIdx.lastIndex)]
}

/** 11-digit national `0XXXXXXXXXX`, or null if not a recognizable TR number. */
internal fun toTrNationalDigits(number: String): String? {
    val digits = number.filter { it.isDigit() }
    return when {
        digits.length >= 12 && digits.startsWith("90") -> {
            val rest = digits.drop(2)
            if (rest.length >= 10) "0" + rest.take(10) else null
        }
        digits.length == 11 && digits.startsWith('0') -> digits
        digits.length == 10 -> "0$digits"
        else -> null
    }
}

private fun looksFormatted(s: String): Boolean =
    s.any { it == ' ' || it == '(' || it == '-' }
