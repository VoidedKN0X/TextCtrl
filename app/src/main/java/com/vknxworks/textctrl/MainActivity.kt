package com.vknxworks.textctrl

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.textctrl.data.settings.ThemeChoice
import com.vknxworks.textctrl.sms.recipientsOf
import com.vknxworks.textctrl.ui.components.ScreenPrivacy
import com.vknxworks.textctrl.ui.components.WindowPrivacy
import com.vknxworks.textctrl.ui.lock.AppLock
import com.vknxworks.textctrl.ui.lock.AppLockGate
import com.vknxworks.textctrl.ui.nav.AppRoot
import com.vknxworks.textctrl.ui.theme.TextCtrlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK, ThemeChoice.BLACK -> true
            }
            // Keep status and navigation bar icons readable when the app theme differs from the system's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            // "Hide in screenshots" (off by default); always on for the lock and PIN screens.
            val covered = (c.vault.enabled && !AppLock.unlocked) || AppLock.request != null
            val secure = settings.hideScreenshots || covered || ScreenPrivacy.forced > 0
            // "Hide in recent apps" (off by default). With the app lock on, Recents always shows a blank
            // card: a preview there would show your texts without the PIN.
            WindowPrivacy(this, secure, hideRecents = settings.hideRecents || c.vault.enabled)
            TextCtrlTheme(settings.theme, settings.dynamicColor) {
                AppLockGate { AppRoot() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        container.refreshDefault()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Turns a notification tap, "text this number", a share or a shortcut into something to open. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val action = when (intent.action) {
            ACTION_OPEN_CHAT -> intent.getLongExtra(EXTRA_THREAD_ID, -1L).takeIf { it >= 0 }?.let { PendingAction.OpenChat(it) }
            ACTION_NEW_CHAT -> PendingAction.NewChat
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                val uri = intent.data?.takeIf { it.scheme in SMS_SCHEMES }
                if (uri == null) {
                    null
                } else {
                    val body = intent.getStringExtra("sms_body")
                        ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                        ?: runCatching { Uri.parse("x://y?" + uri.schemeSpecificPart.substringAfter('?', "")).getQueryParameter("body") }.getOrNull()
                    PendingAction.Compose(recipientsOf(uri), body.orEmpty(), emptyList())
                }
            }
            Intent.ACTION_SEND -> {
                val text = listOfNotNull(
                    intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() && intent.type == "text/plain" },
                    intent.getStringExtra(Intent.EXTRA_TEXT),
                ).joinToString("\n")
                val image = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.takeIf { intent.type?.startsWith("image/") == true && isShareable(it) }
                PendingAction.Compose(emptyList(), text, listOfNotNull(image))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val images = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                PendingAction.Compose(emptyList(), intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(), images.filterNotNull().filter(::isShareable).take(5))
            }
            else -> null
        }
        if (action != null) container.pendingAction.value = action
    }

    /**
     * Only content another app actually shares: never a file path, and never a link back to TextCtrl's
     * own files (another app could otherwise make TextCtrl send its own private files).
     */
    private fun isShareable(uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        // "content://0@<authority>" still reaches <authority> (the number is a user id), so look past it.
        val authority = uri.authority?.substringAfterLast('@')?.takeIf { it.isNotBlank() } ?: return false
        return !authority.startsWith(packageName)
    }

    companion object {
        const val ACTION_OPEN_CHAT = "com.vknxworks.textctrl.OPEN_CHAT"
        const val ACTION_NEW_CHAT = "com.vknxworks.textctrl.NEW_CHAT"
        const val EXTRA_THREAD_ID = "thread_id"
        private val SMS_SCHEMES = setOf("sms", "smsto", "mms", "mmsto")
    }
}
