package com.vknxworks.textctrl.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.vknxworks.textctrl.MainActivity
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.data.lock.LockVault
import com.vknxworks.textctrl.data.settings.ChatPrefs
import com.vknxworks.textctrl.data.settings.NotificationContent
import com.vknxworks.textctrl.data.settings.SettingsRepository
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.sms.MessageStore
import com.vknxworks.textctrl.ui.components.shortTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The "Recent chats" home screen widget. */
class ChatsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val c = context.container
        val pending = goAsync()
        c.appScope.launch {
            try {
                c.widgets.update()
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Keeps the widget up to date with your newest chats. With the app lock on it only says TextCtrl is
 * locked; hidden and archived chats never show; message text follows "Notification content".
 */
class Widgets(
    private val context: Context,
    private val scope: CoroutineScope,
    private val store: MessageStore,
    private val contacts: Contacts,
    private val chats: ChatPrefs,
    private val settings: SettingsRepository,
    private val vault: LockVault,
) {
    private val component = ComponentName(context, ChatsWidgetProvider::class.java)

    fun start() {
        scope.launch {
            combine(store.changes, contacts.changes, chats.hidden, chats.archived, chats.spam, settings.stored) { _ -> }
                .debounce(1500)
                .collect { runCatching { update() } }
        }
    }

    suspend fun update() = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(context) ?: return@withContext
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return@withContext
        val views = RemoteViews(context.packageName, R.layout.widget_chats)
        views.setOnClickPendingIntent(R.id.widget_add, activity(0, Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_CHAT)))
        views.setOnClickPendingIntent(R.id.widget_title, activity(1, Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)))
        views.removeAllViews(R.id.widget_rows)

        val s = settings.stored.first()
        when {
            vault.enabled -> views.addView(R.id.widget_rows, message(R.string.widget_locked))
            !context.container.isDefaultSmsApp -> views.addView(R.id.widget_rows, message(R.string.widget_not_default))
            else -> {
                val hidden = chats.hidden.value
                val archived = chats.archived.value
                val spam = chats.spam.value
                val list = runCatching { store.conversations() }.getOrDefault(emptyList())
                    .filter { it.threadId !in hidden && it.threadId !in archived && it.threadId !in spam }
                    .filter { conv ->
                        !s.separateUnknown || conv.isGroup || conv.hasOutgoing || !contacts.granted ||
                            conv.addresses.firstOrNull()?.let { contacts.lookup(it).name } != null
                    }
                    .take(5)
                if (list.isEmpty()) views.addView(R.id.widget_rows, message(R.string.widget_empty))
                for (conv in list) {
                    val row = RemoteViews(context.packageName, R.layout.widget_chat_row)
                    val title = conv.addresses.joinToString(", ") { contacts.displayName(it) }.ifBlank { "…" }
                    row.setTextViewText(R.id.row_name, title)
                    row.setTextViewText(
                        R.id.row_snippet,
                        if (s.notificationContent == NotificationContent.NAME_AND_MESSAGE) conv.snippet.replace('\n', ' ').ifBlank { context.getString(R.string.attachment_picture_message) }
                        else context.resources.getQuantityString(R.plurals.notify_new_messages, conv.unread, conv.unread).takeIf { conv.unread > 0 }.orEmpty(),
                    )
                    row.setTextViewText(R.id.row_time, shortTime(context, conv.date))
                    row.setViewVisibility(R.id.row_unread, if (conv.unread > 0) View.VISIBLE else View.GONE)
                    row.setOnClickPendingIntent(
                        R.id.row_root,
                        activity(
                            100 + conv.threadId.toInt(),
                            Intent(context, MainActivity::class.java)
                                .setAction(MainActivity.ACTION_OPEN_CHAT)
                                .putExtra(MainActivity.EXTRA_THREAD_ID, conv.threadId),
                        ),
                    )
                    views.addView(R.id.widget_rows, row)
                }
            }
        }
        manager.updateAppWidget(ids, views)
    }

    private fun message(text: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_message).apply { setTextViewText(R.id.widget_message, context.getString(text)) }

    private fun activity(code: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Suppress("unused")
    private fun formatted(n: String) = Contacts.formatNumber(n)
}
