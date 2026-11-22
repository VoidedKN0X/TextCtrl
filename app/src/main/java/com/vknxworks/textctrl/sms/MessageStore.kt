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
import android.util.LruCache
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
    fun touch() {
        // Messages were just written or restored: read their addresses again instead of trusting what was kept.
        mmsAddresses.evictAll()
        _changes.value++
    }

    // ---------------------------------------------------------------- chats

    suspend fun conversations(): List<Conversation> = withContext(Dispatchers.IO) {
        val addresses = canonicalAddresses()
        val unread = unreadCounts()
        class Row(val id: Long, val date: Long, val recipients: List<String>, val snippet: String, val hasAttachment: Boolean, val hasError: Boolean)
        val rows = ArrayList<Row>()
        val counts = HashMap<Long, Long>()
        cr.query(
            THREADS_URI,
            arrayOf("_id", "date", "recipient_ids", "snippet", "snippet_cs", "error", "has_attachment", "message_count"),
            null, null, "date DESC",
        )?.use { c ->
            // Where each column is, looked up once instead of for every field of every row.
            val iCount = c.idx("message_count")
            val iId = c.idx("_id")
            val iDate = c.idx("date")
            val iRecipients = c.idx("recipient_ids")
            val iSnippet = c.idx("snippet")
            val iSnippetCs = c.idx("snippet_cs")
            val iAttachment = c.idx("has_attachment")
            val iError = c.idx("error")
            while (c.moveToNext()) {
                val count = c.longAt(iCount)
                if (count <= 0L) continue
                val id = c.longAt(iId)
                counts[id] = count
                rows += Row(
                    id = id,
                    date = c.longAt(iDate),
                    recipients = c.strAt(iRecipients).split(' ').mapNotNull { it.toLongOrNull()?.let(addresses::get) },
                    snippet = decodeSnippet(c.strAt(iSnippet), c.intAt(iSnippetCs)),
                    hasAttachment = c.intAt(iAttachment) != 0,
                    hasError = c.intAt(iError) != 0,
                )
            }
        }
        val outgoing = threadsWithOutgoing(counts)
        rows.map { r ->
            Conversation(
                threadId = r.id,
                addresses = r.recipients,
                snippet = r.snippet,
                date = r.date,
                unread = unread[r.id] ?: 0,
                hasAttachment = r.hasAttachment,
                hasError = r.hasError,
                hasOutgoing = r.id in outgoing,
            )
        }
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
            val iId = c.idx("_id")
            val iAddress = c.idx("address")
            while (c.moveToNext()) map[c.longAt(iId)] = c.strAt(iAddress)
        }
        return map
    }

    // The chats you've sent something in. Reading this from scratch means going through every message
    // you ever sent, so the answer is kept and only messages newer than the last one seen are added.
    // It is read from scratch again whenever something may have been removed or changed underneath:
    // after TextCtrl deletes anything, when a chat has fewer messages than last time (or is gone), and
    // always while another app is the SMS app (only the SMS app can change stored messages).
    private val outgoingLock = Any()
    private var outgoingThreads: HashSet<Long>? = null
    private var outgoingSmsMax = 0L
    private var outgoingMmsMax = 0L
    private var lastCounts: Map<Long, Long> = emptyMap()

    /** Forget which chats have sent messages; the next chat list reads it from scratch. */
    private fun forgetOutgoing() {
        synchronized(outgoingLock) { outgoingThreads = null }
        mmsAddresses.evictAll()
    }

    private fun isDefaultSmsApp(): Boolean = runCatching {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }.getOrDefault(false)

    /** [counts] is the number of messages per chat right now, to notice messages that went away. */
    private fun threadsWithOutgoing(counts: Map<Long, Long>): Set<Long> = synchronized(outgoingLock) {
        val shrunk = lastCounts.any { (id, n) -> (counts[id] ?: 0L) < n }
        lastCounts = counts
        val kept = outgoingThreads
        if (kept != null && !shrunk && isDefaultSmsApp()) {
            // Anything sent since last time has a higher id than every message seen before: an id is only
            // used again after a delete, and a delete starts this from scratch.
            cr.query(SMS_URI, arrayOf("thread_id", "_id"), "type IN (2, 4, 5, 6) AND _id > $outgoingSmsMax", null, null)?.use { c ->
                while (c.moveToNext()) {
                    kept += c.getLong(0)
                    val id = c.getLong(1)
                    if (id > outgoingSmsMax) outgoingSmsMax = id
                }
            }
            cr.query(MMS_URI, arrayOf("thread_id", "_id"), "msg_box IN (2, 4, 5) AND _id > $outgoingMmsMax", null, null)?.use { c ->
                while (c.moveToNext()) {
                    kept += c.getLong(0)
                    val id = c.getLong(1)
                    if (id > outgoingMmsMax) outgoingMmsMax = id
                }
            }
            return@synchronized HashSet(kept)
        }
        // From scratch, exactly as before.
        outgoingThreads = null
        val ids = HashSet<Long>()
        var smsMax = 0L
        var mmsMax = 0L
        var complete = true
        val sms = cr.query(SMS_URI, arrayOf("thread_id", "_id"), "type IN (2, 4, 5, 6)", null, null)
        if (sms == null) complete = false
        sms?.use { c ->
            while (c.moveToNext()) {
                ids += c.getLong(0)
                val id = c.getLong(1)
                if (id > smsMax) smsMax = id
            }
        }
        val mms = cr.query(MMS_URI, arrayOf("thread_id", "_id"), "msg_box IN (2, 4, 5)", null, null)
        if (mms == null) complete = false
        mms?.use { c ->
            while (c.moveToNext()) {
                ids += c.getLong(0)
                val id = c.getLong(1)
                if (id > mmsMax) mmsMax = id
            }
        }
        // Only kept when both lists were read in full and TextCtrl is the SMS app.
        if (complete && isDefaultSmsApp()) {
            outgoingThreads = HashSet(ids)
            outgoingSmsMax = smsMax
            outgoingMmsMax = mmsMax
        }
        ids
    }

    /** Whether you've ever sent something in [threadId]. */
    suspend fun hasOutgoing(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        val args = arrayOf(threadId.toString())
        val sms = cr.query(SMS_URI, arrayOf("_id"), "thread_id = ? AND type IN (2, 4, 5, 6)", args, "date DESC LIMIT 1")?.use { it.count > 0 } ?: false
        sms || (cr.query(MMS_URI, arrayOf("_id"), "thread_id = ? AND msg_box IN (2, 4, 5)", args, "date DESC LIMIT 1")?.use { it.count > 0 } ?: false)
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
        )?.use { c ->
            val cols = SmsColumns(c)
            while (c.moveToNext()) out += smsFrom(c, cols)
        }
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
        )?.use { c ->
            val cols = SmsColumns(c)
            while (c.moveToNext()) out += smsFrom(c, cols)
        }
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
            )?.use { c -> if (c.moveToFirst()) smsFrom(c, SmsColumns(c)) else null }
        }
    }

    /** Where the columns of a text are in a cursor, looked up once per query instead of once per field of every row. */
    private inner class SmsColumns(c: Cursor) {
        val id = c.idx("_id")
        val thread = c.idx("thread_id")
        val address = c.idx("address")
        val body = c.idx("body")
        val date = c.idx("date")
        val type = c.idx("type")
        val status = c.idx("status")
        val read = c.idx("read")
        val subId = c.idx("sub_id")
    }

    private fun smsFrom(c: Cursor, k: SmsColumns): Message {
        val type = c.intAt(k.type)
        val status = c.intAt(k.status)
        return Message(
            id = c.longAt(k.id),
            isMms = false,
            threadId = c.longAt(k.thread),
            address = c.strAt(k.address),
            body = c.strAt(k.body),
            date = c.longAt(k.date),
            incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = c.intAt(k.read) != 0,
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
            subId = c.intAt(k.subId),
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
            val iId = c.idx("_id")
            val iThread = c.idx("thread_id")
            val iDate = c.idx("date")
            val iBox = c.idx("msg_box")
            val iRead = c.idx("read")
            val iType = c.idx("m_type")
            val iSub = c.idx("sub")
            val iSubCs = c.idx("sub_cs")
            val iSubId = c.idx("sub_id")
            val iSize = c.idx("m_size")
            val iExp = c.idx("exp")
            while (c.moveToNext()) {
                rows += Row(
                    c.longAt(iId), c.longAt(iThread), c.longAt(iDate), c.intAt(iBox), c.intAt(iRead) != 0,
                    c.intAt(iType), c.strOrNullAt(iSub)?.let { decodeSnippet(it, c.intAt(iSubCs)) }?.takeIf { it.isNotBlank() },
                    c.intAt(iSubId), c.longAt(iSize), c.longAt(iExp),
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
                address = mmsAddress(r.id, incoming, r.dateSec * 1000),
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
                val iId = c.idx("_id")
                val iMid = c.idx("mid")
                val iCt = c.idx("ct")
                val iName = c.idx("name")
                val iCl = c.idx("cl")
                val iFn = c.idx("fn")
                val iText = c.idx("text")
                while (c.moveToNext()) {
                    val part = MmsPart(
                        id = c.longAt(iId),
                        contentType = c.strAt(iCt).lowercase(),
                        name = c.strOrNullAt(iName) ?: c.strOrNullAt(iFn) ?: c.strOrNullAt(iCl),
                        text = c.strOrNullAt(iText),
                    )
                    out.getOrPut(c.longAt(iMid)) { ArrayList() } += part
                }
            }
        }
        return out
    }

    // Who a stored picture message is from or to doesn't change, and asking costs one query per message,
    // so answers are kept. Not for messages of the last few minutes (their addresses may still be being
    // written) and never an empty answer; forgotten whenever TextCtrl writes, restores or deletes messages.
    private val mmsAddresses = LruCache<Long, String>(1000)

    /** The sender of an incoming picture message; for a sent one, its recipients. [date] is the message's, in ms. */
    private fun mmsAddress(id: Long, incoming: Boolean, date: Long): String {
        // Sent and received messages have ids of their own, but keep the two kinds apart anyway.
        val key = if (incoming) id else -id
        mmsAddresses.get(key)?.let { return it }
        val type = if (incoming) ADDR_FROM else ADDR_TO
        val address = cr.query(Uri.parse("content://mms/$id/addr"), arrayOf("address"), "type = $type", null, null)?.use { c ->
            buildList { while (c.moveToNext()) c.getString(0)?.let { if (it != INSERT_ADDRESS_TOKEN) add(it) } }
        }.orEmpty().joinToString(", ")
        val settled = date > 0 && System.currentTimeMillis() - date > MMS_ADDRESS_SETTLE_MS
        if (settled && address.isNotEmpty()) mmsAddresses.put(key, address)
        return address
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
            // Only the first 200 picture messages are used below, so reading stops once they are found.
            while (mids.size < 200 && c.moveToNext()) mids.putIfAbsent(c.getLong(0), c.getString(1).orEmpty())
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
                    val date = c.long("date") * 1000
                    hits += SearchHit(c.long("thread_id"), "m$id", mmsAddress(id, incoming, date), mids[id].orEmpty(), date, incoming)
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
        try {
            cr.delete(ContentUris.withAppendedId(THREADS_DELETE_URI, threadId), null, null)
        } finally {
            forgetOutgoing()
        }
        Unit
    }

    /**
     * Auto-delete: messages in [threadId] older than [cutoff] (ms), except ones marked as kept and the
     * starred ones ([keepKeys]: message keys, "s12" for a text and "m7" for a picture message).
     */
    fun deleteOlderThan(threadId: Long, cutoff: Long, keepKeys: Collection<String> = emptyList()): Int {
        val args = arrayOf(threadId.toString())
        val keepSms = keepKeys.filter { it.startsWith("s") }.mapNotNull { it.drop(1).toLongOrNull() }
        val keepMms = keepKeys.filter { it.startsWith("m") }.mapNotNull { it.drop(1).toLongOrNull() }
        fun except(ids: List<Long>) = if (ids.isEmpty()) "" else " AND _id NOT IN (" + ids.joinToString(",") + ")"
        var n = runCatching { cr.delete(SMS_URI, "thread_id = ? AND date < $cutoff AND locked = 0" + except(keepSms), args) }.getOrDefault(0)
        n += runCatching { cr.delete(MMS_URI, "thread_id = ? AND date < ${cutoff / 1000} AND locked = 0" + except(keepMms), args) }.getOrDefault(0)
        forgetOutgoing()
        return n
    }

    /**
     * Deletes received texts older than [cutoff] (ms) that hold a one-time code ([isCode] decides), except
     * ones marked as kept and the starred ones ([keepKeys]). Only texts: picture messages are never codes.
     * Returns how many were deleted.
     */
    fun deleteCodesOlderThan(cutoff: Long, keepKeys: Collection<String>, from: Long = 0L, isCode: (String) -> Boolean): Int {
        val keep = keepKeys.filter { it.startsWith("s") }.mapNotNull { it.drop(1).toLongOrNull() }.toSet()
        val ids = ArrayList<Long>()
        // [from]: texts older than that were already checked by an earlier run.
        val lower = if (from > 0L) " AND date >= $from" else ""
        runCatching {
            cr.query(SMS_URI, arrayOf("_id", "body"), "type = 1 AND date < $cutoff$lower AND locked = 0", null, null)?.use { c ->
                val idAt = c.idx("_id")
                val bodyAt = c.idx("body")
                while (c.moveToNext()) {
                    val id = c.longAt(idAt)
                    if (id in keep) continue
                    val body = c.strAt(bodyAt)
                    // One-time code texts are short; skip the long ones without looking closer.
                    if (body.isEmpty() || body.length > 400) continue
                    if (runCatching { isCode(body) }.getOrDefault(false)) ids += id
                }
            }
        }
        var n = 0
        for (chunk in ids.chunked(200)) {
            n += runCatching { cr.delete(SMS_URI, "type = 1 AND locked = 0 AND _id IN (" + chunk.joinToString(",") + ")", null) }.getOrDefault(0)
        }
        return n
    }

    suspend fun deleteMessage(message: Message) = withContext(Dispatchers.IO) {
        try {
            cr.delete(message.uri, null, null)
        } finally {
            forgetOutgoing()
        }
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
    private fun Cursor.long(name: String): Long = longAt(idx(name))
    private fun Cursor.int(name: String): Int = intAt(idx(name))
    private fun Cursor.str(name: String): String = strOrNull(name).orEmpty()
    private fun Cursor.strOrNull(name: String): String? = strOrNullAt(idx(name))

    // The same reads by column position ([idx]), for loops over many rows. A missing column or an empty
    // field reads as 0, "" or null, as above.
    private fun Cursor.longAt(i: Int): Long = if (i < 0 || isNull(i)) 0L else getLong(i)
    private fun Cursor.intAt(i: Int): Int = if (i < 0 || isNull(i)) 0 else getInt(i)
    private fun Cursor.strAt(i: Int): String = strOrNullAt(i).orEmpty()
    private fun Cursor.strOrNullAt(i: Int): String? = if (i < 0 || isNull(i)) null else getString(i)

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
        private const val MMS_ADDRESS_SETTLE_MS = 5 * 60_000L

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
