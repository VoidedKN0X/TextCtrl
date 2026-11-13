package com.vknxworks.textctrl.data.filters

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A rule as written to and read from an export file (no id, no hit counter). */
@Serializable
data class ExportedRule(
    val kind: String,
    val pattern: String = "",
    val action: String,
    val enabled: Boolean = true,
)

/** The JSON export: your filter rules and Android's blocked numbers. */
@Serializable
data class RulesFile(
    val format: String = RuleTransfer.FORMAT,
    val version: Int = 1,
    val rules: List<ExportedRule> = emptyList(),
    val blocked: List<String> = emptyList(),
)

/** What an import file holds, after checking every line. */
data class ImportedRules(
    val rules: List<ExportedRule>,
    val blocked: List<String>,
    /** Lines or entries left out because they weren't valid. */
    val skipped: Int,
)

/**
 * Export and import of filter rules and blocked numbers, as JSON or CSV. Pure (no Android) and
 * unit-tested; reading and writing the files goes through the system file picker in [RuleFiles].
 *
 * CSV columns: `type,kind,value,action,enabled`, with `type` either `rule` or `blocked`
 * (`blocked,,+32470123456,,`). Values a spreadsheet would read as a formula (starting with = + - @)
 * are written with a leading apostrophe, which is removed again on import.
 */
object RuleTransfer {
    const val FORMAT = "textctrl-filters"
    /** Larger files are refused before they're read. */
    const val MAX_FILE_BYTES = 1_000_000
    const val MAX_RULES = 1000
    const val MAX_BLOCKED = 10_000
    const val MAX_NUMBER = 64
    const val CSV_HEADER = "type,kind,value,action,enabled"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun export(rule: FilterRule) = ExportedRule(rule.kind.name, rule.pattern, rule.action.name, rule.enabled)

    fun toJson(rules: List<FilterRule>, blocked: List<String>): String =
        json.encodeToString(RulesFile.serializer(), RulesFile(rules = rules.map(::export), blocked = blocked))

    fun toCsv(rules: List<FilterRule>, blocked: List<String>): String = buildString {
        append(CSV_HEADER).append("\r\n")
        for (r in rules) {
            append(csvRow(listOf("rule", r.kind.name, r.pattern, r.action.name, r.enabled.toString()))).append("\r\n")
        }
        for (n in blocked) append(csvRow(listOf("blocked", "", n, "", ""))).append("\r\n")
    }

    /** Reads a JSON or CSV export (whichever it looks like). Invalid entries are skipped and counted. */
    fun parse(text: String): ImportedRules {
        val body = text.removePrefix("\uFEFF").trim()
        if (body.isEmpty()) return ImportedRules(emptyList(), emptyList(), 0)
        return if (body.startsWith("{")) parseJson(body) else parseCsv(body)
    }

    private fun parseJson(body: String): ImportedRules {
        val file = runCatching { json.decodeFromString(RulesFile.serializer(), body) }.getOrNull()
            ?: throw IllegalArgumentException("Not a filter export")
        return check(file.rules, file.blocked, 0)
    }

    private fun parseCsv(body: String): ImportedRules {
        val rows = parseCsvRows(body)
        val rules = ArrayList<ExportedRule>()
        val blocked = ArrayList<String>()
        var skipped = 0
        rows.forEachIndexed { i, row ->
            if (row.all { it.isBlank() }) return@forEachIndexed
            if (i == 0 && row.firstOrNull()?.trim()?.equals("type", ignoreCase = true) == true) return@forEachIndexed
            val cells = row.map { unescapeFormula(it.trim()) }
            when (cells.getOrNull(0)?.lowercase()) {
                "rule" -> rules += ExportedRule(
                    kind = cells.getOrElse(1) { "" },
                    pattern = cells.getOrElse(2) { "" },
                    action = cells.getOrElse(3) { "" },
                    enabled = cells.getOrNull(4)?.lowercase() != "false",
                )
                "blocked" -> blocked += cells.getOrElse(2) { "" }
                else -> skipped++
            }
        }
        if (rules.isEmpty() && blocked.isEmpty() && skipped > 0) throw IllegalArgumentException("Not a filter export")
        return check(rules, blocked, skipped)
    }

    /** Keeps the rules and numbers that make sense, within the limits. */
    private fun check(rules: List<ExportedRule>, blocked: List<String>, skippedBefore: Int): ImportedRules {
        var skipped = skippedBefore
        val goodRules = ArrayList<ExportedRule>()
        for (r in rules) {
            val kind = RuleKind.entries.firstOrNull { it.name.equals(r.kind.trim(), ignoreCase = true) }
            val action = RuleAction.entries.firstOrNull { it.name.equals(r.action.trim(), ignoreCase = true) }
            val pattern = if (kind?.needsPattern == true) r.pattern.trim() else ""
            if (kind == null || action == null || RuleMatcher.problem(kind, pattern) != null || goodRules.size >= MAX_RULES) {
                skipped++
                continue
            }
            goodRules += ExportedRule(kind.name, pattern, action.name, r.enabled)
        }
        val goodNumbers = LinkedHashSet<String>()
        for (n in blocked) {
            val number = n.trim()
            if (!validBlocked(number) || goodNumbers.size >= MAX_BLOCKED) {
                skipped++
                continue
            }
            goodNumbers += number
        }
        return ImportedRules(goodRules.distinct(), goodNumbers.toList(), skipped)
    }

    /** A number (or sender name) Android's block list can take: short, printable, with a digit or letter. */
    fun validBlocked(number: String): Boolean =
        number.isNotEmpty() && number.length <= MAX_NUMBER && number.none { it.isISOControl() } && number.any { it.isLetterOrDigit() }

    // ---------------------------------------------------------------- CSV

    private fun csvRow(cells: List<String>): String = cells.joinToString(",") { csvCell(escapeFormula(it)) }

    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\""
        else value

    private fun escapeFormula(value: String): String = if (value.firstOrNull() in FORMULA_START) "'$value" else value

    private fun unescapeFormula(value: String): String =
        if (value.length >= 2 && value[0] == '\'' && value[1] in FORMULA_START) value.substring(1) else value

    private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')

    /** RFC 4180: commas, quoted cells with "" for a quote, line breaks inside quotes; CRLF or LF. */
    fun parseCsvRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"'); i++
                    } else {
                        quoted = false
                    }
                } else {
                    cell.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> quoted = true
                    ',' -> { row += cell.toString(); cell.clear() }
                    '\r' -> Unit
                    '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = ArrayList() }
                    else -> cell.append(ch)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }
}
