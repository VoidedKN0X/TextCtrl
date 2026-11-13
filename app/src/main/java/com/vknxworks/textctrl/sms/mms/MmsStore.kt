package com.vknxworks.textctrl.sms.mms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.vknxworks.textctrl.sms.MessageStore

/** Writes picture messages and their parts into Android's message store. */
object MmsStore {

    /**
     * Saves a picture message (or the notice that one is waiting) and returns where it was stored.
     * [from] is the sender; for messages you send it's [MessageStore.INSERT_ADDRESS_TOKEN].
     */
    fun insert(
        context: Context,
        box: Int,
        threadId: Long,
        dateSeconds: Long,
        read: Boolean,
        subId: Int,
        messageType: Int,
        from: String?,
        to: List<String>,
        cc: List<String> = emptyList(),
        parts: List<PduPart> = emptyList(),
        subject: String? = null,
        transactionId: String? = null,
        messageId: String? = null,
        contentLocation: String? = null,
        size: Long = 0,
        expirySeconds: Long = 0,
    ): Uri? {
        val cr = context.contentResolver
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, dateSeconds)
            put(Telephony.Mms.DATE_SENT, dateSeconds)
            put(Telephony.Mms.MESSAGE_BOX, box)
            put(Telephony.Mms.READ, if (read) 1 else 0)
            put(Telephony.Mms.SEEN, if (read) 1 else 0)
            put(Telephony.Mms.MESSAGE_TYPE, messageType)
            put(Telephony.Mms.MMS_VERSION, 0x12)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            put(Telephony.Mms.MESSAGE_CLASS, "personal")
            put(Telephony.Mms.PRIORITY, 129)
            put(Telephony.Mms.DELIVERY_REPORT, 129)
            put(Telephony.Mms.READ_REPORT, 129)
            put(Telephony.Mms.SUBSCRIPTION_ID, subId)
            put(Telephony.Mms.TEXT_ONLY, if (parts.all { it.isText || it.isSmil }) 1 else 0)
            put(Telephony.Mms.MESSAGE_SIZE, if (size > 0) size else parts.sumOf { it.data.size }.toLong())
            if (!subject.isNullOrBlank()) {
                put(Telephony.Mms.SUBJECT, MessageStore.encodeSubject(subject))
                put(Telephony.Mms.SUBJECT_CHARSET, MmsCharset.UTF8)
            }
            transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
            messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            contentLocation?.let { put(Telephony.Mms.CONTENT_LOCATION, it) }
            if (expirySeconds > 0) put(Telephony.Mms.EXPIRY, expirySeconds)
        }
        val target = when (box) {
            Telephony.Mms.MESSAGE_BOX_INBOX -> Telephony.Mms.Inbox.CONTENT_URI
            Telephony.Mms.MESSAGE_BOX_SENT -> Telephony.Mms.Sent.CONTENT_URI
            else -> Telephony.Mms.Outbox.CONTENT_URI
        }
        val uri = cr.insert(target, values) ?: return null
        val id = ContentUris.parseId(uri)
        try {
            parts.forEachIndexed { i, part -> insertPart(context, id, i, part) }
            from?.let { insertAddress(context, id, it, MessageStore.ADDR_FROM) }
            to.forEach { insertAddress(context, id, it, MessageStore.ADDR_TO) }
            cc.forEach { insertAddress(context, id, it, MessageStore.ADDR_CC) }
        } catch (e: Exception) {
            // Half a message is worse than none.
            runCatching { cr.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    private fun insertPart(context: Context, messageId: Long, seq: Int, part: PduPart) {
        val cr = context.contentResolver
        val textual = part.isText || part.isSmil
        val values = ContentValues().apply {
            put(Telephony.Mms.Part.MSG_ID, messageId)
            put(Telephony.Mms.Part.SEQ, if (part.isSmil) -1 else seq)
            put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
            part.contentId?.let { put(Telephony.Mms.Part.CONTENT_ID, "<$it>") }
            (part.contentLocation ?: part.name)?.let { put(Telephony.Mms.Part.CONTENT_LOCATION, it) }
            part.name?.let { put(Telephony.Mms.Part.NAME, it); put(Telephony.Mms.Part.FILENAME, it) }
            if (textual) {
                put(Telephony.Mms.Part.CHARSET, MmsCharset.UTF8)
                put(Telephony.Mms.Part.TEXT, part.text())
            }
        }
        val partUri = cr.insert(Uri.parse("content://mms/$messageId/part"), values) ?: error("Couldn't save part")
        if (!textual) {
            cr.openOutputStream(partUri, "w")?.use { it.write(part.data) } ?: error("Couldn't save attachment")
        }
    }

    private fun insertAddress(context: Context, messageId: Long, address: String, type: Int) {
        val values = ContentValues().apply {
            put(Telephony.Mms.Addr.MSG_ID, messageId)
            put(Telephony.Mms.Addr.ADDRESS, address)
            put(Telephony.Mms.Addr.TYPE, type)
            put(Telephony.Mms.Addr.CHARSET, MmsCharset.UTF8)
        }
        context.contentResolver.insert(Uri.parse("content://mms/$messageId/addr"), values)
    }

    /** The parts of a stored picture message with their bytes, for sending it again. */
    fun readParts(context: Context, messageId: Long): List<PduPart> {
        val cr = context.contentResolver
        val out = ArrayList<PduPart>()
        cr.query(
            Uri.parse("content://mms/part"),
            arrayOf("_id", "ct", "name", "cl", "cid", "text"),
            "mid = ?", arrayOf(messageId.toString()), "seq ASC, _id ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val partId = c.getLong(0)
                val ct = c.getString(1).orEmpty()
                if (ct.equals("application/smil", ignoreCase = true)) continue
                val textual = ct.equals("text/plain", ignoreCase = true)
                val data = if (textual) c.getString(5).orEmpty().toByteArray(Charsets.UTF_8)
                else cr.openInputStream(Uri.parse("content://mms/part/$partId"))?.use { it.readBytes() } ?: continue
                out += PduPart(
                    contentType = ct,
                    data = data,
                    name = c.getString(2) ?: c.getString(3),
                    charset = if (textual) MmsCharset.UTF8 else 0,
                    contentId = c.getString(4)?.trim('<', '>'),
                    contentLocation = c.getString(3),
                )
            }
        }
        return out
    }
}
