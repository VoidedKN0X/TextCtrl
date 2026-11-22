package com.vknxworks.textctrl.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionsMatchTest {
    @Test fun whitespaceIsFoldedAndTrimmed() {
        assertEquals("a b c", Reactions.normalize("  a \n b\t\tc "))
    }

    @Test fun quoteMatchesTheSameTextWithDifferentSpacing() {
        assertTrue(Reactions.matches("See you  at\neight", "See you at eight"))
    }

    @Test fun cutShortQuoteMatchesTheStartOfALongerText() {
        assertTrue(Reactions.matches("See you at eight…", "See you at eight tonight at the station"))
        assertFalse(Reactions.matches("See…", "See you at eight"))
    }

    @Test fun normalizedFormGivesTheSameAnswer() {
        val q = Reactions.normalizeQuote("Hello  there, how…")
        assertTrue(Reactions.matchesNormalized(q, Reactions.normalize("Hello there, how are you")))
        assertFalse(Reactions.matchesNormalized(q, Reactions.normalize("Something else")))
        assertFalse(Reactions.matchesNormalized("", "x"))
    }
}
