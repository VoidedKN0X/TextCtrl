package com.vknxworks.textctrl.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.textctrl.BuildConfig
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.data.lock.LockVault
import com.vknxworks.textctrl.data.settings.AppSettings
import com.vknxworks.textctrl.data.settings.LockTimeout
import com.vknxworks.textctrl.data.settings.NotificationContent
import com.vknxworks.textctrl.data.settings.ThemeChoice
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.ui.components.ConfirmDialog
import com.vknxworks.textctrl.ui.components.Group
import com.vknxworks.textctrl.ui.components.GroupChoice
import com.vknxworks.textctrl.ui.components.GroupRow
import com.vknxworks.textctrl.ui.components.GroupSwitch
import com.vknxworks.textctrl.ui.components.LoadingView
import com.vknxworks.textctrl.ui.components.LocalBottomClearance
import com.vknxworks.textctrl.ui.components.MessageView
import com.vknxworks.textctrl.ui.components.ScreenTopBar
import com.vknxworks.textctrl.ui.components.TextInputDialog
import com.vknxworks.textctrl.ui.components.runTask
import com.vknxworks.textctrl.ui.lock.AppLock
import com.vknxworks.textctrl.ui.lock.PinRequest
import com.vknxworks.textctrl.ui.lock.findActivity
import com.vknxworks.textctrl.ui.nav.BlockedRoute
import com.vknxworks.textctrl.ui.nav.LocalNavigator
import com.vknxworks.textctrl.ui.nav.SettingsRoute
import com.vknxworks.textctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.NotificationsPaused
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.automirrored.outlined.Undo
import com.vknxworks.textctrl.data.settings.AutoDelete
import com.vknxworks.textctrl.data.settings.BubbleColor
import com.vknxworks.textctrl.data.settings.TextSize
import com.vknxworks.textctrl.data.settings.UndoSend
import com.vknxworks.textctrl.ui.nav.BackupRoute
import com.vknxworks.textctrl.ui.nav.HiddenRoute
import com.vknxworks.textctrl.ui.nav.ScheduledRoute

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/TextCtrl"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** The You page, opened from the profile picture: your SIMs, settings, blocked numbers and About. */
@Composable
fun YouScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val sims = remember { c.sims.active() }
    fun open(url: String) {
        AppLock.expectReturn()
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.you_title))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp + LocalBottomClearance.current)) {
            Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_notification), null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(
                    stringResource(if (c.isDefaultSmsApp) R.string.you_is_default else R.string.you_not_default),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (sims.isNotEmpty()) {
                Group(stringResource(R.string.group_sims)) {
                    sims.forEach { sim ->
                        GroupRow(
                            sim.label,
                            listOfNotNull(stringResource(R.string.sim_slot, sim.slot + 1), sim.number?.let { Contacts.formatNumber(it) }).joinToString("  ·  "),
                            icon = Icons.Outlined.SimCard,
                        )
                    }
                }
            }

            Group {
                GroupRow(stringResource(R.string.settings), icon = Icons.Filled.SettingsIcon, showChevron = true, onClick = { nav.navigate(SettingsRoute) })
                GroupRow(stringResource(R.string.scheduled_list_title), stringResource(R.string.scheduled_list_subtitle), icon = Icons.Outlined.Schedule, showChevron = true, onClick = { nav.navigate(ScheduledRoute) })
                GroupRow(stringResource(R.string.hidden_title), stringResource(R.string.hidden_subtitle), icon = Icons.Outlined.VisibilityOff, showChevron = true, onClick = { nav.navigate(HiddenRoute) })
                GroupRow(stringResource(R.string.blocked_title), stringResource(R.string.blocked_subtitle), icon = Icons.Outlined.Block, showChevron = true, onClick = { nav.navigate(BlockedRoute) })
                GroupRow(stringResource(R.string.backup_title), stringResource(R.string.backup_subtitle), icon = Icons.Outlined.Backup, showChevron = true, onClick = { nav.navigate(BackupRoute) })
                GroupRow(
                    stringResource(R.string.notification_settings),
                    stringResource(R.string.notification_settings_subtitle),
                    icon = Icons.Outlined.NotificationsActive,
                    showChevron = true,
                    onClick = {
                        AppLock.expectReturn()
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    },
                )
            }

            Group(stringResource(R.string.group_about)) {
                GroupRow(stringResource(R.string.support_title), stringResource(R.string.support_subtitle), icon = Icons.Filled.Favorite, showChevron = true, onClick = { open(SPONSOR_URL) })
                GroupRow(stringResource(R.string.bugs_title), stringResource(R.string.bugs_subtitle), icon = Icons.Filled.BugReport, showChevron = true, onClick = { open(ISSUES_URL) })
                GroupRow(stringResource(R.string.source_title), stringResource(R.string.source_subtitle), icon = Icons.Filled.Code, showChevron = true, onClick = { open(REPO_URL) })
                GroupRow(stringResource(R.string.licence_title), stringResource(R.string.licence_subtitle), icon = Icons.Filled.Gavel, showChevron = true, onClick = { open(LICENCE_URL) })
            }
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SettingsScreen() {
    SettingsContent()
}

@Composable
private fun SettingsContent() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }
    val bioAvailable = remember { AppLock.biometricAvailable(context) }
    val sims = remember { c.sims.active() }
    var pendingAutoDelete by remember { mutableStateOf<AutoDelete?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group(stringResource(R.string.group_appearance)) {
                GroupChoice(
                    stringResource(R.string.theme),
                    ThemeChoice.entries.map { it to stringResource(it.label) },
                    s.theme,
                    icon = Icons.Outlined.DarkMode,
                ) { v -> update { it.copy(theme = v) } }
                GroupSwitch(
                    stringResource(R.string.dynamic_colour),
                    stringResource(R.string.dynamic_colour_subtitle),
                    s.dynamicColor,
                    icon = Icons.Outlined.Palette,
                ) { v -> update { it.copy(dynamicColor = v) } }
                GroupChoice(
                    stringResource(R.string.bubble_colour),
                    BubbleColor.entries.map { it to stringResource(it.label) },
                    s.bubbleColor,
                    icon = Icons.Outlined.ChatBubbleOutline,
                ) { v -> update { it.copy(bubbleColor = v) } }
                GroupChoice(
                    stringResource(R.string.text_size),
                    TextSize.entries.map { it to stringResource(it.label) },
                    s.textSize,
                    icon = Icons.Outlined.FormatSize,
                ) { v -> update { it.copy(textSize = v) } }
            }

            Group(stringResource(R.string.group_privacy), footer = stringResource(R.string.privacy_footer)) {
                val lockOn = c.vault.enabled
                GroupSwitch(
                    stringResource(R.string.lock_app),
                    stringResource(R.string.lock_app_subtitle),
                    lockOn,
                    icon = Icons.Outlined.Lock,
                ) { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
                if (lockOn) {
                    GroupRow(
                        stringResource(R.string.lock_change_pin),
                        stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                        icon = Icons.Outlined.Password,
                        showChevron = true,
                        onClick = { AppLock.request = PinRequest.CHANGE },
                    )
                    GroupSwitch(
                        stringResource(R.string.lock_fingerprint),
                        stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.lock_fingerprint_settings_subtitle else R.string.lock_fingerprint_unavailable),
                        c.vault.biometricEnabled,
                        icon = Icons.Outlined.Fingerprint,
                        enabled = bioAvailable || c.vault.biometricEnabled,
                    ) { on ->
                        if (!on) {
                            c.vault.disableBiometric()
                        } else {
                            val activity = context.findActivity() ?: return@GroupSwitch
                            val failed = context.getString(R.string.lock_fingerprint_setup_failed)
                            val cipher = c.vault.biometricEnrolCipher() ?: run {
                                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                                return@GroupSwitch
                            }
                            AppLock.biometricPrompt(
                                activity,
                                context.getString(R.string.lock_fingerprint_enable_title),
                                context.getString(R.string.lock_fingerprint_enable_subtitle),
                                cipher,
                            ) { unlocked ->
                                if (!c.vault.finishBiometricEnrol(unlocked)) Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    GroupChoice(
                        stringResource(R.string.lock_again),
                        LockTimeout.entries.map { it to stringResource(it.label) },
                        s.lockTimeout,
                        icon = Icons.Outlined.Timer,
                    ) { v -> update { it.copy(lockTimeout = v) } }
                }
                GroupSwitch(
                    stringResource(R.string.hide_screenshots),
                    stringResource(R.string.hide_screenshots_subtitle),
                    s.hideScreenshots,
                    icon = Icons.Outlined.VisibilityOff,
                ) { v -> update { it.copy(hideScreenshots = v) } }
                GroupSwitch(
                    stringResource(R.string.hide_recents),
                    stringResource(R.string.hide_recents_subtitle),
                    s.hideRecents,
                    icon = Icons.Outlined.Layers,
                ) { v -> update { it.copy(hideRecents = v) } }
                GroupChoice(
                    stringResource(R.string.notify_content),
                    NotificationContent.entries.map { it to stringResource(it.label) },
                    s.notificationContent,
                    icon = Icons.Outlined.Notifications,
                ) { v ->
                    update { it.copy(notificationContent = v) }
                    if (v != NotificationContent.NAME_AND_MESSAGE) c.notifier.removeAllShortcuts()
                }
                GroupSwitch(
                    stringResource(R.string.separate_unknown),
                    stringResource(if (c.contacts.granted) R.string.separate_unknown_subtitle else R.string.separate_unknown_needs_contacts),
                    s.separateUnknown,
                    icon = Icons.Outlined.PersonOff,
                    enabled = c.contacts.granted || s.separateUnknown,
                ) { v -> update { it.copy(separateUnknown = v) } }
                if (s.separateUnknown) {
                    GroupSwitch(
                        stringResource(R.string.notify_unknown),
                        stringResource(R.string.notify_unknown_subtitle),
                        s.notifyUnknown,
                        icon = Icons.Outlined.NotificationsPaused,
                    ) { v -> update { it.copy(notifyUnknown = v) } }
                }
                GroupChoice(
                    stringResource(R.string.auto_delete),
                    AutoDelete.entries.map { it to stringResource(it.label) },
                    s.autoDelete,
                    icon = Icons.Outlined.AutoDelete,
                ) { v ->
                    if (v == AutoDelete.NEVER) update { it.copy(autoDelete = v) } else pendingAutoDelete = v
                }
            }

            Group(stringResource(R.string.group_messages)) {
                GroupChoice(
                    stringResource(R.string.undo_send),
                    UndoSend.entries.map { it to stringResource(it.label) },
                    s.undoSend,
                    icon = Icons.AutoMirrored.Outlined.Undo,
                ) { v -> update { it.copy(undoSend = v) } }
                GroupSwitch(
                    stringResource(R.string.copy_codes),
                    stringResource(R.string.copy_codes_subtitle),
                    s.copyCodes,
                    icon = Icons.Outlined.Password,
                ) { v -> update { it.copy(copyCodes = v) } }
                GroupSwitch(
                    stringResource(R.string.delivery_reports),
                    stringResource(R.string.delivery_reports_subtitle),
                    s.deliveryReports,
                    icon = Icons.Outlined.DoneAll,
                ) { v -> update { it.copy(deliveryReports = v) } }
                GroupSwitch(
                    stringResource(R.string.auto_download),
                    stringResource(R.string.auto_download_subtitle),
                    s.autoDownloadMms,
                    icon = Icons.Outlined.Download,
                ) { v -> update { it.copy(autoDownloadMms = v) } }
                GroupSwitch(
                    stringResource(R.string.group_mms),
                    stringResource(R.string.group_mms_subtitle),
                    s.groupAsMms,
                    icon = Icons.Outlined.Groups,
                ) { v -> update { it.copy(groupAsMms = v) } }
                if (sims.size > 1) {
                    val options = listOf(-1 to stringResource(R.string.sim_default)) +
                        sims.map { it.subId to (stringResource(R.string.sim_slot, it.slot + 1) + "  ·  " + it.label) }
                    GroupChoice(
                        stringResource(R.string.sim_for_new),
                        options,
                        s.defaultSim.takeIf { d -> sims.any { it.subId == d } } ?: -1,
                        icon = Icons.Outlined.SimCard,
                    ) { v -> update { it.copy(defaultSim = v) } }
                }
                GroupRow(
                    stringResource(R.string.sms_app),
                    stringResource(if (c.isDefaultSmsApp) R.string.sms_app_yes else R.string.sms_app_no),
                    icon = if (c.isDefaultSmsApp) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                    iconTint = if (c.isDefaultSmsApp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    showChevron = true,
                    onClick = {
                        AppLock.expectReturn()
                        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                    },
                )
            }
        }
    }

    // Turning auto-delete on deletes old messages straight away, so ask first.
    pendingAutoDelete?.let { choice ->
        AutoDeleteConfirm(choice, onDismiss = { pendingAutoDelete = null }) {
            pendingAutoDelete = null
            scope.launch {
                c.settings.update { it.copy(autoDelete = choice) }
                c.runTask { c.cleaner.run() }
            }
        }
    }
}

@Composable
private fun AutoDeleteConfirm(choice: AutoDelete, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog(
        stringResource(R.string.auto_delete_confirm_title, stringResource(choice.label).lowercase()),
        stringResource(R.string.auto_delete_confirm_body),
        stringResource(R.string.auto_delete_confirm),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/** Android's block list: texts (and calls) from these numbers never reach you. */
@Composable
fun BlockedScreen() {
    val c = LocalContext.current.container
    var version by remember { mutableIntStateOf(0) }
    val numbers by produceState<List<Pair<String, String>>?>(null, version) {
        value = c.blocklist.all().map { it.number }.let { list ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { list.map { it to c.contacts.displayName(it) } }
        }
    }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.blocked_title)) {
            if (c.blocklist.canBlock) IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, stringResource(R.string.blocked_add)) }
        }
        val list = numbers
        when {
            list == null -> LoadingView()
            list.isEmpty() -> MessageView(Icons.Outlined.Block, stringResource(R.string.blocked_empty), stringResource(R.string.blocked_empty_body))
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Group(footer = stringResource(R.string.blocked_footer)) {
                    list.forEach { (number, name) ->
                        GroupRow(
                            name,
                            Contacts.formatNumber(number),
                            icon = Icons.Outlined.Block,
                            iconTint = MaterialTheme.colorScheme.error,
                            onClick = { removing = number },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        TextInputDialog(
            stringResource(R.string.blocked_add),
            stringResource(R.string.blocked_number),
            stringResource(R.string.block),
            onDismiss = { adding = false },
        ) { number ->
            adding = false
            c.runTask { c.blocklist.block(number); version++ }
        }
    }
    removing?.let { number ->
        ConfirmDialog(
            stringResource(R.string.unblock_title, Contacts.formatNumber(number)),
            stringResource(R.string.unblock_body),
            stringResource(R.string.unblock),
            onDismiss = { removing = null },
        ) {
            removing = null
            c.runTask { c.blocklist.unblock(number); version++ }
        }
    }
}
