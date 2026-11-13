package com.vknxworks.textctrl.data.filters

import android.content.Context
import android.net.Uri
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.sms.Blocklist
import com.vknxworks.textctrl.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Exports your filter rules and Android's blocked numbers to a file you pick, and imports them again
 * (JSON or CSV), through the system file picker: TextCtrl never sees anything but the one file.
 */
class RuleFiles(private val context: Context, private val filters: Filters, private val blocklist: Blocklist) {

    suspend fun export(uri: Uri, csv: Boolean) = withContext(Dispatchers.IO) {
        val blocked = blocklist.all().map { it.number }.filter { it.isNotBlank() }
        val rules = filters.rules.value
        val text = if (csv) RuleTransfer.toCsv(rules, blocked) else RuleTransfer.toJson(rules, blocked)
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException(str(R.string.filters_export_failed))
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** Adds what [uri] holds: rules that aren't there yet, and numbers not blocked yet (as the SMS app). */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                buffer.write(chunk, 0, n)
                if (buffer.size() > RuleTransfer.MAX_FILE_BYTES) throw IOException(str(R.string.filters_import_too_big))
            }
            buffer.toByteArray()
        } ?: throw IOException(str(R.string.filters_import_failed))
        val parsed = try {
            RuleTransfer.parse(bytes.toString(Charsets.UTF_8))
        } catch (_: IllegalArgumentException) {
            throw IOException(str(R.string.filters_import_invalid))
        }
        val rulesAdded = filters.importRules(parsed.rules)
        var skipped = parsed.skipped + (parsed.rules.size - rulesAdded)
        var blockedAdded = 0
        if (parsed.blocked.isNotEmpty()) {
            if (!blocklist.canBlock) {
                skipped += parsed.blocked.size
            } else {
                val existing = blocklist.all().map { it.number }.toSet()
                for (number in parsed.blocked) {
                    if (number in existing || blocklist.isBlocked(number)) {
                        skipped++
                        continue
                    }
                    if (runCatching { blocklist.block(number) }.isSuccess) blockedAdded++ else skipped++
                }
            }
        }
        ImportResult(rulesAdded, blockedAdded, skipped)
    }
}
