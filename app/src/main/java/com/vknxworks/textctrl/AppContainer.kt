package com.vknxworks.textctrl

import android.app.role.RoleManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vknxworks.textctrl.data.backup.Backup
import com.vknxworks.textctrl.data.lock.LockVault
import com.vknxworks.textctrl.data.schedule.Cleaner
import com.vknxworks.textctrl.data.schedule.Scheduler
import com.vknxworks.textctrl.data.settings.ChatPrefs
import com.vknxworks.textctrl.data.settings.SettingsRepository
import com.vknxworks.textctrl.notify.Notifier
import com.vknxworks.textctrl.sms.Blocklist
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.sms.MessageStore
import com.vknxworks.textctrl.sms.Messenger
import com.vknxworks.textctrl.sms.Sims
import com.vknxworks.textctrl.ui.lock.AppLock
import com.vknxworks.textctrl.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Something another app, a notification or a shortcut asked the app to open. */
sealed interface PendingAction {
    data class OpenChat(val threadId: Long) : PendingAction
    data object NewChat : PendingAction
    /** Write to [recipients] (or pick them first, when empty), with some text and files ready. */
    data class Compose(val recipients: List<String>, val text: String, val attachments: List<Uri>) : PendingAction
}

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, appScope)

    /** The app lock's PIN, fingerprint key and the key that encrypts drafts while locked. */
    val vault = LockVault(context, "textctrl")
    val chats = ChatPrefs(context, vault)
    val store = MessageStore(context)
    val contacts = Contacts(context)
    val sims = Sims(context)
    val blocklist = Blocklist(context)
    val notifier = Notifier(context, store, contacts, settings, chats, vault)
    val messenger = Messenger(context, store, sims, settings, chats, notifier)
    val scheduler = Scheduler(context, appScope, messenger, notifier)
    val cleaner = Cleaner(context, store, settings, chats)
    val backup = Backup(context, store, sims)
    val widgets = Widgets(context, appScope, store, contacts, chats, settings, vault)

    /** The hidden chats' own PIN, separate from the app lock. */
    val hiddenVault = LockVault(context, "textctrl_hidden", "hidden_chats_vault")

    /** The hidden chats are showing (their PIN was entered). Closes when you leave the app. */
    var hiddenOpen by mutableStateOf(false)

    /** A chat to hide once the hidden chats' PIN is set up (chosen before there was one). */
    var pendingHide: Long? = null

    fun closeHidden() {
        hiddenOpen = false
        hiddenVault.close()
    }

    /** Set by MainActivity for notifications, shares and "text this number"; the main screen consumes it. */
    val pendingAction = MutableStateFlow<PendingAction?>(null)

    /** Text and files to put in the next chat that opens (a share, "text this number", Forward). */
    val composeDraft = MutableStateFlow<PendingAction.Compose?>(null)

    private val appPrefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    /** The first-start screens (SMS app, then permissions) are done. */
    var setupDone by mutableStateOf(appPrefs.getBoolean("setup_done", false))
        private set

    fun finishSetup() {
        appPrefs.edit().putBoolean("setup_done", true).apply()
        setupDone = true
    }

    /** TextCtrl is the phone's SMS app. Checked again whenever the app comes back on screen. */
    var isDefaultSmsApp by mutableStateOf(checkDefault())
        private set

    fun refreshDefault() {
        isDefaultSmsApp = checkDefault()
    }

    private fun checkDefault(): Boolean = runCatching {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
    }.getOrDefault(false)

    /** Turns the app lock on: the PIN encrypts drafts from now on, and conversation shortcuts go. */
    suspend fun enableLock(pin: String) {
        // Unlocked first, so the lock screen doesn't flash up while the PIN is being saved.
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) { vault.setUp(pin) }
        chats.sealDrafts()
        notifier.removeAllShortcuts()
        runCatching { widgets.update() }
    }

    /** Turns the app lock off (after the PIN was confirmed): drafts are stored plainly again. */
    suspend fun disableLock() {
        withContext(Dispatchers.IO) { chats.unsealDrafts() }
        vault.disable()
        runCatching { widgets.update() }
    }

    /**
     * "Forgot PIN?", after the phone's own screen lock confirmed it's you: turns the app lock off.
     * Drafts encrypted with the forgotten PIN can't be read any more and are removed.
     */
    suspend fun resetLock() {
        withContext(Dispatchers.IO) { chats.dropSealedDrafts() }
        vault.disable()
        AppLock.markUnlocked()
        runCatching { widgets.update() }
    }

    /** Locked: wipe the key from memory. */
    fun onLocked() {
        vault.close()
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as TextCtrlApp).container
