package com.vknxworks.textctrl.sms

import java.text.Normalizer

/**
 * "Send without accents". A text that only uses the mobile network's basic alphabet (GSM 7-bit) fits 160
 * characters in one SMS; a single character outside it (ë, ê, ç, a curly quote) makes the whole text
 * go out in the wide encoding, 70 characters per SMS. This swaps such characters for the nearest basic one,
 * and leaves alone what has no basic look-alike (emoji, other scripts). Pure Kotlin, runs on the phone only.
 */
object PlainSms {
    // The GSM 7-bit default alphabet and its extension table (3GPP TS 23.038, section 6.2.1).
    private const val BASIC =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private const val EXTENSION = "\u000C^{}\\[~]|€"
    private val gsm: Set<Char> = (BASIC + EXTENSION).toSet()

    private val swaps: Map<Char, String> = mapOf(
        '‘' to "'", '’' to "'", '‚' to "'", '‛' to "'", '′' to "'", '`' to "'", '´' to "'",
        '“' to "\"", '”' to "\"", '„' to "\"", '‟' to "\"", '″' to "\"", '«' to "\"", '»' to "\"",
        '‹' to "<", '›' to ">",
        '–' to "-", '—' to "-", '−' to "-", '‐' to "-", '‑' to "-", '‒' to "-", '―' to "-",
        '…' to "...", '•' to "*", '·' to ".",
        ' ' to " ", ' ' to " ", ' ' to " ", ' ' to " ", ' ' to " ", ' ' to " ",
        '\t' to " ",
        'œ' to "oe", 'Œ' to "OE", 'ł' to "l", 'Ł' to "L", 'đ' to "d", 'Đ' to "D", 'ı' to "i",
        'ð' to "d", 'Ð' to "D", 'þ' to "th", 'Þ' to "Th",
        '№' to "No", '™' to "TM", '©' to "(c)", '®' to "(R)", '½' to "1/2", '¼' to "1/4", '¾' to "3/4",
        '×' to "x", '÷' to "/", '°' to " deg",
    )

    /** Whether [c] is in the network's basic alphabet. */
    fun isBasic(c: Char): Boolean = c in gsm

    /** Whether the whole of [text] fits the basic alphabet (so 160 characters go in one SMS). */
    fun isPlain(text: String): Boolean = text.all { it in gsm }

    /** [text] with accents and typographic characters swapped for basic ones where there is a look-alike. */
    fun plain(text: String): String {
        if (isPlain(text)) return text
        val out = StringBuilder(text.length + 8)
        for (c in text) {
            if (c in gsm) {
                out.append(c)
                continue
            }
            val swap = swaps[c]
            if (swap != null) {
                out.append(swap)
                continue
            }
            if (Character.isSurrogate(c)) {
                out.append(c)
                continue
            }
            // An accent typed as a separate combining mark (U+0300 to U+036F) is simply left out.
            if (c in '\u0300'..'\u036F') continue
            // é is in the basic alphabet, ë and ê are not: split the letter from its accent and keep the letter.
            val bare = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            if (bare.isNotEmpty() && bare != c.toString() && bare.all { it in gsm }) out.append(bare) else out.append(c)
        }
        return out.toString()
    }
}
