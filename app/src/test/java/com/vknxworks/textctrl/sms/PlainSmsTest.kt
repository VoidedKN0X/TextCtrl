package com.vknxworks.textctrl.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainSmsTest {
    @Test fun basicTextIsUntouched() {
        val text = "Tot morgen! Kost het €5 of \$5? [ok] {a|b} ~ é è à ù ö ü ñ Ç ß\nnieuwe regel"
        assertTrue(PlainSms.isPlain(text))
        assertEquals(text, PlainSms.plain(text))
    }

    @Test fun accentsOutsideTheBasicAlphabetLoseTheirAccent() {
        assertEquals("Eén idee: cafe, café, Citroen, ca va, hopital", PlainSms.plain("Eén idee: cafe, café, Citroën, ça va, hôpital"))
        assertEquals("Ee", PlainSms.plain("Ëê"))
        // é, è, à, ù, ì, ò and É are basic and stay.
        assertEquals("éèàùìòÉ", PlainSms.plain("éèàùìòÉ"))
        // Capital È, À are not basic.
        assertEquals("EA", PlainSms.plain("ÈÀ"))
        assertTrue(PlainSms.isPlain(PlainSms.plain("Eén coördinatie-vergadering: crème brûlée, façade, señor, Škoda, Łódź")))
    }

    @Test fun typographicCharactersBecomeBasicOnes() {
        assertEquals("\"Hallo\" - 'ok'... 5 x 2", PlainSms.plain("“Hallo” – ‘ok’… 5 × 2"))
        assertEquals("a b", PlainSms.plain("a b"))
        assertEquals("oeuvre", PlainSms.plain("œuvre"))
    }

    @Test fun whatHasNoLookAlikeStays() {
        assertEquals("ok 👍 привет 你好", PlainSms.plain("ok 👍 привет 你好"))
        assertFalse(PlainSms.isPlain("ok 👍"))
        assertEquals("cafe 👍", PlainSms.plain("cafë 👍"))
    }

    @Test fun accentsTypedAsSeparateMarksAreLeftOut() {
        assertEquals("e", PlainSms.plain("e\u0308"))
        // The mark that makes an emoji colourful is not an accent and stays.
        assertEquals("\u2764\uFE0F", PlainSms.plain("\u2764\uFE0F"))
    }
}
