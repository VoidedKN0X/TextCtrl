package com.vknxworks.textctrl.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import androidx.core.content.FileProvider
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.data.settings.ChatPrefs
import com.vknxworks.textctrl.data.settings.SettingsRepository
import com.vknxworks.textctrl.notify.Notifier
import com.vknxworks.textctrl.sms.mms.MmsStore
import com.vknxworks.textctrl.sms.mms.PduComposer
import com.vknxworks.textctrl.sms.mms.PduMessage
import com.vknxworks.textctrl.sms.mms.PduParser
import com.vknxworks.textctrl.sms.mms.PduPart
import com.vknxworks.textctrl.sms.mms.PduType
import com.vknxworks.textctrl.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.SecureRandom

/** A message couldn't be prepared or sent; the message is meant for people. */
class SendException(message: String) : IOException(message)

/**
 * Sends texts and picture messages, and fetches incoming picture messages.
 *
 * Texts go out through Android's SmsManager over the mobile network. Picture messages are built here
 * and handed to Android's MMS service, which talks to the carrier itself: TextCtrl has no internet
 * access of its own.
 */
class Messenger(
    private val context: Context,
    private val store: MessageStore,
    private val sims: Sims,
    private val settings: SettingsRepository,
    private val chats: ChatPrefs,
    private val notifier: Notifier,
) {
    private val random = SecureRandom()

    private val _downloading = MutableStateFlow<Set<Long>>(emptySet())
    /** Picture messages being fetched right now (their ids). */
    val downloading: StateFlow<Set<Long>> = _downloading.asStateFlow()

    // ================================================================ sending

    /**
     * Sends [text] and [attachments] to [addresses] and returns the chat it went to. One person gets a
     * text (or a picture message with attachments); several people get one group picture message, or
     * separate texts when "Group texts as picture messages" is off.
     */
    suspend fun send(addresses: List<String>, text: String, attachments: List<Attachment>, subId: Int): Long = withContext(Dispatchers.IO) {
        val to = addresses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (to.isEmpty()) throw SendException(str(R.string.send_no_recipient))
        if (text.isBlank() && attachments.isEmpty()) throw SendException(str(R.string.send_empty))
        val s = settings.settings.value
        val sub = sims.resolve(subId)
        val group = to.size > 1
        if (attachments.isEmpty() && (!group || !s.groupAsMms)) {
            var thread = -1L
            for (a in to) {
                thread = store.threadIdFor(listOf(a))
                sendSms(a, text, sub, thread, s.deliveryReports)
                chats.unarchive(thread)
            }
            return@withContext if (group) store.threadIdFor(to) else thread
        }
        val thread = store.threadIdFor(to)
        sendMms(thread, to, text, attachments, sub, s.deliveryReports)
        chats.unarchive(thread)
        thread
    }

    private fun sendSms(address: String, text: String, subId: Int, threadId: Long, deliveryReport: Boolean) {
        val uri = store.insertOutgoingSms(address, text, subId, threadId, deliveryReport)
            ?: throw SendException(str(R.string.send_store_failed))
        try {
            val manager = sims.smsManager(subId)
            val parts = manager.divideMessage(text)
            val sent = ArrayList<PendingIntent>()
            val delivered = ArrayList<PendingIntent?>()
            for (i in parts.indices) {
                sent += statusIntent(ACTION_SMS_SENT, uri, i, parts.size)
                delivered += if (deliveryReport && i == parts.lastIndex) statusIntent(ACTION_SMS_DELIVERED, uri, i, parts.size) else null
            }
            if (parts.size > 1) {
                // Only the last part asks for a delivery report; Android accepts empty places in the list.
                @Suppress("UNCHECKED_CAST")
                manager.sendMultipartTextMessage(address, null, parts, sent, if (deliveryReport) delivered as ArrayList<PendingIntent> else null)
            } else {
                manager.sendTextMessage(address, null, text, sent[0], delivered[0])
            }
        } catch (e: Exception) {
            store.setSmsType(uri, Telephony.Sms.MESSAGE_TYPE_FAILED)
            throw SendException(e.message ?: str(R.string.send_failed))
        }
    }

    private fun statusIntent(action: String, uri: Uri, part: Int, parts: Int): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java)
            .setAction(action)
            // A different address for every part, so their PendingIntents don't replace each other.
            .setData(uri.buildUpon().fragment("$action/$part").build())
            .putExtra(EXTRA_URI, uri.toString())
            .putExtra(EXTRA_PART, part)
            .putExtra(EXTRA_PARTS, parts)
        // Mutable: Android adds the result (and the delivery report) to it. Explicit, to TextCtrl only.
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    /** Android's answer to a text TextCtrl sent: sent (or not), and later delivered (or not). */
    suspend fun onSmsStatus(intent: Intent, resultCode: Int) = withContext(Dispatchers.IO) {
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse) ?: return@withContext
        when (intent.action) {
            ACTION_SMS_SENT -> {
                val part = intent.getIntExtra(EXTRA_PART, 0)
                val parts = intent.getIntExtra(EXTRA_PARTS, 1)
                if (resultCode == Activity.RESULT_OK) {
                    if (part == parts - 1 && store.smsType(uri) != Telephony.Sms.MESSAGE_TYPE_FAILED) {
                        store.setSmsType(uri, Telephony.Sms.MESSAGE_TYPE_SENT)
                    }
                } else {
                    store.setSmsType(uri, Telephony.Sms.MESSAGE_TYPE_FAILED, intent.getIntExtra("errorCode", resultCode))
                    store.threadOf(uri)?.let { notifier.notifyFailed(it) }
                }
            }
            ACTION_SMS_DELIVERED -> {
                val pdu = intent.getByteArrayExtra("pdu") ?: return@withContext
                val report = runCatching { SmsMessage.createFromPdu(pdu, intent.getStringExtra("format")) }.getOrNull() ?: return@withContext
                // TP-Status: below 0x20 done, below 0x40 still trying, above that it failed.
                val status = report.status
                store.setSmsStatus(
                    uri,
                    when {
                        status < 0x20 -> Telephony.Sms.STATUS_COMPLETE
                        status < 0x40 -> Telephony.Sms.STATUS_PENDING
                        else -> Telephony.Sms.STATUS_FAILED
                    },
                )
            }
        }
        store.touch()
    }

    private fun sendMms(threadId: Long, to: List<String>, text: String, attachments: List<Attachment>, subId: Int, deliveryReport: Boolean) {
        val manager = sims.smsManager(subId)
        val maxSize = runCatching {
            manager.carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, DEFAULT_MMS_SIZE)
        }.getOrDefault(DEFAULT_MMS_SIZE).takeIf { it > 0 } ?: DEFAULT_MMS_SIZE
        val parts = ArrayList<PduPart>()
        if (text.isNotBlank()) {
            parts += PduPart("text/plain", text.toByteArray(Charsets.UTF_8), name = "text_0.txt", contentId = "text_0", contentLocation = "text_0.txt")
        }
        // What's left after the text and the headers, shared between the attachments.
        val budget = ((maxSize * 0.92).toInt() - parts.sumOf { it.data.size } - 2048).coerceAtLeast(16 * 1024)
        val each = budget / attachments.size.coerceAtLeast(1)
        attachments.forEachIndexed { i, a -> parts += preparePart(a, i + 1, each) }
        sendParts(threadId, to, parts, subId, deliveryReport, manager)
    }

    private fun sendParts(threadId: Long, to: List<String>, parts: List<PduPart>, subId: Int, deliveryReport: Boolean, manager: SmsManager) {
        val transactionId = "T" + java.lang.Long.toHexString(System.currentTimeMillis()) + java.lang.Long.toHexString(random.nextLong() and 0xFFFFFF)
        val pdu = PduComposer.sendRequest(transactionId, to, null, parts, deliveryReport)
        val uri = MmsStore.insert(
            context,
            box = Telephony.Mms.MESSAGE_BOX_OUTBOX,
            threadId = threadId,
            dateSeconds = System.currentTimeMillis() / 1000,
            read = true,
            subId = subId,
            messageType = MessageStore.M_TYPE_SEND_REQ,
            from = MessageStore.INSERT_ADDRESS_TOKEN,
            to = to,
            parts = parts,
            transactionId = transactionId,
        ) ?: throw SendException(str(R.string.send_store_failed))
        val id = ContentUris.parseId(uri)
        try {
            val file = mmsFile("send-$id.pdu")
            file.writeBytes(pdu)
            val contentUri = shareWithMmsService(file, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val intent = Intent(context, MmsResultReceiver::class.java)
                .setAction(ACTION_MMS_SENT)
                .setData(uri)
                .putExtra(EXTRA_FILE, file.name)
            val sent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            manager.sendMultimediaMessage(context, contentUri, null, null, sent)
        } catch (e: Exception) {
            store.setMmsBox(uri, MessageStore.MESSAGE_BOX_FAILED)
            throw SendException(e.message ?: str(R.string.send_failed))
        }
    }

    /** Pictures are shrunk to fit the carrier's limit (which also drops their location and camera details). */
    private fun preparePart(a: Attachment, index: Int, maxBytes: Int): PduPart {
        val cr = context.contentResolver
        val mime = (cr.getType(a.uri) ?: a.mime).lowercase()
        val baseName = a.name.substringBeforeLast('.').ifBlank { "file_$index" }.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        if (mime.startsWith("image/") && mime != "image/gif") {
            val data = compressImage(a.uri, maxBytes)
            val name = "$baseName.jpg"
            return PduPart("image/jpeg", data, name = name, contentId = "image_$index", contentLocation = name)
        }
        val data = cr.openInputStream(a.uri)?.use { it.readBytes() } ?: throw SendException(str(R.string.send_attachment_unreadable))
        if (data.size > maxBytes) throw SendException(str(R.string.send_attachment_too_big, a.name))
        val ext = a.name.substringAfterLast('.', "").take(8)
        val name = if (ext.isNotEmpty()) "$baseName.$ext" else baseName
        return PduPart(mime, data, name = name, contentId = "part_$index", contentLocation = name)
    }

    private fun compressImage(uri: Uri, maxBytes: Int): ByteArray {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        var maxDim = 1600
        repeat(6) {
            val bitmap = runCatching {
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    val scale = minOf(1f, maxDim.toFloat() / maxOf(w, h).coerceAtLeast(1))
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                }
            }.getOrElse { throw SendException(str(R.string.send_attachment_unreadable)) }
            for (quality in intArrayOf(85, 72, 60, 48)) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= maxBytes) {
                    bitmap.recycle()
                    return out.toByteArray()
                }
            }
            bitmap.recycle()
            maxDim = (maxDim * 0.7f).toInt()
        }
        throw SendException(str(R.string.send_picture_too_big))
    }

    /** Android's answer to a picture message TextCtrl sent. */
    suspend fun onMmsSent(intent: Intent, resultCode: Int) = withContext(Dispatchers.IO) {
        val uri = intent.data ?: return@withContext
        intent.getStringExtra(EXTRA_FILE)?.let { mmsFile(it).delete() }
        val conf = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)?.let { PduParser(it).parse() }
        val accepted = resultCode == Activity.RESULT_OK && (conf == null || conf.responseStatus == 0 || conf.responseStatus == 0x80)
        if (accepted) {
            store.setMmsBox(uri, Telephony.Mms.MESSAGE_BOX_SENT, conf?.messageId)
        } else {
            store.setMmsBox(uri, MessageStore.MESSAGE_BOX_FAILED)
            store.threadOf(uri)?.let { notifier.notifyFailed(it) }
        }
        store.touch()
    }

    /** Sends a message that couldn't be sent again, as a new message, and removes the failed one. */
    suspend fun retry(message: Message) = withContext(Dispatchers.IO) {
        val s = settings.settings.value
        val sub = sims.resolve(message.subId)
        if (message.isMms) {
            val to = store.threadAddresses(message.threadId)
            val parts = MmsStore.readParts(context, message.id)
            if (to.isEmpty() || parts.isEmpty()) throw SendException(str(R.string.send_failed))
            store.deleteMessage(message)
            sendParts(message.threadId, to, parts, sub, s.deliveryReports, sims.smsManager(sub))
        } else {
            store.deleteMessage(message)
            sendSms(message.address, message.body, sub, message.threadId, s.deliveryReports)
        }
    }

    // ================================================================ receiving picture messages

    /** A notice that a picture message waits on the network: save it, and fetch it unless that's off. */
    suspend fun onMmsNotification(notification: PduMessage, subId: Int) = withContext(Dispatchers.IO) {
        val location = notification.contentLocation ?: return@withContext
        // Carriers sometimes send the same notice twice.
        val known = context.contentResolver.query(
            MessageStore.MMS_URI, arrayOf("_id"), "ct_l = ?", arrayOf(location), null,
        )?.use { it.count > 0 } ?: false
        if (known) return@withContext
        val from = notification.from ?: str(R.string.unknown_sender)
        val thread = store.threadIdFor(listOf(from))
        val uri = MmsStore.insert(
            context,
            box = Telephony.Mms.MESSAGE_BOX_INBOX,
            threadId = thread,
            dateSeconds = System.currentTimeMillis() / 1000,
            read = false,
            subId = subId,
            messageType = MessageStore.M_TYPE_NOTIFICATION_IND,
            from = from,
            to = emptyList(),
            subject = notification.subject,
            transactionId = notification.transactionId,
            contentLocation = location,
            size = notification.size,
            expirySeconds = notification.expiry,
        ) ?: return@withContext
        chats.unarchive(thread)
        store.touch()
        if (settings.settings.value.autoDownloadMms) download(ContentUris.parseId(uri), automatic = true)
        else notifier.onIncoming(thread)
    }

    /** Fetches a waiting picture message through Android's MMS service. */
    suspend fun download(messageId: Long, automatic: Boolean = false) = withContext(Dispatchers.IO) {
        if (messageId in _downloading.value) return@withContext
        val row = context.contentResolver.query(
            MessageStore.MMS_URI, arrayOf("ct_l", "sub_id", "thread_id"), "_id = ?", arrayOf(messageId.toString()), null,
        )?.use { c -> if (c.moveToFirst()) Triple(c.getString(0), c.getInt(1), c.getLong(2)) else null } ?: return@withContext
        val location = row.first ?: return@withContext
        _downloading.value = _downloading.value + messageId
        try {
            val file = mmsFile("fetch-$messageId.pdu")
            file.delete()
            file.createNewFile()
            val contentUri = shareWithMmsService(file, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val intent = Intent(context, MmsResultReceiver::class.java)
                .setAction(ACTION_MMS_DOWNLOADED)
                .setData(ContentUris.withAppendedId(MessageStore.MMS_URI, messageId))
                .putExtra(EXTRA_FILE, file.name)
                .putExtra(EXTRA_AUTOMATIC, automatic)
                .putExtra(EXTRA_SUB, row.second)
            val done = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            sims.smsManager(row.second).downloadMultimediaMessage(context, location, contentUri, null, done)
        } catch (e: Exception) {
            _downloading.value = _downloading.value - messageId
            if (automatic) notifier.onIncoming(row.third)
            throw SendException(e.message ?: str(R.string.mms_fetch_failed))
        }
    }

    /** The MMS service fetched (or couldn't fetch) a picture message. */
    suspend fun onMmsDownloaded(intent: Intent, resultCode: Int) = withContext(Dispatchers.IO) {
        val notifUri = intent.data ?: return@withContext
        val notifId = ContentUris.parseId(notifUri)
        val automatic = intent.getBooleanExtra(EXTRA_AUTOMATIC, false)
        val subId = intent.getIntExtra(EXTRA_SUB, -1)
        val file = intent.getStringExtra(EXTRA_FILE)?.let { mmsFile(it) }
        _downloading.value = _downloading.value - notifId
        val notifThread = store.threadOf(notifUri)
        try {
            val bytes = if (resultCode == Activity.RESULT_OK) file?.takeIf { it.exists() }?.readBytes() else null
            val message = bytes?.let { PduParser(it).parse() }
            if (message == null || message.type != PduType.RETRIEVE_CONF) {
                if (automatic && notifThread != null) notifier.onIncoming(notifThread)
                store.touch()
                return@withContext
            }
            val notifFrom = store.message("m$notifId")?.address
            val sender = message.from ?: notifFrom ?: str(R.string.unknown_sender)
            val own = sims.ownNumbers()
            val others = (message.to + message.cc).filter { a -> own.none { Contacts.same(it, a) } && !Contacts.same(a, sender) }
            // One "to" is you: a one-to-one picture message. More: a group with everyone but you.
            val people = if (message.to.size + message.cc.size <= 1) listOf(sender) else (listOf(sender) + others).distinct()
            val thread = store.threadIdFor(people)
            MmsStore.insert(
                context,
                box = Telephony.Mms.MESSAGE_BOX_INBOX,
                threadId = thread,
                dateSeconds = message.date.takeIf { it > 0 } ?: (System.currentTimeMillis() / 1000),
                read = false,
                subId = subId,
                messageType = MessageStore.M_TYPE_RETRIEVE_CONF,
                from = sender,
                to = message.to,
                cc = message.cc,
                parts = message.parts,
                subject = message.subject,
                transactionId = message.transactionId,
                messageId = message.messageId,
                size = bytes?.size?.toLong() ?: 0L,
            ) ?: return@withContext
            context.contentResolver.delete(notifUri, null, null)
            chats.unarchive(thread)
            store.touch()
            notifier.onIncoming(thread)
        } finally {
            file?.delete()
        }
    }

    // ================================================================ files for the MMS service

    private fun mmsFile(name: String): File {
        val dir = File(context.cacheDir, "mms").apply { mkdirs() }
        // Only plain names from TextCtrl itself; never a path.
        return File(dir, File(name).name)
    }

    /** A content address for [file] that Android's MMS service (part of the phone app) may open. */
    private fun shareWithMmsService(file: File, flags: Int): Uri {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        for (pkg in MMS_SERVICE_PACKAGES) runCatching { context.grantUriPermission(pkg, uri, flags) }
        return uri
    }

    companion object {
        const val ACTION_SMS_SENT = "com.vknxworks.textctrl.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.vknxworks.textctrl.SMS_DELIVERED"
        const val ACTION_MMS_SENT = "com.vknxworks.textctrl.MMS_SENT"
        const val ACTION_MMS_DOWNLOADED = "com.vknxworks.textctrl.MMS_DOWNLOADED"
        const val EXTRA_URI = "uri"
        const val EXTRA_PART = "part"
        const val EXTRA_PARTS = "parts"
        const val EXTRA_FILE = "file"
        const val EXTRA_AUTOMATIC = "automatic"
        const val EXTRA_SUB = "sub"
        private const val DEFAULT_MMS_SIZE = 300 * 1024
        private val MMS_SERVICE_PACKAGES = listOf("com.android.phone", "com.android.mms.service")

        fun displayName(context: Context, uri: Uri): String =
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment ?: "file"
    }
}
