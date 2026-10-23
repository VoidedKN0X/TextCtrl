package com.vknxworks.textctrl.ui.chat

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsMessage
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vknxworks.textctrl.PendingAction
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.sms.Attachment
import com.vknxworks.textctrl.sms.Contact
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.sms.Message
import com.vknxworks.textctrl.sms.Messenger
import com.vknxworks.textctrl.sms.MmsPart
import com.vknxworks.textctrl.sms.SendStatus
import com.vknxworks.textctrl.sms.Sim
import com.vknxworks.textctrl.ui.components.ActionSheet
import com.vknxworks.textctrl.ui.components.Avatar
import com.vknxworks.textctrl.ui.components.ChoiceSheet
import com.vknxworks.textctrl.ui.components.ConfirmDialog
import com.vknxworks.textctrl.ui.components.InfoDialog
import com.vknxworks.textctrl.ui.components.LoadingView
import com.vknxworks.textctrl.ui.components.MessageView
import com.vknxworks.textctrl.ui.components.SheetAction
import com.vknxworks.textctrl.ui.components.avatarColor
import com.vknxworks.textctrl.ui.components.clockTime
import com.vknxworks.textctrl.ui.components.copySensitive
import com.vknxworks.textctrl.ui.components.dayHeading
import com.vknxworks.textctrl.ui.components.fileSize
import com.vknxworks.textctrl.ui.components.fullTime
import com.vknxworks.textctrl.ui.components.linkify
import com.vknxworks.textctrl.ui.components.openAttachment
import com.vknxworks.textctrl.ui.components.runTask
import com.vknxworks.textctrl.ui.components.sameDay
import com.vknxworks.textctrl.ui.components.saveToPhone
import com.vknxworks.textctrl.ui.lock.AppLock
import com.vknxworks.textctrl.ui.nav.LocalNavigator
import com.vknxworks.textctrl.ui.nav.NewChatRoute
import com.vknxworks.textctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One row of the message list: a day heading or a message. */
private sealed interface ChatRow {
    val key: String
    data class Day(val millis: Long) : ChatRow { override val key = "day$millis" }
    data class Msg(val message: Message, val showSender: Boolean, val showTime: Boolean) : ChatRow { override val key = message.key }
}

@Composable
fun ChatScreen(threadId: Long, highlight: String?) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val changes by c.store.changes.collectAsStateWithLifecycle()
    val contactChanges by c.contacts.changes.collectAsStateWithLifecycle()
    val downloading by c.messenger.downloading.collectAsStateWithLifecycle()
    val muted by c.chats.muted.collectAsStateWithLifecycle()
    val archived by c.chats.archived.collectAsStateWithLifecycle()
    val pinned by c.chats.pinned.collectAsStateWithLifecycle()
    var limit by rememberSaveable { mutableIntStateOf(300) }

    val addresses by produceState<List<String>?>(null, threadId, changes) {
        value = runCatching { c.store.threadAddresses(threadId) }.getOrDefault(value ?: emptyList())
    }
    val messages by produceState<List<Message>?>(null, threadId, changes, limit) {
        if (value != null) delay(100)
        value = runCatching { c.store.messages(threadId, limit) }.getOrElse { value ?: emptyList() }
    }
    val people by produceState(emptyMap<String, Contact>(), addresses, contactChanges) {
        val list = addresses.orEmpty()
        value = withContext(Dispatchers.IO) { list.associateWith { c.contacts.lookup(it) } }
    }
    val to = addresses.orEmpty()
    val group = to.size > 1
    fun nameOf(address: String): String =
        people[address]?.name ?: people.entries.firstOrNull { Contacts.same(it.key, address) }?.value?.name ?: Contacts.formatNumber(address)
    val title = to.joinToString(", ") { nameOf(it) }

    // While this chat is on screen, new messages in it are read rather than notified.
    LifecycleResumeEffect(threadId) {
        c.notifier.visibleChat = threadId
        c.notifier.cancel(threadId)
        onPauseOrDispose { if (c.notifier.visibleChat == threadId) c.notifier.visibleChat = null }
    }
    val unreadHere = messages?.count { it.incoming && !it.read } ?: 0
    LaunchedEffect(threadId, unreadHere) {
        if (unreadHere > 0 && c.notifier.visibleChat == threadId) c.store.markRead(threadId)
    }

    // ---------------------------------------------------------------- composer state
    var text by remember { mutableStateOf(c.chats.draft(threadId)) }
    val attachments = remember { mutableStateListOf<Attachment>() }
    val sims = remember { c.sims.active() }
    var simId by remember { mutableIntStateOf(-1) }
    LaunchedEffect(messages != null) {
        if (simId != -1 || messages == null) return@LaunchedEffect
        // The SIM this chat used last, else the one chosen in settings, else the phone's default.
        val last = messages?.lastOrNull { m -> sims.any { it.subId == m.subId } }?.subId
        simId = last ?: settings.defaultSim.takeIf { d -> sims.any { it.subId == d } } ?: c.sims.defaultSubId()
    }
    LaunchedEffect(threadId) {
        val draft = c.composeDraft.value ?: return@LaunchedEffect
        c.composeDraft.value = null
        if (draft.text.isNotBlank()) text = if (text.isBlank()) draft.text else text + "\n" + draft.text
        val files = withContext(Dispatchers.IO) {
            draft.attachments.distinct().filter { u -> attachments.none { it.uri == u } }
                .take((MAX_ATTACHMENTS - attachments.size).coerceAtLeast(0)).map { uri ->
                Attachment(uri, context.contentResolver.getType(uri) ?: "application/octet-stream", Messenger.displayName(context, uri))
            }
        }
        attachments.addAll(files)
    }
    val currentText by rememberUpdatedState(text)
    // Set when the chat is deleted, so leaving it doesn't save a draft for a chat that's gone.
    val deleted = remember { booleanArrayOf(false) }
    DisposableEffect(threadId) {
        AppLock.saveBeforeLock = { if (!deleted[0]) c.chats.saveDraft(threadId, currentText) }
        onDispose {
            if (!deleted[0]) c.chats.saveDraft(threadId, currentText)
            AppLock.saveBeforeLock = null
        }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS)) { uris ->
        attachments.addAll(
            uris.filter { u -> attachments.none { it.uri == u } }.distinct().take((MAX_ATTACHMENTS - attachments.size).coerceAtLeast(0)).map { uri ->
                Attachment(uri, context.contentResolver.getType(uri) ?: "image/jpeg", Messenger.displayName(context, uri))
            },
        )
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        attachments.addAll(
            uris.filter { u -> attachments.none { it.uri == u } }.distinct().take((MAX_ATTACHMENTS - attachments.size).coerceAtLeast(0)).map { uri ->
                Attachment(uri, context.contentResolver.getType(uri) ?: "application/octet-stream", Messenger.displayName(context, uri))
            },
        )
    }

    fun send() {
        val body = text.trim()
        val files = attachments.toList()
        if ((body.isEmpty() && files.isEmpty()) || to.isEmpty()) return
        text = ""
        attachments.clear()
        c.chats.saveDraft(threadId, "")
        c.runTask { c.messenger.send(to, body, files, simId) }
    }

    // ---------------------------------------------------------------- sheets and dialogs
    var chatMenu by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    var simMenu by remember { mutableStateOf(false) }
    var messageMenu by remember { mutableStateOf<Message?>(null) }
    var details by remember { mutableStateOf<Message?>(null) }
    var deletingMessage by remember { mutableStateOf<Message?>(null) }
    var deletingChat by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<MmsPart?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(title, to.sorted().joinToString(), if (group) null else to.firstOrNull()?.let { people[it]?.photo }, group = group, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title.ifBlank { " " }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = when {
                            group -> pluralStringResource(R.plurals.people, to.size, to.size)
                            to.size == 1 && people[to[0]]?.name != null -> Contacts.formatNumber(to[0])
                            else -> null
                        }
                        sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    }
                }
            },
            actions = {
                if (to.size == 1 && !to[0].contains('@')) {
                    IconButton(onClick = {
                        AppLock.expectReturn()
                        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", to[0], null))) }
                    }) { Icon(Icons.Outlined.Call, stringResource(R.string.call)) }
                }
                IconButton(onClick = { chatMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val list = messages
            when {
                list == null || addresses == null -> LoadingView()
                to.isEmpty() && list.isEmpty() -> MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.chat_gone), stringResource(R.string.chat_gone_body))
                list.isEmpty() -> MessageView(
                    Icons.Outlined.AddCircleOutline,
                    stringResource(R.string.chat_empty),
                    stringResource(if (group && settings.groupAsMms) R.string.chat_empty_group else R.string.chat_empty_body),
                )
                else -> {
                    val rows = remember(list) { buildRows(list) }
                    val state = rememberLazyListState()
                    var scrolledToHighlight by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(rows, highlight) {
                        if (highlight == null || scrolledToHighlight) return@LaunchedEffect
                        val i = rows.indexOfFirst { it.key == highlight }
                        if (i >= 0) state.scrollToItem(i)
                        scrolledToHighlight = true
                    }
                    // The newest message stays at the bottom, also when a new one arrives.
                    LaunchedEffect(list.lastOrNull()?.key) {
                        if (state.firstVisibleItemIndex <= 2) state.animateScrollToItem(0)
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = state,
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                is ChatRow.Day -> DayHeading(row.millis)
                                is ChatRow.Msg -> Bubble(
                                    m = row.message,
                                    sender = if (group && row.showSender && row.message.incoming) nameOf(row.message.address) else null,
                                    showTime = row.showTime,
                                    highlighted = row.key == highlight,
                                    downloading = row.message.id in downloading,
                                    onLongClick = { messageMenu = row.message },
                                    onRetry = { c.runTask { c.messenger.retry(row.message) } },
                                    onDownload = { c.runTask { c.messenger.download(row.message.id) } },
                                    onOpenPart = { part -> if (part.isImage) viewing = part else openAttachment(context, part) },
                                )
                            }
                        }
                        if (list.size >= limit) {
                            item(key = "more") {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = { limit += 300 }) { Text(stringResource(R.string.load_earlier)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Composer(
            text = text,
            onText = { text = it },
            attachments = attachments,
            onRemove = { attachments.remove(it) },
            isMms = attachments.isNotEmpty() || (group && settings.groupAsMms),
            sim = sims.firstOrNull { it.subId == simId }.takeIf { sims.size > 1 },
            onSim = { simMenu = true },
            onAttach = { attachMenu = true },
            onSend = ::send,
            enabled = to.isNotEmpty(),
        )
    }

    // ---------------------------------------------------------------- menus
    if (chatMenu) {
        val actions = buildList {
            add(
                if (threadId in pinned) SheetAction(stringResource(R.string.unpin), Icons.Outlined.PushPin) { c.chats.setPinned(threadId, false) }
                else SheetAction(stringResource(R.string.pin), Icons.Outlined.PushPin) { c.chats.setPinned(threadId, true) },
            )
            add(
                if (threadId in muted) SheetAction(stringResource(R.string.unmute), Icons.Outlined.Notifications) { c.chats.setMuted(threadId, false) }
                else SheetAction(stringResource(R.string.mute), Icons.Outlined.NotificationsOff) { c.chats.setMuted(threadId, true) },
            )
            add(
                if (threadId in archived) SheetAction(stringResource(R.string.unarchive), Icons.Outlined.Unarchive) { c.chats.setArchived(threadId, false) }
                else SheetAction(stringResource(R.string.archive), Icons.Outlined.Archive) { c.chats.setArchived(threadId, true); nav.back() },
            )
            if (to.size == 1 && people[to[0]]?.name == null && !to[0].contains('@')) {
                add(SheetAction(stringResource(R.string.add_contact), Icons.Outlined.PersonAdd) {
                    AppLock.expectReturn()
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                                .putExtra(ContactsContract.Intents.Insert.PHONE, to[0]),
                        )
                    }
                })
            }
            if (to.size == 1 && c.blocklist.canBlock) {
                add(SheetAction(stringResource(R.string.block), Icons.Outlined.Block, destructive = true) { blocking = true })
            }
            add(SheetAction(stringResource(R.string.delete_chat), Icons.Outlined.Delete, destructive = true) { deletingChat = true })
        }
        ActionSheet(title, to.takeIf { group }?.joinToString(", ") { Contacts.formatNumber(it) }, actions, onDismiss = { chatMenu = false })
    }

    if (attachMenu) {
        ActionSheet(
            stringResource(R.string.attach),
            stringResource(R.string.attach_subtitle),
            listOf(
                SheetAction(stringResource(R.string.attach_media), Icons.Outlined.Photo) {
                    AppLock.expectReturn()
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                SheetAction(stringResource(R.string.attach_file), Icons.Outlined.AttachFile) {
                    AppLock.expectReturn()
                    pickFiles.launch(arrayOf("*/*"))
                },
            ),
            onDismiss = { attachMenu = false },
        )
    }

    if (simMenu) {
        ChoiceSheet(
            stringResource(R.string.send_with),
            sims.map { it.subId to simLabel(it) },
            simId,
            onDismiss = { simMenu = false },
        ) { simId = it; simMenu = false }
    }

    messageMenu?.let { m ->
        val actions = buildList {
            if (m.status == SendStatus.FAILED) {
                add(SheetAction(stringResource(R.string.retry), Icons.Outlined.Refresh) { c.runTask { c.messenger.retry(m) } })
            }
            if (m.body.isNotBlank()) {
                add(SheetAction(stringResource(R.string.copy_text), Icons.Outlined.ContentCopy) {
                    copySensitive(context, context.getString(R.string.app_name), m.body)
                    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                })
            }
            add(SheetAction(stringResource(R.string.forward), Icons.AutoMirrored.Outlined.Forward) {
                c.composeDraft.value = PendingAction.Compose(emptyList(), m.body, m.attachments.map { it.uri })
                nav.navigate(NewChatRoute(forward = true))
            })
            if (m.attachments.any { it.isImage || it.isVideo }) {
                add(SheetAction(stringResource(R.string.save_to_phone), Icons.Outlined.SaveAlt) {
                    c.runTask {
                        val ok = withContext(Dispatchers.IO) { m.attachments.filter { it.isImage || it.isVideo }.all { saveToPhone(context, it) } }
                        Toast.makeText(context, context.getString(if (ok) R.string.saved_to_phone else R.string.save_failed), Toast.LENGTH_SHORT).show()
                    }
                })
            }
            add(SheetAction(stringResource(R.string.details), Icons.Outlined.Info) { details = m })
            add(SheetAction(stringResource(R.string.delete), Icons.Outlined.Delete, destructive = true) { deletingMessage = m })
        }
        ActionSheet(null, null, actions, onDismiss = { messageMenu = null })
    }

    details?.let { m ->
        val sim = sims.firstOrNull { it.subId == m.subId }
        val lines = listOfNotNull(
            stringResource(if (m.isMms) R.string.details_type_mms else R.string.details_type_sms),
            if (m.incoming) stringResource(R.string.details_from, nameOf(m.address) + " (" + m.address + ")")
            else stringResource(R.string.details_to, to.joinToString(", ") { nameOf(it) }),
            stringResource(if (m.incoming) R.string.details_received else R.string.details_sent, fullTime(context, m.date)),
            sim?.let { stringResource(R.string.details_sim, simLabel(it)) },
            m.subject?.let { stringResource(R.string.details_subject, it) },
            if (!m.incoming) stringResource(R.string.details_status, statusLabel(m.status)) else null,
            m.attachments.takeIf { it.isNotEmpty() }?.let { parts -> stringResource(R.string.details_attachments, parts.joinToString(", ") { it.name ?: it.contentType }) },
        )
        InfoDialog(stringResource(R.string.details), lines.joinToString("\n"), onDismiss = { details = null })
    }

    deletingMessage?.let { m ->
        ConfirmDialog(
            stringResource(R.string.delete_message_title),
            stringResource(R.string.delete_message_body),
            stringResource(R.string.delete),
            onDismiss = { deletingMessage = null },
        ) {
            deletingMessage = null
            c.runTask { c.store.deleteMessage(m) }
        }
    }

    if (deletingChat) {
        ConfirmDialog(
            stringResource(R.string.delete_chat_title, title),
            stringResource(R.string.delete_chat_body),
            stringResource(R.string.delete),
            onDismiss = { deletingChat = false },
        ) {
            deletingChat = false
            deleted[0] = true
            c.chats.saveDraft(threadId, "")
            text = ""
            c.runTask {
                c.store.deleteThread(threadId)
                c.chats.forget(threadId)
                c.notifier.cancel(threadId)
                c.notifier.removeShortcut(threadId)
            }
            nav.back()
        }
    }

    if (blocking && to.size == 1) {
        ConfirmDialog(
            stringResource(R.string.block_title, title),
            stringResource(R.string.block_body),
            stringResource(R.string.block),
            onDismiss = { blocking = false },
        ) {
            blocking = false
            c.runTask {
                c.blocklist.block(to[0])
                c.chats.setArchived(threadId, true)
            }
            nav.back()
        }
    }

    viewing?.let { part -> ImageViewer(part, onDismiss = { viewing = null }) }
}

private const val MAX_ATTACHMENTS = 5

/** Newest first (the list is drawn bottom-up), with a day heading above each day's first message. */
private fun buildRows(messages: List<Message>): List<ChatRow> {
    val newestFirst = messages.asReversed()
    val rows = ArrayList<ChatRow>(newestFirst.size + 16)
    newestFirst.forEachIndexed { i, m ->
        val newer = newestFirst.getOrNull(i - 1)
        val older = newestFirst.getOrNull(i + 1)
        val showSender = older == null || !older.incoming || older.address != m.address || !sameDay(older.date, m.date)
        val showTime = newer == null || newer.incoming != m.incoming || newer.address != m.address ||
            newer.date - m.date > 5 * 60_000L || m.status == SendStatus.FAILED
        rows += ChatRow.Msg(m, showSender, showTime)
        if (older == null || !sameDay(older.date, m.date)) rows += ChatRow.Day(m.date)
    }
    return rows
}

@Composable
private fun simLabel(sim: Sim): String = listOfNotNull(
    stringResource(R.string.sim_slot, sim.slot + 1),
    sim.label,
    sim.number?.let { Contacts.formatNumber(it) },
).distinct().joinToString("  ·  ")

@Composable
private fun statusLabel(status: SendStatus): String = stringResource(
    when (status) {
        SendStatus.SENDING -> R.string.status_sending
        SendStatus.SENT, SendStatus.NONE -> R.string.status_sent
        SendStatus.DELIVERED -> R.string.status_delivered
        SendStatus.NOT_DELIVERED -> R.string.status_not_delivered
        SendStatus.FAILED -> R.string.status_failed
    },
)

@Composable
private fun DayHeading(millis: Long) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            dayHeading(context, millis),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    m: Message,
    sender: String?,
    showTime: Boolean,
    highlighted: Boolean,
    downloading: Boolean,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onOpenPart: (MmsPart) -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val incoming = m.incoming
    val bubbleColor = when {
        highlighted -> cs.tertiary.copy(alpha = 0.35f)
        incoming -> cs.surfaceContainerHigh
        m.status == SendStatus.FAILED -> cs.errorContainer
        else -> cs.primary
    }
    val textColor = when {
        highlighted -> cs.onSurface
        incoming -> cs.onSurface
        m.status == SendStatus.FAILED -> cs.onErrorContainer
        else -> cs.onPrimary
    }
    val shape = if (incoming) RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp) else RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)

    Column(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = if (incoming) Alignment.Start else Alignment.End,
    ) {
        sender?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = avatarColor(m.address),
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp),
            )
        }
        Column(
            Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (incoming) Alignment.Start else Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (m.notDownloaded) {
                Surface(shape = shape, color = cs.surfaceContainerHigh, modifier = Modifier.combinedClickable(onClick = onDownload, onLongClick = onLongClick)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Photo, null, tint = cs.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(stringResource(R.string.mms_waiting), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                            Text(
                                listOfNotNull(
                                    m.mmsSize.takeIf { it > 0 }?.let { fileSize(it) },
                                    m.mmsExpiry.takeIf { it > 0 }?.let { stringResource(R.string.mms_expires, fullTime(context, it)) },
                                ).joinToString("  ·  ").ifBlank { stringResource(R.string.mms_tap_to_fetch) },
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        if (downloading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Download, stringResource(R.string.mms_fetch), tint = cs.primary)
                    }
                }
            }
            m.attachments.forEach { part ->
                if (part.isImage) {
                    AsyncImage(
                        model = part.uri,
                        contentDescription = part.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .sizeIn(maxWidth = 260.dp, maxHeight = 320.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .combinedClickable(onClick = { onOpenPart(part) }, onLongClick = onLongClick),
                    )
                } else {
                    Surface(shape = shape, color = cs.surfaceContainerHigh, modifier = Modifier.combinedClickable(onClick = { onOpenPart(part) }, onLongClick = onLongClick)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when {
                                    part.isVideo -> Icons.Outlined.Videocam
                                    part.isAudio -> Icons.Outlined.AudioFile
                                    part.isContactCard -> Icons.Outlined.ContactPage
                                    else -> Icons.Outlined.InsertDriveFile
                                },
                                null,
                                tint = cs.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                part.name ?: stringResource(
                                    when {
                                        part.isVideo -> R.string.attachment_video
                                        part.isAudio -> R.string.attachment_audio
                                        part.isContactCard -> R.string.attachment_contact
                                        else -> R.string.attachment_file
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            val body = listOfNotNull(m.subject?.let { "$it\n" }, m.body.takeIf { it.isNotBlank() }).joinToString("")
            if (body.isNotBlank()) {
                Surface(
                    shape = shape,
                    color = bubbleColor,
                    modifier = Modifier.combinedClickable(onClick = { if (m.status == SendStatus.FAILED) onRetry() }, onLongClick = onLongClick),
                ) {
                    Text(
                        linkify(body.trim(), if (incoming || highlighted) cs.primary else textColor),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            } else if (m.status == SendStatus.FAILED && m.attachments.isNotEmpty()) {
                Spacer(Modifier.heightIn(min = 0.dp))
            }
        }
        if (showTime) {
            val status = if (incoming) null else statusLabel(m.status).takeIf { m.status != SendStatus.SENT && m.status != SendStatus.NONE }
            Row(
                Modifier
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .then(if (m.status == SendStatus.FAILED) Modifier.clickable(onClick = onRetry) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (m.status == SendStatus.FAILED) {
                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(14.dp), tint = cs.error)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    listOfNotNull(
                        clockTime(context, m.date),
                        status,
                        if (m.status == SendStatus.FAILED) stringResource(R.string.tap_to_retry) else null,
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (m.status == SendStatus.FAILED) cs.error else cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    attachments: List<Attachment>,
    onRemove: (Attachment) -> Unit,
    isMms: Boolean,
    sim: Sim?,
    onSim: () -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val canSend = enabled && (text.isNotBlank() || attachments.isNotEmpty())
    Column(Modifier.fillMaxWidth().background(cs.background).padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
        if (attachments.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(attachments, key = { it.uri.toString() }) { a ->
                    Box {
                        if (a.mime.startsWith("image/")) {
                            AsyncImage(
                                model = a.uri,
                                contentDescription = a.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)),
                            )
                        } else {
                            Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainerHigh, modifier = Modifier.size(72.dp)) {
                                Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(if (a.mime.startsWith("video/")) Icons.Outlined.Videocam else Icons.Outlined.InsertDriveFile, null, tint = cs.primary)
                                    Text(a.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp).clip(CircleShape).background(cs.inverseSurface.copy(alpha = 0.8f)).clickable { onRemove(a) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, stringResource(R.string.remove), Modifier.size(14.dp), tint = cs.inverseOnSurface) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = onAttach, enabled = enabled, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(Icons.Outlined.AddCircleOutline, stringResource(R.string.attach))
            }
            TextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                placeholder = { Text(stringResource(if (isMms) R.string.compose_mms_hint else R.string.compose_hint)) },
                maxLines = 6,
                shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerHigh,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
                    disabledContainerColor = cs.surfaceContainer,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                enabled = enabled,
                trailingIcon = if (sim != null) {
                    {
                        TextButton(onClick = onSim) { Text(stringResource(R.string.sim_slot, sim.slot + 1), style = MaterialTheme.typography.labelMedium) }
                    }
                } else null,
            )
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.padding(bottom = 2.dp).size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
            ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send)) }
        }
        // Long texts are sent in parts; show how many and what's left in this one.
        if (!isMms && text.length > 120) {
            val info = remember(text) { runCatching { SmsMessage.calculateLength(text, false) }.getOrNull() }
            if (info != null && info.size >= 3) {
                Text(
                    stringResource(R.string.sms_counter, info[2], info[0]),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(end = 64.dp, top = 2.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
