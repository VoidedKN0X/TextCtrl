package com.vknxworks.textctrl.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.sms.MmsPart
import com.vknxworks.textctrl.str
import com.vknxworks.textctrl.ui.lock.AppLock
import java.io.File
import java.util.Calendar
import kotlin.math.abs

// ---------- Time ----------

/** "14:05" today, "Tue" this week, "12 Mar" this year, else the full date. */
fun shortTime(context: Context, millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    return when {
        DateUtils.isToday(millis) -> DateFormat.getTimeFormat(context).format(millis)
        abs(now.timeInMillis - millis) < 6 * DateUtils.DAY_IN_MILLIS && millis < now.timeInMillis ->
            DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY)
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) ->
            DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_MONTH)
        else -> DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NUMERIC_DATE)
    }
}

/** The day heading above messages: "Today", "Yesterday", "Tuesday 12 March". */
fun dayHeading(context: Context, millis: Long): String = when {
    DateUtils.isToday(millis) -> context.getString(R.string.today)
    DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> context.getString(R.string.yesterday)
    else -> DateUtils.formatDateTime(
        context, millis,
        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
            (if (Calendar.getInstance().get(Calendar.YEAR) == Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)) DateUtils.FORMAT_NO_YEAR else 0),
    )
}

fun clockTime(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(millis)

fun fullTime(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_WEEKDAY)

fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

fun fileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

// ---------- Avatars ----------

private val avatarColors = listOf(
    Color(0xFF7FC8F8), Color(0xFF8BD8C6), Color(0xFFF4C76B), Color(0xFFF2A0A0),
    Color(0xFFC3A6F2), Color(0xFFA6D68A), Color(0xFFF2B880), Color(0xFF9FB4F2),
)

/** A steady colour per person, so the same contact always looks the same. */
fun avatarColor(key: String): Color = avatarColors[abs(key.hashCode()) % avatarColors.size]

/** A contact's picture, their initial on a colour, or a group badge. */
@Composable
fun Avatar(name: String, key: String, photo: Uri?, group: Boolean = false, size: Dp = 48.dp) {
    val color = avatarColor(key)
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        val letter = name.firstOrNull { it.isLetter() }?.uppercaseChar()
        when {
            group -> Icon(Icons.Filled.Group, null, Modifier.size(size * 0.5f), tint = color)
            letter != null -> Text(letter.toString(), color = color, style = MaterialTheme.typography.titleMedium, fontSize = (size.value * 0.42f).sp)
            else -> Icon(Icons.Filled.Person, null, Modifier.size(size * 0.55f), tint = color)
        }
        if (photo != null && !group) {
            AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
        }
    }
}

// ---------- Links ----------

/** Message text with tappable web links. Only http(s) links; other schemes stay plain text. */
@Composable
fun linkify(text: String, linkColor: Color): AnnotatedString {
    val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        val m = Patterns.WEB_URL.matcher(text)
        var last = 0
        while (m.find()) {
            val raw = text.substring(m.start(), m.end())
            // "example.com" in a sentence counts too, but only with a dot in the host.
            if (!raw.contains('.')) continue
            append(text.substring(last, m.start()))
            val url = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"
            withLink(LinkAnnotation.Url(url, style)) { append(raw) }
            last = m.end()
        }
        append(text.substring(last))
    }
}

// ---------- Attachments ----------

/**
 * Opens an attachment in another app: it's copied out of the message store into TextCtrl's cache and
 * shared for that one view only.
 */
fun openAttachment(context: Context, part: MmsPart) {
    runCatching {
        val file = copyOut(context, part)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, part.contentType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        AppLock.expectReturn()
        context.startActivity(Intent.createChooser(intent, null))
    }.onFailure { Toast.makeText(context, str(R.string.attachment_open_failed), Toast.LENGTH_SHORT).show() }
}

/** Shares an attachment with another app. */
fun shareAttachment(context: Context, part: MmsPart) {
    runCatching {
        val file = copyOut(context, part)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(part.contentType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        AppLock.expectReturn()
        context.startActivity(Intent.createChooser(intent, null))
    }.onFailure { Toast.makeText(context, str(R.string.attachment_open_failed), Toast.LENGTH_SHORT).show() }
}

private fun copyOut(context: Context, part: MmsPart): File {
    val dir = File(context.cacheDir, "open").apply { mkdirs() }
    // Old copies go: they're only needed while another app has them open.
    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > DateUtils.HOUR_IN_MILLIS }?.forEach { it.delete() }
    val name = (part.name ?: "attachment_${part.id}").substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
    val file = File(dir, name.ifBlank { "attachment_${part.id}" })
    context.contentResolver.openInputStream(part.uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
        ?: error("unreadable")
    return file
}

/** Saves a picture or video from a message to the phone's Pictures/TextCtrl (or Movies/TextCtrl) folder. */
fun saveToPhone(context: Context, part: MmsPart): Boolean = runCatching {
    val video = part.isVideo
    val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val name = (part.name ?: "TextCtrl_${part.id}").substringAfterLast('/')
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, part.contentType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/TextCtrl")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val cr = context.contentResolver
    val target = cr.insert(collection, values) ?: error("no target")
    cr.openOutputStream(target)?.use { out -> cr.openInputStream(part.uri)?.use { it.copyTo(out) } }
    cr.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    true
}.getOrDefault(false)
