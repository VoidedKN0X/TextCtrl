package com.vknxworks.textctrl.data.schedule

import android.content.Context
import com.vknxworks.textctrl.data.settings.AutoDelete
import com.vknxworks.textctrl.data.settings.ChatPrefs
import com.vknxworks.textctrl.data.settings.SettingsRepository
import com.vknxworks.textctrl.sms.MessageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Auto-delete: removes messages older than the chosen age, per chat or for everything. Messages another
 * app marked as locked (kept) are left alone. Runs once a day, and when TextCtrl starts if it's been a while.
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
        val global = settings.stored.first().autoDelete
        return global != AutoDelete.NEVER || chats.autoDelete.value.values.any { it != AutoDelete.NEVER }
    }

    /** Runs if the last run was more than 20 hours ago. */
    suspend fun runIfDue() {
        if (System.currentTimeMillis() - prefs.getLong(K_LAST, 0L) > 20 * HOUR) run()
    }

    suspend fun run() = withContext(Dispatchers.IO) {
        prefs.edit().putLong(K_LAST, System.currentTimeMillis()).apply()
        val global = settings.stored.first().autoDelete
        val overrides = chats.autoDelete.value
        if (global == AutoDelete.NEVER && overrides.values.all { it == AutoDelete.NEVER }) return@withContext
        val now = System.currentTimeMillis()
        var deleted = 0
        for (conv in runCatching { store.conversations() }.getOrDefault(emptyList())) {
            val mode = overrides[conv.threadId] ?: global
            if (mode.days <= 0) continue
            deleted += store.deleteOlderThan(conv.threadId, now - mode.days * DAY)
        }
        if (deleted > 0) store.touch()
    }

    private companion object {
        const val K_LAST = "auto_delete_last"
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
