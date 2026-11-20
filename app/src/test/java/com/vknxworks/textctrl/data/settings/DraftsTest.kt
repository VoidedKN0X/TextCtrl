package com.vknxworks.textctrl.data.settings

import com.vknxworks.textctrl.data.crypto.SealedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How drafts are stored: ordinary text, the data key, the app lock's key, and what happens when one can't be opened. */
class DraftsTest {
    private val key = ByteArray(32) { (it * 5 + 3).toByte() }

    private class Slots : DraftSlots {
        val map = HashMap<String, String>()
        var chats: Set<Long> = emptySet()
        override fun read(name: String): String? = map[name]
        override fun ids(): Set<Long> = chats
        override fun write(puts: Map<String, String>, removes: Set<String>, ids: Set<Long>, now: Boolean) {
            removes.forEach { map.remove(it) }
            map.putAll(puts)
            chats = ids
        }
    }

    /** Stands in for the app lock: "vault1:" + the reversed text; [open] false is a locked app. */
    private class Pin(override var enabled: Boolean = false, var open: Boolean = true) : PinLayer {
        override fun isSealed(value: String) = value.startsWith("vault1:")
        override fun seal(plain: String): String? = if (open) "vault1:" + plain.reversed() else null
        override fun open(sealed: String): String? = if (open) sealed.removePrefix("vault1:").reversed() else null
    }

    private class Key(var bytes: ByteArray?)

    @Test
    fun withoutAKeyDraftsAreOrdinaryTextAsBefore() {
        val slots = Slots()
        val drafts = Drafts(slots, Pin()) { null }
        assertEquals("", drafts.read(4))
        drafts.save(4, "see you at 8")
        assertEquals("see you at 8", slots.map["draft_4"])
        assertEquals(setOf(4L), slots.chats)
        assertEquals("see you at 8", drafts.read(4))
        assertFalse(drafts.needsRecovery(4))
        drafts.save(4, "  ")
        assertNull(slots.map["draft_4"])
        assertTrue(slots.chats.isEmpty())
    }

    @Test
    fun withAKeyDraftsAreSealedAndOrdinaryOnesStillRead() {
        val slots = Slots()
        slots.map["draft_1"] = "old ordinary draft"
        slots.chats = setOf(1L)
        val drafts = Drafts(slots, Pin()) { key }
        assertEquals("old ordinary draft", drafts.read(1))
        drafts.save(2, "new draft")
        val stored = slots.map["draft_2"]!!
        assertTrue(SealedText.isSealed(stored))
        assertFalse(stored.contains("new draft"))
        assertEquals("new draft", drafts.read(2))
        assertEquals(setOf(1L, 2L), slots.chats)
        // The ordinary one is sealed by the pass that runs once the key is in memory.
        drafts.sealOrdinary()
        assertTrue(SealedText.isSealed(slots.map["draft_1"]!!))
        assertEquals("old ordinary draft", drafts.read(1))
        assertEquals(stored, slots.map["draft_2"])
        // Sending or clearing removes it.
        drafts.save(2, "")
        assertNull(slots.map["draft_2"])
        assertEquals(setOf(1L), slots.chats)
    }

    @Test
    fun aSealedDraftReadBeforeTheKeyIsLoadedIsHandedOverLater() {
        val slots = Slots()
        Drafts(slots, Pin()) { key }.save(7, "half a sentence")
        val holder = Key(null)
        val drafts = Drafts(slots, Pin()) { holder.bytes }
        assertEquals("", drafts.read(7))
        assertTrue(drafts.needsRecovery(7))
        // Leaving the chat with an empty field must not delete it.
        drafts.save(7, "")
        assertTrue(SealedText.isSealed(slots.map["draft_7"]!!))
        assertEquals(setOf(7L), slots.chats)
        assertEquals("", drafts.recover(7))
        assertTrue(drafts.needsRecovery(7))
        holder.bytes = key
        assertEquals("half a sentence", drafts.recover(7))
        assertFalse(drafts.needsRecovery(7))
        assertEquals("", drafts.recover(7))
        // From here on it is an ordinary save again.
        drafts.save(7, "")
        assertNull(slots.map["draft_7"])
    }

    @Test
    fun anUnreadableDraftIsSetAsideBeforeANewOneTakesItsPlace() {
        val slots = Slots()
        Drafts(slots, Pin()) { key }.save(7, "first")
        val old = slots.map["draft_7"]!!
        val lost = Drafts(slots, Pin()) { ByteArray(32) }
        assertEquals("", lost.read(7))
        lost.save(7, "second")
        assertEquals(old, slots.map["draft_unreadable_7"])
        assertEquals("second", lost.read(7))
        assertTrue(lost.needsRecovery(7))
        assertEquals("", lost.recover(7))
        // And once more: both stay.
        val lostAgain = Drafts(slots, Pin()) { null }
        assertEquals("", lostAgain.read(7))
        val second = slots.map["draft_7"]!!
        lostAgain.save(7, "third")
        assertEquals(old + "\n" + second, slots.map["draft_unreadable_7"])
        assertEquals("third", slots.map["draft_7"])
        // With the first key back, the first comes back; the one sealed with another key stays aside.
        val back = Drafts(slots, Pin()) { key }
        assertEquals("third", back.read(7))
        assertTrue(back.needsRecovery(7))
        assertEquals("first", back.recover(7))
        assertEquals("", back.recover(7))
        // Until the chat saves, storage still holds it (the app could be killed first).
        assertEquals(old + "\n" + second, slots.map["draft_unreadable_7"])
        // A chat that opens again without having saved gets it offered again.
        assertEquals("third", back.read(7))
        assertTrue(back.needsRecovery(7))
        assertEquals("first", back.recover(7))
        back.save(7, "first\nthird")
        assertEquals(second, slots.map["draft_unreadable_7"])
        assertEquals("first\nthird", back.read(7))
        assertTrue(back.needsRecovery(7))
    }

    @Test
    fun handedBackDraftsLeaveStorageOnAnEmptySaveToo() {
        val slots = Slots()
        Drafts(slots, Pin()) { key }.save(3, "first")
        val holder = Key(null)
        val drafts = Drafts(slots, Pin()) { holder.bytes }
        assertEquals("", drafts.read(3))
        drafts.save(3, "typed meanwhile")
        assertEquals("typed meanwhile", slots.map["draft_3"])
        holder.bytes = key
        assertEquals("first", drafts.recover(3))
        // Sent: the field is empty, and nothing is left behind.
        drafts.save(3, "")
        assertNull(slots.map["draft_3"])
        assertNull(slots.map["draft_unreadable_3"])
        assertFalse(drafts.needsRecovery(3))
        assertTrue(slots.chats.isEmpty())
    }

    @Test
    fun deletingAChatRemovesItsDraftsWhateverTheirState() {
        val slots = Slots()
        Drafts(slots, Pin()) { key }.save(9, "first")
        val lost = Drafts(slots, Pin()) { null }
        lost.read(9)
        lost.save(9, "second")
        lost.discard(9)
        assertTrue(slots.map.isEmpty())
        assertTrue(slots.chats.isEmpty())
        assertFalse(lost.needsRecovery(9))
    }

    @Test
    fun theAppLockKeepsItsOwnLayer() {
        val slots = Slots()
        val pin = Pin()
        val drafts = Drafts(slots, pin) { key }
        drafts.save(1, "one")
        slots.map["draft_2"] = "two"
        slots.chats = setOf(1L, 2L)
        // Lock on: both become vault-sealed text, exactly the format from before.
        pin.enabled = true
        drafts.sealForPin()
        assertEquals("vault1:eno", slots.map["draft_1"])
        assertEquals("vault1:owt", slots.map["draft_2"])
        drafts.save(3, "three")
        assertEquals("vault1:eerht", slots.map["draft_3"])
        assertEquals("three", drafts.read(3))
        drafts.sealOrdinary()
        assertEquals("vault1:eerht", slots.map["draft_3"])
        // Locked: nothing can be read or saved, as before.
        pin.open = false
        assertEquals("", drafts.read(3))
        drafts.save(3, "changed")
        assertEquals("vault1:eerht", slots.map["draft_3"])
        assertFalse(drafts.needsRecovery(3))
        // Lock off again: sealed with the data key.
        pin.open = true
        drafts.openFromPin()
        pin.enabled = false
        for (id in 1L..3L) assertTrue(SealedText.isSealed(slots.map["draft_$id"]!!))
        assertEquals("one", drafts.read(1))
        assertEquals("two", drafts.read(2))
        assertEquals("three", drafts.read(3))
    }

    @Test
    fun aPinResetOnlyDropsWhatTheLockSealed() {
        val slots = Slots()
        val pin = Pin()
        val drafts = Drafts(slots, pin) { key }
        drafts.save(1, "kept")
        pin.enabled = true
        drafts.save(2, "gone")
        pin.open = false
        drafts.dropPinSealed()
        assertEquals(setOf(1L), slots.chats)
        assertNull(slots.map["draft_2"])
        pin.enabled = false
        assertEquals("kept", drafts.read(1))
    }

    @Test
    fun turningTheLockOnLeavesADraftThatCantBeOpenedAlone() {
        val slots = Slots()
        Drafts(slots, Pin()) { key }.save(5, "sealed earlier")
        val sealed = slots.map["draft_5"]!!
        val pin = Pin(enabled = true)
        val drafts = Drafts(slots, pin) { null }
        drafts.sealForPin()
        assertEquals(sealed, slots.map["draft_5"])
        // And it still reads once the key is there, lock on or not.
        assertEquals("sealed earlier", Drafts(slots, pin) { key }.read(5))
    }
}
