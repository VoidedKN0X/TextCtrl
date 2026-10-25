package com.vknxworks.textctrl.ui.chat

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.sms.MmsPart
import com.vknxworks.textctrl.sms.VCard
import com.vknxworks.textctrl.sms.VCardContact
import com.vknxworks.textctrl.ui.components.Avatar
import com.vknxworks.textctrl.ui.components.topDivider
import com.vknxworks.textctrl.ui.lock.AppLock
import com.vknxworks.textctrl.ui.theme.CtrlColors
import com.vknxworks.textctrl.ui.theme.CtrlType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The people on a contact card in a picture message, read from Android's message store. */
private suspend fun readCard(context: Context, part: MmsPart): List<VCardContact> = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = part.text?.toByteArray(Charsets.UTF_8)
            ?: context.contentResolver.openInputStream(part.uri)?.use { it.readBytes() }
            ?: return@runCatching emptyList<VCardContact>()
        VCard.parse(bytes)
    }.getOrDefault(emptyList())
}

/**
 * A contact card sent or received in a picture message: the name, numbers and email addresses, and for
 * cards you received an "Add to contacts" button that opens the contacts app with everything filled in.
 * Falls back to [fallback] (the plain file row) when the card can't be read.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactCardBubble(
    part: MmsPart,
    incoming: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val cards by produceState<List<VCardContact>?>(null, part.id) { value = readCard(context, part) }
    val list = cards ?: return
    if (list.isEmpty()) {
        fallback()
        return
    }
    val shape = if (incoming) RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp) else RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
    Surface(
        shape = shape,
        color = CtrlColors.Surface,
        border = BorderStroke(1.dp, CtrlColors.Outline),
        modifier = Modifier.widthIn(min = 220.dp, max = 300.dp).combinedClickable(onClick = { if (selected) onTap() else onOpen() }, onLongClick = onLongClick),
    ) {
        Column {
            list.take(5).forEachIndexed { i, card ->
                Column(
                    Modifier.fillMaxWidth().then(if (i > 0) Modifier.topDivider(0.dp, CtrlColors.Outline) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(card.name, card.phones.firstOrNull()?.value ?: card.name, null, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(card.name, style = MaterialTheme.typography.titleSmall, color = CtrlColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val sub = listOfNotNull(card.title, card.organization).joinToString(" · ").ifBlank { null }
                                ?: stringResource(R.string.attachment_contact)
                            Text(sub, style = CtrlType.Meta, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val lines = card.phones.map { Contacts.formatNumber(it.value) } + card.emails.map { it.value }
                    lines.take(4).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (lines.size > 4) {
                        Text(pluralStringResource(R.plurals.vcard_more, lines.size - 4, lines.size - 4), style = CtrlType.Meta, color = CtrlColors.Muted)
                    }
                    if (incoming) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    AppLock.expectReturn()
                                    runCatching { context.startActivity(VCard.insertIntent(card)) }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.PersonAdd, null, Modifier.padding(end = 8.dp), tint = CtrlColors.AccentText)
                            Text(stringResource(R.string.add_contact), style = MaterialTheme.typography.labelLarge, color = CtrlColors.AccentText)
                        }
                    }
                }
            }
        }
    }
}
