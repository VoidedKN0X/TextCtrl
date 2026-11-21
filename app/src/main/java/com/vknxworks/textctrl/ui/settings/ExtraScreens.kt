package com.vknxworks.textctrl.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.data.settings.BubbleColor
import com.vknxworks.textctrl.data.settings.NotificationContent
import com.vknxworks.textctrl.data.settings.SettingField
import com.vknxworks.textctrl.data.settings.SettingsFile
import com.vknxworks.textctrl.data.settings.SettingsImport
import com.vknxworks.textctrl.data.settings.Snippets
import com.vknxworks.textctrl.data.settings.Star
import com.vknxworks.textctrl.data.settings.SwipeAction
import com.vknxworks.textctrl.data.settings.TextSize
import com.vknxworks.textctrl.data.settings.UndoSend
import com.vknxworks.textctrl.sms.Message
import com.vknxworks.textctrl.ui.components.ActionSheet
import com.vknxworks.textctrl.ui.components.ConfirmDialog
import com.vknxworks.textctrl.ui.components.CtrlAlertDialog
import com.vknxworks.textctrl.ui.components.CtrlFieldShape
import com.vknxworks.textctrl.ui.components.Group
import com.vknxworks.textctrl.ui.components.GroupRow
import com.vknxworks.textctrl.ui.components.InfoDialog
import com.vknxworks.textctrl.ui.components.LoadingView
import com.vknxworks.textctrl.ui.components.MessageView
import com.vknxworks.textctrl.ui.components.PillButton
import com.vknxworks.textctrl.ui.components.ScreenTopBar
import com.vknxworks.textctrl.ui.components.SheetAction
import com.vknxworks.textctrl.ui.components.ctrlFieldColors
import com.vknxworks.textctrl.ui.components.fullTime
import com.vknxworks.textctrl.ui.components.topDivider
import com.vknxworks.textctrl.ui.lock.AppLock
import com.vknxworks.textctrl.ui.nav.LocalNavigator
import com.vknxworks.textctrl.ui.theme.CtrlColors
import com.vknxworks.textctrl.ui.theme.CtrlType
import com.vknxworks.textctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ======================================================================== starred messages

private data class StarRow(val star: Star, val message: Message, val chat: String)

/** Every starred message, newest first. Messages in hidden chats only show while the hidden chats are open. */
@Composable
fun StarredScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val stars by c.chats.starred.collectAsStateWithLifecycle()
    val hidden by c.chats.hidden.collectAsStateWithLifecycle()
    val spam by c.chats.spam.collectAsStateWithLifecycle()
    val changes by c.store.changes.collectAsStateWithLifecycle()
    val contactChanges by c.contacts.changes.collectAsStateWithLifecycle()
    val hiddenOpen = c.hiddenOpen
    val visible = remember(stars, hidden, spam, hiddenOpen) {
        stars.filter { (it.threadId !in hidden || hiddenOpen) && it.threadId !in spam }
    }
    val rows by produceState<List<StarRow>?>(null, visible, changes, contactChanges) {
        value = withContext(Dispatchers.IO) {
            visible.mapNotNull { star ->
                val m = runCatching { c.store.message(star.key) }.getOrNull() ?: return@mapNotNull null
                // The message store can give a deleted message's number to a new one: only the very same message counts.
                if (m.threadId != star.threadId || m.date != star.date) return@mapNotNull null
                val people = runCatching { c.store.threadAddresses(m.threadId) }.getOrDefault(emptyList())
                StarRow(star, m, people.joinToString(", ") { c.contacts.displayName(it) })
            }.sortedByDescending { it.message.date }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.starred_title))
        val list = rows
        when {
            list == null -> LoadingView()
            list.isEmpty() -> MessageView(Icons.Outlined.StarBorder, stringResource(R.string.starred_empty), stringResource(R.string.starred_empty_body))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(list, key = { _, row -> row.star.encode() }) { index, row ->
                    val m = row.message
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier else Modifier.topDivider())
                            .clickable { nav.chat(m.threadId, m.key) }
                            .padding(start = Gutter, end = 4.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.chat.ifBlank { "…" },
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                color = CtrlColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val body = m.body.ifBlank { stringResource(R.string.attachment_picture_message) }
                            Text(
                                if (m.incoming) body else stringResource(R.string.starred_yours, body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(fullTime(context, m.date).uppercase(), style = CtrlType.Status, color = CtrlColors.Inactive, modifier = Modifier.padding(top = 4.dp))
                        }
                        IconButton(onClick = { c.chats.setStarred(listOf(m), false) }) {
                            Icon(Icons.Filled.Star, stringResource(R.string.unstar), Modifier.size(22.dp), tint = CtrlColors.AccentText)
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================== signature and templates

/** Your signature and your quick-reply templates. */
@Composable
fun SnippetsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val state by c.snippets.state.collectAsStateWithLifecycle()
    val unreadable by c.snippets.unreadable.collectAsStateWithLifecycle()
    var editSignature by remember { mutableStateOf(false) }
    // The template being written: its place in the list, or -1 for a new one.
    var editing by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf<Int?>(null) }
    var startOver by remember { mutableStateOf(false) }

    fun save(next: Snippets) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { c.snippets.save(next) }
            if (!ok) Toast.makeText(context, context.getString(R.string.snippets_save_failed), Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.snippets_title))
        val s = state
        when {
            s == null -> LoadingView()
            unreadable -> Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) {
                    MessageView(Icons.Outlined.Lock, stringResource(R.string.snippets_unreadable), stringResource(R.string.snippets_unreadable_body))
                }
                PillButton(
                    stringResource(R.string.snippets_start_over),
                    onClick = { startOver = true },
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                Group(stringResource(R.string.signature_title), footer = stringResource(R.string.signature_footer)) {
                    GroupRow(
                        s.signature.ifBlank { stringResource(R.string.signature_none) },
                        stringResource(if (s.signature.isBlank()) R.string.signature_add else R.string.signature_change),
                        icon = Icons.Outlined.Create,
                        showChevron = true,
                        onClick = { editSignature = true },
                    )
                    if (s.signature.isNotBlank()) {
                        GroupRow(
                            stringResource(R.string.signature_remove),
                            icon = Icons.Outlined.Delete,
                            iconTint = CtrlColors.Error,
                            onClick = { save(s.copy(signature = "")) },
                        )
                    }
                }
                Group(stringResource(R.string.templates_title), footer = stringResource(R.string.templates_footer)) {
                    s.templates.forEachIndexed { i, t ->
                        GroupRow(
                            t.replace('\n', ' ').let { if (it.length > 90) it.take(90).trimEnd() + "…" else it },
                            icon = Icons.AutoMirrored.Outlined.ShortText,
                            showChevron = true,
                            onClick = { menu = i },
                        )
                    }
                    if (s.templates.size < Snippets.MAX_TEMPLATES) {
                        GroupRow(stringResource(R.string.templates_add), icon = Icons.Outlined.Add, onClick = { editing = -1 })
                    }
                }
            }
        }
    }

    val current = state
    if (editSignature && current != null) {
        SnippetDialog(
            title = stringResource(R.string.signature_title),
            label = stringResource(R.string.signature_label),
            initial = current.signature,
            max = Snippets.MAX_SIGNATURE,
            lines = 2,
            onDismiss = { editSignature = false },
        ) { value ->
            editSignature = false
            save(current.copy(signature = value))
        }
    }
    val editIndex = editing
    if (editIndex != null && current != null) {
        val index: Int = editIndex
        SnippetDialog(
            title = stringResource(if (index < 0) R.string.templates_add else R.string.templates_edit),
            label = stringResource(R.string.templates_label),
            initial = current.templates.getOrNull(index).orEmpty(),
            max = Snippets.MAX_TEMPLATE,
            lines = 6,
            onDismiss = { editing = null },
        ) { value ->
            editing = null
            val list = current.templates.toMutableList()
            if (index in list.indices) list[index] = value else list += value
            save(current.copy(templates = list))
        }
    }
    val menuIndex = menu
    val menuText = if (menuIndex != null) current?.templates?.getOrNull(menuIndex) else null
    if (menuIndex != null && current != null && menuText != null) {
        val index: Int = menuIndex
        val t: String = menuText
        ActionSheet(
            t.take(80),
            null,
            listOf(
                SheetAction(stringResource(R.string.edit), Icons.Outlined.Edit) { editing = index },
                SheetAction(stringResource(R.string.delete), Icons.Outlined.Delete, destructive = true) {
                    save(current.copy(templates = current.templates.filterIndexed { i, _ -> i != index }))
                },
            ),
            onDismiss = { menu = null },
        )
    }
    if (startOver) {
        ConfirmDialog(
            stringResource(R.string.snippets_start_over_title),
            stringResource(R.string.snippets_start_over_body),
            stringResource(R.string.snippets_start_over),
            onDismiss = { startOver = false },
        ) {
            startOver = false
            scope.launch { withContext(Dispatchers.IO) { c.snippets.startOver() } }
        }
    }
}

@Composable
private fun SnippetDialog(
    title: String,
    label: String,
    initial: String,
    max: Int,
    lines: Int,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text,
                { text = it.take(max) },
                label = { Text(label) },
                maxLines = lines,
                shape = CtrlFieldShape,
                colors = ctrlFieldColors(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ======================================================================== settings file

/** The name of a setting in a settings file, as Settings shows it. */
@Composable
private fun fieldLabel(field: SettingField): String = stringResource(
    when (field) {
        SettingField.BUBBLE_COLOR -> R.string.bubble_colour
        SettingField.TEXT_SIZE -> R.string.text_size
        SettingField.SWIPE_RIGHT -> R.string.swipe_right
        SettingField.SWIPE_LEFT -> R.string.swipe_left
        SettingField.HIDE_SCREENSHOTS -> R.string.hide_screenshots
        SettingField.HIDE_RECENTS -> R.string.hide_recents
        SettingField.NOTIFICATION_CONTENT -> R.string.notify_content
        SettingField.SEPARATE_UNKNOWN -> R.string.separate_unknown
        SettingField.NOTIFY_UNKNOWN -> R.string.notify_unknown
        SettingField.WARN_LINKS -> R.string.warn_links
        SettingField.UNDO_SEND -> R.string.undo_send
        SettingField.COPY_CODES -> R.string.copy_codes
        SettingField.DELIVERY_REPORTS -> R.string.delivery_reports
        SettingField.AUTO_DOWNLOAD_MMS -> R.string.auto_download
        SettingField.GROUP_AS_MMS -> R.string.group_mms
        SettingField.PLAIN_SMS -> R.string.plain_sms
    },
)

/** A value from a settings file, as Settings shows it. */
@Composable
private fun fieldValue(field: SettingField, raw: String): String {
    val label: Int? = when (field) {
        SettingField.BUBBLE_COLOR -> BubbleColor.entries.firstOrNull { it.name == raw }?.label
        SettingField.TEXT_SIZE -> TextSize.entries.firstOrNull { it.name == raw }?.label
        SettingField.SWIPE_RIGHT, SettingField.SWIPE_LEFT -> SwipeAction.entries.firstOrNull { it.name == raw }?.label
        SettingField.NOTIFICATION_CONTENT -> NotificationContent.entries.firstOrNull { it.name == raw }?.label
        SettingField.UNDO_SEND -> UndoSend.entries.firstOrNull { it.name == raw }?.label
        else -> when (raw) {
            "true" -> R.string.value_on
            "false" -> R.string.value_off
            else -> null
        }
    }
    return if (label != null) stringResource(label) else raw
}

/** "Settings file" on the Backup screen: export TextCtrl's settings to a file, or bring them back from one. */
@Composable
fun SettingsFileSection() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    // The file that was picked, read and checked, waiting for a yes.
    var pending by remember { mutableStateOf<Pair<String, SettingsImport>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val snippets = c.snippets.state.value ?: Snippets()
                    val text = SettingsFile.export(c.settings.settings.value, snippets)
                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("no stream")
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            message = context.getString(if (ok) R.string.settings_file_exported else R.string.settings_file_export_failed)
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readNBytes(SettingsFile.MAX_BYTES + 1)
                        if (bytes.size > SettingsFile.MAX_BYTES) null else String(bytes, Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            val plan = text?.let { SettingsFile.plan(it, c.settings.settings.value) }
            if (text == null || plan == null) {
                message = context.getString(R.string.settings_file_not_ours)
            } else {
                val mine = c.snippets.state.value
                val snippetsChange = plan.snippets != null && plan.snippets != mine
                if (plan.changes.isEmpty() && !snippetsChange && plan.refused.isEmpty()) {
                    message = context.getString(R.string.settings_file_same)
                } else {
                    pending = text to plan
                }
            }
        }
    }

    Group(stringResource(R.string.settings_file_title), footer = stringResource(R.string.settings_file_footer)) {
        GroupRow(
            stringResource(R.string.settings_file_export),
            stringResource(R.string.settings_file_export_subtitle),
            icon = Icons.Outlined.FileUpload,
            showChevron = true,
            onClick = {
                AppLock.expectReturn()
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                runCatching { create.launch("TextCtrl-settings-$day.json") }
            },
        )
        GroupRow(
            stringResource(R.string.settings_file_import),
            stringResource(R.string.settings_file_import_subtitle),
            icon = Icons.Outlined.FileDownload,
            showChevron = true,
            onClick = {
                AppLock.expectReturn()
                runCatching { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            },
        )
    }

    message?.let { InfoDialog(stringResource(R.string.settings_file_title), it, onDismiss = { message = null }) }

    pending?.let { (text, plan) ->
        val mine = c.snippets.state.collectAsStateWithLifecycle().value
        val unreadable by c.snippets.unreadable.collectAsStateWithLifecycle()
        val fileSnippets = plan.snippets
        val snippetsChange = fileSnippets != null && fileSnippets != mine
        val lines = ArrayList<String>()
        if (plan.changes.isNotEmpty()) {
            lines += stringResource(R.string.settings_file_will_change)
            for (change in plan.changes) lines += "• " + fieldLabel(change.field) + ": " + fieldValue(change.field, change.to)
        }
        if (snippetsChange && fileSnippets != null) {
            if (lines.isNotEmpty()) lines += ""
            lines += if (unreadable || mine == null) stringResource(R.string.settings_file_snippets_skipped)
            else stringResource(
                R.string.settings_file_snippets_replace,
                pluralStringResource(R.plurals.templates_count, fileSnippets.templates.size, fileSnippets.templates.size),
            )
        }
        if (plan.refused.isNotEmpty()) {
            if (lines.isNotEmpty()) lines += ""
            lines += stringResource(R.string.settings_file_refused)
            for (field in plan.refused) lines += "• " + fieldLabel(field)
        }
        val nothingToDo = plan.changes.isEmpty() && !(snippetsChange && !unreadable && mine != null)
        if (nothingToDo) {
            InfoDialog(stringResource(R.string.settings_file_import), lines.joinToString("\n"), onDismiss = { pending = null })
        } else {
            ConfirmDialog(
                stringResource(R.string.settings_file_confirm_title),
                lines.joinToString("\n"),
                stringResource(R.string.settings_file_confirm),
                onDismiss = { pending = null },
            ) {
                pending = null
                scope.launch {
                    // Worked out again against the settings as they are now, with the same rules.
                    c.settings.update { now -> SettingsFile.plan(text, now)?.settings ?: now }
                    val saved = if (snippetsChange && fileSnippets != null) withContext(Dispatchers.IO) { c.snippets.save(fileSnippets) } else true
                    message = context.getString(if (saved) R.string.settings_file_imported else R.string.settings_file_imported_partly)
                }
            }
        }
    }
}
