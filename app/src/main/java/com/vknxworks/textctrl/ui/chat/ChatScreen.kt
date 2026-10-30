package com.vknxworks.textctrl.ui.chat

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.textctrl.PendingAction
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.data.schedule.Scheduled
import com.vknxworks.textctrl.data.settings.AutoDelete
import com.vknxworks.textctrl.sms.Attachment
import com.vknxworks.textctrl.sms.Contact
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.sms.Message
import com.vknxworks.textctrl.sms.Messenger
import com.vknxworks.textctrl.sms.MmsPart
import com.vknxworks.textctrl.sms.Reactions
import com.vknxworks.textctrl.sms.SendStatus
import com.vknxworks.textctrl.sms.VCard
import com.vknxworks.textctrl.ui.components.ActionSheet
import com.vknxworks.textctrl.ui.components.Avatar
import com.vknxworks.textctrl.ui.components.ChoiceSheet
import com.vknxworks.textctrl.ui.components.ConfirmDialog
import com.vknxworks.textctrl.ui.components.CtrlBackButton
import com.vknxworks.textctrl.ui.components.CtrlFieldShape
import com.vknxworks.textctrl.ui.components.CtrlSquareButton
import com.vknxworks.textctrl.ui.components.CtrlSubTopBar
import com.vknxworks.textctrl.ui.components.ctrlFieldColors
import com.vknxworks.textctrl.ui.theme.CtrlColors
import com.vknxworks.textctrl.ui.theme.CtrlType
import com.vknxworks.textctrl.ui.components.InfoDialog
import com.vknxworks.textctrl.ui.components.LoadingView
import com.vknxworks.textctrl.ui.components.MessageView
import com.vknxworks.textctrl.ui.components.PillButton
import com.vknxworks.textctrl.ui.components.SheetAction
import com.vknxworks.textctrl.ui.components.copySensitive
import com.vknxworks.textctrl.ui.components.fullTime
import com.vknxworks.textctrl.ui.components.openAttachment
import com.vknxworks.textctrl.ui.components.runTask
import com.vknxworks.textctrl.ui.components.saveToPhone
import com.vknxworks.textctrl.ui.lock.AppLock
import com.vknxworks.textctrl.ui.nav.HiddenRoute
import com.vknxworks.textctrl.ui.nav.LocalNavigator
import com.vknxworks.textctrl.ui.nav.NewChatRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val MAX_ATTACHMENTS = 5

/**
 * The phone's contact picker. Input: whether TextCtrl may read contacts (then a whole contact is picked);
 * otherwise a single phone number is picked, which the picker lets TextCtrl read without any permission.
 */
private object PickContactContract : androidx.activity.result.contract.ActivityResultContract<Boolean, Uri?>() {
    override fun createIntent(context: android.content.Context, input: Boolean): Intent =
        Intent(Intent.ACTION_PICK, if (input) ContactsContract.Contacts.CONTENT_URI else ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}

/**
 * One chat. [onBack] replaces the back arrow's usual job, for the chat pane next to the chat list on
 * wide screens.
 */
@Composable
fun ChatScreen(threadId: Long, highlight: String?, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val close: () -> Unit = onBack ?: { nav.back() }
    val hidden by c.chats.hidden.collectAsStateWithLifecycle()

    // A hidden chat only opens while the hidden chats are showing.
    if (threadId in hidden && !c.hiddenOpen) {
        Column(Modifier.fillMaxSize()) {
            CtrlSubTopBar("", onBack = close)
            Box(Modifier.weight(1f)) {
                MessageView(Icons.Outlined.Lock, stringResource(R.string.hidden_chat_locked), stringResource(R.string.hidden_chat_locked_body))
            }
            PillButton(
                stringResource(R.string.hidden_show),
                onClick = { nav.navigate(HiddenRoute) },
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
        return
    }

    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val changes by c.store.changes.collectAsStateWithLifecycle()
    val contactChanges by c.contacts.changes.collectAsStateWithLifecycle()
    val downloading by c.messenger.downloading.collectAsStateWithLifecycle()
    val muted by c.chats.muted.collectAsStateWithLifecycle()
    val archived by c.chats.archived.collectAsStateWithLifecycle()
    val pinned by c.chats.pinned.collectAsStateWithLifecycle()
    val spamSet by c.chats.spam.collectAsStateWithLifecycle()
    val autoDeleteOverrides by c.chats.autoDelete.collectAsStateWithLifecycle()
    val scheduledAll by c.scheduler.items.collectAsStateWithLifecycle()
    val pending = remember(scheduledAll, threadId) { scheduledAll.filter { it.threadId == threadId } }

    // ---------------------------------------------------------------- search in this chat
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var matchIndex by remember { mutableIntStateOf(0) }
    var limit by rememberSaveable { mutableIntStateOf(300) }
    val effectiveLimit = if (searching) maxOf(limit, 5000) else limit

    val addresses by produceState<List<String>?>(null, threadId, changes) {
        value = runCatching { c.store.threadAddresses(threadId) }.getOrDefault(value ?: emptyList())
    }
    val messages by produceState<List<Message>?>(null, threadId, changes, effectiveLimit) {
        if (value != null) delay(100)
        value = runCatching { c.store.messages(threadId, effectiveLimit) }.getOrElse { value ?: emptyList() }
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
    fun addAttachments(list: List<Attachment>) {
        val fresh = list.distinctBy { it.uri }.filter { a -> attachments.none { it.uri == a.uri } }
        attachments.addAll(fresh.take((MAX_ATTACHMENTS - attachments.size).coerceAtLeast(0)))
    }
    LaunchedEffect(threadId) {
        val draft = c.composeDraft.value ?: return@LaunchedEffect
        c.composeDraft.value = null
        if (draft.text.isNotBlank()) text = if (text.isBlank()) draft.text else text + "\n" + draft.text
        val files = withContext(Dispatchers.IO) {
            draft.attachments.distinct().map { uri ->
                Attachment(uri, context.contentResolver.getType(uri) ?: "application/octet-stream", Messenger.displayName(context, uri))
            }
        }
        addAttachments(files)
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
        addAttachments(uris.map { uri -> Attachment(uri, context.contentResolver.getType(uri) ?: "image/jpeg", Messenger.displayName(context, uri)) })
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        addAttachments(uris.map { uri -> Attachment(uri, context.contentResolver.getType(uri) ?: "application/octet-stream", Messenger.displayName(context, uri)) })
    }

    // A contact card: with the contacts permission the whole contact is read, without it only the one
    // number picked in the phone's contact picker (which grants access to just that row).
    val wholeContact = c.contacts.granted
    val pickContact = rememberLauncherForActivityResult(PickContactContract) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        c.runTask {
            val card = withContext(Dispatchers.IO) { VCard.read(context, uri, wholeContact) }
            if (card == null) {
                Toast.makeText(context, context.getString(R.string.vcard_unreadable), Toast.LENGTH_SHORT).show()
            } else {
                val file = withContext(Dispatchers.IO) { VCard.toAttachment(context, card) }
                if (attachments.size >= MAX_ATTACHMENTS) Toast.makeText(context, context.getString(R.string.vcard_too_many), Toast.LENGTH_SHORT).show()
                else addAttachments(listOf(file))
            }
        }
    }

    /** Sends now, after the undo window, or at [at] when scheduled. */
    fun send(at: Long? = null) {
        val body = text.trim()
        val files = attachments.toList()
        if ((body.isEmpty() && files.isEmpty()) || to.isEmpty()) return
        text = ""
        attachments.clear()
        c.chats.saveDraft(threadId, "")
        val undo = settings.undoSend.seconds
        if (at == null && undo == 0) {
            c.runTask { c.messenger.send(to, body, files, simId) }
        } else {
            val time = at ?: (System.currentTimeMillis() + undo * 1000L)
            c.runTask { c.scheduler.add(threadId, to, body, files, time, simId, undo = at == null) }
        }
    }

    /** Sends a reaction (or takes yours back) as a text, the way other phones understand it. */
    fun react(m: Message, emoji: String, remove: Boolean) {
        if (to.isEmpty()) return
        val body = if (m.body.isBlank() && m.attachments.isEmpty()) return else m.body
        c.runTask { c.messenger.send(to, Reactions.compose(emoji, body, remove), emptyList(), simId) }
    }

    /** Puts a waiting message back into the text field. */
    fun edit(item: Scheduled) {
        val back = c.scheduler.cancel(item.id, keepFiles = true) ?: return
        text = if (text.isBlank()) back.text else back.text + "\n" + text
        addAttachments(c.scheduler.attachmentsOf(back))
    }

    // ---------------------------------------------------------------- selection
    val selected = remember { mutableStateListOf<String>() }
    BackHandler(enabled = selected.isNotEmpty() || searching) {
        if (selected.isNotEmpty()) selected.clear() else { searching = false; query = "" }
    }
    fun toggle(m: Message) {
        if (m.key in selected) selected.remove(m.key) else selected.add(m.key)
    }

    // ---------------------------------------------------------------- sheets and dialogs
    var chatMenu by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    var simMenu by remember { mutableStateOf(false) }
    var scheduleMenu by remember { mutableStateOf(false) }
    var autoDeleteMenu by remember { mutableStateOf(false) }
    var pendingMenu by remember { mutableStateOf<Scheduled?>(null) }
    var reacting by remember { mutableStateOf<Message?>(null) }
    var details by remember { mutableStateOf<Message?>(null) }
    var deletingMessages by remember { mutableStateOf<List<Message>?>(null) }
    var deletingChat by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<MmsPart?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pending.any { it.undo }) {
        while (pending.any { it.undo }) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }

    val list = messages
    val rows = remember(list, pending) { if (list == null) emptyList() else buildRows(list, pending) }
    val byKey = remember(list) { list.orEmpty().associateBy { it.key } }
    val selectedMessages = selected.mapNotNull { byKey[it] }
    // Matches of the in-chat search, newest first.
    val matches = remember(rows, query, searching) {
        if (!searching || query.trim().length < 2) emptyList()
        else rows.filterIsInstance<ChatRow.Msg>().filter { it.message.body.contains(query.trim(), ignoreCase = true) }.map { it.key }
    }
    val currentMatch = matches.getOrNull(matchIndex.coerceIn(0, (matches.size - 1).coerceAtLeast(0)))
    val textStyle = MaterialTheme.typography.bodyLarge.let { base ->
        val scale = settings.textSize.scale
        base.copy(
            fontSize = if (base.fontSize.isSpecified) base.fontSize * scale else base.fontSize,
            lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * scale else base.lineHeight,
        )
    }
    val ownColors = ownBubbleColors(settings.bubbleColor)

    Column(Modifier.fillMaxSize()) {
        when {
            selected.isNotEmpty() -> SelectionBar(
                count = selected.size,
                messages = selectedMessages,
                onClose = { selected.clear() },
                onReact = { reacting = selectedMessages.firstOrNull(); selected.clear() },
                onCopy = {
                    val joined = selectedMessages.sortedBy { it.date }.mapNotNull { it.body.takeIf(String::isNotBlank) }.joinToString("\n\n")
                    copySensitive(context, context.getString(R.string.app_name), joined)
                    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    selected.clear()
                },
                onForward = {
                    val m = selectedMessages.first()
                    c.composeDraft.value = PendingAction.Compose(emptyList(), m.body, m.attachments.map { it.uri })
                    selected.clear()
                    nav.navigate(NewChatRoute(forward = true))
                },
                onSave = {
                    val parts = selectedMessages.flatMap { it.attachments }.filter { it.isImage || it.isVideo }
                    selected.clear()
                    c.runTask {
                        val ok = withContext(Dispatchers.IO) { parts.all { saveToPhone(context, it) } }
                        Toast.makeText(context, context.getString(if (ok) R.string.saved_to_phone else R.string.save_failed), Toast.LENGTH_SHORT).show()
                    }
                },
                onRetry = { val m = selectedMessages.first(); selected.clear(); c.runTask { c.messenger.retry(m) } },
                onDetails = { details = selectedMessages.first(); selected.clear() },
                onDelete = { deletingMessages = selectedMessages },
            )
            searching -> SearchBar(
                query = query,
                onQuery = { query = it; matchIndex = 0 },
                position = if (matches.isEmpty()) null else (matchIndex.coerceIn(0, matches.size - 1) + 1) to matches.size,
                onOlder = { if (matches.isNotEmpty()) matchIndex = (matchIndex + 1).coerceAtMost(matches.size - 1) },
                onNewer = { if (matches.isNotEmpty()) matchIndex = (matchIndex - 1).coerceAtLeast(0) },
                onClose = { searching = false; query = "" },
            )
            else -> CtrlBar {
                CtrlBackButton(close)
                Spacer(Modifier.width(12.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(title, to.sorted().joinToString(), if (group) null else to.firstOrNull()?.let { people[it]?.photo }, group = group, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title.ifBlank { " " }, style = MaterialTheme.typography.titleMedium, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = when {
                            group -> pluralStringResource(R.plurals.people, to.size, to.size)
                            to.size == 1 && people[to[0]]?.name != null -> Contacts.formatNumber(to[0])
                            else -> null
                        }
                        sub?.let { Text(it, style = CtrlType.Meta, color = CtrlColors.Muted, maxLines = 1) }
                    }
                }
                run {
                    if (to.size == 1 && !to[0].contains('@')) {
                        IconButton(onClick = {
                            AppLock.expectReturn()
                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", to[0], null))) }
                        }) { Icon(Icons.Outlined.Call, stringResource(R.string.call)) }
                    }
                    IconButton(onClick = { chatMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                list == null || addresses == null -> LoadingView()
                to.isEmpty() && list.isEmpty() -> MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.chat_gone), stringResource(R.string.chat_gone_body))
                list.isEmpty() && pending.isEmpty() -> MessageView(
                    Icons.Outlined.AddCircleOutline,
                    stringResource(R.string.chat_empty),
                    stringResource(if (group && settings.groupAsMms) R.string.chat_empty_group else R.string.chat_empty_body),
                )
                else -> {
                    val state = rememberLazyListState()
                    var scrolledToHighlight by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(rows, highlight) {
                        if (highlight == null || scrolledToHighlight) return@LaunchedEffect
                        val i = rows.indexOfFirst { it.key == highlight }
                        if (i >= 0) state.scrollToItem(i)
                        scrolledToHighlight = true
                    }
                    LaunchedEffect(currentMatch) {
                        val i = rows.indexOfFirst { it.key == currentMatch }
                        if (currentMatch != null && i >= 0) state.animateScrollToItem(i)
                    }
                    // The newest message stays at the bottom, also when a new one arrives.
                    LaunchedEffect(list.lastOrNull()?.key, pending.size) {
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
                                is ChatRow.Pending -> PendingBubble(
                                    item = row.item,
                                    now = now,
                                    textStyle = textStyle,
                                    onClick = { pendingMenu = row.item },
                                    onUndo = { edit(row.item) },
                                )
                                is ChatRow.Msg -> {
                                    val m = row.message
                                    Bubble(
                                        m = m,
                                        sender = if (group && row.showSender && m.incoming) nameOf(m.address) else null,
                                        showTime = row.showTime,
                                        highlighted = row.key == highlight || row.key == currentMatch,
                                        selected = m.key in selected,
                                        downloading = m.id in downloading,
                                        reactions = row.reactions,
                                        textStyle = textStyle,
                                        ownColors = ownColors,
                                        showCodes = settings.copyCodes,
                                        onClick = { if (selected.isNotEmpty()) toggle(m) },
                                        onLongClick = { toggle(m) },
                                        onRetry = { c.runTask { c.messenger.retry(m) } },
                                        onDownload = { c.runTask { c.messenger.download(m.id) } },
                                        onOpenPart = { part -> if (part.isImage) viewing = part else openAttachment(context, part) },
                                        onCopyCode = { code ->
                                            copySensitive(context, context.getString(R.string.copy_code), code)
                                            Toast.makeText(context, context.getString(R.string.code_copied), Toast.LENGTH_SHORT).show()
                                        },
                                        onReactionTap = { r -> if (r.mine) react(m, r.emoji, remove = true) else react(m, r.emoji, remove = false) },
                                    )
                                }
                            }
                        }
                        if (list.size >= effectiveLimit) {
                            item(key = "more") {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = { limit = effectiveLimit + 300 }) { Text(stringResource(R.string.load_earlier)) }
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
            onSend = { send() },
            onSchedule = { scheduleMenu = true },
            enabled = to.isNotEmpty(),
        )
    }

    // ---------------------------------------------------------------- menus
    if (chatMenu) {
        val actions = buildList {
            add(SheetAction(stringResource(R.string.search_in_chat), Icons.Outlined.Search) { searching = true; query = ""; matchIndex = 0 })
            add(
                if (threadId in pinned) SheetAction(stringResource(R.string.unpin), Icons.Outlined.PushPin) { c.chats.setPinned(threadId, false) }
                else SheetAction(stringResource(R.string.pin), Icons.Outlined.PushPin) { c.chats.setPinned(threadId, true) },
            )
            add(
                if (threadId in muted) SheetAction(stringResource(R.string.unmute), Icons.Outlined.Notifications) { c.chats.setMuted(threadId, false) }
                else SheetAction(stringResource(R.string.mute), Icons.Outlined.NotificationsOff) { c.chats.setMuted(threadId, true) },
            )
            add(SheetAction(stringResource(R.string.chat_sound), Icons.Outlined.MusicNote) {
                val channel = c.notifier.chatChannel(threadId, title.ifBlank { context.getString(R.string.app_name) })
                AppLock.expectReturn()
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, channel),
                    )
                }
            })
            add(SheetAction(stringResource(R.string.auto_delete_chat), Icons.Outlined.AutoDelete) { autoDeleteMenu = true })
            add(
                if (threadId in archived) SheetAction(stringResource(R.string.unarchive), Icons.Outlined.Unarchive) { c.chats.setArchived(threadId, false) }
                else SheetAction(stringResource(R.string.archive), Icons.Outlined.Archive) { c.chats.setArchived(threadId, true); close() },
            )
            add(
                if (threadId in hidden) SheetAction(stringResource(R.string.unhide), Icons.Outlined.Visibility) { c.chats.setHidden(threadId, false) }
                else SheetAction(stringResource(R.string.hide), Icons.Outlined.VisibilityOff) {
                    if (c.hiddenVault.enabled) {
                        c.chats.setHidden(threadId, true)
                        c.notifier.removeShortcut(threadId)
                        Toast.makeText(context, context.getString(R.string.hidden_done), Toast.LENGTH_SHORT).show()
                        close()
                    } else {
                        // The hidden chats need their PIN first; that screen hides this chat once it's set.
                        c.pendingHide = threadId
                        nav.navigate(HiddenRoute)
                    }
                },
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
            add(
                if (threadId in spamSet) SheetAction(stringResource(R.string.spam_restore), Icons.Outlined.Report) { c.chats.setSpam(threadId, false) }
                else SheetAction(stringResource(R.string.spam_move), Icons.Outlined.Report) {
                    c.chats.setSpam(threadId, true)
                    c.notifier.cancel(threadId)
                    Toast.makeText(context, context.getString(R.string.spam_moved), Toast.LENGTH_SHORT).show()
                    close()
                },
            )
            if (to.size == 1 && c.blocklist.canBlock) {
                add(SheetAction(stringResource(R.string.block), Icons.Outlined.Block, destructive = true) { blocking = true })
            }
            add(SheetAction(stringResource(R.string.delete_chat), Icons.Outlined.Delete, destructive = true) { deletingChat = true })
        }
        ActionSheet(title, to.takeIf { group }?.joinToString(", ") { Contacts.formatNumber(it) }, actions, onDismiss = { chatMenu = false })
    }

    if (autoDeleteMenu) {
        val follow = stringResource(R.string.auto_delete_follow, stringResource(settings.autoDelete.label))
        val options: List<Pair<AutoDelete?, String>> = listOf<Pair<AutoDelete?, String>>(null to follow) + AutoDelete.entries.map { it to stringResource(it.label) }
        ChoiceSheet(
            stringResource(R.string.auto_delete_chat),
            options,
            autoDeleteOverrides[threadId],
            onDismiss = { autoDeleteMenu = false },
        ) { choice ->
            autoDeleteMenu = false
            c.chats.setAutoDelete(threadId, choice)
            c.runTask { c.cleaner.run() }
        }
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
                SheetAction(stringResource(R.string.attach_contact), Icons.Outlined.ContactPage) {
                    AppLock.expectReturn()
                    pickContact.launch(wholeContact)
                },
                SheetAction(stringResource(R.string.schedule_title), Icons.Outlined.Schedule) { scheduleMenu = true },
            ),
            onDismiss = { attachMenu = false },
        )
    }

    if (scheduleMenu) {
        ScheduleSheet(
            onPick = { at ->
                scheduleMenu = false
                if (text.isBlank() && attachments.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.schedule_write_first), Toast.LENGTH_SHORT).show()
                } else {
                    send(at)
                }
            },
            onDismiss = { scheduleMenu = false },
        )
    }

    pendingMenu?.let { item ->
        ActionSheet(
            stringResource(if (item.undo) R.string.undo_title else R.string.scheduled_title),
            null,
            listOf(
                SheetAction(stringResource(R.string.send_now), Icons.AutoMirrored.Outlined.Send) { c.runTask { c.scheduler.fire(item.id) } },
                SheetAction(stringResource(R.string.edit), Icons.Outlined.Edit) { edit(item) },
                SheetAction(stringResource(R.string.delete), Icons.Outlined.Delete, destructive = true) { c.scheduler.cancel(item.id) },
            ),
            onDismiss = { pendingMenu = null },
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

    reacting?.let { m ->
        val mine = remember(rows, m.key) {
            (rows.firstOrNull { it.key == m.key } as? ChatRow.Msg)?.reactions?.filter { it.mine }?.map { it.emoji }?.toSet().orEmpty()
        }
        ReactionPicker(
            mine = mine,
            onPick = { emoji ->
                reacting = null
                react(m, emoji, remove = emoji in mine)
            },
            onDismiss = { reacting = null },
        )
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

    deletingMessages?.let { msgs ->
        ConfirmDialog(
            pluralStringResource(R.plurals.delete_messages_title, msgs.size, msgs.size),
            stringResource(R.string.delete_message_body),
            stringResource(R.string.delete),
            onDismiss = { deletingMessages = null },
        ) {
            deletingMessages = null
            selected.clear()
            c.runTask { msgs.forEach { c.store.deleteMessage(it) } }
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
            pending.forEach { c.scheduler.cancel(it.id) }
            c.runTask {
                c.store.deleteThread(threadId)
                c.chats.forget(threadId)
                c.notifier.cancel(threadId)
                c.notifier.removeShortcut(threadId)
                c.notifier.deleteChatChannel(threadId)
            }
            close()
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
            close()
        }
    }

    viewing?.let { part -> ImageViewer(part, onDismiss = { viewing = null }) }
}

/** The top bar while messages are selected: what can be done with them. */
@Composable
private fun SelectionBar(
    count: Int,
    messages: List<Message>,
    onClose: () -> Unit,
    onReact: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    val single = messages.size == 1
    val one = messages.firstOrNull()
    CtrlBar {
        CtrlSquareButton(Icons.Outlined.Close, stringResource(R.string.close), onClick = onClose)
        Spacer(Modifier.width(12.dp))
        Text(pluralStringResource(R.plurals.selected_count, count, count), style = MaterialTheme.typography.titleLarge, color = CtrlColors.Text, modifier = Modifier.weight(1f), maxLines = 1)
        run {
            if (single && one != null && !one.notDownloaded) IconButton(onClick = onReact) { Icon(Icons.Outlined.AddReaction, stringResource(R.string.react)) }
            if (messages.any { it.body.isNotBlank() }) IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy_text)) }
            if (single) IconButton(onClick = onForward) { Icon(Icons.AutoMirrored.Outlined.Forward, stringResource(R.string.forward)) }
            if (messages.any { m -> m.attachments.any { it.isImage || it.isVideo } }) IconButton(onClick = onSave) { Icon(Icons.Outlined.SaveAlt, stringResource(R.string.save_to_phone)) }
            if (single && one?.status == SendStatus.FAILED) IconButton(onClick = onRetry) { Icon(Icons.Outlined.Refresh, stringResource(R.string.retry)) }
            if (single) IconButton(onClick = onDetails) { Icon(Icons.Outlined.Info, stringResource(R.string.details)) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
        }
    }
}

/** The top bar while searching in a chat: the words, which match you're on, and older/newer. */
@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    position: Pair<Int, Int>?,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    CtrlBar {
        CtrlBackButton(onClose, stringResource(R.string.close))
        Spacer(Modifier.width(12.dp))
        run {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text(stringResource(R.string.search_in_chat_hint)) },
                shape = CtrlFieldShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onOlder() }),
                colors = ctrlFieldColors(),
                suffix = if (position != null) {
                    { Text(stringResource(R.string.search_position, position.first, position.second), style = CtrlType.Meta, color = CtrlColors.Muted) }
                } else null,
            )
        }
        IconButton(onClick = onOlder, enabled = position != null) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.search_older)) }
        IconButton(onClick = onNewer, enabled = position != null) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.search_newer)) }
    }
}

/** A chat's top bar row: no colour fill, under the status bar. */
@Composable
private fun CtrlBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 68.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
