package com.vknxworks.textctrl.data.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {
    private val regex = SafeRegex()
    private var nextId = 1L

    private fun rule(kind: RuleKind, pattern: String = "", action: RuleAction = RuleAction.SPAM, enabled: Boolean = true) =
        FilterRule(nextId++, kind, pattern, action, enabled)

    private fun stranger(address: String) = Sender(address, known = false, contactsGranted = true)
    private fun contact(address: String) = Sender(address, known = true, contactsGranted = true)

    private fun matches(rule: FilterRule, sender: Sender, body: String = "") = RuleMatcher.matches(rule, sender, body, regex)

    // ---------------------------------------------------------------- keywords

    @Test fun keywordMatchesAnyWordIgnoringCase() {
        val r = rule(RuleKind.KEYWORD, "prize, free money ,WIN")
        assertTrue(matches(r, stranger("+3247"), "You WON a Prize!"))
        assertTrue(matches(r, stranger("+3247"), "get free money now"))
        assertTrue(matches(r, stranger("+3247"), "win"))
        assertFalse(matches(r, stranger("+3247"), "free and money"))
    }

    @Test fun keywordListIgnoresEmptyEntries() {
        assertEquals(listOf("a", "b c"), RuleMatcher.keywords(" a ,, \n b c ,"))
        assertFalse(matches(rule(RuleKind.KEYWORD, " , "), stranger("1"), "anything"))
    }

    // ---------------------------------------------------------------- regex

    @Test fun regexMatchesCaseInsensitively() {
        val r = rule(RuleKind.REGEX, "\\bcode:? \\d{6}\\b")
        assertTrue(matches(r, stranger("x"), "Your CODE: 123456 for login"))
        assertFalse(matches(r, stranger("x"), "code 12345"))
    }

    @Test fun invalidOrTooLongRegexNeverMatches() {
        assertFalse(matches(rule(RuleKind.REGEX, "(unclosed"), stranger("x"), "(unclosed"))
        assertNull(RuleMatcher.validRegex("a".repeat(RuleMatcher.MAX_PATTERN + 1)))
        assertNull(RuleMatcher.validRegex("  "))
    }

    @Test fun catastrophicPatternsAreRefused() {
        listOf(
            "^(a+)+\$", "(a|aa)*b", "(\\w+\\s?)*\$", "(x+x+)+y", "((ab)+c)+", "(a*)*", "(.*a){12}",
            "(a)\\1", "(?<n>a)\\k<n>", ".*a.*b.*c.*d.*e.*f.*g",
        ).forEach { p ->
            assertFalse("should refuse $p", RuleMatcher.safePattern(p))
            assertNull(RuleMatcher.validRegex(p))
            assertEquals(RuleMatcher.PatternProblem.UNSAFE_REGEX, RuleMatcher.problem(RuleKind.REGEX, p))
        }
    }

    @Test fun ordinaryPatternsAreAccepted() {
        listOf(
            "\\bcode:? \\d{6}\\b", "(ab)+", "(win|won)?", "(\\d{3} )?\\d+", "win.*prize", "[a-z]+[(+*)]{2,}",
            "(?i)free", "(?:http|www)\\S+", "\\Q(a+)+\\E", "bit\\.ly/\\w+", "^\\+44", "(ha){2}",
        ).forEach { p ->
            assertTrue("should accept $p", RuleMatcher.safePattern(p))
            assertNotNull(RuleMatcher.validRegex(p))
        }
    }

    @Test fun slowMatchTimesOutAndIsSkipped() {
        val sleepy = SafeRegex(timeMs = 50) { regex, text -> Thread.sleep(2_000); regex.containsMatchIn(text) }
        val start = System.nanoTime()
        assertFalse(sleepy.containsMatch("hello", "hello"))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 1_000)
        assertTrue("hello" in sleepy.slowPatterns)
        // Skipped from then on, until the rules change.
        assertFalse(sleepy.containsMatch("hello", "hello"))
        sleepy.reset()
        assertTrue(sleepy.slowPatterns.isEmpty())
        assertTrue(SafeRegex().containsMatch("hello", "well, hello"))
    }

    @Test fun longBodiesAreCutBeforeMatching() {
        val body = "x".repeat(RuleMatcher.MAX_BODY) + "needle"
        assertFalse(regex.containsMatch("needle", body))
        assertTrue(regex.containsMatch("needle", "x".repeat(100) + "needle"))
    }

    // ---------------------------------------------------------------- prefixes

    @Test fun prefixTreatsPlusAnd00Alike() {
        val r = rule(RuleKind.PREFIX, "+44, 0900")
        assertTrue(matches(r, stranger("+44 7700 900123")))
        assertTrue(matches(r, stranger("0044 7700 900123")))
        assertTrue(matches(r, stranger("0900-123456")))
        assertFalse(matches(r, stranger("+32 470 123456")))
        assertFalse(matches(r, stranger("BANK")))
    }

    @Test fun prefixesKeepOnlyDigitsAndPlus() {
        assertEquals(listOf("+44", "0900"), RuleMatcher.prefixes("+44, 09-00 x"))
        assertTrue(RuleMatcher.prefixes("abc").isEmpty())
    }

    // ---------------------------------------------------------------- exact senders

    @Test fun exactNumberMatchesFormattingAndNationalForm() {
        val r = rule(RuleKind.EXACT, "+32 470 12 34 56")
        assertTrue(matches(r, stranger("+32470123456")))
        assertTrue(matches(r, stranger("0032 470 123 456")))
        assertTrue(matches(r, stranger("0470/12.34.56")))
        assertFalse(matches(r, stranger("+32470123457")))
        assertFalse(matches(r, stranger("+32470123456789")))
    }

    @Test fun exactInternationalNumbersMustBeEqualInFull() {
        // Same last 9 digits, other country: not the same number.
        assertFalse(RuleMatcher.sameSender("+44470123456", "+32470123456"))
        assertTrue(RuleMatcher.sameSender("+32470123456", "+32 470 123 456"))
    }

    @Test fun exactShortNumbersMustBeEqual() {
        assertTrue(RuleMatcher.sameSender("1234", "1234"))
        assertFalse(RuleMatcher.sameSender("41234", "1234"))
        assertFalse(RuleMatcher.sameSender("", "1234"))
    }

    @Test fun exactSenderNameIgnoresCaseButNotPartialNames() {
        val r = rule(RuleKind.EXACT, "BANK, DHL-Info")
        assertTrue(matches(r, stranger("bank")))
        assertTrue(matches(r, stranger("DHL-INFO")))
        assertFalse(matches(r, stranger("MyBank")))
        assertFalse(matches(r, stranger("+32470123456")))
    }

    // ---------------------------------------------------------------- sender kinds

    @Test fun shortCodes() {
        assertTrue(RuleMatcher.isShortCode("8888"))
        assertTrue(RuleMatcher.isShortCode("12 345"))
        assertFalse(RuleMatcher.isShortCode("12"))
        assertFalse(RuleMatcher.isShortCode("+3247012"))
        assertFalse(RuleMatcher.isShortCode("123456789"))
    }

    @Test fun alphanumericSenderIds() {
        val r = rule(RuleKind.NAMED_SENDER)
        assertTrue(matches(r, stranger("PROMO")))
        assertTrue(matches(r, stranger("Vodafone1")))
        assertFalse(matches(r, stranger("+32470123456")))
        assertFalse(matches(r, stranger("someone@example.com")))
    }

    @Test fun unknownSendersNeedTheContactsPermission() {
        val r = rule(RuleKind.UNKNOWN)
        assertTrue(matches(r, stranger("+32470")))
        assertFalse(matches(r, contact("+32470")))
        assertFalse(matches(r, Sender("+32470", known = false, contactsGranted = false)))
    }

    // ---------------------------------------------------------------- evaluate

    @Test fun strictestActionWinsAndAllMatchesAreReported() {
        val mute = rule(RuleKind.KEYWORD, "hello", RuleAction.MUTE)
        val spam = rule(RuleKind.UNKNOWN, action = RuleAction.SPAM)
        val block = rule(RuleKind.PREFIX, "+44", RuleAction.BLOCK)
        val v = RuleMatcher.evaluate(listOf(mute, spam, block), stranger("+44123"), "hello", skipKnown = true, regex = regex)
        assertNotNull(v)
        assertEquals(RuleAction.BLOCK, v!!.action)
        assertEquals(setOf(mute.id, spam.id, block.id), v.matched.map { it.id }.toSet())
    }

    @Test fun disabledRulesAreIgnored() {
        val r = rule(RuleKind.KEYWORD, "hello", enabled = false)
        assertNull(RuleMatcher.evaluate(listOf(r), stranger("+1"), "hello", skipKnown = false, regex = regex))
    }

    @Test fun knownSendersAreSkippedOnlyWhenAsked() {
        val r = rule(RuleKind.KEYWORD, "hello")
        assertNull(RuleMatcher.evaluate(listOf(r), contact("+1"), "hello", skipKnown = true, regex = regex))
        assertEquals(RuleAction.SPAM, RuleMatcher.evaluate(listOf(r), contact("+1"), "hello", skipKnown = false, regex = regex)?.action)
    }

    @Test fun noMatchOrBlankSenderLetsTheMessageThrough() {
        val r = rule(RuleKind.KEYWORD, "hello")
        assertNull(RuleMatcher.evaluate(listOf(r), stranger("+1"), "goodbye", skipKnown = true, regex = regex))
        assertNull(RuleMatcher.evaluate(listOf(r), stranger(" "), "hello", skipKnown = true, regex = regex))
        assertNull(RuleMatcher.evaluate(emptyList(), stranger("+1"), "hello", skipKnown = true, regex = regex))
    }

    // ---------------------------------------------------------------- validation

    @Test fun patternProblems() {
        assertNull(RuleMatcher.problem(RuleKind.SHORT_CODE, ""))
        assertEquals(RuleMatcher.PatternProblem.EMPTY, RuleMatcher.problem(RuleKind.KEYWORD, "  "))
        assertEquals(RuleMatcher.PatternProblem.TOO_LONG, RuleMatcher.problem(RuleKind.KEYWORD, "a".repeat(301)))
        assertEquals(RuleMatcher.PatternProblem.BAD_REGEX, RuleMatcher.problem(RuleKind.REGEX, "[a-"))
        assertEquals(RuleMatcher.PatternProblem.BAD_PREFIX, RuleMatcher.problem(RuleKind.PREFIX, "abc"))
        assertEquals(RuleMatcher.PatternProblem.EMPTY, RuleMatcher.problem(RuleKind.EXACT, " , ,"))
        assertNull(RuleMatcher.problem(RuleKind.EXACT, "BANK"))
    }
}
