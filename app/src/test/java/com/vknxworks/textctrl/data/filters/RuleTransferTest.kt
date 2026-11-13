package com.vknxworks.textctrl.data.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTransferTest {
    private val rules = listOf(
        FilterRule(1, RuleKind.KEYWORD, "win, \"free\" prize", RuleAction.SPAM, hits = 7),
        FilterRule(2, RuleKind.PREFIX, "+44", RuleAction.BLOCK, enabled = false),
        FilterRule(3, RuleKind.NAMED_SENDER, "", RuleAction.MUTE),
        FilterRule(4, RuleKind.REGEX, "=cmd|x", RuleAction.SPAM),
    )
    private val blocked = listOf("+32470123456", "BANK", "-123")

    private fun exported() = rules.map(RuleTransfer::export)

    @Test fun jsonRoundTrip() {
        val back = RuleTransfer.parse(RuleTransfer.toJson(rules, blocked))
        assertEquals(exported(), back.rules)
        assertEquals(blocked, back.blocked)
        assertEquals(0, back.skipped)
    }

    @Test fun csvRoundTripWithQuotesCommasAndFormulaGuard() {
        val csv = RuleTransfer.toCsv(rules, blocked)
        assertTrue(csv.startsWith(RuleTransfer.CSV_HEADER + "\r\n"))
        // Spreadsheet formula guard: written with an apostrophe...
        assertTrue(csv.contains("'+44"))
        assertTrue(csv.contains("'=cmd|x"))
        assertTrue(csv.contains("\"win, \"\"free\"\" prize\""))
        // ...and removed again on import.
        val back = RuleTransfer.parse(csv)
        assertEquals(exported(), back.rules)
        assertEquals(blocked, back.blocked)
        assertEquals(0, back.skipped)
    }

    @Test fun csvWithLfLineEndingsAndNoHeader() {
        val back = RuleTransfer.parse("rule,keyword,hello,spam,true\nblocked,,+3247,,\n")
        assertEquals(listOf(ExportedRule("KEYWORD", "hello", "SPAM", true)), back.rules)
        assertEquals(listOf("+3247"), back.blocked)
    }

    @Test fun invalidEntriesAreSkippedAndCounted() {
        val csv = """
            type,kind,value,action,enabled
            rule,KEYWORD,,SPAM,true
            rule,REGEX,(unclosed,SPAM,true
            rule,NOPE,x,SPAM,true
            rule,KEYWORD,ok,EXPLODE,true
            rule,PREFIX,abc,BLOCK,true
            rule,EXACT,BANK,BLOCK,true
            something,else
            blocked,,,,
            blocked,,${"9".repeat(RuleTransfer.MAX_NUMBER + 1)},,
            blocked,,+3247,,
        """.trimIndent()
        val back = RuleTransfer.parse(csv)
        assertEquals(listOf(ExportedRule("EXACT", "BANK", "BLOCK", true)), back.rules)
        assertEquals(listOf("+3247"), back.blocked)
        assertEquals(8, back.skipped)
    }

    @Test fun duplicatesAreMerged() {
        val back = RuleTransfer.parse("rule,KEYWORD,a,SPAM,true\nrule,KEYWORD,a,SPAM,true\nblocked,,1,,\nblocked,,1,,\n")
        assertEquals(1, back.rules.size)
        assertEquals(listOf("1"), back.blocked)
    }

    @Test fun patternIsDroppedForKindsWithoutOne() {
        val back = RuleTransfer.parse("{\"rules\":[{\"kind\":\"SHORT_CODE\",\"pattern\":\"junk\",\"action\":\"SPAM\"}]}")
        assertEquals(listOf(ExportedRule("SHORT_CODE", "", "SPAM", true)), back.rules)
    }

    @Test(expected = IllegalArgumentException::class)
    fun brokenJsonIsRefused() {
        RuleTransfer.parse("{\"rules\": [")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unrelatedCsvIsRefused() {
        RuleTransfer.parse("name,phone\nAlice,123\n")
    }

    @Test fun emptyFileIsEmpty() {
        val back = RuleTransfer.parse("\uFEFF  \n")
        assertTrue(back.rules.isEmpty() && back.blocked.isEmpty())
    }

    @Test fun csvParserHandlesQuotedLineBreaks() {
        val rows = RuleTransfer.parseCsvRows("a,\"b\r\nc\",d\r\ne,f")
        assertEquals(listOf(listOf("a", "b\r\nc", "d"), listOf("e", "f")), rows)
    }

    @Test fun ruleLimitIsEnforced() {
        val many = (1..RuleTransfer.MAX_RULES + 5).joinToString("\n") { "rule,KEYWORD,w$it,SPAM,true" }
        val back = RuleTransfer.parse(many)
        assertEquals(RuleTransfer.MAX_RULES, back.rules.size)
        assertEquals(5, back.skipped)
    }
}
