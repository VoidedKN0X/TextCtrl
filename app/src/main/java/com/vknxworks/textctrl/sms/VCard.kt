package com.vknxworks.textctrl.sms

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

/** A phone number or email address on a contact card, with its vCard type ("CELL", "WORK,FAX" …). */
data class VCardValue(val value: String, val type: String? = null)

data class VCardAddress(
    val street: String = "",
    val city: String = "",
    val region: String = "",
    val postcode: String = "",
    val country: String = "",
    val type: String? = null,
) {
    val oneLine: String get() = listOf(street, listOf(postcode, city).filter { it.isNotBlank() }.joinToString(" "), region, country)
        .filter { it.isNotBlank() }.joinToString(", ")
}

/** One person from a contact card (vCard). */
data class VCardContact(
    val name: String,
    val given: String? = null,
    val family: String? = null,
    val middle: String? = null,
    val prefix: String? = null,
    val suffix: String? = null,
    val phones: List<VCardValue> = emptyList(),
    val emails: List<VCardValue> = emptyList(),
    val organization: String? = null,
    val title: String? = null,
    val addresses: List<VCardAddress> = emptyList(),
    val urls: List<String> = emptyList(),
)

/**
 * Contact cards (vCard 3.0, text/x-vcard) to send in picture messages, and reading the ones people send.
 * Written from RFC 2426 (and the older 2.1 cards many phones still send); no outside code.
 *
 * Only names, numbers, email addresses, company, addresses and websites go on a card you send: never the
 * contact's photo, notes, birthday or anything else.
 */
object VCard {
    const val MIME = "text/x-vcard"
    private const val MAX_BYTES = 256 * 1024

    // ================================================================ reading a picked contact

    /**
     * The contact picked in the phone's contact picker. With the contacts permission [picked] is a whole
     * contact and every number is read; without it, it's the one phone number picked (the picker grants
     * access to just that row).
     */
    fun read(context: Context, picked: Uri, wholeContact: Boolean): VCardContact? = runCatching {
        if (wholeContact) readContact(context, picked) else readPhoneRow(context, picked)
    }.getOrNull()

    private fun readPhoneRow(context: Context, uri: Uri): VCardContact? =
        context.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            val number = c.getString(1)?.takeIf { it.isNotBlank() } ?: return null
            VCardContact(
                name = c.getString(0)?.takeIf { it.isNotBlank() } ?: number,
                phones = listOf(VCardValue(number, phoneTypeName(c.getInt(2)))),
            )
        }

    private fun readContact(context: Context, uri: Uri): VCardContact? {
        val cr = context.contentResolver
        val (id, displayName) = cr.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) to c.getString(1).orEmpty() else null
        } ?: return null
        var card = VCardContact(name = displayName)
        val phones = ArrayList<VCardValue>()
        val emails = ArrayList<VCardValue>()
        val addresses = ArrayList<VCardAddress>()
        val urls = ArrayList<String>()
        val columns = arrayOf(ContactsContract.Data.MIMETYPE) + (1..10).map { "data$it" }
        cr.query(ContactsContract.Data.CONTENT_URI, columns, "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(id.toString()), null)?.use { c ->
            fun d(n: Int): String? = c.getString(n)?.takeIf { it.isNotBlank() }
            while (c.moveToNext()) {
                when (c.getString(0)) {
                    StructuredName.CONTENT_ITEM_TYPE -> card = card.copy(
                        name = d(1) ?: card.name, given = d(2), family = d(3), prefix = d(4), middle = d(5), suffix = d(6),
                    )
                    Phone.CONTENT_ITEM_TYPE -> d(1)?.let { n -> if (phones.none { it.value == n }) phones += VCardValue(n, phoneTypeName(c.getInt(2))) }
                    Email.CONTENT_ITEM_TYPE -> d(1)?.let { e -> emails += VCardValue(e, emailTypeName(c.getInt(2))) }
                    Organization.CONTENT_ITEM_TYPE -> card = card.copy(organization = d(1) ?: card.organization, title = d(4) ?: card.title)
                    StructuredPostal.CONTENT_ITEM_TYPE -> {
                        val a = VCardAddress(
                            street = d(4).orEmpty(), city = d(7).orEmpty(), region = d(8).orEmpty(),
                            postcode = d(9).orEmpty(), country = d(10).orEmpty(), type = postalTypeName(c.getInt(2)),
                        )
                        addresses += if (a.oneLine.isBlank()) VCardAddress(street = d(1).orEmpty(), type = a.type) else a
                    }
                    Website.CONTENT_ITEM_TYPE -> d(1)?.let { urls += it }
                }
            }
        }
        if (card.name.isBlank()) card = card.copy(name = card.organization ?: phones.firstOrNull()?.value ?: emails.firstOrNull()?.value ?: return null)
        return card.copy(phones = phones, emails = emails, addresses = addresses.filter { it.oneLine.isNotBlank() }, urls = urls)
    }

    /** Saves [card] as a .vcf file in TextCtrl's cache, ready to attach. */
    fun toAttachment(context: Context, card: VCardContact): Attachment {
        val dir = File(context.cacheDir, "vcards").apply { mkdirs() }
        // Cards from earlier days are no longer needed (scheduled messages keep their own copy).
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
        val base = card.name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(40).ifBlank { "contact" }
        var file = File(dir, "$base.vcf")
        var n = 2
        while (file.exists()) file = File(dir, "${base}_${n++}.vcf")
        file.writeText(build(card), Charsets.UTF_8)
        return Attachment(Uri.fromFile(file), MIME, file.name)
    }

    // ================================================================ writing

    fun build(card: VCardContact): String {
        val out = StringBuilder()
        fun line(s: String) = fold(s, out)
        line("BEGIN:VCARD")
        line("VERSION:3.0")
        line("N:" + listOf(card.family, card.given, card.middle, card.prefix, card.suffix).joinToString(";") { escape(it.orEmpty()) })
        line("FN:" + escape(card.name))
        card.organization?.let { line("ORG:" + escape(it)) }
        card.title?.let { line("TITLE:" + escape(it)) }
        card.phones.forEach { line("TEL" + typeParam(it.type) + ":" + escape(it.value)) }
        card.emails.forEach { line("EMAIL" + typeParam(listOfNotNull("INTERNET", it.type).joinToString(",")) + ":" + escape(it.value)) }
        card.addresses.forEach { a ->
            line("ADR" + typeParam(a.type) + ":;;" + listOf(a.street, a.city, a.region, a.postcode, a.country).joinToString(";") { escape(it) })
        }
        card.urls.forEach { line("URL:" + escape(it)) }
        line("END:VCARD")
        return out.toString()
    }

    private fun typeParam(type: String?) = if (type.isNullOrBlank()) "" else ";TYPE=$type"

    private fun escape(s: String) = s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;")
        .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "")

    /** Lines longer than 75 characters continue on the next line after a space (RFC 2425 folding). */
    private fun fold(line: String, out: StringBuilder) {
        var rest = line
        var first = true
        while (rest.length > (if (first) 75 else 74)) {
            var cut = if (first) 75 else 74
            // Never split a character made of two UTF-16 units (emoji).
            if (Character.isHighSurrogate(rest[cut - 1])) cut--
            if (!first) out.append(' ')
            out.append(rest, 0, cut).append("\r\n")
            rest = rest.substring(cut)
            first = false
        }
        if (!first) out.append(' ')
        out.append(rest).append("\r\n")
    }

    // ================================================================ reading a received card

    /** The people on a contact card file; empty when it can't be read. */
    fun parse(bytes: ByteArray): List<VCardContact> {
        if (bytes.size > MAX_BYTES) return emptyList()
        return runCatching { parse(String(bytes, Charsets.UTF_8)) }.getOrDefault(emptyList())
    }

    fun parse(text: String): List<VCardContact> {
        val cards = ArrayList<VCardContact>()
        var current: Builder? = null
        for (raw in unfold(text)) {
            val colon = valueStart(raw)
            if (colon < 0) continue
            val head = raw.substring(0, colon)
            var value = raw.substring(colon + 1)
            val segments = head.split(';')
            val prop = segments[0].substringAfterLast('.').trim().uppercase()
            val params = segments.drop(1).map { it.trim() }
            when (prop) {
                "BEGIN" -> if (value.trim().equals("VCARD", true)) current = Builder()
                "END" -> if (value.trim().equals("VCARD", true)) { current?.build()?.let { cards += it }; current = null }
                else -> {
                    val b = current ?: continue
                    val encoding = params.firstNotNullOfOrNull { p ->
                        when {
                            p.startsWith("ENCODING=", true) -> p.substringAfter('=').uppercase()
                            p.equals("QUOTED-PRINTABLE", true) -> "QUOTED-PRINTABLE"
                            p.equals("BASE64", true) -> "BASE64"
                            else -> null
                        }
                    }
                    if (encoding == "B" || encoding == "BASE64") continue // photos and other binary values
                    if (encoding == "QUOTED-PRINTABLE") {
                        val charset = params.firstOrNull { it.startsWith("CHARSET=", true) }?.substringAfter('=') ?: "UTF-8"
                        value = decodeQuotedPrintable(value, charset)
                    }
                    val types = params.flatMap { p ->
                        when {
                            p.startsWith("TYPE=", true) -> p.substringAfter('=').trim('"').split(',')
                            !p.contains('=') -> listOf(p)
                            else -> emptyList()
                        }
                    }.map { it.trim().uppercase() }.filter { it.isNotEmpty() && it != "PREF" && it != "VOICE" && it != "INTERNET" }
                    val type = types.distinct().joinToString(",").ifBlank { null }
                    b.add(prop, value, type)
                }
            }
        }
        return cards
    }

    private class Builder {
        var fn: String? = null
        var n: List<String> = emptyList()
        val phones = ArrayList<VCardValue>()
        val emails = ArrayList<VCardValue>()
        var org: String? = null
        var title: String? = null
        val addresses = ArrayList<VCardAddress>()
        val urls = ArrayList<String>()

        fun add(prop: String, value: String, type: String?) {
            when (prop) {
                "FN" -> fn = unescape(value).trim()
                "N" -> n = splitComponents(value)
                "TEL" -> unescape(value).trim().removePrefix("tel:").takeIf { it.isNotBlank() }?.let { phones += VCardValue(it, type) }
                "EMAIL" -> unescape(value).trim().takeIf { it.isNotBlank() }?.let { emails += VCardValue(it, type) }
                "ORG" -> org = splitComponents(value).firstOrNull { it.isNotBlank() }
                "TITLE" -> title = unescape(value).trim().ifBlank { null }
                "ADR" -> {
                    val c = splitComponents(value) + List(7) { "" }
                    val a = VCardAddress(street = listOf(c[1], c[2]).filter { it.isNotBlank() }.joinToString(", "), city = c[3], region = c[4], postcode = c[5], country = c[6], type = type)
                    if (a.oneLine.isNotBlank()) addresses += a
                }
                "URL" -> unescape(value).trim().takeIf { it.isNotBlank() }?.let { urls += it }
            }
        }

        fun build(): VCardContact? {
            val family = n.getOrNull(0)?.ifBlank { null }
            val given = n.getOrNull(1)?.ifBlank { null }
            val middle = n.getOrNull(2)?.ifBlank { null }
            val composed = listOfNotNull(n.getOrNull(3)?.ifBlank { null }, given, middle, family, n.getOrNull(4)?.ifBlank { null }).joinToString(" ")
            val name = fn?.ifBlank { null } ?: composed.ifBlank { null } ?: org ?: phones.firstOrNull()?.value ?: emails.firstOrNull()?.value ?: return null
            return VCardContact(
                name = name, given = given, family = family, middle = middle,
                prefix = n.getOrNull(3)?.ifBlank { null }, suffix = n.getOrNull(4)?.ifBlank { null },
                phones = phones, emails = emails, organization = org, title = title, addresses = addresses, urls = urls,
            )
        }
    }

    /** Joins folded lines: a line starting with a space or tab continues the one before. */
    private fun unfold(text: String): List<String> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<String>()
        for (line in lines) {
            val last = out.lastOrNull()
            when {
                (line.startsWith(" ") || line.startsWith("\t")) && last != null -> out[out.size - 1] = last + line.substring(1)
                // vCard 2.1 quoted-printable: "=" at the end of a line means it goes on.
                last != null && last.endsWith("=") && isQuotedPrintable(last) -> out[out.size - 1] = last.dropLast(1) + line
                line.isNotEmpty() -> out += line
            }
        }
        return out
    }

    private fun isQuotedPrintable(line: String): Boolean {
        val colon = valueStart(line)
        return colon > 0 && line.substring(0, colon).contains("QUOTED-PRINTABLE", ignoreCase = true)
    }

    /** Where the value starts: after the first ':' that isn't inside a quoted parameter. */
    private fun valueStart(line: String): Int {
        var quoted = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> quoted = !quoted
                ':' -> if (!quoted) return i
            }
        }
        return -1
    }

    private fun splitComponents(value: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                cur.append(ch).append(value[i + 1]); i += 2; continue
            }
            if (ch == ';') { out += unescape(cur.toString()).trim(); cur.clear() } else cur.append(ch)
            i++
        }
        out += unescape(cur.toString()).trim()
        return out
    }

    private fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(ch); i++
            }
        }
        return out.toString()
    }

    private fun decodeQuotedPrintable(s: String, charset: String): String {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '=' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) { bytes.write(hex); i += 3; continue }
            }
            bytes.write(ch.code and 0xFF)
            i++
        }
        val cs = runCatching { Charset.forName(charset) }.getOrDefault(Charsets.UTF_8)
        return String(bytes.toByteArray(), cs)
    }

    // ================================================================ adding to contacts

    /** Opens the phone's contacts app with a new contact filled in from [card], to check and save. */
    fun insertIntent(card: VCardContact): Intent {
        val data = ArrayList<ContentValues>()
        card.phones.forEach { p ->
            data += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                put(Phone.NUMBER, p.value)
                put(Phone.TYPE, phoneTypeOf(p.type))
            }
        }
        card.emails.forEach { e ->
            data += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                put(Email.ADDRESS, e.value)
                put(Email.TYPE, emailTypeOf(e.type))
            }
        }
        if (card.organization != null || card.title != null) {
            data += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                card.organization?.let { put(Organization.COMPANY, it) }
                card.title?.let { put(Organization.TITLE, it) }
            }
        }
        card.addresses.forEach { a ->
            data += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                put(StructuredPostal.STREET, a.street)
                put(StructuredPostal.CITY, a.city)
                put(StructuredPostal.REGION, a.region)
                put(StructuredPostal.POSTCODE, a.postcode)
                put(StructuredPostal.COUNTRY, a.country)
                put(StructuredPostal.TYPE, postalTypeOf(a.type))
            }
        }
        card.urls.forEach { u ->
            data += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, Website.CONTENT_ITEM_TYPE)
                put(Website.URL, u)
                put(Website.TYPE, Website.TYPE_OTHER)
            }
        }
        return Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
            .putExtra(ContactsContract.Intents.Insert.NAME, card.name)
            .putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data)
    }

    // ================================================================ types

    private fun phoneTypeName(type: Int): String? = when (type) {
        Phone.TYPE_HOME -> "HOME"
        Phone.TYPE_MOBILE -> "CELL"
        Phone.TYPE_WORK -> "WORK"
        Phone.TYPE_FAX_WORK -> "WORK,FAX"
        Phone.TYPE_FAX_HOME -> "HOME,FAX"
        Phone.TYPE_PAGER -> "PAGER"
        else -> null
    }

    private fun phoneTypeOf(type: String?): Int {
        val t = type.orEmpty().uppercase()
        return when {
            "CELL" in t || "MOBILE" in t -> Phone.TYPE_MOBILE
            "FAX" in t && "HOME" in t -> Phone.TYPE_FAX_HOME
            "FAX" in t -> Phone.TYPE_FAX_WORK
            "PAGER" in t -> Phone.TYPE_PAGER
            "WORK" in t -> Phone.TYPE_WORK
            "HOME" in t -> Phone.TYPE_HOME
            else -> Phone.TYPE_OTHER
        }
    }

    private fun emailTypeName(type: Int): String? = when (type) {
        Email.TYPE_HOME -> "HOME"
        Email.TYPE_WORK -> "WORK"
        else -> null
    }

    private fun emailTypeOf(type: String?): Int {
        val t = type.orEmpty().uppercase()
        return when {
            "WORK" in t -> Email.TYPE_WORK
            "HOME" in t -> Email.TYPE_HOME
            else -> Email.TYPE_OTHER
        }
    }

    private fun postalTypeName(type: Int): String? = when (type) {
        StructuredPostal.TYPE_HOME -> "HOME"
        StructuredPostal.TYPE_WORK -> "WORK"
        else -> null
    }

    private fun postalTypeOf(type: String?): Int {
        val t = type.orEmpty().uppercase()
        return when {
            "WORK" in t -> StructuredPostal.TYPE_WORK
            "HOME" in t -> StructuredPostal.TYPE_HOME
            else -> StructuredPostal.TYPE_OTHER
        }
    }
}
