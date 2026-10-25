package com.vknxworks.textctrl.notify

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vknxworks.textctrl.MainActivity
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.ui.chat.ChatScreen
import com.vknxworks.textctrl.ui.chat.NewChatScreen
import com.vknxworks.textctrl.ui.components.ScreenPrivacy
import com.vknxworks.textctrl.ui.components.WindowPrivacy
import com.vknxworks.textctrl.ui.lock.AppLock
import com.vknxworks.textctrl.ui.lock.AppLockGate
import com.vknxworks.textctrl.ui.nav.ChatRoute
import com.vknxworks.textctrl.ui.nav.HiddenRoute
import com.vknxworks.textctrl.ui.nav.LocalNavigator
import com.vknxworks.textctrl.ui.nav.Navigator
import com.vknxworks.textctrl.ui.nav.NewChatRoute
import com.vknxworks.textctrl.ui.settings.HiddenChatsScreen
import com.vknxworks.textctrl.ui.theme.TextCtrlTheme

/**
 * One chat in an Android bubble, floating over other apps. Not exported: only TextCtrl's own
 * notifications open it. The app lock, "Hide in screenshots" and "Hide in recent apps" apply here
 * exactly as in the main window; a locked TextCtrl shows its lock screen inside the bubble.
 */
class BubbleActivity : ComponentActivity() {
    private var openThread = -1L

    override fun onDestroy() {
        if (openThread >= 0) container.notifier.bubbleClosed(openThread)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        val threadId = intent.getLongExtra(MainActivity.EXTRA_THREAD_ID, -1L)
        // Hidden chats never float: their notifications don't name anyone, so they get no bubble either.
        if (threadId < 0 || c.chats.isHidden(threadId)) {
            finish()
            return
        }
        c.notifier.bubbleOpened(threadId)
        openThread = threadId
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            val covered = (c.vault.enabled && !AppLock.unlocked) || AppLock.request != null
            val secure = settings.hideScreenshots || covered || ScreenPrivacy.forced > 0
            WindowPrivacy(this, secure, hideRecents = settings.hideRecents || c.vault.enabled)
            TextCtrlTheme(dark) {
                AppLockGate {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val navController = rememberNavController()
                        val navigator = remember(navController) { Navigator(navController) }
                        CompositionLocalProvider(LocalNavigator provides navigator) {
                            Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                                NavHost(navController, startDestination = ChatRoute(threadId)) {
                                    composable<ChatRoute> {
                                        val r = it.toRoute<ChatRoute>()
                                        // The first chat's back arrow closes the bubble; chats opened from it go back.
                                        val first = navController.previousBackStackEntry == null
                                        ChatScreen(r.threadId, r.highlight, onBack = if (first) ({ finish() }) else null)
                                    }
                                    composable<NewChatRoute> { NewChatScreen(it.toRoute<NewChatRoute>().forward) }
                                    composable<HiddenRoute> { HiddenChatsScreen() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
