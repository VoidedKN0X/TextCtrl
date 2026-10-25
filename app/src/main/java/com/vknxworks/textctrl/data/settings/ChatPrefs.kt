package com.vknxworks.textctrl.data.settings

import android.content.Context
import com.vknxworks.textctrl.data.crypto.KeystoreCrypto
import com.vknxworks.textctrl.data.lock.LockVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What TextCtrl keeps about chats on its own: pinned, archived and muted chats, and unsent drafts.
 * Texts themselves stay in Android's message store, like with any SMS app.
 *
 * Drafts are encrypted with the app lock's key while the lock is on, so a locked TextCtrl can't read them.
 */
class ChatPrefs(context: Context, private val vault: LockVault) {
    private val prefs = context.applicationContext.getSharedPreferences("chats", Context.MODE_PRIVATE)

    private val _pinned = MutableStateFlow(readSet(K_PINNED))
    val pinned: StateFlow<Set<Long>> = _pinned.asStateFlow()

    private val _archived = MutableStateFlow(readSet(K_ARCHIVED))
    val archived: StateFlow<Set<Long>> = _archived.asStateFlow()

    private val _muted = MutableStateFlow(readSet(K_MUTED))
    val muted: StateFlow<Set<Long>> = _muted.asStateFlow()

    private val _drafts = MutableStateFlow(readSet(K_DRAFTS))
    /** Chats with an unsent draft. */
    val drafts: StateFlow<Set<Long>> = _drafts.asStateFlow()

    private val _hidden = MutableStateFlow(readHidden())
    /**
     * Hidden chats. Which chats they are is encrypted with a key in the phone's secure hardware, so a copy
     * of TextCtrl's files doesn't tell; the hidden-chats PIN guards the screen that shows them.
     */
    val hidden: StateFlow<Set<Long>> = _hidden.asStateFlow()

    private val _autoDelete = MutableStateFlow(readAutoDelete())
    /** Chats with their own auto-delete choice, instead of the one in Settings. */
    val autoDelete: StateFlow<Map<Long, AutoDelete>> = _autoDelete.asStateFlow()

    private val _bubbled = MutableStateFlow(readSet(K_BUBBLED))
    /** Chats chosen to show as bubbles (with "Bubbles: Chosen chats"). */
    val bubbled: StateFlow<Set<Long>> = _bubbled.asStateFlow()

    fun setBubbled(threadId: Long, on: Boolean) = toggle(K_BUBBLED, _bubbled, threadId, on)
    fun isBubbled(threadId: Long) = threadId in _bubbled.value

    private val _spam = MutableStateFlow(readSet(K_SPAM))
    /** Chats in the Spam folder: kept out of Chats, Archived, Search and the widget, and never notified. */
    val spam: StateFlow<Set<Long>> = _spam.asStateFlow()

    private val _notSpam = MutableStateFlow(readSet(K_NOT_SPAM))

    fun isSpam(threadId: Long) = threadId in _spam.value

    /** A chat you moved out of Spam (or never want there): filters leave it alone from then on. */
    fun isNotSpam(threadId: Long) = threadId in _notSpam.value

    /** Moves a chat into the Spam folder (or out of it again, which also trusts it from then on). */
    fun setSpam(threadId: Long, on: Boolean) {
        toggle(K_SPAM, _spam, threadId, on)
        toggle(K_NOT_SPAM, _notSpam, threadId, !on)
    }

    /** A filter put a new message's chat into Spam. Chats you rescued from Spam aren't touched. */
    fun markSpamByFilter(threadId: Long) {
        if (!isNotSpam(threadId)) toggle(K_SPAM, _spam, threadId, true)
    }

    fun setPinned(threadId: Long, on: Boolean) = toggle(K_PINNED, _pinned, threadId, on)
    fun setArchived(threadId: Long, on: Boolean) = toggle(K_ARCHIVED, _archived, threadId, on)
    fun setMuted(threadId: Long, on: Boolean) = toggle(K_MUTED, _muted, threadId, on)

    fun isMuted(threadId: Long) = threadId in _muted.value

    fun setHidden(threadId: Long, on: Boolean) {
        val next = if (on) _hidden.value + threadId else _hidden.value - threadId
        if (next == _hidden.value) return
        _hidden.value = next
        prefs.edit().putString(K_HIDDEN, KeystoreCrypto.encrypt(next.joinToString(","))).apply()
    }

    fun isHidden(threadId: Long) = threadId in _hidden.value

    private fun readHidden(): Set<Long> {
        val raw = prefs.getString(K_HIDDEN, null) ?: return emptySet()
        return KeystoreCrypto.decrypt(raw).orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet()
    }

    /** [mode] null: follow Settings. */
    fun setAutoDelete(threadId: Long, mode: AutoDelete?) {
        val next = if (mode == null) _autoDelete.value - threadId else _autoDelete.value + (threadId to mode)
        _autoDelete.value = next
        prefs.edit().putString(K_AUTO_DELETE, next.entries.joinToString(",") { "${it.key}:${it.value.name}" }).apply()
    }

    private fun readAutoDelete(): Map<Long, AutoDelete> =
        prefs.getString(K_AUTO_DELETE, null).orEmpty().split(',').mapNotNull { entry ->
            val id = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
            val mode = AutoDelete.entries.firstOrNull { it.name == entry.substringAfter(':') } ?: return@mapNotNull null
            id to mode
        }.toMap()

    /** A new message brings an archived chat back, like in other messaging apps. */
    fun unarchive(threadId: Long) {
        if (threadId in _archived.value) setArchived(threadId, false)
    }

    /** Forgets everything about a deleted chat. */
    fun forget(threadId: Long) {
        setPinned(threadId, false)
        setArchived(threadId, false)
        setMuted(threadId, false)
        setHidden(threadId, false)
        setAutoDelete(threadId, null)
        setBubbled(threadId, false)
        toggle(K_SPAM, _spam, threadId, false)
        toggle(K_NOT_SPAM, _notSpam, threadId, false)
        saveDraft(threadId, "")
    }

    // ---------------------------------------------------------------- drafts

    /** The draft for [threadId], or "" (also while locked, when it can't be read). */
    fun draft(threadId: Long): String {
        val raw = prefs.getString(draftKey(threadId), null) ?: return ""
        return if (LockVault.isSealed(raw)) vault.open(raw).orEmpty() else raw
    }

    fun saveDraft(threadId: Long, text: String) {
        val edit = prefs.edit()
        if (text.isBlank()) {
            edit.remove(draftKey(threadId))
            setIds(K_DRAFTS, _drafts, _drafts.value - threadId, edit)
        } else {
            val stored = if (vault.enabled) vault.seal(text) ?: return else text
            edit.putString(draftKey(threadId), stored)
            setIds(K_DRAFTS, _drafts, _drafts.value + threadId, edit)
        }
        edit.apply()
    }

    /** The app lock went on: encrypt every draft with its key. */
    fun sealDrafts() {
        val edit = prefs.edit()
        for (id in _drafts.value) {
            val raw = prefs.getString(draftKey(id), null) ?: continue
            if (!LockVault.isSealed(raw)) vault.seal(raw)?.let { edit.putString(draftKey(id), it) }
        }
        edit.commit()
    }

    /** The app lock went off (while unlocked): store drafts plainly again. */
    fun unsealDrafts() {
        val edit = prefs.edit()
        for (id in _drafts.value) {
            val raw = prefs.getString(draftKey(id), null) ?: continue
            if (LockVault.isSealed(raw)) {
                val plain = vault.open(raw)
                if (plain != null) edit.putString(draftKey(id), plain) else edit.remove(draftKey(id))
            }
        }
        edit.commit()
    }

    /** The PIN was reset: drafts encrypted with the old key can't be read any more. */
    fun dropSealedDrafts() {
        val edit = prefs.edit()
        var ids = _drafts.value
        for (id in _drafts.value) {
            val raw = prefs.getString(draftKey(id), null)
            if (raw == null || LockVault.isSealed(raw)) {
                edit.remove(draftKey(id))
                ids = ids - id
            }
        }
        setIds(K_DRAFTS, _drafts, ids, edit)
        edit.commit()
    }

    // ---------------------------------------------------------------- helpers

    private fun draftKey(threadId: Long) = "draft_$threadId"

    private fun readSet(key: String): Set<Long> =
        prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    private fun toggle(key: String, flow: MutableStateFlow<Set<Long>>, id: Long, on: Boolean) {
        val next = if (on) flow.value + id else flow.value - id
        if (next == flow.value) return
        val edit = prefs.edit()
        setIds(key, flow, next, edit)
        edit.apply()
    }

    private fun setIds(key: String, flow: MutableStateFlow<Set<Long>>, ids: Set<Long>, edit: android.content.SharedPreferences.Editor) {
        edit.putStringSet(key, ids.map { it.toString() }.toSet())
        flow.value = ids
    }

    private companion object {
        const val K_PINNED = "pinned"
        const val K_ARCHIVED = "archived"
        const val K_MUTED = "muted"
        const val K_DRAFTS = "draft_ids"
        const val K_HIDDEN = "hidden_enc"
        const val K_AUTO_DELETE = "auto_delete"
        const val K_BUBBLED = "bubbled"
        const val K_SPAM = "spam"
        const val K_NOT_SPAM = "not_spam"
    }
}
