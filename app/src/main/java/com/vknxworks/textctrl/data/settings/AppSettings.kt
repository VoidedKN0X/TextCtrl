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
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
