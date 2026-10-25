package com.vknxworks.textctrl.data.filters

import android.content.Context
import androidx.annotation.StringRes
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.data.crypto.KeystoreCrypto
import com.vknxworks.textctrl.data.settings.ChatPrefs
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.sms.MessageStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom

/** What a filter rule looks at. */
enum class RuleKind(@StringRes val label: Int, val needsPattern: Boolean) {
    /** Words or phrases in the message (comma-separated, any of them). */
    KEYWORD(R.string.rule_keyword, true),
    /** A regular expression on the message. */
    REGEX(R.string.rule_regex, true),
    /** Sender numbers starting with this (e.g. +44, 0900). */
    PREFIX(R.string.rule_prefix, true),
    /** Short numbers of 3 to 8 digits, as used by services and adverts. */
    SHORT_CODE(R.string.rule_short_code, false),
    /** Senders with a name instead of a number ("BANK", "Promo"). */
    NAMED_SENDER(R.string.rule_named_sender, false),
    /** Senders not in your contacts that you never wrote to. */
    UNKNOWN(R.string.rule_unknown, false),
}

/** What happens to a message a rule matches. */
enum class RuleAction(@StringRes val label: Int) {
    /** Thrown away straight away: never stored, never shown. */
    BLOCK(R.string.rule_action_block),
    /** Kept in the Spam folder, without a notification. */
    SPAM(R.string.rule_action_spam),
    /** Kept in its chat as usual, without a notification. */
    MUTE(R.string.rule_action_mute),
}

@Serializable
data class FilterRule(
    val id: Long,
    val kind: RuleKind,
    val pattern: String = "",
    val action: RuleAction = RuleAction.SPAM,
    val enabled: Boolean = true,
    /** How many messages it caught. */
    val hits: Int = 0,
)

@Serializable
private data class FilterFile(val rules: List<FilterRule> = emptyList(), val skipContacts: Boolean = true, val blockedCount: Int = 0)

/**
 * Your own keyword and spam filters, applied to every text and picture message when it arrives, before
 * any notification. Everything is worked out on the phone: there's no spam database or online service
 * (TextCtrl has no internet access). The rules are stored encrypted with a key in the phone's secure
 * hardware, because they must work while the app is locked.
 */
class Filters(context: Context, private val store: MessageStore, private val contacts: Contacts, private val chats: ChatPrefs) {
    private val file = File(context.filesDir, "filters.enc")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()
    private val lock = Any()

    private val _state = MutableStateFlow(read())

    private val _rules = MutableStateFlow(_state.value.rules)
    val rules: StateFlow<List<FilterRule>> = _rules.asStateFlow()

    private val _skipContacts = MutableStateFlow(_state.value.skipContacts)
    /** Never filter messages from people in your contacts or chats you wrote in (on by default). */
    val skipContacts: StateFlow<Boolean> = _skipContacts.asStateFlow()

    private val _blockedCount = MutableStateFlow(_state.value.blockedCount)
    /** Messages thrown away by Block rules, ever. */
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    fun add(kind: RuleKind, pattern: String, action: RuleAction) = edit { list ->
        list + FilterRule((random.nextLong() and Long.MAX_VALUE).coerceAtLeast(1), kind, pattern.trim(), action)
    }

    fun update(rule: FilterRule) = edit { list -> list.map { if (it.id == rule.id) rule.copy(pattern = rule.pattern.trim()) else it } }

    fun remove(id: Long) = edit { list -> list.filter { it.id != id } }

    fun setSkipContacts(on: Boolean) {
        synchronized(lock) {
            _state.value = _state.value.copy(skipContacts = on)
            _skipContacts.value = on
            write()
        }
    }

    private fun edit(transform: (List<FilterRule>) -> List<FilterRule>) {
        synchronized(lock) {
            val next = transform(_state.value.rules)
            _state.value = _state.value.copy(rules = next)
            _rules.value = next
            write()
        }
    }

    /**
     * What to do with a message from [address] saying [body], arriving in [threadId] (null when the chat
     * isn't known yet). Null: let it through as usual. The strictest matching rule wins (block, then spam,
     * then mute).
     */
    suspend fun check(address: String, body: String, threadId: Long?, count: Boolean = true): RuleAction? {
        val active = _rules.value.filter { it.enabled }
        if (active.isEmpty() || address.isBlank()) return null
        // A chat you moved back out of Spam is trusted from then on.
        if (threadId != null && chats.isNotSpam(threadId)) return null
        val inContacts = contacts.granted && contacts.lookup(address).name != null
        val wrote = threadId != null && store.hasOutgoing(threadId)
        if (_skipContacts.value && (inContacts || wrote)) return null
        val matched = active.filter { rule -> matches(rule, address, body, known = inContacts || wrote) }
        val winner = matched.minByOrNull { it.action.ordinal } ?: return null
        if (count) recordHit(matched.map { it.id }.toSet(), winner.action == RuleAction.BLOCK)
        return winner.action
    }

    private fun recordHit(ids: Set<Long>, blocked: Boolean) {
        synchronized(lock) {
            val next = _state.value.rules.map { if (it.id in ids) it.copy(hits = it.hits + 1) else it }
            val count = _state.value.blockedCount + if (blocked) 1 else 0
            _state.value = _state.value.copy(rules = next, blockedCount = count)
            _rules.value = next
            _blockedCount.value = count
            write()
        }
    }

    // ---------------------------------------------------------------- matching

    private fun matches(rule: FilterRule, address: String, body: String, known: Boolean): Boolean = when (rule.kind) {
        RuleKind.KEYWORD -> keywords(rule.pattern).any { body.contains(it, ignoreCase = true) }
        // With a time limit: a pattern that takes too long (catastrophic backtracking) just doesn't match,
        // so it can never hold up an incoming message.
        RuleKind.REGEX -> compile(rule.pattern)?.let { r -> regexMatches(rule.pattern, r, body.take(MAX_BODY)) } ?: false
        RuleKind.PREFIX -> prefixes(rule.pattern).any { startsWithNumber(address, it) }
        RuleKind.SHORT_CODE -> isShortCode(address)
        RuleKind.NAMED_SENDER -> address.any { it.isLetter() } && !address.contains('@')
        // Only when TextCtrl may read contacts: otherwise everyone would look unknown.
        RuleKind.UNKNOWN -> contacts.granted && !known
    }

    private val regexCache = HashMap<String, Regex?>()

    private fun compile(pattern: String): Regex? = synchronized(regexCache) {
        regexCache.getOrPut(pattern) { validRegex(pattern) }
    }

    /** Patterns that took too long once; they're skipped from then on (until the app restarts or the rule changes). */
    private val slowPatterns = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    private val regexThreads = java.util.concurrent.Executors.newCachedThreadPool { r ->
        Thread(r, "textctrl-filter").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    /**
     * Runs a regular expression with a time limit: one that takes too long (catastrophic backtracking)
     * counts as no match, so a rule can never hold up or lose an incoming message.
     */
    private fun regexMatches(pattern: String, regex: Regex, text: String): Boolean {
        if (pattern in slowPatterns) return false
        val task = runCatching { regexThreads.submit<Boolean> { regex.containsMatchIn(text) } }.getOrNull() ?: return false
        return try {
            task.get(REGEX_TIME_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: java.util.concurrent.TimeoutException) {
            slowPatterns += pattern
            task.cancel(true)
            false
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val MAX_BODY = 4000
        private const val REGEX_TIME_MS = 300L
        const val MAX_PATTERN = 300

        fun keywords(pattern: String): List<String> = pattern.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

        fun prefixes(pattern: String): List<String> = pattern.split(',', '\n', ' ').map { p -> p.filter { it.isDigit() || it == '+' } }.filter { it.isNotEmpty() }

        /** A regular expression that compiles and isn't absurdly long; null otherwise. */
        fun validRegex(pattern: String): Regex? =
            if (pattern.isBlank() || pattern.length > MAX_PATTERN) null
            else runCatching { Regex(pattern, setOf(RegexOption.IGNORE_CASE)) }.getOrNull()

        fun isShortCode(address: String): Boolean {
            val clean = address.filter { !it.isWhitespace() && it != '-' }
            return clean.length in 3..8 && clean.all { it.isDigit() }
        }

        /** "+32 470 12…" starts with "+32"; "0900…" with "0900". "+" and "00" are treated alike. */
        fun startsWithNumber(address: String, prefix: String): Boolean {
            val a = address.filter { it.isDigit() || it == '+' }.replaceFirst(Regex("^00"), "+")
            val p = prefix.replaceFirst(Regex("^00"), "+")
            return a.startsWith(p)
        }
    }

    // ---------------------------------------------------------------- storage

    private fun read(): FilterFile = runCatching {
        if (!file.exists()) return FilterFile()
        val plain = KeystoreCrypto.decrypt(file.readText()) ?: return FilterFile()
        json.decodeFromString(FilterFile.serializer(), plain)
    }.getOrDefault(FilterFile())

    /** Saves the rules. A failure (for example the secure hardware being busy) keeps them in memory and never crashes. */
    private fun write() {
        runCatching {
            val plain = json.encodeToString(FilterFile.serializer(), _state.value)
            val tmp = File(file.path + ".tmp")
            tmp.writeText(KeystoreCrypto.encrypt(plain))
            if (!tmp.renameTo(file)) tmp.delete()
        }
    }
}
