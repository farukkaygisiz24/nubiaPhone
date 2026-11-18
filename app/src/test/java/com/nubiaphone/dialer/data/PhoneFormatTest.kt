package com.nubiaphone.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneFormatTest {
    @Test
    fun formatsNationalMobileWithParens() {
        assertEquals("0 (539) 575 32 08", formatTrDisplay("05395753208"))
        assertEquals("0 (539) 575 32 08", formatTrDisplay("0 (539) 575 32 08"))
    }

    @Test
    fun formatsE164TurkeyAsNational() {
        assertEquals("0 (537) 253 52 78", formatTrDisplay("+905372535278"))
        assertEquals("0 (532) 434 34 59", formatTrDisplay("+905324343459"))
        assertEquals("0 (537) 253 52 78", formatTrDisplay("905372535278"))
    }

    @Test
    fun formatsTenDigitLocal() {
        assertEquals("0 (532) 123 45 67", formatTrDisplay("5321234567"))
    }

    @Test
    fun leavesShortOrUnknownAlone() {
        assertEquals("123", formatTrDisplay("123"))
        assertEquals("*123#", formatTrDisplay("*123#"))
    }

    @Test
    fun mapsDigitHighlightOntoFormatted() {
        val raw = "+905372535278"
        val shown = formatTrDisplay(raw)
        // digits "537" start at index after +90 → raw indices of '5','3','7'
        val range = raw.indexOf('5').let { it until it + 3 }
        val mapped = mapDigitRange(raw, shown, range)!!
        assertEquals("537", shown.substring(mapped))
    }

    @Test
    fun mapDigitRangeNullSafe() {
        assertNull(mapDigitRange("+90537", "0 (537)", null))
    }
}
