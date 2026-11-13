package com.vknxworks.textctrl.sms

/** Finds one-time codes ("Your verification code is 482913") in a text. Runs on the phone only. */
object OneTimeCode {
    private val keywords = Regex(
        "(?i)(code|codes|otp|pin|passcode|password|verif|verificatie|wachtwoord|bevestiging|beveiliging|toegang|inlog|login|log in|sign in|sign-in|authenticat|2fa|\\btan\\b|bestätigung|sicherheit|einmal|código|codigo|codice|vérification|verification|clave|kod|код|验证码|認証)",
    )
    private val prefixed = Regex("\\b[A-Z]{1,3}-(\\d{4,8})\\b")
    private val candidate = Regex("(?<![\\d€$£¥.,:/+#-])(\\d{3}[- ]\\d{3}|\\d{4,8})(?![\\d%]|[.,:/]\\d)")

    /** The code in [text], digits only, or null when the text doesn't look like a one-time code message. */
    fun find(text: String): String? {
        if (text.length > 400 || !keywords.containsMatchIn(text)) return null
        prefixed.find(text)?.let { return it.groupValues[1] }
        val found = candidate.findAll(text).map { it.groupValues[1].filter(Char::isDigit) }.toList()
        if (found.isEmpty()) return null
        // Six digits is by far the most common; four-digit years are least likely to be the code.
        return found.firstOrNull { it.length == 6 }
            ?: found.firstOrNull { !(it.length == 4 && (it.startsWith("19") || it.startsWith("20"))) }
            ?: found.first()
    }
}

/** A reaction sent as a text, the way iPhones and Google Messages send them to SMS phones. */
data class Reaction(
    val emoji: String,
    /** The text reacted to (possibly cut short with "…"), or null for a picture or video. */
    val target: String?,
    val removed: Boolean,
)

/**
 * Reactions over SMS. iPhones and Google Messages send reactions to phones without iMessage or RCS as
 * ordinary texts: Liked “see you at 8”, Reacted 😮 to “see you at 8”, Removed a like from “…”. TextCtrl
 * shows those as badges on the message they're about, and sends its own reactions in the same format.
 */
object Reactions {
    private val verbs = linkedMapOf(
        "Liked" to "👍",
        "Loved" to "❤️",
        "Disliked" to "👎",
        "Laughed at" to "😂",
        "Emphasized" to "‼️",
        "Emphasised" to "‼️",
        "Questioned" to "❓",
    )
    private val removals = linkedMapOf(
        "a like" to "👍",
        "a heart" to "❤️",
        "a dislike" to "👎",
        "a laugh" to "😂",
        "an exclamation mark" to "‼️",
        "an exclamation" to "‼️",
        "an emphasis" to "‼️",
        "a question mark" to "❓",
    )

    /** The six offered when you react, like other messaging apps. */
    val choices = listOf("👍", "❤️", "😂", "😮", "😢", "👎", "‼️", "❓")

    private const val Q = "[“\"„«]"
    private const val QE = "[”\"“»]"
    private const val MEDIA = "(an image|a photo|a picture|a video|a movie|an attachment|an audio message|a sticker|a GIF)"
    private val verbAlt = verbs.keys.joinToString("|") { Regex.escape(it) }
    private val removalAlt = removals.keys.joinToString("|") { Regex.escape(it) }
    private val opts = setOf(RegexOption.DOT_MATCHES_ALL)

    private val verbText = Regex("^($verbAlt) $Q(.+)$QE$", opts)
    private val verbMedia = Regex("^($verbAlt) $MEDIA$")
    private val reactedText = Regex("^Reacted (\\S{1,16}) to $Q(.+)$QE$", opts)
    private val reactedMedia = Regex("^Reacted (\\S{1,16}) to $MEDIA$")
    private val removedNamed = Regex("^Removed ($removalAlt) from $Q(.+)$QE$", opts)
    private val removedNamedMedia = Regex("^Removed ($removalAlt) from $MEDIA$")
    private val removedEmoji = Regex("^Removed (\\S{1,16}) from $Q(.+)$QE$", opts)
    private val removedEmojiMedia = Regex("^Removed (\\S{1,16}) from $MEDIA$")

    private fun isEmoji(s: String) = s.isNotEmpty() && s.none { it.isLetterOrDigit() }

    fun parse(text: String): Reaction? {
        val t = text.trim()
        if (t.length > 2000) return null
        verbText.matchEntire(t)?.let { return Reaction(verbs.getValue(it.groupValues[1]), it.groupValues[2], false) }
        verbMedia.matchEntire(t)?.let { return Reaction(verbs.getValue(it.groupValues[1]), null, false) }
        reactedText.matchEntire(t)?.let { if (isEmoji(it.groupValues[1])) return Reaction(it.groupValues[1], it.groupValues[2], false) }
        reactedMedia.matchEntire(t)?.let { if (isEmoji(it.groupValues[1])) return Reaction(it.groupValues[1], null, false) }
        removedNamed.matchEntire(t)?.let { return Reaction(removals.getValue(it.groupValues[1]), it.groupValues[2], true) }
        removedNamedMedia.matchEntire(t)?.let { return Reaction(removals.getValue(it.groupValues[1]), null, true) }
        removedEmoji.matchEntire(t)?.let { if (isEmoji(it.groupValues[1])) return Reaction(it.groupValues[1], it.groupValues[2], true) }
        removedEmojiMedia.matchEntire(t)?.let { if (isEmoji(it.groupValues[1])) return Reaction(it.groupValues[1], null, true) }
        return null
    }

    /** Whether [quoted] (as written in a reaction, maybe cut short) is about the message text [body]. */
    fun matches(quoted: String, body: String): Boolean {
        val q = normalize(quoted).removeSuffix("…").removeSuffix("...").trimEnd()
        val b = normalize(body)
        if (q.isEmpty() || b.isEmpty()) return false
        return b == q || (q.length >= 8 && b.startsWith(q))
    }

    private fun normalize(s: String) = s.replace(Regex("\\s+"), " ").trim()

    /** The text to send for reacting with [emoji] to a message with [body] (blank for a picture). */
    fun compose(emoji: String, body: String, remove: Boolean = false): String {
        val media = body.isBlank()
        val quoted = if (media) "" else "“" + shorten(body) + "”"
        val named = if (remove) removals.entries.firstOrNull { it.value == emoji && it.key != "an exclamation" && it.key != "an emphasis" }?.key
        else verbs.entries.firstOrNull { it.value == emoji && it.key != "Emphasised" }?.key
        val what = if (media) "an image" else quoted
        return when {
            remove && named != null -> "Removed $named from $what"
            remove -> "Removed $emoji from $what"
            named != null -> "$named $what"
            else -> "Reacted $emoji to $what"
        }
    }

    private fun shorten(body: String): String {
        val flat = normalize(body)
        return if (flat.length <= 100) flat else flat.take(100).trimEnd() + "…"
    }
}
