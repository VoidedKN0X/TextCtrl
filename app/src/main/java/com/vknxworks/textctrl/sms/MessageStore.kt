package com.vknxworks.textctrl.sms

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Reads and writes Android's own message store (the Telephony provider), where every SMS app keeps
 * texts. As the default SMS app, TextCtrl is the one app allowed to write to it.
 */
class MessageStore(private val context: Context) {
    private val cr: ContentResolver = context.contentResolver

    private val _changes = MutableStateFlow(0)
    /** Goes up whenever a message or chat changes, from TextCtrl or anything else. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    init {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { _changes.value++ }
        }
        for (uri in listOf(SMS_URI, MMS_URI, MMS_SMS_URI)) {
            runCatching { cr.registerContentObserver(uri, true, observer) }
        }
    }

    /** Pokes everything that shows messages, after a change the provider may not announce. */
    fun touch() { _changes.value++ }

    // ---------------------------------------------------------------- chats

    suspend fun conversations(): List<Conversation> = withContext(Dispatchers.IO) {
        val addresses = canonicalAddresses()
        val unread = unreadCounts()
        val out = ArrayList<Conversation>()
        cr.query(
            THREADS_URI,
            arrayOf("_id", "date", "recipient_ids", "snippet", "snippet_cs", "error", "has_attachment", "message_count"),
            null, null, "date DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.long("message_count") <= 0L) continue
                val id = c.long("_id")
                val recipients = c.str("recipient_ids").split(' ').mapNotNull { it.toLongOrNull()?.let(addresses::get) }
                out += Conversation(
                    threadId = id,
                    addresses = recipients,
                    snippet = decodeSnippet(c.str("snippet"), c.int("snippet_cs")),
                    date = c.long("date"),
                    unread = unread[id] ?: 0,
                    hasAttachment = c.int("has_attachment") != 0,
                    hasError = c.int("error") != 0,
                )
            }
        }
        out
    }

    /** The people in a chat. */
    suspend fun threadAddresses(threadId: Long): List<String> = withContext(Dispatchers.IO) {
        val addresses = canonicalAddresses()
        cr.query(THREADS_URI, arrayOf("_id", "recipient_ids"), "_id = ?", arrayOf(threadId.toString()), null)?.use { c ->
            if (c.moveToFirst()) c.str("recipient_ids").split(' ').mapNotNull { it.toLongOrNull()?.let(addresses::get) } else null
        } ?: emptyList()
    }

    /** The chat with exactly these people, made if it doesn't exist yet. */
    fun threadIdFor(addresses: Collection<String>): Long =
        Telephony.Threads.getOrCreateThreadId(context, addresses.toSet())

    private fun canonicalAddresses(): Map<Long, String> {
        val map = HashMap<Long, String>()
        cr.query(CANONICAL_URI, arrayOf("_id", "address"), null, null, null)?.use { c ->
            while (c.moveToNext()) map[c.long("_id")] = c.str("address")
        }
        return map
    }

    private fun unreadCounts(): Map<Long, Int> {
        val counts = HashMap<Long, Int>()
        cr.query(SMS_URI, arrayOf("thread_id"), "read = 0 AND type = 1", null, null)?.use { c ->
            while (c.moveToNext()) counts.merge(c.getLong(0), 1, Int::plus)
        }
        cr.query(MMS_URI, arrayOf("thread_id"), "read = 0 AND msg_box = 1", null, null)?.use { c ->
            while (c.moveToNext()) counts.merge(c.getLong(0), 1, Int::plus)
        }
        return counts
    }

    // ---------------------------------------------------------------- messages

    /** The newest [limit] texts and picture messages of a chat, oldest first. */
    suspend fun messages(threadId: Long, limit: Int = 300): List<Message> = withContext(Dispatchers.IO) {
        val out = ArrayList<Message>()
        cr.query(
            SMS_URI,
            arrayOf("_id", "thread_id", "address", "body", "date", "type", "status", "read", "sub_id"),
            "thread_id = ? AND type != 3", arrayOf(threadId.toString()), "date DESC LIMIT $limit",
        )?.use { c -> while (c.moveToNext()) out += smsFrom(c) }
        out += mmsMessages("thread_id = ? AND m_type IN (128, 130, 132)", arrayOf(threadId.toString()), limit)
        out.sortedBy { it.date }.takeLast(limit)
    }

    /** Unread incoming messages of a chat, oldest first, for its notification. */
    suspend fun unreadIncoming(threadId: Long): List<Message> = withContext(Dispatchers.IO) {
        val out = ArrayList<Message>()
        cr.query(
            SMS_URI,
            arrayOf("_id", "thread_id", "address", "body", "date", "type", "status", "read", "sub_id"),
            "thread_id = ? AND type = 1 AND read = 0", arrayOf(threadId.toString()), "date DESC LIMIT 20",
        )?.use { c -> while (c.moveToNext()) out += smsFrom(c) }
        out += mmsMessages("thread_id = ? AND msg_box = 1 AND read = 0 AND m_type IN (130, 132)", arrayOf(threadId.toString()), 20)
        out.sortedBy { it.date }
    }

    suspend fun message(key: String): Message? = withContext(Dispatchers.IO) {
        val id = key.drop(1).toLongOrNull() ?: return@withContext null
        if (key.startsWith("m")) {
            mmsMessages("_id = ?", arrayOf(id.toString()), 1).firstOrNull()
        } else {
            cr.query(
                SMS_URI,
                arrayOf("_id", "thread_id", "address", "body", "date", "type", "status", "read", "sub_id"),
                "_id = ?", arrayOf(id.toString()), null,
            )?.use { c -> if (c.moveToFirst()) smsFrom(c) else null }
        }
    }

    private fun smsFrom(c: Cursor): Message {
        val type = c.int("type")
        val status = c.int("status")
        return Message(
            id = c.long("_id"),
            isMms = false,
            threadId = c.long("thread_id"),
            address = c.str("address"),
            body = c.str("body"),
            date = c.long("date"),
            incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = c.int("read") != 0,
            status = when (type) {
                Telephony.Sms.MESSAGE_TYPE_INBOX -> SendStatus.NONE
                Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> SendStatus.SENDING
                Telephony.Sms.MESSAGE_TYPE_FAILED -> SendStatus.FAILED
                else -> when {
                    status == Telephony.Sms.STATUS_COMPLETE -> SendStatus.DELIVERED
                    status >= Telephony.Sms.STATUS_FAILED -> SendStatus.NOT_DELIVERED
                    else -> SendStatus.SENT
                }
            },
            subId = c.int("sub_id"),
        )
    }

    private fun mmsMessages(selection: String, args: Array<String>, limit: Int): List<Message> {
        data class Row(
            val id: Long, val thread: Long, val dateSec: Long, val box: Int, val read: Boolean, val type: Int,
            val subject: String?, val subId: Int, val size: Long, val expiry: Long,
        )
        val rows = ArrayList<Row>()
        cr.query(
            MMS_URI,
            arrayOf("_id", "thread_id", "date", "msg_box", "read", "m_type", "sub", "sub_cs", "sub_id", "m_size", "exp"),
            selection, args, "date DESC LIMIT $limit",
        )?.use { c ->
            while (c.moveToNext()) {
                rows += Row(
                    c.long("_id"), c.long("thread_id"), c.long("date"), c.int("msg_box"), c.int("read") != 0,
                    c.int("m_type"), c.strOrNull("sub")?.let { decodeSnippet(it, c.int("sub_cs")) }?.takeIf { it.isNotBlank() },
                    c.int("sub_id"), c.long("m_size"), c.long("exp"),
                )
            }
        }
        if (rows.isEmpty()) return emptyList()
        val parts = partsOf(rows.map { it.id })
        return rows.map { r ->
            val p = parts[r.id].orEmpty()
            val incoming = r.box == Telephony.Mms.MESSAGE_BOX_INBOX
            Message(
                id = r.id,
                isMms = true,
                threadId = r.thread,
                address = mmsAddress(r.id, incoming),
                body = p.filter { it.isText }.mapNotNull { it.text }.joinToString("\n").trim(),
                date = r.dateSec * 1000,
                incoming = incoming,
                read = r.read,
                status = when (r.box) {
                    Telephony.Mms.MESSAGE_BOX_INBOX -> SendStatus.NONE
                    Telephony.Mms.MESSAGE_BOX_OUTBOX -> SendStatus.SENDING
                    MESSAGE_BOX_FAILED -> SendStatus.FAILED
                    else -> SendStatus.SENT
                },
                subId = r.subId,
                subject = r.subject,
                parts = p,
                notDownloaded = r.type == M_TYPE_NOTIFICATION_IND,
                mmsSize = r.size,
                mmsExpiry = r.expiry * 1000,
            )
        }
    }

    private fun partsOf(ids: List<Long>): Map<Long, List<MmsPart>> {
        val out = HashMap<Long, MutableList<MmsPart>>()
        ids.chunked(200).forEach { chunk ->
            cr.query(
                PART_URI,
                arrayOf("_id", "mid", "ct", "name", "cl", "fn", "text"),
                "mid IN (${chunk.joinToString(",")})", null, "seq ASC, _id ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val part = MmsPart(
                        id = c.long("_id"),
                        contentType = c.str("ct").lowercase(),
                        name = c.strOrNull("name") ?: c.strOrNull("fn") ?: c.strOrNull("cl"),
                        text = c.strOrNull("text"),
                    )
                    out.getOrPut(c.long("mid")) { ArrayList() } += part
                }
            }
        }
        return out
    }

    /** The sender of an incoming picture message; for a sent one, its recipients. */
    private fun mmsAddress(id: Long, incoming: Boolean): String {
        val type = if (incoming) ADDR_FROM else ADDR_TO
        return cr.query(Uri.parse("content://mms/$id/addr"), arrayOf("address"), "type = $type", null, null)?.use { c ->
            buildList { while (c.moveToNext()) c.getString(0)?.let { if (it != INSERT_ADDRESS_TOKEN) add(it) } }
        }.orEmpty().joinToString(", ")
    }

    // ---------------------------------------------------------------- search

    suspend fun search(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val hits = ArrayList<SearchHit>()
        cr.query(
            SMS_URI,
            arrayOf("_id", "thread_id", "address", "body", "date", "type"),
            "body LIKE ? ESCAPE '\\' AND type != 3", arrayOf(like), "date DESC LIMIT 200",
        )?.use { c ->
            while (c.moveToNext()) {
                hits += SearchHit(c.long("thread_id"), "s" + c.long("_id"), c.str("address"), c.str("body"), c.long("date"), c.int("type") == 1)
            }
        }
        val mids = LinkedHashMap<Long, String>()
        cr.query(PART_URI, arrayOf("mid", "text"), "ct = 'text/plain' AND text LIKE ? ESCAPE '\\'", arrayOf(like), null)?.use { c ->
            while (c.moveToNext()) mids.putIfAbsent(c.getLong(0), c.getString(1).orEmpty())
        }
        if (mids.isNotEmpty()) {
            cr.query(
                MMS_URI,
                arrayOf("_id", "thread_id", "date", "msg_box"),
                "_id IN (${mids.keys.take(200).joinToString(",")})", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.long("_id")
                    val incoming = c.int("msg_box") == Telephony.Mms.MESSAGE_BOX_INBOX
                    hits += SearchHit(c.long("thread_id"), "m$id", mmsAddress(id, incoming), mids[id].orEmpty(), c.long("date") * 1000, incoming)
                }
            }
        }
        hits.sortedByDescending { it.date }
    }

    // ---------------------------------------------------------------- changes

    suspend fun markRead(threadId: Long) = withContext(Dispatchers.IO) {
        val read = ContentValues().apply { put("read", 1); put("seen", 1) }
        runCatching { cr.update(SMS_URI, read, "thread_id = ? AND (read = 0 OR seen = 0)", arrayOf(threadId.toString())) }
        runCatching { cr.update(MMS_URI, read, "thread_id = ? AND (read = 0 OR seen = 0)", arrayOf(threadId.toString())) }
        Unit
    }

    suspend fun markAllRead() = withContext(Dispatchers.IO) {
        val read = ContentValues().apply { put("read", 1); put("seen", 1) }
        runCatching { cr.update(SMS_URI, read, "read = 0 OR seen = 0", null) }
        runCatching { cr.update(MMS_URI, read, "read = 0 OR seen = 0", null) }
        Unit
    }

    suspend fun markUnread(threadId: Long) = withContext(Dispatchers.IO) {
        // The newest incoming message becomes unread again.
        val newest = cr.query(SMS_URI, arrayOf("_id"), "thread_id = ? AND type = 1", arrayOf(threadId.toString()), "date DESC LIMIT 1")
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        if (newest != null) {
            cr.update(ContentUris.withAppendedId(SMS_URI, newest), ContentValues().apply { put("read", 0) }, null, null)
        }
        Unit
    }

    suspend fun deleteThread(threadId: Long) = withContext(Dispatchers.IO) {
        cr.delete(ContentUris.withAppendedId(THREADS_DELETE_URI, threadId), null, null)
        Unit
    }

    suspend fun deleteMessage(message: Message) = withContext(Dispatchers.IO) {
        cr.delete(message.uri, null, null)
        Unit
    }

    // ---------------------------------------------------------------- writing texts

    fun insertIncomingSms(
        address: String,
        body: String,
        sentAt: Long,
        subId: Int,
        protocol: Int,
        serviceCenter: String?,
        replyPath: Boolean,
    ): Uri? {
        val threadId = runCatching { threadIdFor(listOf(address)) }.getOrNull()
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, sentAt)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.SUBSCRIPTION_ID, subId)
            put(Telephony.Sms.PROTOCOL, protocol)
            put(Telephony.Sms.REPLY_PATH_PRESENT, if (replyPath) 1 else 0)
            serviceCenter?.let { put(Telephony.Sms.SERVICE_CENTER, it) }
            threadId?.let { put(Telephony.Sms.THREAD_ID, it) }
        }
        return cr.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
    }

    fun insertOutgoingSms(address: String, body: String, subId: Int, threadId: Long, deliveryReport: Boolean): Uri? {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, now)
            put(Telephony.Sms.DATE_SENT, now)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, if (deliveryReport) Telephony.Sms.STATUS_PENDING else Telephony.Sms.STATUS_NONE)
            put(Telephony.Sms.SUBSCRIPTION_ID, subId)
            put(Telephony.Sms.THREAD_ID, threadId)
        }
        return cr.insert(SMS_URI, values)
    }

    fun setSmsType(uri: Uri, type: Int, errorCode: Int = 0) {
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, type)
            if (errorCode != 0) put(Telephony.Sms.ERROR_CODE, errorCode)
        }
        runCatching { cr.update(uri, values, null, null) }
    }

    fun smsType(uri: Uri): Int? =
        cr.query(uri, arrayOf(Telephony.Sms.TYPE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getInt(0) else null }

    fun setSmsStatus(uri: Uri, status: Int) {
        runCatching { cr.update(uri, ContentValues().apply { put(Telephony.Sms.STATUS, status) }, null, null) }
    }

    fun setMmsBox(uri: Uri, box: Int, messageId: String? = null) {
        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_BOX, box)
            messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
        }
        runCatching { cr.update(uri, values, null, null) }
    }

    fun threadOf(uri: Uri): Long? =
        runCatching {
            cr.query(uri, arrayOf("thread_id"), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        }.getOrNull()

    // ---------------------------------------------------------------- helpers

    private fun Cursor.idx(name: String) = getColumnIndex(name)
    private fun Cursor.long(name: String): Long = idx(name).let { if (it < 0 || isNull(it)) 0L else getLong(it) }
    private fun Cursor.int(name: String): Int = idx(name).let { if (it < 0 || isNull(it)) 0 else getInt(it) }
    private fun Cursor.str(name: String): String = strOrNull(name).orEmpty()
    private fun Cursor.strOrNull(name: String): String? = idx(name).let { if (it < 0 || isNull(it)) null else getString(it) }

    companion object {
        val SMS_URI: Uri = Telephony.Sms.CONTENT_URI
        val MMS_URI: Uri = Telephony.Mms.CONTENT_URI
        val MMS_SMS_URI: Uri = Uri.parse("content://mms-sms/")
        val THREADS_URI: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val THREADS_DELETE_URI: Uri = Uri.parse("content://mms-sms/conversations")
        val CANONICAL_URI: Uri = Uri.parse("content://mms-sms/canonical-addresses")
        val PART_URI: Uri = Uri.parse("content://mms/part")

        const val MESSAGE_BOX_FAILED = 5
        const val M_TYPE_SEND_REQ = 128
        const val M_TYPE_NOTIFICATION_IND = 130
        const val M_TYPE_RETRIEVE_CONF = 132
        const val ADDR_FROM = 137
        const val ADDR_TO = 151
        const val ADDR_CC = 130
        const val INSERT_ADDRESS_TOKEN = "insert-address-token"

        /**
         * Picture message subjects (and the chat snippets made from them) are stored as their raw bytes
         * read as ISO-8859-1, with the real character set next to them.
         */
        fun decodeSnippet(raw: String, charset: Int): String {
            if (charset == 0 || raw.isEmpty() || raw.any { it.code > 0xFF }) return raw
            val bytes = raw.toByteArray(Charsets.ISO_8859_1)
            val decoded = com.vknxworks.textctrl.sms.mms.MmsCharset.decode(bytes, charset)
            return if (decoded.contains('�')) raw else decoded
        }

        /** The other way round, for subjects TextCtrl stores. */
        fun encodeSubject(subject: String): String = String(subject.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
    }
}
