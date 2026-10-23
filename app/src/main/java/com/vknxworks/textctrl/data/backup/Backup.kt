package com.vknxworks.textctrl.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.sms.MessageStore
import com.vknxworks.textctrl.sms.Sims
import com.vknxworks.textctrl.sms.mms.MmsCharset
import com.vknxworks.textctrl.sms.mms.MmsStore
import com.vknxworks.textctrl.sms.mms.PduPart
import com.vknxworks.textctrl.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class SmsRecord(
    val address: String,
    val body: String,
    val date: Long,
    val dateSent: Long = 0,
    val type: Int,
    val read: Int = 1,
    val status: Int = -1,
)

@Serializable
data class MmsAddressRecord(val address: String, val type: Int)

@Serializable
data class MmsPartRecord(
    val ct: String,
    val name: String? = null,
    val cl: String? = null,
    val cid: String? = null,
    val text: String? = null,
    /** Name of the zip entry with the part's bytes, for non-text parts. */
    val file: String? = null,
)

@Serializable
data class MmsRecord(
    val box: Int,
    /** Seconds since 1970. */
    val date: Long,
    val dateSent: Long = 0,
    val read: Int = 1,
    val type: Int,
    val subject: String? = null,
    val messageId: String? = null,
    val transactionId: String? = null,
    val addresses: List<MmsAddressRecord> = emptyList(),
    val parts: List<MmsPartRecord> = emptyList(),
)

/** What a backup or restore is doing. */
sealed interface BackupState {
    data object Idle : BackupState
    data class Running(val restoring: Boolean, val done: Int) : BackupState
    data class Done(val restoring: Boolean, val count: Int, val skipped: Int = 0) : BackupState
    data class Failed(val message: String) : BackupState
}

/**
 * Backups of every text and picture message, to a file you choose (on the phone, a USB stick or any
 * storage app), encrypted with a password only you know.
 *
 * File format: "TCBAK1\n", a 16-byte salt, the PBKDF2 round count and a 4-byte nonce prefix, then the
 * content in 1 MiB chunks, each sealed with AES-256-GCM. A chunk's nonce and associated data carry its
 * number and whether it's the last one, so chunks can't be reordered, dropped or cut off unnoticed. The key
 * comes from the password with PBKDF2-HMAC-SHA256 (600,000 rounds). Inside is a zip: sms.json, then for each
 * picture message mms/N.json followed by its files.
 */
class Backup(
    private val context: Context,
    private val store: MessageStore,
    private val sims: Sims,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun reset() {
        if (_state.value !is BackupState.Running) _state.value = BackupState.Idle
    }

    // ================================================================ back up

    suspend fun export(target: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        _state.value = BackupState.Running(restoring = false, done = 0)
        try {
            var count = 0
            context.contentResolver.openOutputStream(target, "wt").use { raw ->
                raw ?: throw IOException(str(R.string.backup_cant_write))
                val sealed = SealingOutputStream(raw, password)
                ZipOutputStream(sealed).use { zip ->
                    val sms = readSms()
                    zip.putNextEntry(ZipEntry("sms.json"))
                    zip.write(json.encodeToString(ListSerializer(SmsRecord.serializer()), sms).toByteArray())
                    zip.closeEntry()
                    count += sms.size
                    _state.value = BackupState.Running(false, count)
                    forEachMms { n, record, files ->
                        zip.putNextEntry(ZipEntry("mms/$n.json"))
                        zip.write(json.encodeToString(MmsRecord.serializer(), record).toByteArray())
                        zip.closeEntry()
                        for ((name, partUri) in files) {
                            zip.putNextEntry(ZipEntry(name))
                            context.contentResolver.openInputStream(partUri)?.use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                        count++
                        if (count % 25 == 0) _state.value = BackupState.Running(false, count)
                    }
                }
            }
            _state.value = BackupState.Done(restoring = false, count = count)
        } catch (e: Exception) {
            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, target) }
            _state.value = BackupState.Failed(e.message ?: str(R.string.backup_failed))
        } finally {
            password.fill(' ')
        }
    }

    private fun readSms(): List<SmsRecord> {
        val out = ArrayList<SmsRecord>()
        context.contentResolver.query(
            MessageStore.SMS_URI,
            arrayOf("address", "body", "date", "date_sent", "type", "read", "status"),
            "type != 3", null, "date ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += SmsRecord(
                    address = c.getString(0).orEmpty(),
                    body = c.getString(1).orEmpty(),
                    date = c.getLong(2),
                    dateSent = c.getLong(3),
                    type = c.getInt(4),
                    read = c.getInt(5),
                    status = c.getInt(6),
                )
            }
        }
        return out
    }

    private fun forEachMms(block: (Int, MmsRecord, List<Pair<String, Uri>>) -> Unit) {
        val cr = context.contentResolver
        var n = 0
        cr.query(
            MessageStore.MMS_URI,
            arrayOf("_id", "msg_box", "date", "date_sent", "read", "m_type", "sub", "sub_cs", "m_id", "tr_id"),
            "m_type IN (128, 132)", null, "date ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val addresses = ArrayList<MmsAddressRecord>()
                cr.query(Uri.parse("content://mms/$id/addr"), arrayOf("address", "type"), null, null, null)?.use { a ->
                    while (a.moveToNext()) addresses += MmsAddressRecord(a.getString(0).orEmpty(), a.getInt(1))
                }
                val parts = ArrayList<MmsPartRecord>()
                val files = ArrayList<Pair<String, Uri>>()
                cr.query(MessageStore.PART_URI, arrayOf("_id", "ct", "name", "cl", "cid", "text"), "mid = ?", arrayOf(id.toString()), "seq ASC, _id ASC")?.use { p ->
                    while (p.moveToNext()) {
                        val ct = p.getString(1).orEmpty()
                        val textual = ct.equals("text/plain", true) || ct.equals("application/smil", true)
                        val file = if (textual) null else "mms/$n/${p.getLong(0)}"
                        if (file != null) files += file to Uri.parse("content://mms/part/${p.getLong(0)}")
                        parts += MmsPartRecord(ct, p.getString(2), p.getString(3), p.getString(4), if (textual) p.getString(5) else null, file)
                    }
                }
                val subject = c.getString(6)?.let { MessageStore.decodeSnippet(it, c.getInt(7)) }
                block(
                    n,
                    MmsRecord(c.getInt(1), c.getLong(2), c.getLong(3), c.getInt(4), c.getInt(5), subject, c.getString(8), c.getString(9), addresses, parts),
                    files,
                )
                n++
            }
        }
    }

    // ================================================================ restore

    suspend fun restore(source: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        _state.value = BackupState.Running(restoring = true, done = 0)
        var added = 0
        var skipped = 0
        try {
            val existingSms = existingSmsKeys()
            val existingMms = existingMmsKeys()
            val own = sims.ownNumbers()
            context.contentResolver.openInputStream(source).use { raw ->
                raw ?: throw IOException(str(R.string.backup_cant_read))
                ZipInputStream(OpeningInputStream(raw, password)).use { zip ->
                    var pending: MmsRecord? = null
                    val files = HashMap<String, ByteArray>()
                    fun flush() {
                        val record = pending ?: return
                        pending = null
                        if (mmsKey(record.date, record.box, record.messageId) in existingMms) skipped++
                        else if (insertMms(record, files, own)) added++
                        files.clear()
                        if ((added + skipped) % 25 == 0) _state.value = BackupState.Running(true, added + skipped)
                    }
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        when {
                            name == "sms.json" -> {
                                val list = json.decodeFromString(ListSerializer(SmsRecord.serializer()), zip.readBytes().decodeToString())
                                for (r in list) {
                                    if (smsKey(r.date, r.type, r.address, r.body) in existingSms) { skipped++; continue }
                                    insertSms(r)
                                    added++
                                    if (added % 200 == 0) _state.value = BackupState.Running(true, added + skipped)
                                }
                            }
                            name.startsWith("mms/") && name.endsWith(".json") && name.count { it == '/' } == 1 -> {
                                flush()
                                pending = json.decodeFromString(MmsRecord.serializer(), zip.readBytes().decodeToString())
                            }
                            name.startsWith("mms/") -> files[name] = zip.readBytes()
                        }
                        zip.closeEntry()
                    }
                    flush()
                }
            }
            store.touch()
            _state.value = BackupState.Done(restoring = true, count = added, skipped = skipped)
        } catch (e: BackupFormatException) {
            _state.value = BackupState.Failed(
                str(
                    when (e.problem) {
                        BackupProblem.WRONG_PASSWORD -> R.string.backup_wrong_password
                        BackupProblem.NOT_A_BACKUP -> R.string.backup_not_a_backup
                        BackupProblem.CUT_OFF -> R.string.backup_cut_off
                        BackupProblem.DAMAGED -> R.string.backup_damaged
                    },
                ),
            )
        } catch (e: Exception) {
            if (added > 0) store.touch()
            _state.value = BackupState.Failed(e.message ?: str(R.string.backup_failed))
        } finally {
            password.fill(' ')
        }
    }

    private fun smsKey(date: Long, type: Int, address: String, body: String) =
        "$date|$type|${address.filter { it.isDigit() }.takeLast(9)}|${body.hashCode()}"

    private fun mmsKey(dateSec: Long, box: Int, messageId: String?) = "$dateSec|$box|${messageId.orEmpty()}"

    private fun existingSmsKeys(): HashSet<String> {
        val keys = HashSet<String>()
        context.contentResolver.query(MessageStore.SMS_URI, arrayOf("date", "type", "address", "body"), null, null, null)?.use { c ->
            while (c.moveToNext()) keys += smsKey(c.getLong(0), c.getInt(1), c.getString(2).orEmpty(), c.getString(3).orEmpty())
        }
        return keys
    }

    private fun existingMmsKeys(): HashSet<String> {
        val keys = HashSet<String>()
        context.contentResolver.query(MessageStore.MMS_URI, arrayOf("date", "msg_box", "m_id"), null, null, null)?.use { c ->
            while (c.moveToNext()) keys += mmsKey(c.getLong(0), c.getInt(1), c.getString(2))
        }
        return keys
    }

    private fun insertSms(r: SmsRecord) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, r.address)
            put(Telephony.Sms.BODY, r.body)
            put(Telephony.Sms.DATE, r.date)
            put(Telephony.Sms.DATE_SENT, r.dateSent)
            // Messages that were still going out when backed up come back as not sent, never as sending.
            put(Telephony.Sms.TYPE, if (r.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || r.type == Telephony.Sms.MESSAGE_TYPE_QUEUED) Telephony.Sms.MESSAGE_TYPE_FAILED else r.type)
            put(Telephony.Sms.READ, r.read)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.STATUS, r.status)
        }
        context.contentResolver.insert(MessageStore.SMS_URI, values)
    }

    private fun insertMms(r: MmsRecord, files: Map<String, ByteArray>, own: List<String>): Boolean = runCatching {
        val from = r.addresses.firstOrNull { it.type == MessageStore.ADDR_FROM }?.address
        val to = r.addresses.filter { it.type == MessageStore.ADDR_TO }.map { it.address }
        val cc = r.addresses.filter { it.type == MessageStore.ADDR_CC }.map { it.address }
        val incoming = r.box == Telephony.Mms.MESSAGE_BOX_INBOX
        val people = if (incoming) {
            val sender = from ?: return@runCatching false
            val others = (to + cc).filter { a -> own.none { Contacts.same(it, a) } && !Contacts.same(a, sender) }
            if (to.size + cc.size <= 1) listOf(sender) else (listOf(sender) + others).distinct()
        } else {
            to + cc
        }
        if (people.isEmpty()) return@runCatching false
        val parts = r.parts.map { p ->
            val textual = p.file == null
            PduPart(
                contentType = p.ct,
                data = if (textual) p.text.orEmpty().toByteArray() else files[p.file] ?: ByteArray(0),
                name = p.name,
                charset = if (textual) MmsCharset.UTF8 else 0,
                contentId = p.cid?.trim('<', '>'),
                contentLocation = p.cl,
            )
        }
        MmsStore.insert(
            context,
            box = if (r.box == Telephony.Mms.MESSAGE_BOX_OUTBOX) MessageStore.MESSAGE_BOX_FAILED else r.box,
            threadId = store.threadIdFor(people),
            dateSeconds = r.date,
            read = r.read != 0,
            subId = -1,
            messageType = r.type,
            from = from ?: MessageStore.INSERT_ADDRESS_TOKEN,
            to = to,
            cc = cc,
            parts = parts,
            subject = r.subject,
            transactionId = r.transactionId,
            messageId = r.messageId,
        ) != null
    }.getOrDefault(false)

}
