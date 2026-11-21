package com.vknxworks.textctrl.data.settings

import android.content.Context
import com.vknxworks.textctrl.data.crypto.KeystoreCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Your signature and your quick-reply templates. */
@Serializable
data class Snippets(
    /** Added on a new line under every text you send from a chat; blank for none. */
    val signature: String = "",
    /** Ready-made texts to put in the text field with one tap. */
    val templates: List<String> = emptyList(),
) {
    /** The same, cut down to what TextCtrl accepts (also for a file that was edited by hand). */
    fun cleaned(): Snippets = Snippets(
        signature = signature.trim().take(MAX_SIGNATURE),
        templates = templates.map { it.trim().take(MAX_TEMPLATE) }.filter { it.isNotEmpty() }.distinct().take(MAX_TEMPLATES),
    )

    companion object {
        const val MAX_SIGNATURE = 160
        const val MAX_TEMPLATE = 1000
        const val MAX_TEMPLATES = 50

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(value: Snippets): String = json.encodeToString(serializer(), value)

        fun decode(text: String): Snippets? = runCatching { json.decodeFromString(serializer(), text).cleaned() }.getOrNull()

        /** [text] with [signature] under it, the way it is sent. Nothing is added to an empty text or twice. */
        fun sign(text: String, signature: String): String {
            val sig = signature.trim()
            if (sig.isEmpty() || text.isBlank() || text.trimEnd().endsWith("\n" + sig)) return text
            return text.trimEnd() + "\n" + sig
        }

        /** The opposite of [sign], for a message that goes back into the text field to be changed. */
        fun unsign(text: String, signature: String): String {
            val sig = signature.trim()
            if (sig.isEmpty() || !text.endsWith("\n" + sig)) return text
            return text.removeSuffix("\n" + sig)
        }
    }
}

/**
 * Keeps [Snippets] on the phone, encrypted with the same key in the phone's secure hardware as scheduled
 * messages ([KeystoreCrypto]). Reading asks the secure hardware, which is slow, so it happens on a thread of
 * its own from the moment the app starts; until then [state] is null.
 *
 * If what is stored can't be opened (the key is gone, say after moving to another phone), it is left exactly
 * as it is: [unreadable] is true and nothing can be saved over it.
 */
class SnippetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("snippets", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<Snippets?>(null)
    val state: StateFlow<Snippets?> = _state.asStateFlow()

    private val _unreadable = MutableStateFlow(false)
    val unreadable: StateFlow<Boolean> = _unreadable.asStateFlow()

    init {
        Thread({ load() }, "snippets").start()
    }

    private fun load() {
        val raw = runCatching { prefs.getString(KEY, null) }.getOrNull()
        if (raw == null) {
            _state.value = Snippets()
            return
        }
        val opened = runCatching { KeystoreCrypto.decrypt(raw) }.getOrNull()?.let { Snippets.decode(it) }
        if (opened == null) _unreadable.value = true
        _state.value = opened ?: Snippets()
    }

    /** The signature, or "" while it isn't read yet. */
    val signature: String get() = _state.value?.signature.orEmpty()

    /**
     * Saves [next]. Slow (the secure hardware): never on the main thread. False when it couldn't be saved:
     * not read yet, stored data that can't be opened, or the secure hardware refused.
     */
    @Synchronized
    fun save(next: Snippets): Boolean {
        if (_state.value == null || _unreadable.value) return false
        val clean = next.cleaned()
        val sealed = runCatching { KeystoreCrypto.encrypt(Snippets.encode(clean)) }.getOrNull() ?: return false
        if (!prefs.edit().putString(KEY, sealed).commit()) return false
        _state.value = clean
        return true
    }

    /**
     * Throws away stored data that can't be opened, so a new signature and new templates can be made. Only
     * ever called after you said so on the screen that explains it. Does nothing when the data can be read.
     */
    @Synchronized
    fun startOver() {
        if (!_unreadable.value) return
        if (!prefs.edit().remove(KEY).commit()) return
        _state.value = Snippets()
        _unreadable.value = false
    }

    private companion object {
        const val KEY = "data"
    }
}
