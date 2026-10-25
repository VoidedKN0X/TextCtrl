package com.vknxworks.textctrl.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Base64
import android.util.Xml
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.sms.MessageStore
import com.vknxworks.textctrl.sms.Sims
import com.vknxworks.textctrl.sms.mms.MmsCharset
import com.vknxworks.textctrl.sms.mms.MmsStore
import com.vknxworks.textctrl.sms.mms.PduPart
import com.vknxworks.textctrl.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/** What an import or export in the SMS Backup & Restore format is doing. */
sealed interface XmlState {
    data object Idle : XmlState
    /** [total] is 0 when not known yet. */
    data class Running(val importing: Boolean, val done: Int, val total: Int) : XmlState
    data class Done(val importing: Boolean, val sms: Int, val mms: Int, val skipped: Int = 0, val failed: Int = 0) : XmlState
    data class Failed(val message: String) : XmlState
}

/**
 * Import and export in the XML format of "SMS Backup & Restore", the format most Android SMS apps and
 * backup tools can read, so you can move your messages between TextCtrl and other apps both ways.
 *
 * The file is read as a stream (never all at once), so big backups with pictures work too. Messages that
 * are already on the phone (same time, same number and same text) aren't added twice. Unlike TextCtrl's
 * own backup the file is not encrypted: that's the format other apps expect.
 */
class XmlTransfer(
    private val context: Context,
    private val store: MessageStore,
    private val sims: Sims,
    private val contacts: Contacts,
) {
    private val _state = MutableStateFlow<XmlState>(XmlState.Idle)
    val state: StateFlow<XmlState> = _state.asStateFlow()

    fun reset() {
        if (_state.value !is XmlState.Running) _state.value = XmlState.Idle
    }

    // ================================================================ import

    suspend fun import(source: Uri) = withContext(Dispatchers.IO) {
        _state.value = XmlState.Running(importing = true, done = 0, total = 0)
        var sms = 0
        var mms = 0
        var skipped = 0
        var failed = 0
        var total = 0
        try {
            val existingSms = existingSmsKeys()
            val existingMms = existingMmsKeys()
            val own = sims.ownNumbers()
            context.contentResolver.openInputStream(source).use { raw ->
                raw ?: throw IOException(str(R.string.xml_cant_read))
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(BufferedInputStream(raw, 64 * 1024), null)
                var sawRoot = false
                var mmsRecord: XmlMms? = null
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "smses", "allsms" -> {
                                sawRoot = true
                                total = parser.attr("count")?.toIntOrNull() ?: 0
                            }
                            "sms" -> if (sawRoot) {
                                val r = readSms(parser)
                                when {
                                    r == null -> failed++
                                    smsKey(r.date, r.address, r.body) in existingSms -> skipped++
                                    runCatching { insertSms(r) }.getOrDefault(false) -> {
                                        sms++
                                        existingSms += smsKey(r.date, r.address, r.body)
                                    }
                                    else -> failed++
                                }
                            }
                            "mms" -> if (sawRoot) mmsRecord = readMmsHead(parser)
                            "part" -> mmsRecord?.parts?.let { parts -> readPart(parser)?.let { parts += it } }
                            "addr" -> mmsRecord?.addrs?.let { addrs ->
                                val a = parser.attr("address")
                                val t = parser.attr("type")?.toIntOrNull()
                                if (a != null && t != null) addrs += a to t
                            }
                        }
                    } else if (event == XmlPullParser.END_TAG && parser.name == "mms") {
                        val r = mmsRecord
                        mmsRecord = null
                        if (r != null) {
                            val key = mmsKey(r.dateSec, r.box, r.text())
                            when {
                                key in existingMms -> skipped++
                                insertMms(r, own) -> { mms++; existingMms += key }
                                else -> failed++
                            }
                        }
                    }
                    val done = sms + mms + skipped + failed
                    if (event == XmlPullParser.END_TAG && done > 0 && done % 50 == 0) {
                        currentCoroutineContext().ensureActive()
                        _state.value = XmlState.Running(true, done, total)
                    }
                    event = parser.next()
                }
                if (!sawRoot) throw IOException(str(R.string.xml_not_backup))
            }
            store.touch()
            _state.value = XmlState.Done(importing = true, sms = sms, mms = mms, skipped = skipped, failed = failed)
        } catch (e: Exception) {
            if (sms + mms > 0) store.touch()
            val message = when (e) {
                is org.xmlpull.v1.XmlPullParserException ->
                    if (sms + mms > 0) str(R.string.xml_damaged_partial, sms + mms) else str(R.string.xml_not_backup)
                else -> e.message ?: str(R.string.xml_failed)
            }
            _state.value = XmlState.Failed(message)
        }
    }

    private class XmlSms(
        val address: String, val body: String, val date: Long, val dateSent: Long, val type: Int,
        val read: Int, val status: Int, val locked: Int, val subject: String?, val serviceCenter: String?, val protocol: Int,
    )

    private class XmlMms(
        val dateSec: Long, val dateSentSec: Long, val box: Int, val read: Boolean, val type: Int,
        val subject: String?, val subjectCharset: Int, val messageId: String?, val transactionId: String?, val locked: Int,
        val address: String?,
        val parts: MutableList<PduPart> = ArrayList(),
        val addrs: MutableList<Pair<String, Int>> = ArrayList(),
    ) {
        fun text(): String = parts.filter { it.isText }.joinToString("\n") { it.text() }.trim()
    }

    /** An attribute, with SMS Backup & Restore's "null" read as missing. */
    private fun XmlPullParser.attr(name: String): String? =
        getAttributeValue(null, name)?.takeIf { it != "null" }

    private fun readSms(p: XmlPullParser): XmlSms? {
        val address = p.attr("address")?.takeIf { it.isNotBlank() } ?: return null
        val date = p.attr("date")?.toLongOrNull() ?: return null
        return XmlSms(
            address = address,
            body = p.attr("body").orEmpty(),
            date = date,
            dateSent = p.attr("date_sent")?.toLongOrNull() ?: 0L,
            type = p.attr("type")?.toIntOrNull() ?: Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = p.attr("read")?.toIntOrNull() ?: 1,
            status = p.attr("status")?.toIntOrNull() ?: -1,
            locked = p.attr("locked")?.toIntOrNull() ?: 0,
            subject = p.attr("subject"),
            serviceCenter = p.attr("service_center"),
            protocol = p.attr("protocol")?.toIntOrNull() ?: 0,
        )
    }

    private fun readMmsHead(p: XmlPullParser): XmlMms? {
        val raw = p.attr("date")?.toLongOrNull() ?: return null
        // The format stores milliseconds; some tools write seconds.
        val dateSec = if (raw > 100_000_000_000L) raw / 1000 else raw
        val sentRaw = p.attr("date_sent")?.toLongOrNull() ?: 0L
        return XmlMms(
            dateSec = dateSec,
            dateSentSec = if (sentRaw > 100_000_000_000L) sentRaw / 1000 else sentRaw,
            box = p.attr("msg_box")?.toIntOrNull() ?: Telephony.Mms.MESSAGE_BOX_INBOX,
            read = (p.attr("read")?.toIntOrNull() ?: 1) != 0,
            type = p.attr("m_type")?.toIntOrNull() ?: MessageStore.M_TYPE_RETRIEVE_CONF,
            subject = p.attr("sub"),
            subjectCharset = p.attr("sub_cs")?.toIntOrNull() ?: 0,
            messageId = p.attr("m_id"),
            transactionId = p.attr("tr_id"),
            locked = p.attr("locked")?.toIntOrNull() ?: 0,
            address = p.attr("address"),
        )
    }

    private fun readPart(p: XmlPullParser): PduPart? {
        val ct = p.attr("ct")?.lowercase() ?: return null
        val textual = ct == "text/plain" || ct == "application/smil"
        val data: ByteArray = when {
            textual -> (p.attr("text") ?: p.attr("data")?.let { runCatching { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull() }).orEmpty().toByteArray(Charsets.UTF_8)
            else -> p.attr("data")?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() } ?: return null
        }
        val name = p.attr("name") ?: p.attr("fn") ?: p.attr("cl")
        return PduPart(
            contentType = ct,
            data = data,
            name = name,
            charset = if (textual) MmsCharset.UTF8 else 0,
            contentId = p.attr("cid")?.trim('<', '>'),
            contentLocation = p.attr("cl"),
        )
    }

    private fun insertSms(r: XmlSms): Boolean {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, r.address)
            put(Telephony.Sms.BODY, r.body)
            put(Telephony.Sms.DATE, r.date)
            put(Telephony.Sms.DATE_SENT, r.dateSent)
            // Messages that were still going out come in as not sent, never as sending.
            put(
                Telephony.Sms.TYPE,
                when (r.type) {
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> Telephony.Sms.MESSAGE_TYPE_FAILED
                    in 1..6 -> r.type
                    else -> Telephony.Sms.MESSAGE_TYPE_INBOX
                },
            )
            put(Telephony.Sms.READ, if (r.read != 0) 1 else 0)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.STATUS, r.status)
            put(Telephony.Sms.LOCKED, if (r.locked != 0) 1 else 0)
            put(Telephony.Sms.PROTOCOL, r.protocol)
            r.subject?.let { put(Telephony.Sms.SUBJECT, it) }
            r.serviceCenter?.let { put(Telephony.Sms.SERVICE_CENTER, it) }
        }
        return context.contentResolver.insert(MessageStore.SMS_URI, values) != null
    }

    private fun insertMms(r: XmlMms, own: List<String>): Boolean = runCatching {
        val token = MessageStore.INSERT_ADDRESS_TOKEN
        val addrs = r.addrs.filter { it.first.isNotBlank() }.ifEmpty {
            // No <addrs>: use the "address" attribute ("+32…~+33…").
            r.address.orEmpty().split('~').filter { it.isNotBlank() }.map { it to if (r.box == Telephony.Mms.MESSAGE_BOX_INBOX) MessageStore.ADDR_FROM else MessageStore.ADDR_TO }
        }
        val from = addrs.firstOrNull { it.second == MessageStore.ADDR_FROM }?.first
        val to = addrs.filter { it.second == MessageStore.ADDR_TO && it.first != token }.map { it.first }
        val cc = addrs.filter { it.second == MessageStore.ADDR_CC && it.first != token }.map { it.first }
        val incoming = r.box == Telephony.Mms.MESSAGE_BOX_INBOX
        val people = if (incoming) {
            val sender = from?.takeIf { it != token } ?: return@runCatching false
            val others = (to + cc).filter { a -> own.none { Contacts.same(it, a) } && !Contacts.same(a, sender) }
            if (to.size + cc.size <= 1) listOf(sender) else (listOf(sender) + others).distinct()
        } else {
            (to + cc).filter { a -> own.none { Contacts.same(it, a) } }.ifEmpty { to + cc }.distinct()
        }
        if (people.isEmpty() || r.parts.isEmpty()) return@runCatching false
        val subject = r.subject?.let { MessageStore.decodeSnippet(it, r.subjectCharset) }
        val uri = MmsStore.insert(
            context,
            box = when (r.box) {
                Telephony.Mms.MESSAGE_BOX_INBOX, Telephony.Mms.MESSAGE_BOX_SENT, Telephony.Mms.MESSAGE_BOX_DRAFTS -> r.box
                else -> MessageStore.MESSAGE_BOX_FAILED
            },
            threadId = store.threadIdFor(people),
            dateSeconds = r.dateSec,
            read = r.read,
            subId = -1,
            messageType = if (r.type == MessageStore.M_TYPE_NOTIFICATION_IND) MessageStore.M_TYPE_RETRIEVE_CONF else r.type,
            from = if (incoming) from else token,
            to = to,
            cc = cc,
            parts = r.parts,
            subject = subject,
            transactionId = r.transactionId,
            messageId = r.messageId,
        ) ?: return@runCatching false
        if (r.locked != 0) runCatching { context.contentResolver.update(uri, ContentValues().apply { put(Telephony.Mms.LOCKED, 1) }, null, null) }
        true
    }.getOrDefault(false)

    // ---------------------------------------------------------------- duplicates

    private fun digits(address: String) = address.filter { it.isDigit() }.takeLast(9).ifEmpty { address.lowercase() }

    private fun smsKey(date: Long, address: String, body: String) = "$date|${digits(address)}|${body.hashCode()}"

    private fun mmsKey(dateSec: Long, box: Int, text: String) = "$dateSec|$box|${text.hashCode()}"

    private fun existingSmsKeys(): HashSet<String> {
        val keys = HashSet<String>()
        context.contentResolver.query(MessageStore.SMS_URI, arrayOf("date", "address", "body"), null, null, null)?.use { c ->
            while (c.moveToNext()) keys += smsKey(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
        }
        return keys
    }

    private fun existingMmsKeys(): HashSet<String> {
        val texts = HashMap<Long, StringBuilder>()
        context.contentResolver.query(MessageStore.PART_URI, arrayOf("mid", "text"), "ct = 'text/plain'", null, "seq ASC, _id ASC")?.use { c ->
            while (c.moveToNext()) {
                val sb = texts.getOrPut(c.getLong(0)) { StringBuilder() }
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(c.getString(1).orEmpty())
            }
        }
        val keys = HashSet<String>()
        context.contentResolver.query(MessageStore.MMS_URI, arrayOf("_id", "date", "msg_box"), null, null, null)?.use { c ->
            while (c.moveToNext()) keys += mmsKey(c.getLong(1), c.getInt(2), texts[c.getLong(0)]?.toString().orEmpty().trim())
        }
        return keys
    }

    // ================================================================ export

    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        _state.value = XmlState.Running(importing = false, done = 0, total = 0)
        val cr = context.contentResolver
        var sms = 0
        var mms = 0
        try {
            val smsCount = cr.query(MessageStore.SMS_URI, arrayOf("_id"), "type != 3", null, null)?.use { it.count } ?: 0
            val mmsCount = cr.query(MessageStore.MMS_URI, arrayOf("_id"), "m_type IN (128, 132)", null, null)?.use { it.count } ?: 0
            val total = smsCount + mmsCount
            val names = HashMap<String, String>()
            fun nameOf(address: String): String = names.getOrPut(address) {
                if (contacts.granted) contacts.lookup(address).name ?: UNKNOWN_NAME else UNKNOWN_NAME
            }
            val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
            cr.openOutputStream(target, "wt").use { raw ->
                raw ?: throw IOException(str(R.string.xml_cant_write))
                val out = BufferedOutputStream(raw, 64 * 1024)
                val x = Xml.newSerializer()
                x.setOutput(out, "UTF-8")
                x.startDocument("UTF-8", true)
                x.startTag(null, "smses")
                x.attribute(null, "count", total.toString())
                x.attribute(null, "backup_set", java.util.UUID.randomUUID().toString())
                x.attribute(null, "backup_date", System.currentTimeMillis().toString())
                x.attribute(null, "type", "full")
                x.text("\n")

                cr.query(
                    MessageStore.SMS_URI,
                    arrayOf("address", "date", "date_sent", "type", "subject", "body", "service_center", "read", "status", "locked", "protocol", "sub_id"),
                    "type != 3", null, "date ASC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val address = c.getString(0).orEmpty()
                        val date = c.getLong(1)
                        x.startTag(null, "sms")
                        x.attribute(null, "protocol", c.getInt(10).toString())
                        x.attribute(null, "address", clean(address))
                        x.attribute(null, "date", date.toString())
                        x.attribute(null, "type", c.getInt(3).toString())
                        x.attribute(null, "subject", cleanOrNull(c.getString(4)) ?: "null")
                        x.attribute(null, "body", clean(c.getString(5).orEmpty()))
                        x.attribute(null, "toa", "null")
                        x.attribute(null, "sc_toa", "null")
                        x.attribute(null, "service_center", cleanOrNull(c.getString(6)) ?: "null")
                        x.attribute(null, "read", c.getInt(7).toString())
                        x.attribute(null, "status", c.getInt(8).toString())
                        x.attribute(null, "locked", c.getInt(9).toString())
                        x.attribute(null, "date_sent", c.getLong(2).toString())
                        x.attribute(null, "sub_id", c.getInt(11).toString())
                        x.attribute(null, "readable_date", dateFormat.format(Date(date)))
                        x.attribute(null, "contact_name", clean(nameOf(address)))
                        x.endTag(null, "sms")
                        x.text("\n")
                        sms++
                        if (sms % 200 == 0) {
                            currentCoroutineContext().ensureActive()
                            _state.value = XmlState.Running(false, sms, total)
                        }
                    }
                }

                cr.query(
                    MessageStore.MMS_URI,
                    arrayOf("_id", "date", "date_sent", "msg_box", "read", "seen", "m_type", "sub", "sub_cs", "m_id", "tr_id", "ct_t", "m_size", "text_only", "locked", "sub_id", "m_cls", "d_rpt", "rr", "pri", "v"),
                    "m_type IN (128, 132)", null, "date ASC",
                )?.use { c ->
                    fun s(i: Int): String = if (c.isNull(i)) "null" else cleanOrNull(c.getString(i)) ?: "null"
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val addrs = ArrayList<Triple<String, Int, Int>>()
                        cr.query(Uri.parse("content://mms/$id/addr"), arrayOf("address", "type", "charset"), null, null, null)?.use { a ->
                            while (a.moveToNext()) addrs += Triple(a.getString(0).orEmpty(), a.getInt(1), a.getInt(2))
                        }
                        val box = c.getInt(3)
                        val people = addrs.map { it.first }.filter { it.isNotBlank() && it != MessageStore.INSERT_ADDRESS_TOKEN }.distinct()
                        val dateMs = c.getLong(1) * 1000
                        x.startTag(null, "mms")
                        x.attribute(null, "date", dateMs.toString())
                        x.attribute(null, "date_sent", c.getLong(2).toString())
                        x.attribute(null, "msg_box", box.toString())
                        x.attribute(null, "read", c.getInt(4).toString())
                        x.attribute(null, "seen", c.getInt(5).toString())
                        x.attribute(null, "m_type", c.getInt(6).toString())
                        x.attribute(null, "sub", s(7))
                        x.attribute(null, "sub_cs", s(8))
                        x.attribute(null, "m_id", s(9))
                        x.attribute(null, "tr_id", s(10))
                        x.attribute(null, "ct_t", s(11))
                        x.attribute(null, "m_size", s(12))
                        x.attribute(null, "text_only", c.getInt(13).toString())
                        x.attribute(null, "locked", c.getInt(14).toString())
                        x.attribute(null, "sub_id", c.getInt(15).toString())
                        x.attribute(null, "m_cls", s(16))
                        x.attribute(null, "d_rpt", s(17))
                        x.attribute(null, "rr", s(18))
                        x.attribute(null, "pri", s(19))
                        x.attribute(null, "v", s(20))
                        x.attribute(null, "ct_l", "null")
                        x.attribute(null, "exp", "null")
                        x.attribute(null, "address", clean(people.joinToString("~")))
                        x.attribute(null, "readable_date", dateFormat.format(Date(dateMs)))
                        x.attribute(null, "contact_name", clean(people.joinToString(", ") { nameOf(it) }))
                        x.startTag(null, "parts")
                        cr.query(
                            MessageStore.PART_URI,
                            arrayOf("_id", "seq", "ct", "name", "chset", "cd", "fn", "cid", "cl", "text"),
                            "mid = ?", arrayOf(id.toString()), "seq ASC, _id ASC",
                        )?.use { p ->
                            fun ps(i: Int): String = if (p.isNull(i)) "null" else cleanOrNull(p.getString(i)) ?: "null"
                            while (p.moveToNext()) {
                                val ct = p.getString(2).orEmpty()
                                val textual = ct.equals("text/plain", true) || ct.equals("application/smil", true)
                                x.startTag(null, "part")
                                x.attribute(null, "seq", p.getInt(1).toString())
                                x.attribute(null, "ct", clean(ct))
                                x.attribute(null, "name", ps(3))
                                x.attribute(null, "chset", ps(4))
                                x.attribute(null, "cd", ps(5))
                                x.attribute(null, "fn", ps(6))
                                x.attribute(null, "cid", ps(7))
                                x.attribute(null, "cl", ps(8))
                                x.attribute(null, "ctt_s", "null")
                                x.attribute(null, "ctt_t", "null")
                                if (textual) {
                                    x.attribute(null, "text", ps(9))
                                } else {
                                    x.attribute(null, "text", "null")
                                    val bytes = cr.openInputStream(Uri.parse("content://mms/part/${p.getLong(0)}"))?.use { it.readBytes() }
                                    x.attribute(null, "data", if (bytes != null) Base64.encodeToString(bytes, Base64.NO_WRAP) else "null")
                                }
                                x.endTag(null, "part")
                            }
                        }
                        x.endTag(null, "parts")
                        x.startTag(null, "addrs")
                        addrs.forEach { (address, type, charset) ->
                            x.startTag(null, "addr")
                            x.attribute(null, "address", clean(address))
                            x.attribute(null, "type", type.toString())
                            x.attribute(null, "charset", (if (charset == 0) MmsCharset.UTF8 else charset).toString())
                            x.endTag(null, "addr")
                        }
                        x.endTag(null, "addrs")
                        x.endTag(null, "mms")
                        x.text("\n")
                        mms++
                        if (mms % 25 == 0) {
                            currentCoroutineContext().ensureActive()
                            _state.value = XmlState.Running(false, sms + mms, total)
                        }
                    }
                }
                x.endTag(null, "smses")
                x.endDocument()
                out.flush()
            }
            _state.value = XmlState.Done(importing = false, sms = sms, mms = mms)
        } catch (e: Exception) {
            runCatching { android.provider.DocumentsContract.deleteDocument(cr, target) }
            _state.value = XmlState.Failed(e.message ?: str(R.string.xml_failed))
        }
    }

    /** Characters XML 1.0 can't hold at all (control characters some texts contain) are dropped. */
    private fun cleanOrNull(s: String?): String? = s?.filter { ch ->
        ch == '\t' || ch == '\n' || ch == '\r' || (ch.code >= 0x20 && ch.code != 0xFFFE && ch.code != 0xFFFF)
    }

    private fun clean(s: String?): String = cleanOrNull(s).orEmpty()

    private companion object {
        const val UNKNOWN_NAME = "(Unknown)"
    }
}
