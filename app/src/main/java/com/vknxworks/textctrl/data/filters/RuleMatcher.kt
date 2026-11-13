package com.vknxworks.textctrl.data.filters

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Who sent a message, as far as the rules care. */
data class Sender(
    val address: String,
    /** In your contacts, or a chat you wrote in. */
    val known: Boolean,
    /** TextCtrl may read contacts (otherwise everyone would look unknown). */
    val contactsGranted: Boolean,
)

/** The strictest matching rule's action, and every rule that matched (for the hit counters). */
data class Verdict(val action: RuleAction, val matched: List<FilterRule>)

/**
 * The rule matching itself, without anything Android: pure and unit-tested. [Filters] feeds it the
 * sender, the text and the rules when a message arrives.
 */
object RuleMatcher {
    const val MAX_PATTERN = 300
    const val MAX_BODY = 4000
    /** How long one regular expression may run on one message before it counts as no match. */
    const val REGEX_TIME_MS = 200L

    /**
     * What to do with a message from [sender] saying [body]. Null: let it through. With [skipKnown], people
     * in your contacts and chats you wrote in are never filtered. The strictest rule wins (block, spam, mute).
     */
    fun evaluate(rules: List<FilterRule>, sender: Sender, body: String, skipKnown: Boolean, regex: SafeRegex): Verdict? {
        val active = rules.filter { it.enabled }
        if (active.isEmpty() || sender.address.isBlank()) return null
        if (skipKnown && sender.known) return null
        val matched = active.filter { matches(it, sender, body, regex) }
        val winner = matched.minByOrNull { it.action.ordinal } ?: return null
        return Verdict(winner.action, matched)
    }

    fun matches(rule: FilterRule, sender: Sender, body: String, regex: SafeRegex): Boolean = when (rule.kind) {
        RuleKind.KEYWORD -> keywords(rule.pattern).any { body.contains(it, ignoreCase = true) }
        RuleKind.REGEX -> regex.containsMatch(rule.pattern, body)
        RuleKind.PREFIX -> prefixes(rule.pattern).any { startsWithNumber(sender.address, it) }
        RuleKind.EXACT -> senders(rule.pattern).any { sameSender(sender.address, it) }
        RuleKind.SHORT_CODE -> isShortCode(sender.address)
        RuleKind.NAMED_SENDER -> isNamedSender(sender.address)
        // Only when TextCtrl may read contacts: otherwise everyone would look unknown.
        RuleKind.UNKNOWN -> sender.contactsGranted && !sender.known
    }

    /** Is [rule]'s pattern usable? Null when it is, else what's wrong. */
    fun problem(kind: RuleKind, pattern: String): PatternProblem? = when {
        !kind.needsPattern -> null
        pattern.isBlank() -> PatternProblem.EMPTY
        pattern.length > MAX_PATTERN -> PatternProblem.TOO_LONG
        kind == RuleKind.REGEX && compiles(pattern) == null -> PatternProblem.BAD_REGEX
        kind == RuleKind.REGEX && !safePattern(pattern) -> PatternProblem.UNSAFE_REGEX
        kind == RuleKind.PREFIX && prefixes(pattern).isEmpty() -> PatternProblem.BAD_PREFIX
        kind == RuleKind.EXACT && senders(pattern).isEmpty() -> PatternProblem.EMPTY
        else -> null
    }

    enum class PatternProblem { EMPTY, TOO_LONG, BAD_REGEX, UNSAFE_REGEX, BAD_PREFIX }

    fun keywords(pattern: String): List<String> = pattern.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun prefixes(pattern: String): List<String> =
        pattern.split(',', '\n', ' ').map { p -> p.filter { it.isDigit() || it == '+' } }.filter { it.isNotEmpty() }

    /** Numbers or sender names, separated by commas or new lines. */
    fun senders(pattern: String): List<String> = pattern.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }

    /** A regular expression that compiles, isn't absurdly long and is in the safe syntax; null otherwise. */
    fun validRegex(pattern: String): Regex? = compiles(pattern)?.takeIf { safePattern(pattern) }

    private fun compiles(pattern: String): Regex? =
        if (pattern.isBlank() || pattern.length > MAX_PATTERN) null
        else runCatching { Regex(pattern, setOf(RegexOption.IGNORE_CASE)) }.getOrNull()

    /** At most this many open-ended repeats (`*`, `+`, `{n,}`) in one pattern. */
    const val MAX_OPEN_REPEATS = 6

    /**
     * The restricted syntax rules may use, which can't blow up into exponential time:
     * - no back-references (`\1`, `\k<name>`);
     * - a group repeated with `*`, `+` or `{n,m}` may not itself contain a repeat or an alternative (`|`),
     *   so `(ab)+`, `(win|won)?` and `(\d{3} )?` are fine but `(a+)+`, `(a|aa)*` and `(\w+\s?)*` aren't;
     * - at most [MAX_OPEN_REPEATS] open-ended repeats.
     */
    fun safePattern(pattern: String): Boolean {
        class Group(var repeats: Boolean = false, var alternatives: Boolean = false)
        val stack = ArrayDeque<Group>().apply { addLast(Group()) }
        var open = 0
        var i = 0
        val n = pattern.length

        /** Reads a quantifier at [at]: (length, repeats more than once, open-ended), or null. */
        fun quantifier(at: Int): Triple<Int, Boolean, Boolean>? {
            if (at >= n) return null
            return when (pattern[at]) {
                '*', '+' -> Triple(1, true, true)
                '?' -> Triple(1, false, false)
                '{' -> {
                    val m = BRACES.matchAt(pattern, at) ?: return null
                    val min = m.groupValues[1].toIntOrNull() ?: return null
                    val comma = m.groupValues[2].isNotEmpty()
                    val max = m.groupValues[3].toIntOrNull()
                    val openEnded = comma && max == null
                    Triple(m.value.length, openEnded || (max ?: min) > 1, openEnded)
                }
                else -> null
            }
        }

        while (i < n) {
            val ch = pattern[i]
            when {
                ch == '\\' -> {
                    val next = pattern.getOrNull(i + 1) ?: return false
                    if (next in '1'..'9' || next == 'k') return false
                    // \Q…\E quotes literally.
                    if (next == 'Q') {
                        val end = pattern.indexOf("\\E", i + 2)
                        i = if (end < 0) n else end + 2
                        continue
                    }
                    i += 2
                    continue
                }
                ch == '[' -> {
                    // A character class: skip to its end (nested classes and escapes included).
                    var j = i + 1
                    if (pattern.getOrNull(j) == '^') j++
                    if (pattern.getOrNull(j) == ']') j++ // a literal ] first in the class
                    var depth = 0
                    while (j < n) {
                        val c = pattern[j]
                        if (c == '\\') { j += 2; continue }
                        if (c == '[') depth++
                        if (c == ']') {
                            if (depth == 0) break
                            depth--
                        }
                        j++
                    }
                    i = j + 1
                    continue
                }
                ch == '(' -> {
                    stack.addLast(Group())
                    i++
                    // Group modifiers: (?: (?= (?! (?<= (?<! (?<name> (?i) (?i:
                    if (pattern.getOrNull(i) == '?') {
                        i++
                        when (pattern.getOrNull(i)) {
                            ':', '=', '!', '>' -> i++
                            '<' -> {
                                i++
                                if (pattern.getOrNull(i) == '=' || pattern.getOrNull(i) == '!') i++
                                else { while (i < n && pattern[i] != '>') i++; i++ }
                            }
                            else -> {
                                while (i < n && pattern[i] != ':' && pattern[i] != ')') i++
                                if (pattern.getOrNull(i) == ':') i++
                            }
                        }
                    }
                    continue
                }
                ch == ')' -> {
                    if (stack.size < 2) return false
                    val group = stack.removeLast()
                    i++
                    val q = quantifier(i)
                    if (q != null) {
                        if (q.second && (group.repeats || group.alternatives)) return false
                        if (q.third) open++
                        i += q.first
                        if (pattern.getOrNull(i) == '?' || pattern.getOrNull(i) == '+') i++ // lazy or possessive
                    }
                    val parent = stack.last()
                    parent.repeats = parent.repeats || group.repeats || (q?.second == true)
                    parent.alternatives = parent.alternatives || group.alternatives
                    continue
                }
                ch == '|' -> stack.last().alternatives = true
                else -> {
                    val q = quantifier(i)
                    if (q != null) {
                        if (q.second) stack.last().repeats = true
                        if (q.third) open++
                        i += q.first
                        if (pattern.getOrNull(i) == '?' || pattern.getOrNull(i) == '+') i++
                        continue
                    }
                }
            }
            i++
        }
        return open <= MAX_OPEN_REPEATS
    }

    private val BRACES = Regex("""\{(\d{1,4})(,?)(\d{0,4})}""")

    fun isShortCode(address: String): Boolean {
        val clean = address.filter { !it.isWhitespace() && it != '-' }
        return clean.length in 3..8 && clean.all { it.isDigit() }
    }

    /** "BANK", "Promo", "DHL-Info": an alphanumeric sender ID instead of a number (not an e-mail address). */
    fun isNamedSender(address: String): Boolean = address.any { it.isLetter() } && !address.contains('@')

    /** "+32 470 12…" starts with "+32"; "0900…" with "0900". "+" and "00" are treated alike. */
    fun startsWithNumber(address: String, prefix: String): Boolean {
        val a = cleanNumber(address)
        val p = prefix.filter { it.isDigit() || it == '+' }.replaceFirst(LEADING_00, "+")
        return p.isNotEmpty() && a.startsWith(p)
    }

    /**
     * [address] is [entry]: the same sender name (case doesn't matter), or the same number. Numbers are
     * compared on their digits ("+32 470 12 34 56" = "+32470123456"), and a national form matches the
     * international one ("0470 12 34 56" = "+32 470 12 34 56") on the last 9 digits. Two international
     * numbers must be the same in full.
     */
    fun sameSender(address: String, entry: String): Boolean {
        val e = entry.trim()
        val a = address.trim()
        if (e.isEmpty() || a.isEmpty()) return false
        if (e.any { it.isLetter() } || a.any { it.isLetter() }) return a.equals(e, ignoreCase = true)
        val na = cleanNumber(a)
        val ne = cleanNumber(e)
        val da = na.filter { it.isDigit() }
        val de = ne.filter { it.isDigit() }
        if (da.isEmpty() || de.isEmpty()) return false
        if (na == ne) return true
        if (na.startsWith('+') && ne.startsWith('+')) return da == de
        if (da.length < NATIONAL_DIGITS || de.length < NATIONAL_DIGITS) return da == de
        return da.takeLast(NATIONAL_DIGITS) == de.takeLast(NATIONAL_DIGITS)
    }

    private const val NATIONAL_DIGITS = 9
    private val LEADING_00 = Regex("^00")

    private fun cleanNumber(s: String): String = s.filter { it.isDigit() || it == '+' }.replaceFirst(LEADING_00, "+")
}

/**
 * Runs the rules' regular expressions safely. Two guards, because Android's regex engine (ICU) runs in
 * native code that can't be interrupted:
 * - Only a restricted syntax is allowed ([RuleMatcher.safePattern]): no back-references and no repeated
 *   group that itself repeats or has alternatives, the patterns that can take exponential time.
 * - A time limit: the match runs on a background thread, and one that takes too long counts as no match.
 *   That pattern is skipped from then on (until the rules change or the app restarts), so it can never
 *   hold up an incoming message.
 */
class SafeRegex(
    private val timeMs: Long = RuleMatcher.REGEX_TIME_MS,
    private val match: (Regex, String) -> Boolean = { regex, text -> regex.containsMatchIn(text) },
) {
    private val cache = HashMap<String, Regex?>()
    private val slow: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val threads = Executors.newCachedThreadPool { r ->
        Thread(r, "textctrl-filter").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    /** Patterns that ran out of time. */
    val slowPatterns: Set<String> get() = slow

    fun containsMatch(pattern: String, text: String): Boolean {
        if (pattern in slow) return false
        val regex = synchronized(cache) { cache.getOrPut(pattern) { RuleMatcher.validRegex(pattern) } } ?: return false
        val input = text.take(RuleMatcher.MAX_BODY)
        val task = runCatching { threads.submit<Boolean> { match(regex, input) } }.getOrNull() ?: return false
        return try {
            task.get(timeMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            slow += pattern
            task.cancel(true)
            false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) {
            false
        }
    }

    /** Forget slow patterns (after the rules change). */
    fun reset() {
        slow.clear()
        synchronized(cache) { cache.clear() }
    }
}