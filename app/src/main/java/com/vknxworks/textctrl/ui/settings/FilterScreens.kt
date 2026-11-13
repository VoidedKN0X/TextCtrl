package com.vknxworks.textctrl.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.data.filters.FilterRule
import com.vknxworks.textctrl.data.filters.RuleAction
import com.vknxworks.textctrl.data.filters.RuleKind
import com.vknxworks.textctrl.data.filters.RuleMatcher
import com.vknxworks.textctrl.ui.components.runTask
import com.vknxworks.textctrl.ui.lock.AppLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.vknxworks.textctrl.ui.chats.SpamChatList
import com.vknxworks.textctrl.ui.components.ChoiceSheet
import com.vknxworks.textctrl.ui.components.CtrlAlertDialog
import com.vknxworks.textctrl.ui.components.CtrlFieldShape
import com.vknxworks.textctrl.ui.components.CtrlSwitch
import com.vknxworks.textctrl.ui.components.Group
import com.vknxworks.textctrl.ui.components.GroupRow
import com.vknxworks.textctrl.ui.components.GroupSwitch
import com.vknxworks.textctrl.ui.components.MessageView
import com.vknxworks.textctrl.ui.components.ScreenTopBar
import com.vknxworks.textctrl.ui.components.ctrlFieldColors
import com.vknxworks.textctrl.ui.nav.FiltersRoute
import com.vknxworks.textctrl.ui.nav.LocalNavigator
import com.vknxworks.textctrl.ui.theme.CtrlColors


/** The Spam folder, under the profile picture. */
@Composable
fun SpamScreen() {
    val nav = LocalNavigator.current
    val c = LocalContext.current.container
    // Seen: the summary of filtered messages starts again from zero.
    LaunchedEffect(Unit) {
        c.notifier.cancelFiltered()
        c.filters.markSpamSeen()
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.spam_title)) {
            IconButton(onClick = { nav.navigate(FiltersRoute) }) { Icon(Icons.Outlined.FilterAlt, stringResource(R.string.filters_title)) }
        }
        SpamChatList()
    }
}

@Composable
private fun ruleSummary(rule: FilterRule): String = when (rule.kind) {
    RuleKind.KEYWORD -> stringResource(R.string.rule_summary_keyword, RuleMatcher.keywords(rule.pattern).joinToString(", "))
    RuleKind.REGEX -> stringResource(R.string.rule_summary_regex, rule.pattern)
    RuleKind.PREFIX -> stringResource(R.string.rule_summary_prefix, RuleMatcher.prefixes(rule.pattern).joinToString(", "))
    RuleKind.EXACT -> stringResource(R.string.rule_summary_exact, RuleMatcher.senders(rule.pattern).joinToString(", "))
    else -> stringResource(rule.kind.label)
}

/** Your keyword and spam filters, applied on the phone to every message that arrives. */
@Composable
fun FiltersScreen() {
    val context = LocalContext.current
    val c = context.container
    val rules by c.filters.rules.collectAsStateWithLifecycle()
    val skipContacts by c.filters.skipContacts.collectAsStateWithLifecycle()
    val notifySummary by c.filters.notifySummary.collectAsStateWithLifecycle()
    val blocked by c.filters.blockedCount.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FilterRule?>(null) }
    var adding by remember { mutableStateOf(false) }
    var exportCsv by remember { mutableStateOf(false) }

    val exportDone = stringResource(R.string.filters_exported)
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) c.runTask { c.ruleFiles.export(uri, csv = false); Toast.makeText(context, exportDone, Toast.LENGTH_SHORT).show() }
    }
    val exportCsvFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) c.runTask { c.ruleFiles.export(uri, csv = true); Toast.makeText(context, exportDone, Toast.LENGTH_SHORT).show() }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) c.runTask {
            val r = c.ruleFiles.import(uri)
            val text = listOfNotNull(
                context.getString(R.string.filters_imported, r.rules, r.blocked),
                if (r.skipped > 0) context.resources.getQuantityString(R.plurals.filters_import_skipped, r.skipped, r.skipped) else null,
            ).joinToString(" ")
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.filters_title)) {
            IconButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.filter_add)) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.filters_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Group {
                GroupSwitch(
                    stringResource(R.string.filters_skip_contacts),
                    stringResource(R.string.filters_skip_contacts_subtitle),
                    skipContacts,
                    icon = Icons.Outlined.Contacts,
                ) { c.filters.setSkipContacts(it) }
                GroupSwitch(
                    stringResource(R.string.filters_summary),
                    stringResource(R.string.filters_summary_subtitle),
                    notifySummary,
                    icon = Icons.Outlined.NotificationsNone,
                ) { on ->
                    c.filters.setNotifySummary(on)
                    if (!on) c.notifier.cancelFiltered()
                }
            }
            if (rules.isEmpty()) {
                MessageView(
                    Icons.Outlined.FilterAlt,
                    stringResource(R.string.filters_empty),
                    stringResource(R.string.filters_empty_body),
                    modifier = Modifier.padding(top = 8.dp),
                    actionLabel = stringResource(R.string.filter_add),
                    onAction = { adding = true },
                )
            } else {
                val slow = c.filters.slowPatterns
                Group(
                    stringResource(R.string.filters_rules),
                    footer = if (blocked > 0) pluralStringResource(R.plurals.filters_blocked_count, blocked, blocked) else null,
                ) {
                    rules.forEach { rule ->
                        GroupRow(
                            ruleSummary(rule),
                            listOfNotNull(
                                stringResource(rule.action.label),
                                pluralStringResource(R.plurals.filter_hits, rule.hits, rule.hits),
                                if (rule.kind == RuleKind.REGEX && rule.pattern in slow) stringResource(R.string.rule_regex_slow) else null,
                            ).joinToString("  ·  "),
                            icon = Icons.Outlined.Tune,
                            iconTint = if (rule.enabled) CtrlColors.AccentText else CtrlColors.Inactive,
                            titleColor = if (rule.enabled) CtrlColors.Text else CtrlColors.Inactive,
                            onClick = { editing = rule },
                            trailing = { CtrlSwitch(rule.enabled, onCheckedChange = { on -> c.filters.update(rule.copy(enabled = on)) }) },
                        )
                    }
                }
            }
            Group(stringResource(R.string.filters_transfer), footer = stringResource(R.string.filters_transfer_footer)) {
                GroupRow(
                    stringResource(R.string.filters_import),
                    stringResource(if (c.isDefaultSmsApp) R.string.filters_import_subtitle else R.string.filters_import_rules_only),
                    icon = Icons.Outlined.FileOpen,
                    showChevron = true,
                    onClick = {
                        AppLock.expectReturn()
                        import.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"))
                    },
                )
                GroupRow(
                    stringResource(R.string.filters_export),
                    stringResource(R.string.filters_export_subtitle),
                    icon = Icons.Outlined.SaveAs,
                    showChevron = true,
                    onClick = { exportCsv = true },
                )
            }
        }
    }

    if (exportCsv) {
        val stamp = remember { SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date()) }
        ChoiceSheet(
            stringResource(R.string.filters_export),
            listOf(false to stringResource(R.string.filters_export_json), true to stringResource(R.string.filters_export_csv)),
            null,
            onDismiss = { exportCsv = false },
        ) { csv ->
            exportCsv = false
            AppLock.expectReturn()
            if (csv) exportCsvFile.launch("textctrl-filters-$stamp.csv") else exportJson.launch("textctrl-filters-$stamp.json")
        }
    }
    if (adding) {
        RuleDialog(null, onDismiss = { adding = false }, onDelete = null) { kind, pattern, action ->
            adding = false
            c.filters.add(kind, pattern, action)
        }
    }
    editing?.let { rule ->
        RuleDialog(
            rule,
            onDismiss = { editing = null },
            onDelete = { editing = null; c.filters.remove(rule.id) },
        ) { kind, pattern, action ->
            editing = null
            c.filters.update(rule.copy(kind = kind, pattern = pattern, action = action))
        }
    }
}

@Composable
private fun RuleDialog(
    rule: FilterRule?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (RuleKind, String, RuleAction) -> Unit,
) {
    var kind by remember { mutableStateOf(rule?.kind ?: RuleKind.KEYWORD) }
    var pattern by remember { mutableStateOf(rule?.pattern.orEmpty()) }
    var action by remember { mutableStateOf(rule?.action ?: RuleAction.SPAM) }
    var pickKind by remember { mutableStateOf(false) }
    var pickAction by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }
    val error: String? = when (RuleMatcher.problem(kind, pattern)) {
        null -> null
        RuleMatcher.PatternProblem.EMPTY -> stringResource(R.string.rule_pattern_needed)
        RuleMatcher.PatternProblem.TOO_LONG -> stringResource(R.string.rule_pattern_long)
        RuleMatcher.PatternProblem.BAD_REGEX -> stringResource(R.string.rule_regex_invalid)
        RuleMatcher.PatternProblem.UNSAFE_REGEX -> stringResource(R.string.rule_regex_unsafe)
        RuleMatcher.PatternProblem.BAD_PREFIX -> stringResource(R.string.rule_prefix_invalid)
    }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (rule == null) R.string.filter_add else R.string.filter_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Group(padded = false) {
                    GroupRow(stringResource(R.string.rule_kind), stringResource(kind.label), showChevron = true, onClick = { pickKind = true })
                    GroupRow(stringResource(R.string.rule_action), stringResource(action.label), showChevron = true, onClick = { pickAction = true })
                }
                if (kind.needsPattern) {
                    OutlinedTextField(
                        pattern, { pattern = it },
                        label = {
                            Text(
                                stringResource(
                                    when (kind) {
                                        RuleKind.REGEX -> R.string.rule_pattern_regex
                                        RuleKind.PREFIX -> R.string.rule_pattern_prefix
                                        RuleKind.EXACT -> R.string.rule_pattern_exact
                                        else -> R.string.rule_pattern_keyword
                                    },
                                ),
                            )
                        },
                        isError = tried && error != null,
                        supportingText = {
                            Text(
                                if (tried && error != null) error
                                else stringResource(
                                    when (kind) {
                                        RuleKind.REGEX -> R.string.rule_pattern_hint_regex
                                        RuleKind.EXACT -> R.string.rule_pattern_hint_exact
                                        else -> R.string.rule_pattern_hint
                                    },
                                ),
                            )
                        },
                        shape = CtrlFieldShape,
                        colors = ctrlFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        stringResource(if (kind == RuleKind.UNKNOWN && !LocalContext.current.container.contacts.granted) R.string.rule_unknown_needs_contacts else R.string.rule_no_pattern),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(stringResource(R.string.rule_action_help), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tried = true
                if (error == null) onSave(kind, if (kind.needsPattern) pattern else "", action)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
    if (pickKind) {
        ChoiceSheet(stringResource(R.string.rule_kind), RuleKind.entries.map { it to stringResource(it.label) }, kind, onDismiss = { pickKind = false }) {
            kind = it; pickKind = false
        }
    }
    if (pickAction) {
        ChoiceSheet(stringResource(R.string.rule_action), RuleAction.entries.map { it to stringResource(it.label) }, action, onDismiss = { pickAction = false }) {
            action = it; pickAction = false
        }
    }
}
