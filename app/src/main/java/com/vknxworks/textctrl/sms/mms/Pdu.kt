package com.vknxworks.textctrl.sms.mms

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/*
 * A small reader and writer for MMS messages in their wire format (OMA MMS Encapsulation, with WAP
 * WSP header encoding). Android's own MMS service sends and fetches the bytes over the mobile network;
 * TextCtrl only turns them into messages and back. Written from the specifications, with no outside code.
 */

/** X-Mms-Message-Type values. */
object PduType {
    const val SEND_REQ = 0x80
    const val SEND_CONF = 0x81
    const val NOTIFICATION_IND = 0x82
    const val NOTIFYRESP_IND = 0x83
    const val RETRIEVE_CONF = 0x84
    const val ACKNOWLEDGE_IND = 0x85
    const val DELIVERY_IND = 0x86
}

/** One part of a picture message: some text, a picture, a video, a contact card … */
class PduPart(
    val contentType: String,
    val data: ByteArray,
    val name: String? = null,
    /** IANA MIBenum of the text's character set, 0 when not given. */
    val charset: Int = 0,
    val contentId: String? = null,
    val contentLocation: String? = null,
) {
    val isText: Boolean get() = contentType.equals("text/plain", ignoreCase = true)
    val isSmil: Boolean get() = contentType.equals("application/smil", ignoreCase = true)

    /** The part as text, for text and SMIL parts. */
    fun text(): String = MmsCharset.decode(data, if (charset == 0) MmsCharset.UTF8 else charset)
}

class PduMessage(
    val type: Int,
    val transactionId: String? = null,
    val from: String? = null,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val subject: String? = null,
    /** Seconds since 1970, 0 when not given. */
    val date: Long = 0,
    val messageId: String? = null,
    /** Where to fetch the message (notification) . */
    val contentLocation: String? = null,
    val size: Long = 0,
    /** Seconds since 1970, 0 when not given. */
    val expiry: Long = 0,
    val contentType: String? = null,
    val parts: List<PduPart> = emptyList(),
    val status: Int = 0,
    val responseStatus: Int = 0,
)

/** MIBenum character sets used in MMS. */
object MmsCharset {
    const val US_ASCII = 3
    const val ISO_8859_1 = 4
    const val UTF8 = 106
    const val UCS2 = 1000
    const val UTF16 = 1015

    private val names = mapOf(
        US_ASCII to "US-ASCII",
        ISO_8859_1 to "ISO-8859-1",
        UTF8 to "UTF-8",
        UCS2 to "UTF-16BE",
        UTF16 to "UTF-16",
        17 to "Shift_JIS",
        18 to "EUC-JP",
        38 to "EUC-KR",
        113 to "GBK",
        114 to "GB18030",
        2025 to "GB2312",
        2026 to "Big5",
        5 to "ISO-8859-2",
        6 to "ISO-8859-3",
        7 to "ISO-8859-4",
        8 to "ISO-8859-5",
        9 to "ISO-8859-6",
        10 to "ISO-8859-7",
        11 to "ISO-8859-8",
        12 to "ISO-8859-9",
        2252 to "windows-1252",
    )

    fun decode(bytes: ByteArray, mib: Int): String {
        val name = names[mib] ?: "UTF-8"
        return runCatching { String(bytes, Charset.forName(name)) }.getOrElse { String(bytes, kotlin.text.Charsets.UTF_8) }
    }

    fun fromName(name: String): Int {
        val n = name.trim().lowercase()
        return names.entries.firstOrNull { it.value.lowercase() == n }?.key ?: when (n) {
            "utf8" -> UTF8
            "ascii" -> US_ASCII
            "latin1" -> ISO_8859_1
            else -> UTF8
        }
    }
}

/** WSP's numbered content types (WAP-230 Appendix A), indexed by their number. */
private val WELL_KNOWN_TYPES = listOf(
    "*/*", "text/*", "text/html", "text/plain", "text/x-hdml", "text/x-ttml", "text/x-vCalendar",
    "text/x-vCard", "text/vnd.wap.wml", "text/vnd.wap.wmlscript", "text/vnd.wap.wta-event", "multipart/*",
    "multipart/mixed", "multipart/form-data", "multipart/byterantes", "multipart/alternative", "application/*",
    "application/java-vm", "application/x-www-form-urlencoded", "application/x-hdmlc", "application/vnd.wap.wmlc",
    "application/vnd.wap.wmlscriptc", "application/vnd.wap.wta-eventc", "application/vnd.wap.uaprof",
    "application/vnd.wap.wtls-ca-certificate", "application/vnd.wap.wtls-user-certificate",
    "application/x-x509-ca-cert", "application/x-x509-user-cert", "image/*", "image/gif", "image/jpeg",
    "image/tiff", "image/png", "image/vnd.wap.wbmp", "application/vnd.wap.multipart.*",
    "application/vnd.wap.multipart.mixed", "application/vnd.wap.multipart.form-data",
    "application/vnd.wap.multipart.byteranges", "application/vnd.wap.multipart.alternative", "application/xml",
    "text/xml", "application/vnd.wap.wbxml", "application/x-x968-cross-cert", "application/x-x968-ca-cert",
    "application/x-x968-user-cert", "text/vnd.wap.si", "application/vnd.wap.sic", "text/vnd.wap.sl",
    "application/vnd.wap.slc", "text/vnd.wap.co", "application/vnd.wap.coc", "application/vnd.wap.multipart.related",
    "application/vnd.wap.sia", "text/vnd.wap.connectivity-xml", "application/vnd.wap.connectivity-wbxml",
    "application/pkcs7-mime", "application/vnd.wap.hashed-certificate", "application/vnd.wap.signed-certificate",
    "application/vnd.wap.cert-response", "application/xhtml+xml", "application/wml+xml", "text/css",
    "application/vnd.wap.mms-message", "application/vnd.wap.rollover-certificate", "application/vnd.wap.locc+wbxml",
    "application/vnd.wap.loc+xml", "application/vnd.syncml.dm+wbxml", "application/vnd.syncml.dm+xml",
    "application/vnd.syncml.notification", "application/vnd.wap.xhtml+xml", "application/vnd.wv.csp.cir",
    "application/vnd.oma.dd+xml", "application/vnd.oma.drm.message", "application/vnd.oma.drm.content",
    "application/vnd.oma.drm.rights+xml", "application/vnd.oma.drm.rights+wbxml",
)

private fun wellKnownType(n: Int): String = WELL_KNOWN_TYPES.getOrNull(n) ?: "application/octet-stream"

private fun wellKnownIndex(mime: String): Int = WELL_KNOWN_TYPES.indexOfFirst { it.equals(mime, ignoreCase = true) }

/** Strips the "/TYPE=PLMN" suffix MMS puts on phone numbers. */
fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE=").substringBefore("/type=").trim()

// ==================================================================== reading

private class ContentTypeInfo(val mime: String, val params: Map<String, String>)

/** Reads a received picture message, or the notice that one is waiting. Returns null for anything unreadable. */
class PduParser(private val b: ByteArray) {
    private var p = 0

    fun parse(): PduMessage? = runCatching { parseMessage() }.getOrNull()

    private fun parseMessage(): PduMessage {
        var type = 0
        var transactionId: String? = null
        var from: String? = null
        val to = ArrayList<String>()
        val cc = ArrayList<String>()
        var subject: String? = null
        var date = 0L
        var messageId: String? = null
        var contentLocation: String? = null
        var size = 0L
        var expiry = 0L
        var status = 0
        var responseStatus = 0
        var ctInfo: ContentTypeInfo? = null

        headers@ while (p < b.size) {
            val h = peek()
            when {
                h >= 0x80 -> {
                    p++
                    when (h and 0x7F) {
                        0x0C -> type = u8()
                        0x18 -> transactionId = textString()
                        0x0B -> messageId = textString()
                        0x03 -> contentLocation = textString()
                        0x09 -> {
                            val len = valueLength()
                            val end = p + len
                            if (len > 0 && u8() == 0x80) from = cleanAddress(encodedString())
                            p = end
                        }
                        0x17 -> to += cleanAddress(encodedString())
                        0x02 -> cc += cleanAddress(encodedString())
                        0x01 -> encodedString() // Bcc: not kept
                        0x16 -> subject = encodedString()
                        0x05 -> date = longInteger()
                        0x0E -> size = longInteger()
                        0x08 -> {
                            val len = valueLength()
                            val end = p + len
                            val token = u8()
                            val value = longInteger()
                            expiry = if (token == 0x80) value else System.currentTimeMillis() / 1000 + value
                            p = end
                        }
                        0x15 -> status = u8()
                        0x12 -> responseStatus = u8()
                        0x04 -> {
                            ctInfo = contentType()
                            break@headers // the body follows Content-Type
                        }
                        else -> skipValue()
                    }
                }
                h >= 32 -> { textString(); textString() } // an application header: name, value
                else -> p++
            }
        }

        val ct = ctInfo
        val parts = when {
            ct == null || p >= b.size -> emptyList()
            ct.mime.contains("multipart", ignoreCase = true) -> multipart()
            else -> listOf(
                PduPart(ct.mime, b.copyOfRange(p, b.size), ct.params["name"] ?: ct.params["filename"], ct.params["charset"]?.toIntOrNull() ?: 0),
            )
        }
        return PduMessage(
            type = type,
            transactionId = transactionId,
            from = from,
            to = to,
            cc = cc,
            subject = subject?.takeIf { it.isNotBlank() },
            date = date,
            messageId = messageId,
            contentLocation = contentLocation,
            size = size,
            expiry = expiry,
            contentType = ct?.mime,
            parts = parts,
            status = status,
            responseStatus = responseStatus,
        )
    }

    private fun multipart(): List<PduPart> {
        val count = uintvar().toInt()
        val parts = ArrayList<PduPart>(count)
        repeat(count) {
            if (p >= b.size) return parts
            val headersLen = uintvar().toInt()
            val dataLen = uintvar().toInt()
            val headersEnd = p + headersLen
            val ct = contentType()
            var name = ct.params["name"] ?: ct.params["filename"]
            var contentId: String? = null
            var contentLocation: String? = null
            while (p < headersEnd) {
                val h = peek()
                when {
                    h >= 0x80 -> {
                        p++
                        when (h and 0x7F) {
                            0x0E -> contentLocation = textString()
                            0x40 -> contentId = textString().trim('<', '>')
                            0x2E, 0x45 -> {
                                val len = valueLength()
                                val end = p + len
                                if (len > 0) {
                                    if (peek() >= 0x80) p++ else textString()
                                    val params = HashMap<String, String>()
                                    while (p < end) parameter(params)
                                    name = name ?: params["filename"] ?: params["name"]
                                }
                                p = end
                            }
                            else -> skipValue()
                        }
                    }
                    h >= 32 -> { textString(); textString() }
                    else -> p++
                }
            }
            p = headersEnd
            val end = (p + dataLen).coerceAtMost(b.size)
            val data = b.copyOfRange(p, end)
            p = end
            parts += PduPart(
                contentType = ct.mime.lowercase(),
                data = data,
                name = name ?: contentLocation,
                charset = ct.params["charset"]?.toIntOrNull() ?: 0,
                contentId = contentId,
                contentLocation = contentLocation,
            )
        }
        return parts
    }

    private fun contentType(): ContentTypeInfo {
        val c = peek()
        if (c >= 0x80) {
            p++
            return ContentTypeInfo(wellKnownType(c and 0x7F), emptyMap())
        }
        if (c >= 32) return ContentTypeInfo(textString(), emptyMap())
        val len = valueLength()
        val end = p + len
        val first = peek()
        val mime = when {
            first >= 0x80 -> { p++; wellKnownType(first and 0x7F) }
            first < 31 -> wellKnownType(longInteger().toInt())
            else -> textString()
        }
        val params = HashMap<String, String>()
        while (p < end) parameter(params)
        p = end
        return ContentTypeInfo(mime, params)
    }

    private fun parameter(out: MutableMap<String, String>) {
        val t = peek()
        val key = if (t >= 0x80) {
            p++
            when (t and 0x7F) {
                0x01 -> "charset"
                0x03, 0x09 -> "type"
                0x05, 0x17 -> "name"
                0x06, 0x18 -> "filename"
                0x0A, 0x19 -> "start"
                else -> "p" + (t and 0x7F)
            }
        } else {
            textString().lowercase()
        }
        val v = peek()
        out[key] = when {
            key == "charset" && v in 32..127 -> MmsCharset.fromName(textString()).toString()
            key == "charset" && v == 0x80 -> { p++; "0" } // Any-charset
            v >= 0x80 -> {
                p++
                if (key == "type") wellKnownType(v and 0x7F) else (v and 0x7F).toString()
            }
            v in 1..30 -> longInteger().toString()
            v == 0 -> { p++; "" }
            else -> textString()
        }
    }

    // ---------------------------------------------------------------- primitives

    private fun peek(): Int = b[p].toInt() and 0xFF
    private fun u8(): Int = b[p++].toInt() and 0xFF

    private fun uintvar(): Long {
        var result = 0L
        for (i in 0 until 5) {
            val c = u8()
            result = (result shl 7) or (c and 0x7F).toLong()
            if (c and 0x80 == 0) return result
        }
        error("uintvar too long")
    }

    private fun valueLength(): Int {
        val c = u8()
        return when {
            c < 31 -> c
            c == 31 -> uintvar().toInt()
            else -> error("not a value length")
        }
    }

    private fun textBytes(): ByteArray {
        if (p < b.size && (peek() == 0x7F || peek() == 0x22)) p++ // quote
        val start = p
        while (p < b.size && b[p].toInt() != 0) p++
        val out = b.copyOfRange(start, p)
        if (p < b.size) p++ // the terminating 0
        return out
    }

    private fun textString(): String = String(textBytes(), kotlin.text.Charsets.UTF_8)

    private fun longInteger(): Long {
        val n = u8()
        require(n in 1..8)
        var r = 0L
        repeat(n) { r = (r shl 8) or u8().toLong() }
        return r
    }

    private fun integerValue(): Long {
        val c = peek()
        return if (c >= 0x80) { p++; (c and 0x7F).toLong() } else longInteger()
    }

    /** Text, possibly with its character set in front. */
    private fun encodedString(): String {
        val c = peek()
        if (c <= 31) {
            val len = valueLength()
            val end = p + len
            val charset = integerValue().toInt()
            val bytes = textBytes()
            p = end
            return MmsCharset.decode(bytes, charset)
        }
        return MmsCharset.decode(textBytes(), MmsCharset.UTF8)
    }

    /** Skips a header value of any shape, so unknown headers don't stop the reading. */
    private fun skipValue() {
        val c = peek()
        when {
            c < 31 -> { p++; p += c }
            c == 31 -> { p++; p += uintvar().toInt() }
            c < 128 -> textBytes()
            else -> p++
        }
    }
}

// ==================================================================== writing

object PduComposer {

    /** A picture message to send (M-Send.req): text, pictures and other files to one or more people. */
    fun sendRequest(
        transactionId: String,
        to: List<String>,
        subject: String?,
        parts: List<PduPart>,
        deliveryReport: Boolean,
    ): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(0x8C); o.write(PduType.SEND_REQ)
        o.write(0x98); text(o, transactionId)
        o.write(0x8D); o.write(0x92) // MMS 1.2
        o.write(0x85); longInteger(o, System.currentTimeMillis() / 1000)
        o.write(0x89); o.write(0x01); o.write(0x81) // From: filled in by the network
        for (a in to) { o.write(0x97); text(o, toPduAddress(a)) }
        if (!subject.isNullOrBlank()) { o.write(0x96); encodedUtf8(o, subject) }
        o.write(0x8A); o.write(0x80) // Message class: personal
        o.write(0x8F); o.write(0x81) // Priority: normal
        o.write(0x86); o.write(if (deliveryReport) 0x80 else 0x81)
        o.write(0x90); o.write(0x81) // Read report: no
        o.write(0x84); o.write(0x80 or wellKnownIndex("application/vnd.wap.multipart.mixed"))

        uintvar(o, parts.size.toLong())
        for (part in parts) {
            val headers = ByteArrayOutputStream()
            contentType(headers, part)
            part.contentLocation?.let { headers.write(0x8E); text(headers, it) }
            part.contentId?.let {
                headers.write(0xC0)
                headers.write(0x22) // quoted string
                headers.write("<$it>".toByteArray(kotlin.text.Charsets.UTF_8))
                headers.write(0)
            }
            uintvar(o, headers.size().toLong())
            uintvar(o, part.data.size.toLong())
            o.write(headers.toByteArray())
            o.write(part.data)
        }
        return o.toByteArray()
    }

    private fun contentType(o: ByteArrayOutputStream, part: PduPart) {
        val inner = ByteArrayOutputStream()
        val idx = wellKnownIndex(part.contentType)
        if (idx >= 0) inner.write(0x80 or idx) else text(inner, part.contentType)
        if (part.contentType.startsWith("text/") || part.isSmil) {
            inner.write(0x81) // charset
            inner.write(0x80 or MmsCharset.UTF8) // 106 fits a short integer
        }
        part.name?.let { inner.write(0x85); text(inner, it) }
        valueLength(o, inner.size())
        o.write(inner.toByteArray())
    }

    private fun encodedUtf8(o: ByteArrayOutputStream, s: String) {
        val inner = ByteArrayOutputStream()
        inner.write(0x80 or MmsCharset.UTF8)
        text(inner, s)
        valueLength(o, inner.size())
        o.write(inner.toByteArray())
    }

    private fun text(o: ByteArrayOutputStream, s: String) {
        val bytes = s.replace("\u0000", "").toByteArray(kotlin.text.Charsets.UTF_8)
        if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFF) >= 0x80) o.write(0x7F)
        o.write(bytes)
        o.write(0)
    }

    private fun valueLength(o: ByteArrayOutputStream, n: Int) {
        if (n < 31) o.write(n) else { o.write(31); uintvar(o, n.toLong()) }
    }

    private fun longInteger(o: ByteArrayOutputStream, v: Long) {
        var bytes = 0
        var t = v
        while (t != 0L) { bytes++; t = t ushr 8 }
        if (bytes == 0) bytes = 1
        o.write(bytes)
        for (i in bytes - 1 downTo 0) o.write(((v ushr (8 * i)) and 0xFF).toInt())
    }

    private fun uintvar(o: ByteArrayOutputStream, v: Long) {
        var groups = 1
        while (groups < 5 && (v ushr (7 * groups)) != 0L) groups++
        for (i in groups - 1 downTo 0) {
            val bits = ((v ushr (7 * i)) and 0x7F).toInt()
            o.write(if (i > 0) bits or 0x80 else bits)
        }
    }

    /** Phone numbers get "/TYPE=PLMN"; email addresses are sent as they are. */
    private fun toPduAddress(a: String): String {
        if (a.contains('@')) return a
        val digits = a.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        return "${digits.ifEmpty { a }}/TYPE=PLMN"
    }
}
