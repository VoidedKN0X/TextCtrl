package com.vknxworks.textctrl.data.filters

import android.content.Context
import androidx.annotation.StringRes
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.data.crypto.KeystoreCrypto
import com.vknxworks.textctrl.data.settings.ChatPrefs
import com.vknxworks.textctrl.sms.Blocklist
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
    /** These exact numbers or sender names. */
    EXACT(R.string.rule_exact, true),
    /** Short numbers of 3 to 8 digits, as used by services and adverts. */
    SHORT_CODE(R.string.rule_short_code, false),
    /** Senders with a name instead of a number ("BANK", "Promo"). */
    NAMED_SENDER(R.string.rule_named_sender, false),
    /** Senders not in your contacts that you never wrote to. */
    UNKNOWN(R.string.rule_unknown, false),
}

/**
 * What happens to a message a rule matches. Messages are never deleted: both Block and Spam keep the
 * message in the Spam folder. The order matters: the first (strictest) matching action wins.
 */
enum class RuleAction(@StringRes val label: Int) {
    /** Kept in the Spam folder, and the sender goes on Android's block list so nothing more arrives from it. */
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
private data class FilterFile(
    val rules: List<FilterRule> = emptyList(),
    val skipContacts: Boolean = true,
    /** Senders put on the block list by Block rules (before 0.7.0: messages thrown away). */
    val blockedCount: Int = 0,
    /** A quiet notification that sums up what went to Spam. */
    val notifySummary: Boolean = false,
    /** Messages filtered since the Spam folder was last opened. */
    val unseenFiltered: Int = 0,
)

/** Result of an import: what was added, and what was left out. */
data class ImportResult(val rules: Int, val blocked: Int, val skipped: Int)

/**
 * Your own keyword and spam filters, applied to every text and picture message when it arrives, before
 * any notification. Everything is worked out on the phone: there's no spam database or online service
 * (TextCtrl has no internet access). The rules are stored encrypted with a key in the phone's secure
 * hardware, because they must work while the app is locked. The matching itself is in [RuleMatcher].
 */
class Filters(
    context: Context,
    private val store: MessageStore,
    private val contacts: Contacts,
    private val chats: ChatPrefs,
    private val blocklist: Blocklist,
) {
    private val file = File(context.filesDir, "filters.enc")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()
    private val lock = Any()
    private val regex = SafeRegex()

    private val _state = MutableStateFlow(read())

    private val _rules = MutableStateFlow(_state.value.rules)
    val rules: StateFlow<List<FilterRule>> = _rules.asStateFlow()

    private val _skipContacts = MutableStateFlow(_state.value.skipContacts)
    /** Never filter messages from people in your contacts or chats you wrote in (on by default). */
    val skipContacts: StateFlow<Boolean> = _skipContacts.asStateFlow()

    private val _blockedCount = MutableStateFlow(_state.value.blockedCount)
    /** Senders put on the block list by Block rules, ever. */
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private val _notifySummary = MutableStateFlow(_state.value.notifySummary)
    /** Show a quiet notification with the number of messages that went to Spam (off by default). */
    val notifySummary: StateFlow<Boolean> = _notifySummary.asStateFlow()

    /** Messages filtered to Spam since the Spam folder was last opened. */
    val unseenFiltered: Int get() = _state.value.unseenFiltered

    /** Regular expressions that took too long and are skipped. */
    val slowPatterns: Set<String> get() = regex.slowPatterns

    fun add(kind: RuleKind, pattern: String, action: RuleAction) = edit { list ->
        list + FilterRule(newId(), kind, pattern.trim(), action)
    }

    fun update(rule: FilterRule) = edit { list -> list.map { if (it.id == rule.id) rule.copy(pattern = rule.pattern.trim()) else it } }

    fun remove(id: Long) = edit { list -> list.filter { it.id != id } }

    fun setSkipContacts(on: Boolean) = save { it.copy(skipContacts = on) }

    fun setNotifySummary(on: Boolean) = save { it.copy(notifySummary = on, unseenFiltered = 0) }

    /** The Spam folder was opened: the summary starts again from zero. */
    fun markSpamSeen() {
        if (_state.value.unseenFiltered != 0) save { it.copy(unseenFiltered = 0) }
    }

    private fun newId() = (random.nextLong() and Long.MAX_VALUE).coerceAtLeast(1)

    private fun edit(transform: (List<FilterRule>) -> List<FilterRule>) {
        regex.reset()
        save { it.copy(rules = transform(it.rules)) }
    }

    private fun save(transform: (FilterFile) -> FilterFile) {
        synchronized(lock) {
            val next = transform(_state.value)
            _state.value = next
            _rules.value = next.rules
            _skipContacts.value = next.skipContacts
            _blockedCount.value = next.blockedCount
            _notifySummary.value = next.notifySummary
            write()
        }
    }

    /**
     * What to do with a message from [address] saying [body], arriving in [threadId] (null when the chat
     * isn't known yet). Null: let it through as usual. The strictest matching rule wins (block, then spam,
     * then mute). With [count], the rules' hit counters go up and a Block rule puts the sender on Android's
     * block list; without, it's only a look (nothing changes).
     */
    suspend fun check(address: String, body: String, threadId: Long?, count: Boolean = true): RuleAction? {
        if (_rules.value.none { it.enabled } || address.isBlank()) return null
        // A chat you moved back out of Spam is trusted from then on.
        if (threadId != null && chats.isNotSpam(threadId)) return null
        val inContacts = contacts.granted && contacts.lookup(address).name != null
        val wrote = threadId != null && store.hasOutgoing(threadId)
        val sender = Sender(address, known = inContacts || wrote, contactsGranted = contacts.granted)
        val verdict = RuleMatcher.evaluate(_rules.value, sender, body, _skipContacts.value, regex) ?: return null
        if (count) {
            val blocked = verdict.action == RuleAction.BLOCK && blockSender(address)
            recordHit(verdict.matched.map { it.id }.toSet(), blocked, filtered = verdict.action != RuleAction.MUTE)
        }
        return verdict.action
    }

    /** Puts [address] on Android's block list, when TextCtrl may (as the SMS app) and it isn't there yet. */
    private suspend fun blockSender(address: String): Boolean = runCatching {
        if (!blocklist.canBlock || blocklist.isBlocked(address)) return false
        blocklist.block(address)
        true
    }.getOrDefault(false)

    private fun recordHit(ids: Set<Long>, blocked: Boolean, filtered: Boolean) = save { s ->
        s.copy(
            rules = s.rules.map { if (it.id in ids) it.copy(hits = it.hits + 1) else it },
            blockedCount = s.blockedCount + if (blocked) 1 else 0,
            unseenFiltered = s.unseenFiltered + if (filtered) 1 else 0,
        )
    }

    // ---------------------------------------------------------------- import and export

    /** Adds the imported rules that aren't there yet (same kind, pattern and action), with fresh counters. */
    fun importRules(imported: List<ExportedRule>): Int {
        var added = 0
        edit { list ->
            val existing = list.map { Triple(it.kind.name, it.pattern, it.action.name) }.toMutableSet()
            val fresh = imported.mapNotNull { r ->
                val kind = RuleKind.entries.firstOrNull { it.name == r.kind } ?: return@mapNotNull null
                val action = RuleAction.entries.firstOrNull { it.name == r.action } ?: return@mapNotNull null
                if (!existing.add(Triple(kind.name, r.pattern, action.name))) return@mapNotNull null
                FilterRule(newId(), kind, r.pattern, action, r.enabled)
            }.take((RuleTransfer.MAX_RULES - list.size).coerceAtLeast(0))
            added = fresh.size
            list + fresh
        }
        return added
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
