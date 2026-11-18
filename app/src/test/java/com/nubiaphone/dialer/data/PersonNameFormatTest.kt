package com.nubiaphone.dialer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonNameFormatTest {
    @Test
    fun titleCasesWords() {
        assertEquals("Abcd", formatPersonName("abcd"))
        assertEquals("Abcd Abcd", formatPersonName("abcd abcd"))
        assertEquals("Ahmet Yılmaz", formatPersonName("ahmet yılmaz"))
        assertEquals("Ahmet Yılmaz", formatPersonName("AHMET YILMAZ"))
    }

    @Test
    fun turkishLetters() {
        assertEquals("İbrahim", formatPersonName("ibrahim"))
        assertEquals("Işıl", formatPersonName("ışıl"))
        assertEquals("Çağrı Öztürk", formatPersonName("çağrı öztürk"))
    }

    @Test
    fun keepsTrailingSpaceWhileTyping() {
        assertEquals("Ahmet ", formatPersonName("ahmet "))
        assertEquals("Ahmet Yılmaz ", formatPersonName("ahmet yılmaz "))
    }

    @Test
    fun hyphenatedParts() {
        assertEquals("Ali-Veli", formatPersonName("ali-veli"))
    }

    @Test
    fun emptySafe() {
        assertEquals("", formatPersonName(""))
        assertEquals(" ", formatPersonName(" "))
    }
}
