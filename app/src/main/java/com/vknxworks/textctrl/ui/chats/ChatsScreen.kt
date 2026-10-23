package com.vknxworks.textctrl.ui.chats

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.textctrl.AppContainer
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.sms.Conversation
import com.vknxworks.textctrl.ui.chat.ChatScreen
import com.vknxworks.textctrl.ui.components.ActionSheet
import com.vknxworks.textctrl.ui.components.Avatar
import com.vknxworks.textctrl.ui.components.ConfirmDialog
import com.vknxworks.textctrl.ui.components.LoadingView
import com.vknxworks.textctrl.ui.components.LocalBottomClearance
import com.vknxworks.textctrl.ui.components.MessageView
import com.vknxworks.textctrl.ui.components.SheetAction
import com.vknxworks.textctrl.ui.components.TabTitle
import com.vknxworks.textctrl.ui.components.runTask
import com.vknxworks.textctrl.ui.components.shortTime
import com.vknxworks.textctrl.ui.nav.HiddenRoute
import com.vknxworks.textctrl.ui.nav.LocalNavigator
import com.vknxworks.textctrl.ui.nav.LocalTwoPane
import com.vknxworks.textctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A chat with its people's names and picture looked up. */
data class ChatItem(
    val conversation: Conversation,
    val title: String,
    val photo: Uri?,
    /** One person, not in your contacts, and you never wrote to them. */
    val unknown: Boolean,
)

/** All chats, refreshed whenever a message or contact changes. Null while loading. */
@Composable
fun rememberChats(): State<List<ChatItem>?> {
    val c = LocalContext.current.container
    val changes by c.store.changes.collectAsStateWithLifecycle()
    val contactChanges by c.contacts.changes.collectAsStateWithLifecycle()
    return produceState<List<ChatItem>?>(initialValue = null, changes, contactChanges) {
        // Changes come in bursts (a message and its parts); wait for the last one.
        if (value != null) delay(150)
        value = runCatching { loadChats(c) }.getOrElse { value ?: emptyList() }
    }
}

private suspend fun loadChats(c: AppContainer): List<ChatItem> = withContext(Dispatchers.IO) {
    val canTell = c.contacts.granted
    c.store.conversations().map { conv ->
        val people = conv.addresses.map { c.contacts.lookup(it) }
        ChatItem(
            conversation = conv,
            title = conv.addresses.mapIndexed { i, a -> people[i].name ?: Contacts.formatNumber(a) }.joinToString(", ").ifBlank { "…" },
            photo = if (conv.addresses.size == 1) people.firstOrNull()?.photo else null,
            unknown = canTell && conv.addresses.size == 1 && people.firstOrNull()?.name == null && !conv.hasOutgoing,
        )
    }
}

private enum class Filter { ALL, UNREAD, UNKNOWN }

/** The Chats tab: every chat that isn't archived or hidden, pinned ones first, with filters. */
@Composable
fun ChatsScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val twoPane = LocalTwoPane.current
    val chats by rememberChats()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val archived by c.chats.archived.collectAsStateWithLifecycle()
    val hidden by c.chats.hidden.collectAsStateWithLifecycle()
    val pinned by c.chats.pinned.collectAsStateWithLifecycle()
    var chosenFilter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var openThread by rememberSaveable { mutableStateOf<Long?>(null) }

    val visible = remember(chats, archived, hidden) { chats?.filter { it.conversation.threadId !in archived && it.conversation.threadId !in hidden } }
    val unknownCount = visible?.count { it.unknown } ?: 0
    val unreadCount = visible?.count { it.conversation.unread > 0 } ?: 0
    // "Unknown senders" only exists while that setting is on.
    val filter = if (!settings.separateUnknown && chosenFilter == Filter.UNKNOWN) Filter.ALL else chosenFilter
    val list = remember(visible, pinned, filter, settings.separateUnknown) {
        visible?.filter {
            when (filter) {
                Filter.ALL -> !(settings.separateUnknown && it.unknown)
                Filter.UNREAD -> it.conversation.unread > 0
                Filter.UNKNOWN -> it.unknown
            }
        }?.sortedWith(compareByDescending<ChatItem> { it.conversation.threadId in pinned }.thenByDescending { it.conversation.date })
    }
    val listState = rememberLazyListState()
    val expandedFab by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val selection = remember { mutableStateListOf<Long>() }

    val listPane: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                if (selection.isEmpty()) {
                    TabTitle(stringResource(R.string.tab_chats)) {
                        if (unreadCount > 0) {
                            IconButton(onClick = { c.runTask { c.store.markAllRead(); c.notifier.cancelAll() } }) {
                                Icon(Icons.Outlined.DoneAll, stringResource(R.string.mark_all_read))
                            }
                        }
                    }
                } else {
                    SelectionTitle(selection.size) { selection.clear() }
                }
                if (visible != null && visible.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(filter == Filter.ALL, onClick = { chosenFilter = Filter.ALL }, label = { Text(stringResource(R.string.filter_all)) })
                        FilterChip(
                            filter == Filter.UNREAD,
                            onClick = { chosenFilter = if (filter == Filter.UNREAD) Filter.ALL else Filter.UNREAD },
                            label = { Text(if (unreadCount > 0) stringResource(R.string.filter_unread_n, unreadCount) else stringResource(R.string.filter_unread)) },
                        )
                        if (settings.separateUnknown) {
                            FilterChip(
                                filter == Filter.UNKNOWN,
                                onClick = { chosenFilter = if (filter == Filter.UNKNOWN) Filter.ALL else Filter.UNKNOWN },
                                label = { Text(if (unknownCount > 0) stringResource(R.string.filter_unknown_n, unknownCount) else stringResource(R.string.filter_unknown)) },
                                leadingIcon = { Icon(Icons.Outlined.PersonOff, null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
                when {
                    list == null -> LoadingView()
                    list.isEmpty() -> MessageView(
                        Icons.AutoMirrored.Outlined.Chat,
                        stringResource(
                            when (filter) {
                                Filter.ALL -> R.string.chats_empty
                                Filter.UNREAD -> R.string.unread_empty
                                Filter.UNKNOWN -> R.string.unknown_empty
                            },
                        ),
                        stringResource(
                            when (filter) {
                                Filter.ALL -> R.string.chats_empty_body
                                Filter.UNREAD -> R.string.unread_empty_body
                                Filter.UNKNOWN -> R.string.unknown_empty_body
                            },
                        ),
                        modifier = Modifier.padding(bottom = LocalBottomClearance.current),
                    )
                    else -> ChatList(
                        list,
                        archivedTab = false,
                        state = listState,
                        selection = selection,
                        openThread = if (twoPane) openThread else null,
                        onOpen = { id -> if (twoPane) openThread = id else nav.chat(id) },
                    )
                }
            }
            if (selection.isEmpty()) {
                val clearance = LocalBottomClearance.current
                ExtendedFloatingActionButton(
                    onClick = { nav.newChat() },
                    expanded = expandedFab,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(stringResource(R.string.new_chat)) },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = if (clearance > 0.dp) clearance + 12.dp else 16.dp),
                )
            }
        }
    }

    if (twoPane) {
        // Wide screens: the list and the open chat side by side.
        Row(Modifier.fillMaxSize()) {
            listPane(Modifier.width(380.dp).fillMaxHeight())
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight().statusBarsPadding()) {
                val id = openThread
                if (id == null) {
                    MessageView(Icons.Outlined.TouchApp, stringResource(R.string.pick_chat), stringResource(R.string.pick_chat_body))
                } else {
                    key(id) { ChatScreen(id, null, onBack = { openThread = null }) }
                }
            }
        }
    } else {
        listPane(Modifier.fillMaxSize())
    }
}

/** The Archived tab: chats put away. A new message brings a chat back. */
@Composable
fun ArchivedScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val chats by rememberChats()
    val archived by c.chats.archived.collectAsStateWithLifecycle()
    val hidden by c.chats.hidden.collectAsStateWithLifecycle()
    val list = remember(chats, archived, hidden) { chats?.filter { it.conversation.threadId in archived && it.conversation.threadId !in hidden } }
    val selection = remember { mutableStateListOf<Long>() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (selection.isEmpty()) TabTitle(stringResource(R.string.tab_archived)) else SelectionTitle(selection.size) { selection.clear() }
        when {
            list == null -> LoadingView()
            list.isEmpty() -> MessageView(
                Icons.Outlined.Archive,
                stringResource(R.string.archived_empty),
                stringResource(R.string.archived_empty_body),
                modifier = Modifier.padding(bottom = LocalBottomClearance.current),
            )
            else -> ChatList(list, archivedTab = true, state = rememberLazyListState(), selection = selection, openThread = null, onOpen = { nav.chat(it) })
        }
    }
}

/** Hidden chats, once their PIN was entered. */
@Composable
fun HiddenChatList() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val chats by rememberChats()
    val hidden by c.chats.hidden.collectAsStateWithLifecycle()
    val list = remember(chats, hidden) { chats?.filter { it.conversation.threadId in hidden } }
    val selection = remember { mutableStateListOf<Long>() }
    Column(Modifier.fillMaxSize()) {
        if (selection.isNotEmpty()) SelectionTitle(selection.size) { selection.clear() }
        when {
            list == null -> LoadingView()
            list.isEmpty() -> MessageView(Icons.Outlined.VisibilityOff, stringResource(R.string.hidden_empty), stringResource(R.string.hidden_empty_body))
            else -> ChatList(list, archivedTab = false, state = rememberLazyListState(), selection = selection, openThread = null, onOpen = { nav.chat(it) }, hiddenList = true)
        }
    }
}

@Composable
private fun SelectionTitle(count: Int, onClose: () -> Unit) {
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) } },
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count)) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

/**
 * The chat rows. Tap opens a chat; long-press starts selecting, and the bar at the bottom of the list
 * acts on everything selected.
 */
@Composable
private fun ChatList(
    list: List<ChatItem>,
    archivedTab: Boolean,
    state: LazyListState,
    selection: MutableList<Long>,
    openThread: Long?,
    onOpen: (Long) -> Unit,
    hiddenList: Boolean = false,
) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val pinned by c.chats.pinned.collectAsStateWithLifecycle()
    val muted by c.chats.muted.collectAsStateWithLifecycle()
    val drafts by c.chats.drafts.collectAsStateWithLifecycle()
    val scheduled by c.scheduler.items.collectAsStateWithLifecycle()
    val scheduledThreads = remember(scheduled) { scheduled.map { it.threadId }.toSet() }
    var more by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<List<ChatItem>?>(null) }
    var blocking by remember { mutableStateOf<ChatItem?>(null) }
    // Rows that disappeared (deleted, archived elsewhere) leave the selection.
    val ids = remember(list) { list.map { it.conversation.threadId }.toSet() }
    androidx.compose.runtime.LaunchedEffect(ids) { selection.retainAll { it in ids } }
    BackHandler(enabled = selection.isNotEmpty()) { selection.clear() }
    val chosen = list.filter { it.conversation.threadId in selection }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = state,
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp + LocalBottomClearance.current),
        ) {
            items(list, key = { it.conversation.threadId }) { item ->
                val id = item.conversation.threadId
                ChatRow(
                    item = item,
                    pinned = id in pinned,
                    muted = id in muted,
                    draft = id in drafts,
                    scheduled = id in scheduledThreads,
                    selected = id in selection,
                    open = id == openThread,
                    modifier = Modifier.animateItem(),
                    onClick = {
                        if (selection.isNotEmpty()) {
                            if (id in selection) selection.remove(id) else selection.add(id)
                        } else {
                            onOpen(id)
                        }
                    },
                    onLongClick = { if (id in selection) selection.remove(id) else selection.add(id) },
                )
            }
        }
        if (selection.isNotEmpty()) {
            val allPinned = chosen.all { it.conversation.threadId in pinned }
            val anyUnread = chosen.any { it.conversation.unread > 0 }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(bottom = LocalBottomClearance.current)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (anyUnread) {
                    BarAction(Icons.Outlined.MarkChatRead, stringResource(R.string.mark_read)) {
                        val sel = chosen.map { it.conversation.threadId }
                        selection.clear()
                        c.runTask { sel.forEach { c.store.markRead(it); c.notifier.cancel(it) } }
                    }
                } else {
                    BarAction(Icons.Outlined.MarkChatUnread, stringResource(R.string.mark_unread)) {
                        val sel = chosen.map { it.conversation.threadId }
                        selection.clear()
                        c.runTask { sel.forEach { c.store.markUnread(it) } }
                    }
                }
                if (!archivedTab && !hiddenList) {
                    BarAction(Icons.Outlined.PushPin, stringResource(if (allPinned) R.string.unpin else R.string.pin)) {
                        chosen.forEach { c.chats.setPinned(it.conversation.threadId, !allPinned) }
                        selection.clear()
                    }
                }
                if (hiddenList) {
                    BarAction(Icons.Outlined.Visibility, stringResource(R.string.unhide)) {
                        chosen.forEach { c.chats.setHidden(it.conversation.threadId, false) }
                        selection.clear()
                    }
                } else {
                    BarAction(if (archivedTab) Icons.Outlined.Unarchive else Icons.Outlined.Archive, stringResource(if (archivedTab) R.string.unarchive else R.string.archive)) {
                        chosen.forEach { c.chats.setArchived(it.conversation.threadId, !archivedTab) }
                        selection.clear()
                    }
                }
                BarAction(Icons.Outlined.Delete, stringResource(R.string.delete)) { deleting = chosen }
                BarAction(Icons.Outlined.MoreVert, stringResource(R.string.more)) { more = true }
            }
        }
    }

    if (more && chosen.isNotEmpty()) {
        val allMuted = chosen.all { it.conversation.threadId in muted }
        val actions = buildList {
            add(
                if (allMuted) SheetAction(stringResource(R.string.unmute), Icons.Outlined.Notifications) {
                    chosen.forEach { c.chats.setMuted(it.conversation.threadId, false) }
                    selection.clear()
                } else SheetAction(stringResource(R.string.mute), Icons.Outlined.NotificationsOff) {
                    chosen.forEach { c.chats.setMuted(it.conversation.threadId, true); c.notifier.cancel(it.conversation.threadId) }
                    selection.clear()
                },
            )
            if (!hiddenList) {
                add(SheetAction(stringResource(R.string.hide), Icons.Outlined.VisibilityOff) {
                    val sel = chosen.map { it.conversation.threadId }
                    selection.clear()
                    if (c.hiddenVault.enabled) {
                        sel.forEach { c.chats.setHidden(it, true); c.notifier.removeShortcut(it) }
                        android.widget.Toast.makeText(context, context.getString(R.string.hidden_done), android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        c.pendingHide = sel.first()
                        nav.navigate(HiddenRoute)
                    }
                })
            }
            val single = chosen.singleOrNull()
            if (single != null && !single.conversation.isGroup && single.conversation.addresses.isNotEmpty() && c.blocklist.canBlock) {
                add(SheetAction(stringResource(R.string.block), Icons.Outlined.Block, destructive = true) { blocking = single })
            }
        }
        ActionSheet(pluralStringResource(R.plurals.selected_count, chosen.size, chosen.size), null, actions, onDismiss = { more = false })
    }

    deleting?.let { items ->
        ConfirmDialog(
            if (items.size == 1) stringResource(R.string.delete_chat_title, items[0].title)
            else pluralStringResource(R.plurals.delete_chats_title, items.size, items.size),
            stringResource(R.string.delete_chat_body),
            stringResource(R.string.delete),
            onDismiss = { deleting = null },
        ) {
            deleting = null
            selection.clear()
            val ids = items.map { it.conversation.threadId }
            c.runTask {
                for (id in ids) {
                    c.scheduler.items.value.filter { it.threadId == id }.forEach { c.scheduler.cancel(it.id) }
                    c.store.deleteThread(id)
                    c.chats.forget(id)
                    c.notifier.cancel(id)
                    c.notifier.removeShortcut(id)
                    c.notifier.deleteChatChannel(id)
                }
            }
        }
    }

    blocking?.let { item ->
        val number = item.conversation.addresses.first()
        ConfirmDialog(
            stringResource(R.string.block_title, item.title),
            stringResource(R.string.block_body),
            stringResource(R.string.block),
            onDismiss = { blocking = null },
        ) {
            blocking = null
            selection.clear()
            c.runTask {
                c.blocklist.block(number)
                c.chats.setArchived(item.conversation.threadId, true)
            }
        }
    }
}

@Composable
private fun BarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    item: ChatItem,
    pinned: Boolean,
    muted: Boolean,
    draft: Boolean,
    scheduled: Boolean,
    selected: Boolean,
    open: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val conv = item.conversation
    val unread = conv.unread > 0
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .background(
                when {
                    selected -> cs.primary.copy(alpha = 0.14f)
                    open -> cs.surfaceContainerHigh
                    else -> Color.Transparent
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(item.title, conv.addresses.sorted().joinToString(), item.photo, group = conv.isGroup)
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    null,
                    Modifier.align(Alignment.BottomEnd).size(20.dp).background(cs.background, CircleShape),
                    tint = cs.primary,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.PushPin, stringResource(R.string.pinned), Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                }
                if (muted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.NotificationsOff, stringResource(R.string.muted), Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                }
                if (scheduled) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Schedule, stringResource(R.string.has_scheduled), Modifier.size(14.dp), tint = cs.primary)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    shortTime(context, conv.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread) cs.primary else cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conv.hasError) {
                    Icon(Icons.Filled.ErrorOutline, stringResource(R.string.not_sent), Modifier.size(16.dp), tint = cs.error)
                    Spacer(Modifier.width(4.dp))
                }
                val snippet = when {
                    draft -> stringResource(R.string.draft_prefix)
                    conv.snippet.isNotBlank() -> conv.snippet.replace('\n', ' ')
                    conv.hasAttachment -> stringResource(R.string.attachment_picture_message)
                    else -> ""
                }
                Text(
                    snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        draft -> cs.error
                        unread -> cs.onSurface
                        else -> cs.onSurfaceVariant
                    },
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.widthIn(min = 22.dp).height(22.dp).background(cs.primary, CircleShape).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (conv.unread > 99) "99+" else conv.unread.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

/** "3 people" style subtitle for a group, used by the chat screen too. */
@Composable
fun peopleCount(n: Int): String = pluralStringResource(R.plurals.people, n, n)
