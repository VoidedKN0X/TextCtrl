package com.vknxworks.textctrl.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.vknxworks.textctrl.MainActivity
import com.vknxworks.textctrl.R
import com.vknxworks.textctrl.data.lock.LockVault
import com.vknxworks.textctrl.data.settings.ChatPrefs
import com.vknxworks.textctrl.data.settings.NotificationContent
import com.vknxworks.textctrl.data.settings.SettingsRepository
import com.vknxworks.textctrl.sms.Contacts
import com.vknxworks.textctrl.sms.Message
import com.vknxworks.textctrl.sms.MessageStore
import com.vknxworks.textctrl.sms.OneTimeCode
import kotlinx.coroutines.flow.first
import com.vknxworks.textctrl.str

/**
 * New-message notifications, one per chat, with Reply and Mark as read. What they show follows
 * "Notification content"; the lock screen only ever shows "New message" unless Android is set to show
 * everything there. Android's suggested replies and actions are turned off: they'd have the system read
 * your messages to make them.
 */
class Notifier(
    private val context: Context,
    private val store: MessageStore,
    private val contacts: Contacts,
    private val settings: SettingsRepository,
    private val chats: ChatPrefs,
    private val vault: LockVault,
) {
    private val nm = NotificationManagerCompat.from(context)

    /** The chat open on screen right now; its new messages are marked read instead of notified. */
    @Volatile var visibleChat: Long? = null

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, str(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH).apply {
                description = str(R.string.channel_messages_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FAILED, str(R.string.channel_failed), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = str(R.string.channel_failed_desc)
            },
        )
    }

    private val allowed: Boolean
        get() = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            nm.areNotificationsEnabled()

    /** Something arrived in [threadId]. */
    suspend fun onIncoming(threadId: Long) {
        if (threadId == visibleChat) {
            store.markRead(threadId)
            return
        }
        if (chats.isMuted(threadId)) return
        val s = settings.stored.first()
        // Texts from strangers can stay quiet when they're kept apart from your chats.
        if (s.separateUnknown && !s.notifyUnknown && isUnknown(threadId)) return
        show(threadId, alert = true)
    }

    /** One person, not in your contacts, and you never wrote to them. */
    suspend fun isUnknown(threadId: Long): Boolean {
        val addresses = store.threadAddresses(threadId)
        if (addresses.size != 1 || !contacts.granted) return false
        return contacts.lookup(addresses[0]).name == null && !store.hasOutgoing(threadId)
    }

    /** Shows (or updates, or removes) the notification of [threadId] for its unread messages. */
    suspend fun show(threadId: Long, alert: Boolean = false) {
        if (!allowed) return
        val unread = store.unreadIncoming(threadId)
        if (unread.isEmpty()) {
            cancel(threadId)
            return
        }
        val s = settings.stored.first()
        val hidden = chats.isHidden(threadId)
        // A hidden chat never shows who it's with or what was said.
        val content = if (hidden) NotificationContent.NOTHING else s.notificationContent
        val addresses = store.threadAddresses(threadId).ifEmpty { listOf(unread.last().address) }
        val group = addresses.size > 1
        val title = addresses.joinToString(", ") { contacts.displayName(it) }
        val open = openIntent(threadId)

        val builder = NotificationCompat.Builder(context, channelFor(threadId))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .setWhen(unread.last().date)
            .setShowWhen(true)
            .setNumber(unread.size)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(open))
            .setAllowSystemGeneratedContextualActions(false)
            .addAction(replyAction(threadId))
            .addAction(markReadAction(threadId))
        // "Copy code" for a one-time code in the newest message.
        if (s.copyCodes) {
            val code = unread.lastOrNull()?.body?.let { OneTimeCode.find(it) }
            if (code != null) builder.addAction(copyCodeAction(threadId, code, showCode = content == NotificationContent.NAME_AND_MESSAGE))
        }

        when (content) {
            NotificationContent.NOTHING -> builder
                .setContentTitle(str(R.string.app_name))
                .setContentText(context.resources.getQuantityString(R.plurals.notify_new_messages, unread.size, unread.size))
            else -> {
                val showText = content == NotificationContent.NAME_AND_MESSAGE
                val me = Person.Builder().setName(str(R.string.you)).setKey("me").build()
                val style = NotificationCompat.MessagingStyle(me)
                if (group) {
                    style.setConversationTitle(title)
                    style.setGroupConversation(true)
                }
                val people = HashMap<String, Person>()
                for (m in unread.takeLast(8)) {
                    val person = people.getOrPut(m.address) { personFor(m.address) }
                    style.addMessage(if (showText) preview(m) else str(R.string.notify_new_message), m.date, person)
                }
                builder.setStyle(style).setContentTitle(title)
                // Conversation shortcuts put the chat in Android's Conversations section. Not with the app
                // lock on: they'd show names in the launcher and share sheet.
                if (!vault.enabled && !hidden) {
                    runCatching {
                        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId(threadId))
                            .setShortLabel(title.take(40).ifBlank { str(R.string.app_name) })
                            .setLongLived(true)
                            .setIntent(openActivityIntent(threadId))
                            .setPersons(people.values.toTypedArray())
                            .setIcon(people.values.firstOrNull()?.icon ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                            .build()
                        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
                        builder.setShortcutId(shortcutId(threadId))
                    }
                }
            }
        }
        runCatching { nm.notify(threadId.toInt(), builder.build()) }
    }

    /** A message to [threadId] couldn't be sent. */
    suspend fun notifyFailed(threadId: Long) {
        if (!allowed || threadId == visibleChat) return
        val content = if (chats.isHidden(threadId)) NotificationContent.NOTHING else settings.stored.first().notificationContent
        val addresses = store.threadAddresses(threadId)
        val text = if (content == NotificationContent.NOTHING || addresses.isEmpty()) str(R.string.notify_failed_body_private)
        else str(R.string.notify_failed_body, addresses.joinToString(", ") { contacts.displayName(it) })
        val n = NotificationCompat.Builder(context, CHANNEL_FAILED)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentTitle(str(R.string.notify_failed_title))
            .setContentText(text)
            .setContentIntent(openIntent(threadId))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        runCatching { nm.notify(TAG_FAILED, threadId.toInt(), n) }
    }

    // ---------------------------------------------------------------- per-chat sound

    private fun chatChannelId(threadId: Long) = "chat_$threadId"

    private fun channelFor(threadId: Long): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = chatChannelId(threadId)
        return if (manager?.getNotificationChannel(id) != null) id else CHANNEL_MESSAGES
    }

    /**
     * A notification channel of this chat's own, so it can have its own sound and vibration in Android's
     * settings. Returns its id. [name] is how the chat shows there.
     */
    fun chatChannel(threadId: Long, name: String): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = chatChannelId(threadId)
        if (manager?.getNotificationChannel(id) == null) {
            val base = manager?.getNotificationChannel(CHANNEL_MESSAGES)
            manager?.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = str(R.string.channel_chat_desc)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                    base?.let {
                        setSound(it.sound, it.audioAttributes)
                        enableVibration(it.shouldVibrate())
                        vibrationPattern = it.vibrationPattern
                    }
                },
            )
        }
        return id
    }

    fun hasChatChannel(threadId: Long): Boolean =
        context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(chatChannelId(threadId)) != null

    /** Back to the sound of all messages. */
    fun deleteChatChannel(threadId: Long) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.deleteNotificationChannel(chatChannelId(threadId)) }
    }

    fun cancel(threadId: Long) {
        nm.cancel(threadId.toInt())
        nm.cancel(TAG_FAILED, threadId.toInt())
    }

    fun cancelAll() = nm.cancelAll()

    /** Forgets the chat's shortcut, for example after it's deleted. */
    fun removeShortcut(threadId: Long) {
        runCatching { ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(shortcutId(threadId))) }
    }

    /** With the app lock on, conversation shortcuts are removed so names don't show outside the app. */
    fun removeAllShortcuts() {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
        runCatching {
            val ids = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_CACHED).map { it.id }
            if (ids.isNotEmpty()) ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
        }
    }

    private fun preview(m: Message): String {
        val text = listOfNotNull(m.subject, m.body.takeIf { it.isNotBlank() }).joinToString("\n")
        return when {
            m.notDownloaded -> str(R.string.mms_waiting)
            text.isNotBlank() -> text
            m.attachments.any { it.isImage } -> str(R.string.attachment_picture)
            m.attachments.any { it.isVideo } -> str(R.string.attachment_video)
            m.attachments.any { it.isAudio } -> str(R.string.attachment_audio)
            m.attachments.isNotEmpty() -> str(R.string.attachment_file)
            else -> str(R.string.notify_new_message)
        }
    }

    private fun personFor(address: String): Person {
        val contact = contacts.lookup(address)
        val builder = Person.Builder()
            .setName(contact.name ?: Contacts.formatNumber(address))
            .setKey(address)
        contact.lookupUri?.let { builder.setUri(it.toString()) }
        contact.photo?.let { photo -> loadBitmap(photo)?.let { builder.setIcon(IconCompat.createWithBitmap(it)) } }
        return builder.build()
    }

    private fun loadBitmap(uri: Uri): Bitmap? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun publicVersion(open: PendingIntent) = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(ACCENT)
        .setContentTitle(str(R.string.app_name))
        .setContentText(str(R.string.notify_new_message))
        .setContentIntent(open)
        .build()

    private fun openActivityIntent(threadId: Long) = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_CHAT)
        .putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun openIntent(threadId: Long): PendingIntent = PendingIntent.getActivity(
        context, threadId.toInt(), openActivityIntent(threadId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun replyAction(threadId: Long): NotificationCompat.Action {
        val input = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY)
            .setLabel(str(R.string.notify_reply_hint))
            .build()
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_REPLY)
            .setData(Uri.parse("textctrl://reply/$threadId"))
            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
        // Mutable so Android can add the typed reply; explicit, so only TextCtrl gets it.
        val pi = PendingIntent.getBroadcast(context, threadId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, str(R.string.notify_reply), pi)
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun markReadAction(threadId: Long): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_MARK_READ)
            .setData(Uri.parse("textctrl://read/$threadId"))
            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
        val pi = PendingIntent.getBroadcast(context, threadId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, str(R.string.notify_mark_read), pi)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    private fun copyCodeAction(threadId: Long, code: String, showCode: Boolean): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_COPY_CODE)
            .setData(Uri.parse("textctrl://code/$threadId"))
            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            .putExtra(NotificationActionReceiver.EXTRA_CODE, code)
        val pi = PendingIntent.getBroadcast(context, threadId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val label = if (showCode) str(R.string.copy_code_value, code) else str(R.string.copy_code)
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, label, pi)
            .setShowsUserInterface(false)
            .build()
    }

    private fun shortcutId(threadId: Long) = "chat_$threadId"

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_FAILED = "failed"
        private const val TAG_FAILED = "failed"
        private const val ACCENT = 0xFF7FC8F8.toInt()
    }
}
