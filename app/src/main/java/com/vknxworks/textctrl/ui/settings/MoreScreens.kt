package com.vknxworks.textctrl.ui.settings

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import com.vknxworks.textctrl.ui.components.CtrlAlertDialog
import com.vknxworks.textctrl.ui.components.CtrlFieldShape
import com.vknxworks.textctrl.ui.components.ctrlFieldColors
import com.vknxworks.textctrl.ui.components.topDivider
import com.vknxworks.textctrl.ui.theme.CtrlColors
import com.vknxworks.textctrl.ui.theme.CtrlType
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.data.backup.BackupState
import com.vknxworks.textctrl.data.lock.LockVault
import com.vknxworks.textctrl.data.schedule.Scheduled
import com.vknxworks.textctrl.ui.chats.HiddenChatList
import com.vknxworks.textctrl.ui.components.ActionSheet
import com.vknxworks.textctrl.ui.components.ConfirmDialog
import com.vknxworks.textctrl.ui.components.Group
import com.vknxworks.textctrl.ui.components.GroupRow
import com.vknxworks.textctrl.ui.components.MessageView
import com.vknxworks.textctrl.ui.components.ScreenTopBar
import com.vknxworks.textctrl.ui.components.SecureScreen
import com.vknxworks.textctrl.ui.components.SheetAction
import com.vknxworks.textctrl.ui.components.clockTime
import com.vknxworks.textctrl.ui.components.runTask
import com.vknxworks.textctrl.ui.components.shortTime
import com.vknxworks.textctrl.ui.lock.AppLock
import com.vknxworks.textctrl.ui.lock.PinPadScreen
import com.vknxworks.textctrl.ui.lock.findActivity
import com.vknxworks.textctrl.ui.nav.LocalNavigator
import com.vknxworks.textctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ======================================================================== hidden chats

private enum class HiddenStep { CHOOSE, REPEAT, UNLOCK, CHANGE, CHANGE_REPEAT }

/**
 * Hidden chats: kept out of Chats, Archived, Search, the widget and notifications' text, behind a PIN of
 * their own. They close again as soon as you leave the app.
 */
@Composable
fun HiddenChatsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val vault = c.hiddenVault
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    var step by remember { mutableStateOf(if (vault.enabled) HiddenStep.UNLOCK else HiddenStep.CHOOSE) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockout by remember { mutableLongStateOf(vault.lockoutRemaining()) }
    var menu by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf(false) }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(500)
            lockout = vault.lockoutRemaining()
        }
    }

    fun wrong(text: String) {
        message = text
        pin = ""
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45)) }
    }

    fun opened() {
        c.hiddenOpen = true
        c.pendingHide?.let { id ->
            c.chats.setHidden(id, true)
            c.notifier.removeShortcut(id)
            c.pendingHide = null
        }
    }

    fun next() {
        if (busy || lockout > 0 || pin.length < LockVault.PIN_MIN) return
        when (step) {
            HiddenStep.CHOOSE, HiddenStep.CHANGE -> {
                if (pin.toSet().size == 1 || "0123456789012".contains(pin) || "9876543210987".contains(pin)) {
                    wrong(context.getString(R.string.lock_too_easy))
                    return
                }
                first = pin
                pin = ""
                message = null
                step = if (step == HiddenStep.CHOOSE) HiddenStep.REPEAT else HiddenStep.CHANGE_REPEAT
            }
            HiddenStep.REPEAT, HiddenStep.CHANGE_REPEAT -> {
                if (pin != first) {
                    first = ""
                    step = if (step == HiddenStep.REPEAT) HiddenStep.CHOOSE else HiddenStep.CHANGE
                    wrong(context.getString(R.string.lock_mismatch))
                    return
                }
                val chosen = pin
                val changing = step == HiddenStep.CHANGE_REPEAT
                busy = true
                scope.launch {
                    runCatching { withContext(Dispatchers.Default) { if (changing) vault.changePin(chosen) else vault.setUp(chosen) } }
                    busy = false
                    pin = ""
                    first = ""
                    message = null
                    step = HiddenStep.UNLOCK
                    opened()
                }
            }
            HiddenStep.UNLOCK -> {
                val entered = pin
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) { vault.unlockWithPin(entered) }
                    busy = false
                    pin = ""
                    if (result == LockVault.PinResult.Ok) {
                        message = null
                        opened()
                    } else {
                        lockout = vault.lockoutRemaining()
                        wrong(context.getString(R.string.lock_wrong_pin))
                    }
                }
            }
        }
    }

    val showingList = c.hiddenOpen && step == HiddenStep.UNLOCK
    if (!showingList) {
        // The PIN screens are kept out of screenshots, like the app lock's.
        SecureScreen()
        BackHandler {
            if (step == HiddenStep.CHANGE || step == HiddenStep.CHANGE_REPEAT) { step = HiddenStep.UNLOCK; pin = "" } else nav.back()
        }
        PinPadScreen(
            title = stringResource(if (vault.enabled && step == HiddenStep.UNLOCK) R.string.hidden_title else R.string.hidden_setup_title),
            message = when {
                lockout > 0 -> stringResource(R.string.lock_too_many, ((lockout + 999) / 1000).toInt())
                message != null -> message!!
                step == HiddenStep.UNLOCK -> stringResource(R.string.hidden_enter_pin)
                step == HiddenStep.CHOOSE -> stringResource(R.string.hidden_choose_intro, LockVault.PIN_MIN)
                step == HiddenStep.CHANGE -> stringResource(R.string.lock_change_intro, LockVault.PIN_MIN)
                else -> stringResource(R.string.lock_repeat_pin)
            },
            isError = lockout > 0 || message != null,
            pin = pin,
            shake = shake.value,
            checking = busy,
            enabled = !busy && lockout == 0L,
            onDigit = { d ->
                if (pin.length < LockVault.PIN_MAX) {
                    message = null
                    pin += d
                    if (step == HiddenStep.UNLOCK && pin.length == vault.pinLength) next()
                }
            },
            onBackspace = { pin = pin.dropLast(1) },
            onConfirm = ::next,
            showConfirm = pin.length >= LockVault.PIN_MIN && !(step == HiddenStep.UNLOCK && pin.length == vault.pinLength),
            confirmLabel = stringResource(if (step == HiddenStep.REPEAT || step == HiddenStep.CHANGE_REPEAT) R.string.lock_done else if (step == HiddenStep.UNLOCK) R.string.lock_ok else R.string.lock_next),
            bottom = {
                if (step == HiddenStep.UNLOCK && vault.enabled) {
                    TextButton(onClick = { forgetting = true }) { Text(stringResource(R.string.lock_forgot)) }
                }
                TextButton(onClick = { if (step == HiddenStep.CHANGE || step == HiddenStep.CHANGE_REPEAT) { step = HiddenStep.UNLOCK; pin = "" } else nav.back() }) {
                    Text(stringResource(R.string.lock_cancel))
                }
            },
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.hidden_title)) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
            }
            Text(
                stringResource(R.string.hidden_list_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
            )
            HiddenChatList()
        }
    }

    if (menu) {
        ActionSheet(
            stringResource(R.string.hidden_title),
            null,
            listOf(
                SheetAction(stringResource(R.string.hidden_change_pin), Icons.Outlined.Password) { step = HiddenStep.CHANGE; pin = ""; message = null },
                SheetAction(stringResource(R.string.hidden_stop), Icons.Outlined.Visibility, destructive = true) { stopping = true },
            ),
            onDismiss = { menu = false },
        )
    }

    if (stopping) {
        ConfirmDialog(
            stringResource(R.string.hidden_stop_title),
            stringResource(R.string.hidden_stop_body),
            stringResource(R.string.hidden_stop_confirm),
            onDismiss = { stopping = false },
        ) {
            stopping = false
            c.chats.hidden.value.forEach { c.chats.setHidden(it, false) }
            vault.disable()
            c.closeHidden()
            nav.back()
        }
    }

    if (forgetting) {
        val canReset = AppLock.screenLockAvailable(context)
        ConfirmDialog(
            stringResource(R.string.hidden_forgot_title),
            stringResource(if (canReset) R.string.hidden_forgot_body else R.string.hidden_forgot_no_screen_lock),
            stringResource(if (canReset) R.string.lock_forgot_confirm else R.string.close),
            onDismiss = { forgetting = false },
        ) {
            forgetting = false
            val activity = context.findActivity() ?: return@ConfirmDialog
            if (!canReset) return@ConfirmDialog
            // Only the phone's owner may reset it; the hidden chats come back to Chats.
            AppLock.screenLockAuthenticate(activity) { error ->
                if (error == null) {
                    c.chats.hidden.value.forEach { c.chats.setHidden(it, false) }
                    vault.disable()
                    c.closeHidden()
                    step = HiddenStep.CHOOSE
                    nav.back()
                } else {
                    message = error
                }
            }
        }
    }
}

// ======================================================================== scheduled messages

/** Every message waiting to go out later, in all chats. */
@Composable
fun ScheduledScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val all by c.scheduler.items.collectAsStateWithLifecycle()
    val hidden by c.chats.hidden.collectAsStateWithLifecycle()
    val items = all.filter { !it.undo && (it.threadId !in hidden || c.hiddenOpen) }
    val contactChanges by c.contacts.changes.collectAsStateWithLifecycle()
    val names by produceState(emptyMap<Long, String>(), items.map { it.threadId }.toSet(), contactChanges) {
        value = withContext(Dispatchers.IO) { items.associate { it.threadId to it.addresses.joinToString(", ") { a -> c.contacts.displayName(a) } } }
    }
    var menu by remember { mutableStateOf<Scheduled?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.scheduled_list_title))
        if (items.isEmpty()) {
            MessageView(Icons.Outlined.Schedule, stringResource(R.string.scheduled_empty), stringResource(R.string.scheduled_empty_body))
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(items, key = { _, row -> row.id }) { index, item ->
                    Row(
                        Modifier.fillMaxWidth().then(if (index == 0) Modifier else Modifier.topDivider()).clickable { menu = item }.padding(horizontal = Gutter, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(names[item.threadId] ?: "…", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                item.text.ifBlank { stringResource(R.string.attachment_picture_message) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            (shortTime(context, item.at) + "\n" + clockTime(context, item.at)).uppercase(),
                            style = CtrlType.Status,
                            color = CtrlColors.Accent,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
    }

    menu?.let { item ->
        ActionSheet(
            names[item.threadId],
            item.text.take(80),
            listOf(
                SheetAction(stringResource(R.string.open_chat), Icons.AutoMirrored.Outlined.Chat) { nav.chat(item.threadId) },
                SheetAction(stringResource(R.string.send_now), Icons.AutoMirrored.Outlined.Send) { c.runTask { c.scheduler.fire(item.id) } },
                SheetAction(stringResource(R.string.delete), Icons.Outlined.Delete, destructive = true) { c.scheduler.cancel(item.id) },
            ),
            onDismiss = { menu = null },
        )
    }
}

// ======================================================================== backup

private enum class BackupAction { EXPORT, RESTORE }

/** Back up every message to an encrypted file you keep, or bring one back. */
@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val c = context.container
    val state by c.backup.state.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf<BackupAction?>(null) }
    var password by remember { mutableStateOf<CharArray?>(null) }
    val running = state is BackupState.Running

    // A long backup shouldn't be stopped by the screen turning off.
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = password
        password = null
        if (uri != null && pw != null) c.runTask { c.backup.export(uri, pw) } else pw?.fill(' ')
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pw = password
        password = null
        if (uri != null && pw != null) c.runTask { c.backup.restore(uri, pw) } else pw?.fill(' ')
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.backup_title))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.backup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
            )
            Group(footer = stringResource(R.string.backup_footer)) {
                GroupRow(
                    stringResource(R.string.backup_now),
                    stringResource(R.string.backup_now_subtitle),
                    icon = Icons.Outlined.Backup,
                    showChevron = true,
                    onClick = if (running) null else ({ c.backup.reset(); asking = BackupAction.EXPORT }),
                )
                GroupRow(
                    stringResource(R.string.backup_restore),
                    stringResource(R.string.backup_restore_subtitle),
                    icon = Icons.Outlined.Restore,
                    showChevron = true,
                    onClick = if (running || !c.isDefaultSmsApp) null else ({ c.backup.reset(); asking = BackupAction.RESTORE }),
                )
            }
            when (val s = state) {
                is BackupState.Running -> Column(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(if (s.restoring) R.string.backup_restoring else R.string.backup_running, s.done),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = CtrlColors.Accent,
                        trackColor = CtrlColors.Track,
                        gapSize = 0.dp,
                        strokeCap = StrokeCap.Butt,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.backup_keep_open), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is BackupState.Done -> Text(
                    if (s.restoring) stringResource(R.string.backup_restored, s.count, s.skipped) else stringResource(R.string.backup_done, s.count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 12.dp),
                )
                is BackupState.Failed -> Text(
                    s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 12.dp),
                )
                BackupState.Idle -> Unit
            }
        }
    }

    asking?.let { action ->
        PasswordDialog(
            confirmTwice = action == BackupAction.EXPORT,
            onDismiss = { asking = null },
        ) { pw ->
            asking = null
            password = pw
            AppLock.expectReturn()
            when (action) {
                BackupAction.EXPORT -> {
                    val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                    create.launch("TextCtrl-backup-$day.tcbak")
                }
                BackupAction.RESTORE -> open.launch(arrayOf("*/*"))
            }
        }
    }
}

@Composable
private fun PasswordDialog(confirmTwice: Boolean, onDismiss: () -> Unit, onDone: (CharArray) -> Unit) {
    // The password is typed here, so this is kept out of screenshots.
    SecureScreen()
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var tries by remember { mutableIntStateOf(0) }
    val tooShort = first.length < 8
    val mismatch = confirmTwice && first != second
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        // Its own window: kept out of screenshots from its very first frame.
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(if (confirmTwice) R.string.backup_password_title else R.string.backup_password_enter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confirmTwice) Text(stringResource(R.string.backup_password_body), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    first, { first = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = CtrlFieldShape,
                    colors = ctrlFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (confirmTwice) {
                    OutlinedTextField(
                        second, { second = it },
                        label = { Text(stringResource(R.string.backup_password_repeat)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = tries > 0 && mismatch,
                        shape = CtrlFieldShape,
                        colors = ctrlFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (tries > 0 && (tooShort || mismatch)) {
                    Text(
                        stringResource(if (tooShort) R.string.backup_password_short else R.string.backup_password_mismatch),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tries++
                if (!tooShort && !mismatch) onDone(first.toCharArray())
            }) { Text(stringResource(R.string.lock_next)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
