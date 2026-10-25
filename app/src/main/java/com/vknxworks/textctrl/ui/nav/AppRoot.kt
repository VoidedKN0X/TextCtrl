package com.vknxworks.textctrl.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vknxworks.textctrl.PendingAction
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.ui.chat.ChatScreen
import com.vknxworks.textctrl.ui.chat.NewChatScreen
import com.vknxworks.textctrl.ui.chats.ArchivedScreen
import com.vknxworks.textctrl.ui.chats.ChatsScreen
import com.vknxworks.textctrl.ui.components.CtrlBottomBar
import com.vknxworks.textctrl.ui.components.CtrlNavItem
import com.vknxworks.textctrl.ui.components.LocalBottomClearance
import com.vknxworks.textctrl.ui.theme.CtrlColors
import com.vknxworks.textctrl.ui.search.SearchScreen
import com.vknxworks.textctrl.ui.settings.BackupScreen
import com.vknxworks.textctrl.ui.settings.BlockedScreen
import com.vknxworks.textctrl.ui.settings.HiddenChatsScreen
import com.vknxworks.textctrl.ui.settings.ScheduledScreen
import com.vknxworks.textctrl.ui.settings.SettingsScreen
import com.vknxworks.textctrl.ui.settings.YouScreen
import com.vknxworks.textctrl.ui.setup.SetupScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The chat list and the open chat sit side by side (wide tablets and unfolded foldables). */
val LocalTwoPane = compositionLocalOf { false }

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!c.isDefaultSmsApp || !c.setupDone) SetupScreen() else MainScaffold()
    }
}

private data class TopDest(val route: Any, val item: CtrlNavItem)

private val topDestinations = listOf(
    TopDest(ChatsRoute, CtrlNavItem(R.string.tab_chats, Icons.Outlined.ChatBubbleOutline)),
    TopDest(ArchivedRoute, CtrlNavItem(R.string.tab_archived, Icons.Outlined.Archive)),
    TopDest(SearchRoute, CtrlNavItem(R.string.tab_search, Icons.Outlined.Search)),
)

@Composable
private fun MainScaffold() {
    val c = LocalContext.current.container
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = topDestinations.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }

    fun selectTab(i: Int) {
        navController.navigate(topDestinations[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Notifications, "text this number", shares from other apps and the launcher shortcut.
    val pending by c.pendingAction.collectAsStateWithLifecycle()
    LaunchedEffect(pending, backStack != null) {
        val action = pending ?: return@LaunchedEffect
        if (backStack == null) return@LaunchedEffect
        c.pendingAction.value = null
        when (action) {
            is PendingAction.OpenChat -> navigator.openChatFresh(action.threadId)
            PendingAction.NewChat -> navigator.navigateNow(NewChatRoute())
            is PendingAction.Compose -> {
                c.composeDraft.value = action
                if (action.recipients.isEmpty()) {
                    navigator.navigateNow(NewChatRoute(forward = true))
                } else {
                    val thread = runCatching { withContext(Dispatchers.IO) { c.store.threadIdFor(action.recipients) } }.getOrNull()
                    if (thread != null) navigator.openChatFresh(thread)
                }
            }
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val twoPane = maxWidth >= 840.dp
            if (maxWidth >= 600.dp) {
                // Tablets and unfolded foldables: a side rail.
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        topDestinations.forEachIndexed { i, top ->
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(top.item.icon, contentDescription = stringResource(top.item.label), modifier = Modifier.size(20.dp)) },
                                label = { Text(stringResource(top.item.label)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CtrlColors.OnAccent,
                                    selectedTextColor = CtrlColors.Text,
                                    indicatorColor = CtrlColors.Accent,
                                    unselectedIconColor = CtrlColors.Muted,
                                    unselectedTextColor = CtrlColors.Muted,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    CompositionLocalProvider(LocalTwoPane provides twoPane) {
                        Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding().imePadding()) { AppNavHost(navController) }
                    }
                }
            } else {
                PhoneLayout(navController, showTabs = selectedIndex >= 0, selectedIndex = selectedIndex, onSelect = ::selectTab)
            }
        }
    }
}

/**
 * Phones: content runs edge to edge and scrolls under the floating Ctrl bottom bar.
 * The bar shows on the main tabs; pushed screens get the full height.
 */
@Composable
private fun PhoneLayout(
    navController: NavHostController,
    showTabs: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp) {
        Box(Modifier.fillMaxSize()) {
            // Pushed screens stop above the navigation bar and the keyboard; the tabs scroll under the bar.
            Box(Modifier.fillMaxSize().then(if (showTabs) Modifier else Modifier.navigationBarsPadding().imePadding())) {
                AppNavHost(navController)
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = showTabs,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    CtrlBottomBar(items = topDestinations.map { it.item }, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = ChatsRoute) {
        composable<ChatsRoute> { ChatsScreen() }
        composable<ArchivedRoute> { ArchivedScreen() }
        composable<SearchRoute> { SearchScreen() }

        composable<ChatRoute> {
            val r = it.toRoute<ChatRoute>()
            ChatScreen(r.threadId, r.highlight)
        }
        composable<NewChatRoute> { NewChatScreen(it.toRoute<NewChatRoute>().forward) }

        composable<YouRoute> { YouScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<BlockedRoute> { BlockedScreen() }
        composable<HiddenRoute> { HiddenChatsScreen() }
        composable<ScheduledRoute> { ScheduledScreen() }
        composable<BackupRoute> { BackupScreen() }
    }
}
