package com.vknxworks.textctrl.ui.chat

import android.telephony.SmsMessage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.data.schedule.Scheduled
import com.vknxworks.textctrl.data.settings.BubbleColor
import com.vknxworks.textctrl.sms.Attachment
import com.vknxworks.textctrl.sms.Message
import com.vknxworks.textctrl.sms.MmsPart
import com.vknxworks.textctrl.sms.OneTimeCode
import com.vknxworks.textctrl.sms.Reaction
import com.vknxworks.textctrl.sms.Reactions
import com.vknxworks.textctrl.sms.SendStatus
import com.vknxworks.textctrl.sms.Sim
import com.vknxworks.textctrl.ui.components.ActionItem
import com.vknxworks.textctrl.ui.components.avatarColor
import com.vknxworks.textctrl.ui.components.clockTime
import com.vknxworks.textctrl.ui.components.dayHeading
import com.vknxworks.textctrl.ui.components.fileSize
import com.vknxworks.textctrl.ui.components.fullTime
import com.vknxworks.textctrl.ui.components.linkify
import com.vknxworks.textctrl.ui.components.sameDay
import com.vknxworks.textctrl.ui.components.shortTime
import java.util.Calendar
import java.util.TimeZone

// ======================================================================== rows

/** Reactions on one message: each emoji with how many people used it, and whether you're one of them. */
data class ReactionSummary(val emoji: String, val count: Int, val mine: Boolean)

/** One row of the message list: a day heading, a message, or a message waiting to go out. */
sealed interface ChatRow {
    val key: String
    data class Day(val millis: Long) : ChatRow { override val key = "day$millis" }
    data class Msg(
        val message: Message,
        val showSender: Boolean,
        val showTime: Boolean,
        val reactions: List<ReactionSummary>,
    ) : ChatRow { override val key = message.key }
    data class Pending(val item: Scheduled) : ChatRow { override val key = "sch${item.id}" }
}

/**
 * Newest first (the list is drawn bottom-up), with a day heading above each day's first message.
 * Reaction texts that match a message become badges on it instead of rows of their own.
 */
fun buildRows(messages: List<Message>, pending: List<Scheduled>): List<ChatRow> {
    // Who reacted with what to which message (sender "me" for your own).
    val byTarget = HashMap<String, LinkedHashMap<String, String>>()
    val absorbed = HashSet<String>()
    messages.forEachIndexed { i, m ->
        if (m.isMms && m.attachments.isNotEmpty()) return@forEachIndexed
        val r: Reaction = Reactions.parse(m.body) ?: return@forEachIndexed
        // The newest earlier message it's about, looking back a fair way.
        var j = i - 1
        var target: Message? = null
        while (j >= 0 && j >= i - 300) {
            val candidate = messages[j]
            val isReaction = candidate.key in absorbed
            if (!isReaction) {
                val q = r.target
                val hit = if (q == null) candidate.attachments.any { it.isImage || it.isVideo } else Reactions.matches(q, candidate.body)
                if (hit) { target = candidate; break }
            }
            j--
        }
        val t = target ?: return@forEachIndexed
        absorbed += m.key
        val who = if (m.incoming) m.address else "me"
        val map = byTarget.getOrPut(t.key) { LinkedHashMap() }
        if (r.removed) {
            if (map[who] == r.emoji || r.emoji.isEmpty()) map.remove(who)
        } else {
            map[who] = r.emoji
        }
    }

    val visible = messages.filter { it.key !in absorbed }
    val newestFirst = visible.asReversed()
    val rows = ArrayList<ChatRow>(newestFirst.size + pending.size + 16)
    pending.sortedByDescending { it.at }.forEach { rows += ChatRow.Pending(it) }
    newestFirst.forEachIndexed { i, m ->
        val newer = newestFirst.getOrNull(i - 1)
        val older = newestFirst.getOrNull(i + 1)
        val showSender = older == null || !older.incoming || older.address != m.address || !sameDay(older.date, m.date)
        val showTime = newer == null || newer.incoming != m.incoming || newer.address != m.address ||
            newer.date - m.date > 5 * 60_000L || m.status == SendStatus.FAILED
        val reactions = byTarget[m.key].orEmpty().entries
            .groupBy { it.value }
            .map { (emoji, who) -> ReactionSummary(emoji, who.size, who.any { it.key == "me" }) }
        rows += ChatRow.Msg(m, showSender, showTime, reactions)
        if (older == null || !sameDay(older.date, m.date)) rows += ChatRow.Day(m.date)
    }
    return rows
}

// ======================================================================== pieces

@Composable
fun DayHeading(millis: Long) {
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

/** The colour of your own bubbles, and the text colour that reads on it. */
@Composable
fun ownBubbleColors(choice: BubbleColor): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    if (choice == BubbleColor.ACCENT) return cs.primary to cs.onPrimary
    val bg = Color(choice.argb)
    return bg to if (bg.luminance() > 0.45f) Color(0xFF15181D) else Color.White
}

@Composable
fun statusLabel(status: SendStatus): String = stringResource(
    when (status) {
        SendStatus.SENDING -> R.string.status_sending
        SendStatus.SENT, SendStatus.NONE -> R.string.status_sent
        SendStatus.DELIVERED -> R.string.status_delivered
        SendStatus.NOT_DELIVERED -> R.string.status_not_delivered
        SendStatus.FAILED -> R.string.status_failed
    },
)

@Composable
fun simLabel(sim: Sim): String = listOfNotNull(
    stringResource(R.string.sim_slot, sim.slot + 1),
    sim.label,
    sim.number?.let { com.vknxworks.textctrl.sms.Contacts.formatNumber(it) },
).distinct().joinToString("  ·  ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Bubble(
    m: Message,
    sender: String?,
    showTime: Boolean,
    highlighted: Boolean,
    selected: Boolean,
    downloading: Boolean,
    reactions: List<ReactionSummary>,
    textStyle: TextStyle,
    ownColors: Pair<Color, Color>,
    showCodes: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onOpenPart: (MmsPart) -> Unit,
    onCopyCode: (String) -> Unit,
    onReactionTap: (ReactionSummary) -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val incoming = m.incoming
    val failed = m.status == SendStatus.FAILED
    val bubbleColor = when {
        highlighted -> cs.tertiary.copy(alpha = 0.35f)
        incoming -> cs.surfaceContainerHigh
        failed -> cs.errorContainer
        else -> ownColors.first
    }
    val textColor = when {
        highlighted || incoming -> cs.onSurface
        failed -> cs.onErrorContainer
        else -> ownColors.second
    }
    val shape = if (incoming) RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp) else RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    val tap: () -> Unit = onClick

    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) cs.primary.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(vertical = 2.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected && !incoming) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), tint = cs.primary)
                Spacer(Modifier.width(6.dp))
            }
            Column(
                Modifier.widthIn(max = 300.dp),
                horizontalAlignment = if (incoming) Alignment.Start else Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (m.notDownloaded) {
                    Surface(shape = shape, color = cs.surfaceContainerHigh, modifier = Modifier.combinedClickable(onClick = { if (selected) tap() else onDownload() }, onLongClick = onLongClick)) {
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
                                .combinedClickable(onClick = { if (selected) tap() else onOpenPart(part) }, onLongClick = onLongClick),
                        )
                    } else {
                        Surface(shape = shape, color = cs.surfaceContainerHigh, modifier = Modifier.combinedClickable(onClick = { if (selected) tap() else onOpenPart(part) }, onLongClick = onLongClick)) {
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
                    Box {
                        Surface(
                            shape = shape,
                            color = bubbleColor,
                            modifier = Modifier.combinedClickable(
                                onClick = { if (selected) tap() else if (failed) onRetry() else tap() },
                                onLongClick = onLongClick,
                            ),
                        ) {
                            Text(
                                linkify(body.trim(), if (incoming || highlighted) cs.primary else textColor),
                                style = textStyle,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
                if (reactions.isNotEmpty()) {
                    Row(
                        Modifier.offset(y = (-2).dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        reactions.forEach { r ->
                            Surface(
                                shape = CircleShape,
                                color = if (r.mine) cs.primary.copy(alpha = 0.22f) else cs.surfaceContainerHighest,
                                border = BorderStroke(1.dp, cs.background),
                                modifier = Modifier.clip(CircleShape).clickable { onReactionTap(r) },
                            ) {
                                Text(
                                    if (r.count > 1) "${r.emoji} ${r.count}" else r.emoji,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                if (showCodes && incoming) {
                    val code = androidx.compose.runtime.remember(m.body) { OneTimeCode.find(m.body) }
                    if (code != null) {
                        Surface(
                            shape = CircleShape,
                            color = cs.primary.copy(alpha = 0.16f),
                            modifier = Modifier.clip(CircleShape).clickable { onCopyCode(code) },
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp), tint = cs.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.copy_code_value, code), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                            }
                        }
                    }
                }
            }
            if (selected && incoming) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), tint = cs.primary)
            }
        }
        if (showTime) {
            val status = if (incoming) null else statusLabel(m.status).takeIf { m.status != SendStatus.SENT && m.status != SendStatus.NONE }
            Row(
                Modifier
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .then(if (failed) Modifier.clickable(onClick = onRetry) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (failed) {
                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(14.dp), tint = cs.error)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    listOfNotNull(
                        clockTime(context, m.date),
                        status,
                        if (failed) stringResource(R.string.tap_to_retry) else null,
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed) cs.error else cs.onSurfaceVariant,
                )
            }
        }
    }
}

/** A message waiting to go out: scheduled for later, or in its undo window. */
@Composable
fun PendingBubble(item: Scheduled, now: Long, textStyle: TextStyle, onClick: () -> Unit, onUndo: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = Alignment.End) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            color = cs.primary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.5f)),
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).clickable(onClick = onClick),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                if (item.text.isNotBlank()) Text(item.text, style = textStyle, color = cs.onSurface)
                if (item.files.isNotEmpty()) {
                    Text(
                        androidx.compose.ui.res.pluralStringResource(R.plurals.attachments_count, item.files.size, item.files.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = cs.primary)
            Spacer(Modifier.width(4.dp))
            if (item.undo) {
                val left = ((item.at - now + 999) / 1000).coerceAtLeast(0)
                Text(stringResource(R.string.undo_sending_in, left.toInt()), style = MaterialTheme.typography.labelSmall, color = cs.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.undo),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onUndo).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    stringResource(R.string.scheduled_for, shortTime(context, item.at) + " " + clockTime(context, item.at)),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.primary,
                )
            }
        }
    }
}

// ======================================================================== composer

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Composer(
    text: String,
    onText: (String) -> Unit,
    attachments: List<Attachment>,
    onRemove: (Attachment) -> Unit,
    isMms: Boolean,
    sim: Sim?,
    onSim: () -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onSchedule: () -> Unit,
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
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                enabled = enabled,
                trailingIcon = if (sim != null) {
                    {
                        TextButton(onClick = onSim) { Text(stringResource(R.string.sim_slot, sim.slot + 1), style = MaterialTheme.typography.labelMedium) }
                    }
                } else null,
            )
            Spacer(Modifier.width(6.dp))
            // Tap to send; hold to schedule it for later.
            val sendLabel = stringResource(R.string.send)
            Box(
                Modifier
                    .padding(bottom = 2.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (canSend) cs.primary else cs.onSurface.copy(alpha = 0.12f))
                    .combinedClickable(enabled = canSend, onClick = onSend, onLongClick = onSchedule),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, sendLabel, tint = if (canSend) cs.onPrimary else cs.onSurface.copy(alpha = 0.38f))
            }
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

// ======================================================================== reactions

/** The emoji row for reacting to a message. */
@Composable
fun ReactionPicker(mine: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.react)) },
        text = {
            Column {
                Text(stringResource(R.string.react_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Reactions.choices.take(4).forEach { e -> EmojiKey(e, e in mine) { onPick(e) } }
                }
                Spacer(Modifier.size(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Reactions.choices.drop(4).forEach { e -> EmojiKey(e, e in mine) { onPick(e) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EmojiKey(emoji: String, chosen: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
}

// ======================================================================== scheduling

/** Quick times, or any date and time. Calls [onPick] with the time in milliseconds. */
@Composable
fun ScheduleSheet(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }
    val options = remember(now) {
        val cal = Calendar.getInstance()
        fun at(daysAhead: Int, hour: Int): Long = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        buildList {
            add(now + 60 * 60_000L)
            if (cal.get(Calendar.HOUR_OF_DAY) < 19) add(at(0, 20))
            add(at(1, 8))
            add(at(1, 13))
        }
    }
    if (!picking) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Text(stringResource(R.string.schedule_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text(
                stringResource(R.string.schedule_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.size(8.dp))
            options.forEach { t ->
                ActionItem(Icons.Outlined.Schedule, shortTime(context, t) + "  " + clockTime(context, t)) { onPick(t) }
            }
            ActionItem(Icons.Outlined.Schedule, stringResource(R.string.schedule_pick)) { picking = true }
            Spacer(Modifier.size(24.dp))
        }
    } else {
        DateTimePicker(onPick = onPick, onDismiss = onDismiss)
    }
}

@Composable
private fun DateTimePicker(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val todayUtc = remember {
        val local = Calendar.getInstance()
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    var date by remember { mutableStateOf<Long?>(null) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = todayUtc,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
        },
    )
    val chosenDate = date
    if (chosenDate == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { date = dateState.selectedDateMillis ?: todayUtc }) { Text(stringResource(R.string.lock_next)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(dateState) }
    } else {
        val start = remember { Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) } }
        val timeState = rememberTimePickerState(
            initialHour = start.get(Calendar.HOUR_OF_DAY),
            initialMinute = 0,
            is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
        )
        var tooEarly by remember { mutableStateOf(false) }
        // A plain dialog rather than AlertDialog: the clock needs more width than an alert's text area gives.
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.padding(16.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.schedule_time), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.size(16.dp))
                    TimePicker(timeState)
                    if (tooEarly) Text(stringResource(R.string.schedule_past), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = {
                            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = chosenDate }
                            val local = Calendar.getInstance().apply {
                                clear()
                                set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), timeState.hour, timeState.minute)
                            }.timeInMillis
                            if (local <= System.currentTimeMillis() + 30_000) tooEarly = true else onPick(local)
                        }) { Text(stringResource(R.string.schedule_confirm)) }
                    }
                }
            }
        }
    }
}
