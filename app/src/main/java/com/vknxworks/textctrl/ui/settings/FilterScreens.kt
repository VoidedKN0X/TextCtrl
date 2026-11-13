package com.vknxworks.textctrl.ui.settings

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
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.vknxworks.textctrl.data.filters.Filters
import com.vknxworks.textctrl.data.filters.RuleAction
import com.vknxworks.textctrl.data.filters.RuleKind
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
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.spam_title)) {
            IconButton(onClick = { nav.navigate(FiltersRoute) }) { Icon(Icons.Outlined.FilterAlt, stringResource(R.string.filters_title)) }
        }
        SpamChatList()
    }
}

@Composable
private fun ruleSummary(rule: FilterRule): String = when (rule.kind) {
    RuleKind.KEYWORD -> stringResource(R.string.rule_summary_keyword, Filters.keywords(rule.pattern).joinToString(", "))
    RuleKind.REGEX -> stringResource(R.string.rule_summary_regex, rule.pattern)
    RuleKind.PREFIX -> stringResource(R.string.rule_summary_prefix, Filters.prefixes(rule.pattern).joinToString(", "))
    else -> stringResource(rule.kind.label)
}

/** Your keyword and spam filters, applied on the phone to every message that arrives. */
@Composable
fun FiltersScreen() {
    val c = LocalContext.current.container
    val rules by c.filters.rules.collectAsStateWithLifecycle()
    val skipContacts by c.filters.skipContacts.collectAsStateWithLifecycle()
    val blocked by c.filters.blockedCount.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FilterRule?>(null) }
    var adding by remember { mutableStateOf(false) }

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
                Group(
                    stringResource(R.string.filters_rules),
                    footer = if (blocked > 0) pluralStringResource(R.plurals.filters_blocked_count, blocked, blocked) else null,
                ) {
                    rules.forEach { rule ->
                        GroupRow(
                            ruleSummary(rule),
                            listOf(
                                stringResource(rule.action.label),
                                pluralStringResource(R.plurals.filter_hits, rule.hits, rule.hits),
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
    val error: String? = when {
        !kind.needsPattern -> null
        pattern.isBlank() -> stringResource(R.string.rule_pattern_needed)
        pattern.length > Filters.MAX_PATTERN -> stringResource(R.string.rule_pattern_long)
        kind == RuleKind.REGEX && Filters.validRegex(pattern) == null -> stringResource(R.string.rule_regex_invalid)
        kind == RuleKind.PREFIX && Filters.prefixes(pattern).isEmpty() -> stringResource(R.string.rule_prefix_invalid)
        else -> null
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
                                        else -> R.string.rule_pattern_keyword
                                    },
                                ),
                            )
                        },
                        isError = tried && error != null,
                        supportingText = { Text(if (tried && error != null) error else stringResource(R.string.rule_pattern_hint)) },
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
