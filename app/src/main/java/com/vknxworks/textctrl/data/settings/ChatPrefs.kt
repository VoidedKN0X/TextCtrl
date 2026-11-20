package com.vknxworks.textctrl.data.settings

import android.content.Context
import com.vknxworks.textctrl.data.crypto.DataKeyStore
import com.vknxworks.textctrl.data.crypto.KeystoreCrypto
import com.vknxworks.textctrl.data.crypto.KeystoreWrapper
import com.vknxworks.textctrl.data.lock.LockVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * What TextCtrl keeps about chats on its own: pinned, archived and muted chats, and unsent drafts.
 * Texts themselves stay in Android's message store, like with any SMS app.
 *
 * Drafts are encrypted with the app lock's key while the lock is on, so a locked TextCtrl can't read them,
 * and with the app's data key while it is off, so a copy of TextCtrl's files doesn't show them ([Drafts]).
 */
class ChatPrefs(context: Context, private val vault: LockVault) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("chats", Context.MODE_PRIVATE)

    init {
        // Bubbles were removed in 0.6.3; forget which chats had them.
        if (prefs.contains("bubbled")) prefs.edit().remove("bubbled").apply()
    }

    private val _pinned = MutableStateFlow(readSet(K_PINNED))
    val pinned: StateFlow<Set<Long>> = _pinned.asStateFlow()

    private val _archived = MutableStateFlow(readSet(K_ARCHIVED))
    val archived: StateFlow<Set<Long>> = _archived.asStateFlow()

    private val _muted = MutableStateFlow(readSet(K_MUTED))
    val muted: StateFlow<Set<Long>> = _muted.asStateFlow()

    private val _drafts = MutableStateFlow(readSet(K_DRAFTS))
    /** Chats with an unsent draft. */
    val drafts: StateFlow<Set<Long>> = _drafts.asStateFlow()

    // Reading the hidden chats asks the phone's secure hardware, which is slow, and TextCtrl also starts for
    // every incoming message. So the list is read on a thread of its own from the moment the app starts.
    // Nothing ever sees it half-read: every way in ([hidden], [isHidden], [setHidden]) goes through
    // [ensureHidden], which waits for the read to finish (or does it itself when it hasn't begun), so a hidden
    // chat can't be listed or shown in a notification before the list is known.
    private val _hidden = MutableStateFlow<Set<Long>>(emptySet())
    private val hiddenFlow = _hidden.asStateFlow()
    private val hiddenLock = Any()
    @Volatile private var hiddenLoaded = false

    init {
        if (prefs.contains(K_HIDDEN)) {
            Thread({ runCatching { ensureHidden() } }, "hidden-chats").start()
        } else {
            hiddenLoaded = true
        }
    }

    private fun ensureHidden() {
        if (hiddenLoaded) return
        synchronized(hiddenLock) {
            if (!hiddenLoaded) {
                _hidden.value = readHidden()
                hiddenLoaded = true
            }
        }
    }

    /**
     * Hidden chats. Which chats they are is encrypted with a key in the phone's secure hardware, so a copy
     * of TextCtrl's files doesn't tell; the hidden-chats PIN guards the screen that shows them.
     */
    val hidden: StateFlow<Set<Long>>
        get() {
            ensureHidden()
            return hiddenFlow
        }

    private val _autoDelete = MutableStateFlow(readAutoDelete())
    /** Chats with their own auto-delete choice, instead of the one in Settings. */
    val autoDelete: StateFlow<Map<Long, AutoDelete>> = _autoDelete.asStateFlow()



    private val _spam = MutableStateFlow(readSet(K_SPAM))
    /** Chats in the Spam folder: kept out of Chats, Archived and Search, and never notified. */
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
        ensureHidden()
        val next = if (on) _hidden.value + threadId else _hidden.value - threadId
        if (next == _hidden.value) return
        _hidden.value = next
        prefs.edit().putString(K_HIDDEN, KeystoreCrypto.encrypt(next.joinToString(","))).apply()
    }

    fun isHidden(threadId: Long): Boolean {
        ensureHidden()
        return threadId in _hidden.value
    }

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
        toggle(K_SPAM, _spam, threadId, false)
        toggle(K_NOT_SPAM, _notSpam, threadId, false)
        discardDraft(threadId)
    }

    // ---------------------------------------------------------------- drafts

    // The data key that seals drafts while the app lock is off. It is fetched off the main thread
    // ([prepareDrafts]); everything else only uses it once it is in memory.
    private val draftKeys by lazy { DataKeyStore(appContext.filesDir, KeystoreWrapper(DATA_KEY_ALIAS)) }
    @Volatile private var draftKey: ByteArray? = null
    private val draftKeyTried = MutableStateFlow(false)

    private val draftBox = Drafts(
        object : DraftSlots {
            override fun read(name: String): String? = prefs.getString(name, null)
            override fun ids(): Set<Long> = _drafts.value
            override fun write(puts: Map<String, String>, removes: Set<String>, ids: Set<Long>, now: Boolean) {
                val edit = prefs.edit()
                for (name in removes) edit.remove(name)
                for ((name, value) in puts) edit.putString(name, value)
                setIds(K_DRAFTS, _drafts, ids, edit)
                if (now) edit.commit() else edit.apply()
            }
        },
        object : PinLayer {
            override val enabled: Boolean get() = vault.enabled
            override fun isSealed(value: String): Boolean = LockVault.isSealed(value)
            override fun seal(plain: String): String? = vault.seal(plain)
            override fun open(sealed: String): String? = vault.open(sealed)
        },
        { draftKey },
    )

    /**
     * Gets the data key ready (made at the first start) and seals the drafts that are still ordinary text.
     * Slow: it asks the phone's secure hardware. Never on the main thread. Never throws.
     */
    fun prepareDrafts() {
        try {
            draftKey = draftKeys.get(create = true)
            draftBox.sealOrdinary()
        } catch (e: Throwable) {
            // Without a key drafts are read and written as ordinary text, as before.
        } finally {
            draftKeyTried.value = true
        }
    }

    /** Asks the secure hardware for the data key again, after it couldn't be had. Slow; never on the main thread. */
    fun retryDraftKey() {
        try {
            draftKeys.reset()
            draftKey = draftKeys.get(create = true)
            draftBox.sealOrdinary()
        } catch (e: Throwable) {
        }
    }

    /** Returns once [prepareDrafts] has finished, with or without a key. */
    suspend fun awaitDraftKey() {
        draftKeyTried.first { it }
    }

    /** The draft for [threadId], or "" (also while locked, or when it can't be opened right now). */
    fun draft(threadId: Long): String = draftBox.read(threadId)

    /** Keeps [text] as the chat's draft; blank removes it. A draft that couldn't be opened is never removed here. */
    fun saveDraft(threadId: Long, text: String) = draftBox.save(threadId, text)

    /** The chat is being deleted: its draft goes too, also one that couldn't be opened. */
    fun discardDraft(threadId: Long) = draftBox.discard(threadId)

    /** The chat has a draft its text field hasn't shown: it couldn't be opened, or was set aside for a newer one. */
    fun draftNeedsRecovery(threadId: Long): Boolean = draftBox.needsRecovery(threadId)

    /** The text of such drafts that can be opened now, or "". */
    fun recoverDraft(threadId: Long): String = draftBox.recover(threadId)

    /** The app lock went on: encrypt every draft with its key. */
    fun sealDrafts() = draftBox.sealForPin()

    /** The app lock went off (while unlocked): drafts leave its key and are sealed with the data key instead. */
    fun unsealDrafts() = draftBox.openFromPin()

    /** The PIN was reset: drafts encrypted with the old key can't be read any more. */
    fun dropSealedDrafts() = draftBox.dropPinSealed()

    // ---------------------------------------------------------------- helpers

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
        const val DATA_KEY_ALIAS = "textctrl_data_at_rest"
        const val K_HIDDEN = "hidden_enc"
        const val K_AUTO_DELETE = "auto_delete"
        const val K_SPAM = "spam"
        const val K_NOT_SPAM = "not_spam"
    }
}
