package com.vknxworks.textctrl.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.sms.SearchHit
import com.vknxworks.textctrl.ui.components.Avatar
import com.vknxworks.textctrl.ui.components.LocalBottomClearance
import com.vknxworks.textctrl.ui.components.MessageView
import com.vknxworks.textctrl.ui.components.TabTitle
import com.vknxworks.textctrl.ui.components.shortTime
import com.vknxworks.textctrl.ui.nav.LocalNavigator
import com.vknxworks.textctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class Found(val hit: SearchHit, val name: String)

/** The Search tab: finds texts and picture messages on this phone by their words. */
@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    val changes by c.store.changes.collectAsStateWithLifecycle()
    val results by produceState<List<Found>?>(null, query, changes) {
        if (query.trim().length < 2) {
            value = null
            return@produceState
        }
        delay(250)
        value = runCatching {
            withContext(Dispatchers.IO) { c.store.search(query).map { Found(it, c.contacts.displayName(it.address.substringBefore(", "))) } }
        }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_search))
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        val list = results
        when {
            list == null -> MessageView(
                Icons.Filled.Search,
                stringResource(R.string.search_start),
                stringResource(R.string.search_start_body),
                modifier = Modifier.padding(bottom = LocalBottomClearance.current),
            )
            list.isEmpty() -> MessageView(
                Icons.Outlined.SearchOff,
                stringResource(R.string.search_none),
                stringResource(R.string.search_none_body),
                modifier = Modifier.padding(bottom = LocalBottomClearance.current),
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp + LocalBottomClearance.current)) {
                items(list, key = { it.hit.messageKey }) { f ->
                    val hit = f.hit
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { focus.clearFocus(); nav.chat(hit.threadId, hit.messageKey) }
                            .padding(horizontal = Gutter, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(f.name, hit.address, null, size = 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(
                                    if (hit.incoming) f.name else stringResource(R.string.search_you_to, f.name),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(shortTime(context, hit.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                highlight(hit.body, query.trim(), MaterialTheme.colorScheme.primary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The text around the first match, with the matches in bold. */
private fun highlight(text: String, query: String, color: Color) = buildAnnotatedString {
    val flat = text.replace('\n', ' ')
    val first = flat.indexOf(query, ignoreCase = true)
    val start = if (first > 40) first - 30 else 0
    val shown = (if (start > 0) "…" else "") + flat.substring(start)
    var i = 0
    while (i < shown.length) {
        val at = shown.indexOf(query, i, ignoreCase = true)
        if (at < 0 || query.isEmpty()) {
            append(shown.substring(i))
            break
        }
        append(shown.substring(i, at))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(shown.substring(at, at + query.length)) }
        i = at + query.length
    }
}

@Suppress("unused")
private fun nameOrNumber(address: String) = Contacts.formatNumber(address)
