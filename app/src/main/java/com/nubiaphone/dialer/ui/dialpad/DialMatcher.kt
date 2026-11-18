package com.nubiaphone.dialer.ui.dialpad

import com.nubiaphone.dialer.data.CallDay
import com.nubiaphone.dialer.data.ContactSummary
import com.nubiaphone.dialer.data.numberKey
import java.util.Locale

private val TR = Locale("tr", "TR")

/** Phone keypad letters, with the Turkish letters on the key of their base letter. */
private val KeyOf: Map<Char, Char> =
    buildMap {
        fun put(key: Char, letters: String) = letters.forEach { put(it, key) }
        put('2', "ABCÇ")
        put('3', "DEF")
        put('4', "GĞHIİ")
        put('5', "JKL")
        put('6', "MNOÖ")
        put('7', "PQRSŞ")
        put('8', "TUÜV")
        put('9', "WXYZ")
    }

/** Name → key digits, one output char per input char so match ranges map back onto the name. */
internal fun t9(name: String): String =
    buildString(name.length) {
        name.uppercase(TR).let { up ->
            // uppercase() can change length (e.g. "ß"); fall back to per-char mapping on the original then.
            val src = if (up.length == name.length) up else name
            src.forEach { c -> append(if (c.isDigit()) c else KeyOf[c.uppercaseChar()] ?: ' ') }
        }
    }

data class DialMatch(
    val name: String?,
    val number: String,
    val photoUri: String?,
    /** Matched range in [name] (T9) or null. */
    val nameRange: IntRange?,
    /** Matched digit run in [number] or null. */
    val numberRange: IntRange?,
)

/**
 * Suggestions while typing on the keypad, best first: T9 match at the start of the name, at the
 * start of another word, by number (contacts and unknown numbers from the call log), inside a
 * word. Within each rank the most recently called comes first. At most [limit].
 */
fun matchDialed(
    typed: String,
    contacts: List<ContactSummary>,
    days: List<CallDay>,
    limit: Int = 4,
): List<DialMatch> {
    val digits = typed.filter { it.isDigit() }
    if (digits.isEmpty()) return emptyList()
    data class Scored(val score: Int, val recency: Int, val match: DialMatch)
    val out = ArrayList<Scored>()
    val seen = HashSet<String>()
    // Rank of each number in the call log (0 = most recent): ties go to people you call.
    val recency = HashMap<String, Int>()
    days.asSequence().flatMap { it.groups }.forEachIndexed { i, g -> recency.putIfAbsent(numberKey(g.number), i) }
    fun recencyOf(numbers: List<String>) = numbers.minOfOrNull { recency[numberKey(it)] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE

    for (c in contacts) {
        val number = c.numbers.firstOrNull() ?: continue
        val keys = t9(c.name)
        var nameRange: IntRange? = null
        var score = Int.MAX_VALUE
        // Word start: beginning of the name or after a space.
        var i = keys.indexOf(digits)
        while (i >= 0) {
            // 0 = start of the name, 1 = start of another word, 3 = inside a word.
            val wordStart = i == 0 || c.name[i - 1] == ' '
            val s = if (i == 0) 0 else if (wordStart) 1 else 3
            if (s < score) {
                score = s
                nameRange = i until i + digits.length
            }
            if (i == 0) break
            i = keys.indexOf(digits, i + 1)
        }
        var numberRange: IntRange? = null
        var matchedNumber = number
        if (digits.length >= 2) {
            for (n in c.numbers) {
                val at = n.indexOfDigits(digits)
                if (at != null) {
                    matchedNumber = n
                    numberRange = at
                    if (score > 2) score = 2
                    break
                }
            }
        }
        if (score == Int.MAX_VALUE) continue
        c.numbers.forEach { seen += numberKey(it) }
        out += Scored(score, recencyOf(c.numbers), DialMatch(c.name, matchedNumber, c.photoUri, nameRange, numberRange))
    }

    if (digits.length >= 2) {
        days.asSequence().flatMap { it.groups }.filter { !it.isHidden && it.contact == null }.forEach { g ->
            val key = numberKey(g.number)
            if (key in seen) return@forEach
            val at = g.number.indexOfDigits(digits) ?: return@forEach
            seen += key
            out += Scored(2, recency[key] ?: Int.MAX_VALUE, DialMatch(null, g.number, null, null, at))
        }
    }
    return out.sortedWith(compareBy({ it.score }, { it.recency })).take(limit).map { it.match }
}

/** Range in this string covering [digits] when only its digits are compared, or null. */
private fun String.indexOfDigits(digits: String): IntRange? {
    val positions = indices.filter { this[it].isDigit() }
    val only = positions.map { this[it] }.joinToString("")
    val at = only.indexOf(digits)
    if (at < 0) return null
    return positions[at]..positions[at + digits.length - 1]
}
