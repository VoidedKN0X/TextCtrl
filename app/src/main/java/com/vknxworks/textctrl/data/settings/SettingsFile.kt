package com.vknxworks.textctrl.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A setting that travels in a settings file. */
enum class SettingField(val key: String) {
    BUBBLE_COLOR("bubble_colour"),
    TEXT_SIZE("text_size"),
    SWIPE_RIGHT("swipe_right"),
    SWIPE_LEFT("swipe_left"),
    HIDE_SCREENSHOTS("hide_screenshots"),
    HIDE_RECENTS("hide_recents"),
    NOTIFICATION_CONTENT("notification_content"),
    SEPARATE_UNKNOWN("separate_unknown"),
    NOTIFY_UNKNOWN("notify_unknown"),
    WARN_LINKS("warn_links"),
    UNDO_SEND("undo_send"),
    COPY_CODES("copy_codes"),
    DELIVERY_REPORTS("delivery_reports"),
    AUTO_DOWNLOAD_MMS("auto_download_mms"),
    GROUP_AS_MMS("group_as_mms"),
    PLAIN_SMS("plain_sms"),
}

/** One setting an import would change, with the values as they are written in the file. */
data class SettingChange(val field: SettingField, val from: String, val to: String)

/** What importing a settings file would do, worked out before anything is changed. */
data class SettingsImport(
    /** The settings as they would be afterwards. */
    val settings: AppSettings,
    val changes: List<SettingChange>,
    /** Settings in the file that are left as they are because the file would make TextCtrl less private. */
    val refused: List<SettingField>,
    /** The signature and templates in the file, or null when the file has none. */
    val snippets: Snippets?,
)

/**
 * TextCtrl's settings as a file: export and import. Pure Kotlin.
 *
 * In the file: how TextCtrl looks and behaves, your signature and your templates. Never in the file: the app
 * lock (PIN, fingerprint, when it locks again), the hidden chats and their PIN, the auto-delete choices
 * (an import must never start deleting messages), the SIM for new chats (it differs per phone) and anything
 * kept per chat. An import can turn "Hide in screenshots", "Hide in recent apps" and the link warning on but
 * never off, and can make notifications show less but never more.
 */
object SettingsFile {
    const val APP = "TextCtrl"
    const val FORMAT = 1
    /** Larger files aren't read at all. */
    const val MAX_BYTES = 256 * 1024

    @Serializable
    private data class Doc(
        val app: String = "",
        val format: Int = 0,
        val settings: Map<String, String> = emptyMap(),
        val signature: String? = null,
        val templates: List<String>? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private fun read(s: AppSettings, f: SettingField): String = when (f) {
        SettingField.BUBBLE_COLOR -> s.bubbleColor.name
        SettingField.TEXT_SIZE -> s.textSize.name
        SettingField.SWIPE_RIGHT -> s.swipeRight.name
        SettingField.SWIPE_LEFT -> s.swipeLeft.name
        SettingField.HIDE_SCREENSHOTS -> s.hideScreenshots.toString()
        SettingField.HIDE_RECENTS -> s.hideRecents.toString()
        SettingField.NOTIFICATION_CONTENT -> s.notificationContent.name
        SettingField.SEPARATE_UNKNOWN -> s.separateUnknown.toString()
        SettingField.NOTIFY_UNKNOWN -> s.notifyUnknown.toString()
        SettingField.WARN_LINKS -> s.warnLinks.toString()
        SettingField.UNDO_SEND -> s.undoSend.name
        SettingField.COPY_CODES -> s.copyCodes.toString()
        SettingField.DELIVERY_REPORTS -> s.deliveryReports.toString()
        SettingField.AUTO_DOWNLOAD_MMS -> s.autoDownloadMms.toString()
        SettingField.GROUP_AS_MMS -> s.groupAsMms.toString()
        SettingField.PLAIN_SMS -> s.plainSms.toString()
    }

    private fun bool(raw: String): Boolean? = when (raw) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private inline fun <reified E : Enum<E>> enumOf(raw: String): E? = enumValues<E>().firstOrNull { it.name == raw }

    /** [s] with [f] set to [raw], or null when [raw] isn't a value TextCtrl knows. */
    private fun write(s: AppSettings, f: SettingField, raw: String): AppSettings? = when (f) {
        SettingField.BUBBLE_COLOR -> enumOf<BubbleColor>(raw)?.let { s.copy(bubbleColor = it) }
        SettingField.TEXT_SIZE -> enumOf<TextSize>(raw)?.let { s.copy(textSize = it) }
        SettingField.SWIPE_RIGHT -> enumOf<SwipeAction>(raw)?.let { s.copy(swipeRight = it) }
        SettingField.SWIPE_LEFT -> enumOf<SwipeAction>(raw)?.let { s.copy(swipeLeft = it) }
        SettingField.HIDE_SCREENSHOTS -> bool(raw)?.let { s.copy(hideScreenshots = it) }
        SettingField.HIDE_RECENTS -> bool(raw)?.let { s.copy(hideRecents = it) }
        SettingField.NOTIFICATION_CONTENT -> enumOf<NotificationContent>(raw)?.let { s.copy(notificationContent = it) }
        SettingField.SEPARATE_UNKNOWN -> bool(raw)?.let { s.copy(separateUnknown = it) }
        SettingField.NOTIFY_UNKNOWN -> bool(raw)?.let { s.copy(notifyUnknown = it) }
        SettingField.WARN_LINKS -> bool(raw)?.let { s.copy(warnLinks = it) }
        SettingField.UNDO_SEND -> enumOf<UndoSend>(raw)?.let { s.copy(undoSend = it) }
        SettingField.COPY_CODES -> bool(raw)?.let { s.copy(copyCodes = it) }
        SettingField.DELIVERY_REPORTS -> bool(raw)?.let { s.copy(deliveryReports = it) }
        SettingField.AUTO_DOWNLOAD_MMS -> bool(raw)?.let { s.copy(autoDownloadMms = it) }
        SettingField.GROUP_AS_MMS -> bool(raw)?.let { s.copy(groupAsMms = it) }
        SettingField.PLAIN_SMS -> bool(raw)?.let { s.copy(plainSms = it) }
    }

    /** Whether going from [from] to [to] would make TextCtrl less private or less careful. */
    private fun weakens(f: SettingField, from: String, to: String): Boolean = when (f) {
        SettingField.HIDE_SCREENSHOTS, SettingField.HIDE_RECENTS, SettingField.WARN_LINKS -> from == "true" && to == "false"
        // The choices are ordered from showing everything to showing nothing.
        SettingField.NOTIFICATION_CONTENT -> {
            val a = enumOf<NotificationContent>(from)
            val b = enumOf<NotificationContent>(to)
            a != null && b != null && b.ordinal < a.ordinal
        }
        else -> false
    }

    fun export(settings: AppSettings, snippets: Snippets): String = json.encodeToString(
        Doc.serializer(),
        Doc(
            app = APP,
            format = FORMAT,
            settings = SettingField.entries.associate { it.key to read(settings, it) },
            signature = snippets.signature,
            templates = snippets.templates,
        ),
    )

    /**
     * Reads a settings file and works out what it would change in [current]. Null when [text] isn't a
     * TextCtrl settings file (or one from a newer format). Unknown settings and values are skipped.
     */
    fun plan(text: String, current: AppSettings): SettingsImport? {
        val doc = runCatching { json.decodeFromString(Doc.serializer(), text) }.getOrNull() ?: return null
        if (doc.app != APP || doc.format != FORMAT) return null
        var next = current
        val changes = ArrayList<SettingChange>()
        val refused = ArrayList<SettingField>()
        for (f in SettingField.entries) {
            val raw = doc.settings[f.key] ?: continue
            val from = read(next, f)
            if (raw == from) continue
            val changed = write(next, f, raw) ?: continue
            if (weakens(f, from, raw)) {
                refused += f
                continue
            }
            next = changed
            changes += SettingChange(f, from, raw)
        }
        val snippets = if (doc.signature == null && doc.templates == null) null
        else Snippets(doc.signature.orEmpty(), doc.templates.orEmpty()).cleaned()
        return SettingsImport(next, changes, refused, snippets)
    }
}
