package com.vknxworks.textctrl.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.sms.ContactPhone
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.ui.components.Avatar
import com.vknxworks.textctrl.ui.components.ScreenTopBar
import com.vknxworks.textctrl.ui.lock.AppLock
import com.vknxworks.textctrl.ui.nav.LocalNavigator
import com.vknxworks.textctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A person picked to write to. */
private data class Recipient(val name: String, val number: String)

/**
 * Who to write to: search your contacts or type a number. Tapping a contact opens the chat with them;
 * the group button next to each one adds them to a group chat instead.
 */
@Composable
fun NewChatScreen(forward: Boolean) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Recipient>() }
    var contactsAllowed by remember { mutableStateOf(c.contacts.granted) }
    val contactChanges by c.contacts.changes.collectAsStateWithLifecycle()
    val results by produceState<List<ContactPhone>>(emptyList(), query, contactsAllowed, contactChanges) {
        delay(120)
        value = runCatching { c.contacts.search(query) }.getOrDefault(emptyList())
    }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        contactsAllowed = ok
        if (ok) c.contacts.refresh()
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Text and files waiting to be forwarded or shared are dropped when you leave without picking anyone.
    val opening = remember { booleanArrayOf(false) }
    DisposableEffect(Unit) {
        onDispose { if (!opening[0]) c.composeDraft.value = null }
    }

    fun open(numbers: List<String>) {
        if (numbers.isEmpty()) return
        opening[0] = true
        scope.launch {
            val thread = runCatching { withContext(Dispatchers.IO) { c.store.threadIdFor(numbers) } }.getOrNull() ?: return@launch
            nav.replaceWithChat(thread)
        }
    }

    fun toggle(r: Recipient) {
        val existing = picked.firstOrNull { Contacts.same(it.number, r.number) }
        if (existing != null) picked.remove(existing) else picked.add(r)
        query = ""
    }

    val typed = query.trim()
    val typedIsNumber = typed.length >= 3 && typed.all { it.isDigit() || it in "+ -()*#" } && typed.any { it.isDigit() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(if (forward) R.string.forward_title else R.string.new_chat))
            if (picked.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    picked.forEach { r ->
                        InputChip(
                            selected = true,
                            onClick = { picked.remove(r) },
                            label = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { Icon(Icons.Filled.CheckCircle, null) },
                            shape = CircleShape,
                            colors = InputChipDefaults.inputChipColors(),
                        )
                    }
                }
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp).focusRequester(focus),
                placeholder = { Text(stringResource(R.string.new_chat_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    if (typedIsNumber) {
                        if (picked.isEmpty()) open(listOf(typed)) else toggle(Recipient(Contacts.formatNumber(typed), typed))
                    } else if (picked.isNotEmpty()) open(picked.map { it.number })
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                if (typedIsNumber) {
                    item(key = "typed") {
                        PersonRow(
                            name = stringResource(R.string.new_chat_send_to, Contacts.formatNumber(typed)),
                            detail = null,
                            number = typed,
                            photo = null,
                            selected = false,
                            icon = true,
                            onClick = { if (picked.isEmpty()) open(listOf(typed)) else toggle(Recipient(Contacts.formatNumber(typed), typed)) },
                            onGroup = { toggle(Recipient(Contacts.formatNumber(typed), typed)) },
                        )
                    }
                }
                if (!contactsAllowed) {
                    item(key = "allow") {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                AppLock.expectReturn()
                                askContacts.launch(Manifest.permission.READ_CONTACTS)
                            }.padding(horizontal = Gutter, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Contacts, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.contacts_allow), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.contacts_allow_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                items(results, key = { it.name + "|" + it.number }) { p ->
                    val selected = picked.any { Contacts.same(it.number, p.number) }
                    PersonRow(
                        name = p.name,
                        detail = listOf(p.label, Contacts.formatNumber(p.number)).filter { it.isNotBlank() }.joinToString("  ·  "),
                        number = p.number,
                        photo = p.photo,
                        selected = selected,
                        icon = false,
                        onClick = { if (picked.isEmpty()) open(listOf(p.number)) else toggle(Recipient(p.name, p.number)) },
                        onGroup = { toggle(Recipient(p.name, p.number)) },
                    )
                }
            }
        }
        if (picked.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { open(picked.map { it.number }) },
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                text = {
                    Text(
                        if (picked.size == 1) stringResource(R.string.new_chat_start_one, picked[0].name)
                        else pluralStringResource(R.plurals.new_chat_start_group, picked.size, picked.size),
                    )
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            )
        }
    }
}

@Composable
private fun PersonRow(
    name: String,
    detail: String?,
    number: String,
    photo: android.net.Uri?,
    selected: Boolean,
    icon: Boolean,
    onClick: () -> Unit,
    onGroup: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = Gutter, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Dialpad, null, tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            Avatar(name, number, photo, size = 44.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        IconButton(onClick = onGroup) {
            if (selected) Icon(Icons.Filled.CheckCircle, stringResource(R.string.new_chat_remove_from_group), tint = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Outlined.GroupAdd, stringResource(R.string.new_chat_add_to_group), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
