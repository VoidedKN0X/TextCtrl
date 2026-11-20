package com.vknxworks.textctrl.data.settings

import com.vknxworks.textctrl.data.crypto.SealedText

/** Where [Drafts] keeps its values: the `chats` preferences in the app, a map in tests. */
interface DraftSlots {
    fun read(name: String): String?

    /** The chats that have a draft. */
    fun ids(): Set<Long>

    /** Stores [puts], removes [removes] and sets the chats with a draft to [ids], all in one write. */
    fun write(puts: Map<String, String>, removes: Set<String>, ids: Set<Long>, now: Boolean)
}

/** The app lock's own encryption of drafts (the vault key, only in memory while unlocked). */
interface PinLayer {
    val enabled: Boolean
    fun isSealed(value: String): Boolean
    fun seal(plain: String): String?
    fun open(sealed: String): String?
}

/**
 * Unsent drafts, one per chat, and how they are stored:
 *
 * - app lock on: encrypted with the lock's vault key (`vault1:…`), exactly as before;
 * - app lock off: encrypted with the app's data key (`TEXTCTRL-REST1:…`, [SealedText]) when that key is in
 *   memory, and as ordinary text when it isn't (no key yet, or none to be had), exactly as before;
 * - reading accepts all three, whatever the lock's state is now.
 *
 * [dataKey] must only hand over a key that is already in memory: [read] and [save] run on the main thread
 * while a chat opens and closes, so nothing here may wait for the phone's secure hardware.
 *
 * A draft sealed with the data key that can't be opened (the key isn't loaded yet, or is lost) is never
 * thrown away by saving: an empty save leaves it where it is, and a new draft first moves it aside under
 * [asideName]. [recover] hands such drafts back once they can be opened. Only deleting the chat
 * ([discard]) removes them.
 */
class Drafts(
    private val slots: DraftSlots,
    private val pin: PinLayer,
    private val dataKey: () -> ByteArray?,
) {
    /** Chats whose stored draft couldn't be opened when their composer asked for it. */
    private val unseen = HashSet<Long>()

    /** Set-aside drafts that [recover] handed to a composer; they go at that chat's next save. */
    private val handed = HashMap<Long, Set<String>>()

    /** The draft for [id], or "" (also while locked, or when it can't be opened right now). */
    @Synchronized
    fun read(id: Long): String {
        // A new composer: whatever was handed to an earlier one that never saved it is offered again.
        handed.remove(id)
        val raw = slots.read(name(id)) ?: return ""
        if (pin.isSealed(raw)) return pin.open(raw).orEmpty()
        if (!SealedText.isSealed(raw)) return raw
        val plain = openRest(raw)
        if (plain == null) {
            unseen += id
            return ""
        }
        unseen -= id
        return plain
    }

    @Synchronized
    fun save(id: Long, text: String) {
        val name = name(id)
        val raw = slots.read(name)
        // A draft the composer never got to see: it must not be lost to this save.
        val stuck = raw != null && !pin.isSealed(raw) && SealedText.isSealed(raw) && (id in unseen || openRest(raw) == null)
        val puts = HashMap<String, String>()
        val removes = HashSet<String>()
        var ids = slots.ids()
        var movedAside = false
        if (text.isBlank()) {
            if (!stuck) {
                removes += name
                ids = ids - id
            }
        } else {
            val stored = if (pin.enabled) pin.seal(text) ?: return else sealRest(text)
            puts[name] = stored
            ids = ids + id
            movedAside = stuck
        }
        // What stays set aside: everything that wasn't handed back to the composer, plus the stuck draft.
        val asideName = asideName(id)
        val asideRaw = slots.read(asideName)
        val before = entries(asideRaw)
        val after = before.filter { it !in handed[id].orEmpty() } + (if (movedAside && raw != null) listOf(raw) else emptyList())
        if (after != before) {
            if (after.isEmpty()) removes += asideName else puts[asideName] = after.joinToString("\n")
        }
        slots.write(puts, removes, ids, now = false)
        handed.remove(id)
        if (!stuck || movedAside) unseen -= id
    }

    /** The chat is gone: its draft goes too, whatever state it is in. */
    @Synchronized
    fun discard(id: Long) {
        slots.write(emptyMap(), setOf(name(id), asideName(id)), slots.ids() - id, now = false)
        unseen -= id
        handed.remove(id)
    }

    /** [id] has a draft its composer hasn't seen: one that couldn't be opened, or one that was set aside. */
    @Synchronized
    fun needsRecovery(id: Long): Boolean =
        id in unseen || entries(slots.read(asideName(id))).any { it !in handed[id].orEmpty() }

    /**
     * The text of [id]'s drafts that couldn't be opened before and can be now (oldest first, one per line
     * block), or "". Each is handed over once; what was set aside leaves storage at the chat's next save.
     */
    @Synchronized
    fun recover(id: Long): String {
        val parts = ArrayList<String>()
        val already = handed[id].orEmpty()
        val nowHanded = HashSet(already)
        for (entry in entries(slots.read(asideName(id)))) {
            if (entry in already) continue
            val plain = openAny(entry) ?: continue
            parts += plain
            nowHanded += entry
        }
        if (nowHanded.isNotEmpty()) handed[id] = nowHanded
        if (id in unseen) {
            val raw = slots.read(name(id))
            if (raw == null || pin.isSealed(raw) || !SealedText.isSealed(raw)) {
                // Replaced or removed since; there is nothing unseen left under this name.
                unseen -= id
            } else {
                val plain = openRest(raw)
                if (plain != null) {
                    parts += plain
                    unseen -= id
                }
            }
        }
        return parts.filter { it.isNotBlank() }.joinToString("\n")
    }

    /** The data key is in memory now: seals the drafts that are still ordinary text (app lock off only). */
    @Synchronized
    fun sealOrdinary() {
        if (pin.enabled) return
        val key = dataKey() ?: return
        val puts = HashMap<String, String>()
        for (id in slots.ids()) {
            val raw = slots.read(name(id)) ?: continue
            if (pin.isSealed(raw) || SealedText.isSealed(raw)) continue
            // seal() opens its own result again and compares; when that fails the ordinary text stays.
            SealedText.seal(key, raw)?.let { puts[name(id)] = it }
        }
        if (puts.isNotEmpty()) slots.write(puts, emptySet(), slots.ids(), now = false)
    }

    /** The app lock went on: encrypt every draft with its key. One that can't be opened stays as it is. */
    @Synchronized
    fun sealForPin() {
        val puts = HashMap<String, String>()
        for (id in slots.ids()) {
            val raw = slots.read(name(id)) ?: continue
            if (pin.isSealed(raw)) continue
            val plain = if (SealedText.isSealed(raw)) openRest(raw) ?: continue else raw
            pin.seal(plain)?.let { puts[name(id)] = it }
        }
        slots.write(puts, emptySet(), slots.ids(), now = true)
    }

    /** The app lock went off (while unlocked): drafts leave its key, and are sealed with the data key instead. */
    @Synchronized
    fun openFromPin() {
        val puts = HashMap<String, String>()
        val removes = HashSet<String>()
        for (id in slots.ids()) {
            val raw = slots.read(name(id)) ?: continue
            if (pin.isSealed(raw)) {
                val plain = pin.open(raw)
                if (plain != null) puts[name(id)] = sealRest(plain) else removes += name(id)
            }
        }
        slots.write(puts, removes, slots.ids(), now = true)
    }

    /** The PIN was reset: drafts encrypted with the old key can't be read any more. */
    @Synchronized
    fun dropPinSealed() {
        val removes = HashSet<String>()
        var ids = slots.ids()
        for (id in slots.ids()) {
            val raw = slots.read(name(id))
            if (raw == null || pin.isSealed(raw)) {
                removes += name(id)
                ids = ids - id
            }
        }
        slots.write(emptyMap(), removes, ids, now = true)
    }

    private fun sealRest(plain: String): String {
        val key = dataKey() ?: return plain
        return SealedText.seal(key, plain) ?: plain
    }

    private fun openRest(sealed: String): String? {
        val key = dataKey() ?: return null
        return try {
            SealedText.open(key, sealed)
        } catch (e: Exception) {
            null
        }
    }

    /** A set-aside value: sealed with the data key, or (never written by this class) ordinary text. */
    private fun openAny(value: String): String? = if (SealedText.isSealed(value)) openRest(value) else value

    private fun entries(aside: String?): List<String> = aside.orEmpty().split('\n').filter { it.isNotEmpty() }

    companion object {
        fun name(id: Long) = "draft_$id"

        /** Sealed drafts of chat [id] that couldn't be opened when a new draft took their place, one per line. */
        fun asideName(id: Long) = "draft_unreadable_$id"
    }
}
