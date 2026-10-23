package com.vknxworks.textctrl.ui.chats

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.vknxworks.textctrl.ui.nav.LocalNavigator
import com.vknxworks.textctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A chat with its people's names and picture looked up. */
data class ChatItem(
    val conversation: Conversation,
    val title: String,
    val photo: Uri?,
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
    c.store.conversations().map { conv ->
        val people = conv.addresses.map { c.contacts.lookup(it) }
        ChatItem(
            conversation = conv,
            title = conv.addresses.mapIndexed { i, a -> people[i].name ?: Contacts.formatNumber(a) }.joinToString(", ").ifBlank { "…" },
            photo = if (conv.addresses.size == 1) people.firstOrNull()?.photo else null,
        )
    }
}

/** The Chats tab: every chat that isn't archived, pinned ones first, and a button to start one. */
@Composable
fun ChatsScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val chats by rememberChats()
    val archived by c.chats.archived.collectAsStateWithLifecycle()
    val pinned by c.chats.pinned.collectAsStateWithLifecycle()
    val list = remember(chats, archived, pinned) {
        chats?.filter { it.conversation.threadId !in archived }
            ?.sortedWith(compareByDescending<ChatItem> { it.conversation.threadId in pinned }.thenByDescending { it.conversation.date })
    }
    val listState = rememberLazyListState()
    val expandedFab by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val anyUnread = list?.any { it.conversation.unread > 0 } == true

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_chats)) {
                if (anyUnread) {
                    IconButton(onClick = { c.runTask { c.store.markAllRead(); c.notifier.cancelAll() } }) {
                        Icon(Icons.Outlined.DoneAll, stringResource(R.string.mark_all_read))
                    }
                }
            }
            when {
                list == null -> LoadingView()
                list.isEmpty() -> MessageView(
                    Icons.AutoMirrored.Outlined.Chat,
                    stringResource(R.string.chats_empty),
                    stringResource(R.string.chats_empty_body),
                    modifier = Modifier.padding(bottom = LocalBottomClearance.current),
                )
                else -> ChatList(list, archivedTab = false, state = listState)
            }
        }
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

/** The Archived tab: chats put away. A new message brings a chat back. */
@Composable
fun ArchivedScreen() {
    val c = LocalContext.current.container
    val chats by rememberChats()
    val archived by c.chats.archived.collectAsStateWithLifecycle()
    val list = remember(chats, archived) { chats?.filter { it.conversation.threadId in archived } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_archived))
        when {
            list == null -> LoadingView()
            list.isEmpty() -> MessageView(
                Icons.Outlined.Archive,
                stringResource(R.string.archived_empty),
                stringResource(R.string.archived_empty_body),
                modifier = Modifier.padding(bottom = LocalBottomClearance.current),
            )
            else -> ChatList(list, archivedTab = true, state = rememberLazyListState())
        }
    }
}

@Composable
private fun ChatList(list: List<ChatItem>, archivedTab: Boolean, state: androidx.compose.foundation.lazy.LazyListState) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val pinned by c.chats.pinned.collectAsStateWithLifecycle()
    val muted by c.chats.muted.collectAsStateWithLifecycle()
    val drafts by c.chats.drafts.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<ChatItem?>(null) }
    var deleting by remember { mutableStateOf<ChatItem?>(null) }
    var blocking by remember { mutableStateOf<ChatItem?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
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
                modifier = Modifier.animateItem(),
                onClick = { nav.chat(id) },
                onLongClick = { menuFor = item },
            )
        }
    }

    menuFor?.let { item ->
        val conv = item.conversation
        val id = conv.threadId
        val actions = buildList {
            if (!archivedTab) {
                add(
                    if (id in pinned) SheetAction(stringResource(R.string.unpin), Icons.Outlined.PushPin) { c.chats.setPinned(id, false) }
                    else SheetAction(stringResource(R.string.pin), Icons.Outlined.PushPin) { c.chats.setPinned(id, true) },
                )
            }
            add(
                if (conv.unread > 0) SheetAction(stringResource(R.string.mark_read), Icons.Outlined.MarkChatRead) {
                    c.runTask { c.store.markRead(id); c.notifier.cancel(id) }
                } else SheetAction(stringResource(R.string.mark_unread), Icons.Outlined.MarkChatUnread) {
                    c.runTask { c.store.markUnread(id) }
                },
            )
            add(
                if (id in muted) SheetAction(stringResource(R.string.unmute), Icons.Outlined.Notifications) { c.chats.setMuted(id, false) }
                else SheetAction(stringResource(R.string.mute), Icons.Outlined.NotificationsOff) { c.chats.setMuted(id, true); c.notifier.cancel(id) },
            )
            add(
                if (archivedTab) SheetAction(stringResource(R.string.unarchive), Icons.Outlined.Unarchive) { c.chats.setArchived(id, false) }
                else SheetAction(stringResource(R.string.archive), Icons.Outlined.Archive) { c.chats.setArchived(id, true) },
            )
            if (!conv.isGroup && conv.addresses.isNotEmpty() && c.blocklist.canBlock) {
                add(SheetAction(stringResource(R.string.block), Icons.Outlined.Block, destructive = true) { blocking = item })
            }
            add(SheetAction(stringResource(R.string.delete_chat), Icons.Outlined.Delete, destructive = true) { deleting = item })
        }
        ActionSheet(item.title, null, actions, onDismiss = { menuFor = null })
    }

    deleting?.let { item ->
        ConfirmDialog(
            stringResource(R.string.delete_chat_title, item.title),
            stringResource(R.string.delete_chat_body),
            stringResource(R.string.delete),
            onDismiss = { deleting = null },
        ) {
            deleting = null
            val id = item.conversation.threadId
            c.runTask {
                c.store.deleteThread(id)
                c.chats.forget(id)
                c.notifier.cancel(id)
                c.notifier.removeShortcut(id)
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
            c.runTask {
                c.blocklist.block(number)
                c.chats.setArchived(item.conversation.threadId, true)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    item: ChatItem,
    pinned: Boolean,
    muted: Boolean,
    draft: Boolean,
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item.title, conv.addresses.sorted().joinToString(), item.photo, group = conv.isGroup)
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
