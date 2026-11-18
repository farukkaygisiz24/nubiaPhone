package com.nubiaphone.dialer.data

import java.util.Locale

private val TR = Locale("tr", "TR")

/**
 * Person-name input as `Abcd Abcd`: each whitespace- or hyphen-separated part
 * gets Turkish title case. Spaces are preserved so the next word can be typed.
 */
fun formatPersonName(raw: String): String {
    if (raw.isEmpty()) return raw
    val out = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
        if (raw[i].isWhitespace()) {
            out.append(raw[i])
            i++
            continue
        }
        val start = i
        while (i < raw.length && !raw[i].isWhitespace()) i++
        val word = raw.substring(start, i)
        out.append(word.split('-').joinToString("-") { titlePart(it) })
    }
    return out.toString()
}

private fun titlePart(part: String): String {
    if (part.isEmpty()) return part
    val lower = part.lowercase(TR)
    return lower.first().titlecase(TR) + lower.drop(1)
}
