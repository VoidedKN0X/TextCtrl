package com.vknxworks.textctrl.data.settings

import android.content.Context
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

    fun setPinned(threadId: Long, on: Boolean) = toggle(K_PINNED, _pinned, threadId, on)
    fun setArchived(threadId: Long, on: Boolean) = toggle(K_ARCHIVED, _archived, threadId, on)
    fun setMuted(threadId: Long, on: Boolean) = toggle(K_MUTED, _muted, threadId, on)

    fun isMuted(threadId: Long) = threadId in _muted.value

    /** A new message brings an archived chat back, like in other messaging apps. */
    fun unarchive(threadId: Long) {
        if (threadId in _archived.value) setArchived(threadId, false)
    }

    /** Forgets everything about a deleted chat. */
    fun forget(threadId: Long) {
        setPinned(threadId, false)
        setArchived(threadId, false)
        setMuted(threadId, false)
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
    }
}
