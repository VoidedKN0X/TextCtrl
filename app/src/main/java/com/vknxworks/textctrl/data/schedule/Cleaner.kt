package com.vknxworks.textctrl.data.schedule

import android.content.Context
import com.vknxworks.textctrl.data.settings.AutoDelete
import com.vknxworks.textctrl.data.settings.ChatPrefs
import com.vknxworks.textctrl.data.settings.SettingsRepository
import com.vknxworks.textctrl.sms.MessageStore
import com.vknxworks.textctrl.sms.OneTimeCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Auto-delete: removes messages older than the chosen age, per chat or for everything, and (only when turned
 * on) received one-time codes that are a day old. Messages another app marked as locked (kept) and starred
 * messages are left alone. Runs once a day, and when TextCtrl starts if it's been a while.
 */
class Cleaner(
    context: Context,
    private val store: MessageStore,
    private val settings: SettingsRepository,
    private val chats: ChatPrefs,
) {
    private val prefs = context.applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE)

    /** Whether anything needs auto-delete at all (in Settings or in a chat). */
    suspend fun needed(): Boolean {
        val s = settings.stored.first()
        return s.deleteCodes || s.autoDelete != AutoDelete.NEVER || chats.autoDelete.value.values.any { it != AutoDelete.NEVER }
    }

    /** Runs if the last run was more than 20 hours ago. */
    suspend fun runIfDue() {
        if (System.currentTimeMillis() - prefs.getLong(K_LAST, 0L) > 20 * HOUR) run()
    }

    suspend fun run() = withContext(Dispatchers.IO) {
        prefs.edit().putLong(K_LAST, System.currentTimeMillis()).apply()
        val stored = settings.stored.first()
        val global = stored.autoDelete
        val overrides = chats.autoDelete.value
        val now = System.currentTimeMillis()
        // Starred messages are never deleted automatically.
        val starred = chats.starred.value
        var deleted = 0
        if (global != AutoDelete.NEVER || overrides.values.any { it != AutoDelete.NEVER }) {
            for (conv in runCatching { store.conversations() }.getOrDefault(emptyList())) {
                val mode = overrides[conv.threadId] ?: global
                if (mode.days <= 0) continue
                val keep = starred.filter { it.threadId == conv.threadId }.map { it.key }
                deleted += store.deleteOlderThan(conv.threadId, now - mode.days * DAY, keep)
            }
        }
        // One-time codes: only when you turned it on. A code is deleted once it is a day old.
        // Only the texts that turned a day old since the last check are read (with two days to spare), not the
        // whole history every day; the first run after turning it on checks everything.
        if (stored.deleteCodes) {
            val cutoff = now - DAY
            val from = codeScanFrom(prefs.getLong(K_CODES_UNTIL, 0L), cutoff)
            deleted += store.deleteCodesOlderThan(cutoff, starred.map { it.key }, from) { OneTimeCode.find(it) != null }
            prefs.edit().putLong(K_CODES_UNTIL, cutoff).apply()
        } else {
            prefs.edit().remove(K_CODES_UNTIL).apply()
        }
        if (deleted > 0) store.touch()
    }

    internal companion object {
        const val K_LAST = "auto_delete_last"
        /** The cut-off of the last one-time code check: texts older than this were all checked. */
        const val K_CODES_UNTIL = "codes_checked_until"
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR

        /**
         * Where the one-time code check starts: two days before the last check's cut-off ([lastUntil]), so a
         * text that arrived late or a clock change isn't missed; 0 (everything) when there was no earlier check
         * or its cut-off is in the future (the clock went back).
         */
        fun codeScanFrom(lastUntil: Long, cutoff: Long): Long =
            if (lastUntil <= 0L || lastUntil > cutoff) 0L else (lastUntil - 2 * DAY).coerceAtLeast(0L)
    }
}
