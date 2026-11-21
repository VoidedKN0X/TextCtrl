package com.vknxworks.textctrl.sms

/**
 * Looks at a web link before it is opened: where it really goes, and the tricks scam texts use to make a
 * link look like something else. Pure Kotlin, on the phone only; nothing is looked up anywhere.
 */
object LinkCheck {
    data class Info(
        /** The site the link goes to ("example.com"), or "" when it can't be told. */
        val host: String,
        /** The address has letters from another alphabet or is written in the coded form for them ("xn--"). */
        val lookalike: Boolean,
        /** The address is a bare number (an IP address) instead of a name. */
        val numeric: Boolean,
        /** The link has a name in front of an "@", which makes it look like it goes to that name. */
        val disguised: Boolean,
        /** Plain http: what you type on the page can be read on the way. */
        val unencrypted: Boolean,
    )

    private val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun inspect(url: String): Info {
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf("://")
        val scheme = if (schemeEnd > 0) trimmed.substring(0, schemeEnd).lowercase() else ""
        val rest = if (schemeEnd > 0) trimmed.substring(schemeEnd + 3) else trimmed
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' && it != '\\' }
        val at = authority.lastIndexOf('@')
        val hostPort = if (at >= 0) authority.substring(at + 1) else authority
        val host = when {
            hostPort.startsWith("[") -> hostPort.substringBefore(']') + "]"
            else -> hostPort.substringBefore(':')
        }.lowercase().trimEnd('.')
        return Info(
            host = host,
            lookalike = host.any { it.code > 127 } || host.split('.').any { it.startsWith("xn--") },
            numeric = host.startsWith("[") || ipv4.matches(host),
            disguised = at >= 0,
            unencrypted = scheme == "http",
        )
    }
}
