package com.vknxworks.textctrl.data.backup

import com.vknxworks.textctrl.sms.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One chat as a text file you can read anywhere: a short heading, then every message on its own line with
 * the time and who wrote it. Pictures and files are named, not included. Not encrypted: it is meant to be
 * read, so keep it somewhere safe.
 */
object ChatExport {
    /** The words the file needs, in the phone's language. */
    data class Words(
        val heading: String,
        val with: String,
        val exported: String,
        val messages: String,
        val me: String,
        val attachment: String,
        val notDownloaded: String,
    )

    fun fileName(title: String, now: Long): String {
        val safe = title.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("").trim('_').take(40)
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(now))
        return "TextCtrl-chat-" + (if (safe.isEmpty()) "" else "$safe-") + day + ".txt"
    }

    /** [messages] oldest first. [people] are the chat's numbers; [nameOf] gives the name shown for a number. */
    fun text(people: List<String>, messages: List<Message>, now: Long, words: Words, nameOf: (String) -> String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
        val out = StringBuilder(messages.size * 64 + 256)
        out.append(words.heading).append('\n')
        out.append(words.with).append(": ")
        out.append(people.joinToString(", ") { number -> nameOf(number).let { if (it == number) number else "$it ($number)" } }).append('\n')
        out.append(words.exported).append(": ").append(stamp.format(Date(now))).append('\n')
        out.append(words.messages).append(": ").append(messages.size).append('\n')
        out.append('\n')
        for (m in messages) {
            val who = if (m.incoming) nameOf(m.address) else words.me
            out.append('[').append(stamp.format(Date(m.date))).append("] ").append(who).append(": ")
            val lines = ArrayList<String>()
            m.subject?.takeIf { it.isNotBlank() }?.let { lines += it }
            if (m.body.isNotBlank()) lines += m.body.trim()
            if (m.notDownloaded) lines += "[" + words.notDownloaded + "]"
            for (part in m.attachments) lines += "[" + words.attachment + ": " + (part.name ?: part.contentType) + "]"
            // Later lines of one message are indented, so every new message starts at the margin.
            out.append(lines.joinToString("\n").replace("\r\n", "\n").replace("\n", "\n    ")).append('\n')
        }
        return out.toString()
    }
}
