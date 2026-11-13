package com.vknxworks.textctrl.data.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vknxworks.textctrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "textctrl")

enum class ThemeChoice(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    BLACK(R.string.theme_black),
}

/** When the app lock asks again after you leave the app. */
enum class LockTimeout(@StringRes val label: Int, val millis: Long) {
    IMMEDIATELY(R.string.lock_again_immediately, 0L),
    MINUTE_1(R.string.lock_again_1m, 60_000L),
    MINUTES_5(R.string.lock_again_5m, 5 * 60_000L),
    MINUTES_15(R.string.lock_again_15m, 15 * 60_000L),
    HOUR_1(R.string.lock_again_1h, 60 * 60_000L),
    RESTART(R.string.lock_again_restart, -1L),
}

/** What a new-message notification shows. */
enum class NotificationContent(@StringRes val label: Int) {
    NAME_AND_MESSAGE(R.string.notify_content_all),
    NAME_ONLY(R.string.notify_content_name),
    NOTHING(R.string.notify_content_nothing),
}

/** How long a sent message waits before it really goes out, so it can be taken back. */
enum class UndoSend(@StringRes val label: Int, val seconds: Int) {
    OFF(R.string.undo_off, 0),
    SECONDS_5(R.string.undo_5s, 5),
    SECONDS_10(R.string.undo_10s, 10),
}

/** Delete messages older than this. */
enum class AutoDelete(@StringRes val label: Int, val days: Int) {
    NEVER(R.string.auto_delete_never, 0),
    DAYS_30(R.string.auto_delete_30d, 30),
    DAYS_90(R.string.auto_delete_90d, 90),
    YEAR_1(R.string.auto_delete_1y, 365),
}

/** Size of the text in chats, relative to the normal size. */
enum class TextSize(@StringRes val label: Int, val scale: Float) {
    SMALL(R.string.text_size_small, 0.9f),
    DEFAULT(R.string.text_size_default, 1f),
    LARGE(R.string.text_size_large, 1.15f),
    LARGER(R.string.text_size_larger, 1.3f),
}

/** Colour of your own message bubbles. */
enum class BubbleColor(@StringRes val label: Int, val argb: Long) {
    ACCENT(R.string.bubble_accent, 0),
    SKY(R.string.bubble_sky, 0xFF7FC8F8),
    MINT(R.string.bubble_mint, 0xFF8BD8C6),
    MARIGOLD(R.string.bubble_marigold, 0xFFF4C76B),
    ROSE(R.string.bubble_rose, 0xFFF2A0A0),
    LAVENDER(R.string.bubble_lavender, 0xFFC3A6F2),
    GRAPHITE(R.string.bubble_graphite, 0xFF4A505B),
}

/** What swiping a chat in the chat list does. */
enum class SwipeAction(@StringRes val label: Int) {
    NONE(R.string.swipe_none),
    ARCHIVE(R.string.archive),
    DELETE(R.string.delete),
    READ(R.string.swipe_read),
    PIN(R.string.swipe_pin),
    MUTE(R.string.swipe_mute),
    BLOCK(R.string.block),
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATELY,
    /** "Hide in screenshots": FLAG_SECURE on every window (also blanks the recent-apps preview). */
    val hideScreenshots: Boolean = false,
    /** "Hide in recent apps": the recent-apps view shows a blank card. Screenshots still work. */
    val hideRecents: Boolean = false,
    val notificationContent: NotificationContent = NotificationContent.NAME_AND_MESSAGE,
    /** Ask the network to report back when a text reaches the other phone. */
    val deliveryReports: Boolean = false,
    /** Fetch picture messages as soon as they arrive. Off: tap a message to fetch it. */
    val autoDownloadMms: Boolean = true,
    /** Texts to several people go out as one group picture message (everyone sees the replies). */
    val groupAsMms: Boolean = true,
    /** The SIM new texts go out on (subscription id), or -1 to ask / use the phone's default. */
    val defaultSim: Int = -1,
    val undoSend: UndoSend = UndoSend.SECONDS_5,
    /** Offer "Copy code" for one-time codes in texts and their notifications. */
    val copyCodes: Boolean = true,
    /** Chats with numbers that aren't in your contacts (and you never wrote to) get their own filter. */
    val separateUnknown: Boolean = false,
    /** With [separateUnknown] on: still notify about texts from unknown senders. */
    val notifyUnknown: Boolean = true,
    val autoDelete: AutoDelete = AutoDelete.NEVER,
    val textSize: TextSize = TextSize.DEFAULT,
    val bubbleColor: BubbleColor = BubbleColor.ACCENT,
    /** Swiping a chat to the right (start to end) in the chat list. */
    val swipeRight: SwipeAction = SwipeAction.READ,
    /** Swiping a chat to the left (end to start) in the chat list. */
    val swipeLeft: SwipeAction = SwipeAction.ARCHIVE,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val lockTimeout = stringPreferencesKey("lock_timeout")
        val hideScreenshots = booleanPreferencesKey("hide_screenshots")
        val hideRecents = booleanPreferencesKey("hide_recents")
        val notificationContent = stringPreferencesKey("notification_content")
        val deliveryReports = booleanPreferencesKey("delivery_reports")
        val autoDownloadMms = booleanPreferencesKey("auto_download_mms")
        val groupAsMms = booleanPreferencesKey("group_as_mms")
        val defaultSim = intPreferencesKey("default_sim")
        val undoSend = stringPreferencesKey("undo_send")
        val copyCodes = booleanPreferencesKey("copy_codes")
        val separateUnknown = booleanPreferencesKey("separate_unknown")
        val notifyUnknown = booleanPreferencesKey("notify_unknown")
        val autoDelete = stringPreferencesKey("auto_delete")
        val textSize = stringPreferencesKey("text_size")
        val bubbleColor = stringPreferencesKey("bubble_color")
        val swipeRight = stringPreferencesKey("swipe_right")
        val swipeLeft = stringPreferencesKey("swipe_left")
        val bubbles = stringPreferencesKey("bubbles")
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** The stored settings, without [settings]' placeholder defaults at start-up. */
    val stored: Flow<AppSettings> = context.appDataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[K.theme], ThemeChoice.SYSTEM),
        dynamicColor = this[K.dynamic] ?: false,
        lockTimeout = enumOr(this[K.lockTimeout], LockTimeout.IMMEDIATELY),
        hideScreenshots = this[K.hideScreenshots] ?: false,
        hideRecents = this[K.hideRecents] ?: false,
        notificationContent = enumOr(this[K.notificationContent], NotificationContent.NAME_AND_MESSAGE),
        deliveryReports = this[K.deliveryReports] ?: false,
        autoDownloadMms = this[K.autoDownloadMms] ?: true,
        groupAsMms = this[K.groupAsMms] ?: true,
        defaultSim = this[K.defaultSim] ?: -1,
        undoSend = enumOr(this[K.undoSend], UndoSend.SECONDS_5),
        copyCodes = this[K.copyCodes] ?: true,
        separateUnknown = this[K.separateUnknown] ?: false,
        notifyUnknown = this[K.notifyUnknown] ?: true,
        autoDelete = enumOr(this[K.autoDelete], AutoDelete.NEVER),
        textSize = enumOr(this[K.textSize], TextSize.DEFAULT),
        bubbleColor = enumOr(this[K.bubbleColor], BubbleColor.ACCENT),
        swipeRight = enumOr(this[K.swipeRight], SwipeAction.READ),
        swipeLeft = enumOr(this[K.swipeLeft], SwipeAction.ARCHIVE),
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.dynamic] = s.dynamicColor
            p[K.lockTimeout] = s.lockTimeout.name
            p[K.hideScreenshots] = s.hideScreenshots
            p[K.hideRecents] = s.hideRecents
            p[K.notificationContent] = s.notificationContent.name
            p[K.deliveryReports] = s.deliveryReports
            p[K.autoDownloadMms] = s.autoDownloadMms
            p[K.groupAsMms] = s.groupAsMms
            p[K.defaultSim] = s.defaultSim
            p[K.undoSend] = s.undoSend.name
            p[K.copyCodes] = s.copyCodes
            p[K.separateUnknown] = s.separateUnknown
            p[K.notifyUnknown] = s.notifyUnknown
            p[K.autoDelete] = s.autoDelete.name
            p[K.textSize] = s.textSize.name
            p[K.bubbleColor] = s.bubbleColor.name
            p[K.swipeRight] = s.swipeRight.name
            p[K.swipeLeft] = s.swipeLeft.name
            // Bubbles were removed in 0.6.3; forget their old setting.
            p.remove(K.bubbles)
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
