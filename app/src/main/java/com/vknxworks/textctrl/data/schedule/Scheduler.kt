package com.vknxworks.textctrl.data.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.data.crypto.KeystoreCrypto
import com.vknxworks.textctrl.notify.Notifier
import com.vknxworks.textctrl.sms.Attachment
import com.vknxworks.textctrl.sms.Messenger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom

/** A file waiting in TextCtrl's own storage to go out with a scheduled message. */
@Serializable
data class StoredFile(val path: String, val mime: String, val name: String)

/** A message that goes out later: at a time you chose, or after the undo-send window. */
@Serializable
data class Scheduled(
    val id: Long,
    val threadId: Long,
    val addresses: List<String>,
    val text: String,
    val files: List<StoredFile> = emptyList(),
    /** Milliseconds since 1970. */
    val at: Long,
    val subId: Int,
    /** Just sent, waiting out the undo window (rather than scheduled for later). */
    val undo: Boolean = false,
)

/**
 * Messages that go out later. They're kept encrypted with a key in the phone's secure hardware (not the
 * app lock's PIN: they must go out while the app is locked), with their attachments copied into TextCtrl's
 * private storage, and an exact alarm wakes TextCtrl to send each one, also after a restart.
 */
class Scheduler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val messenger: Messenger,
    private val notifier: Notifier,
) {
    private val file = File(context.filesDir, "scheduled.enc")
    private val filesDir = File(context.filesDir, "scheduled")
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()
    private val lock = Any()

    private val _items = MutableStateFlow(read())
    /** Everything waiting to go out, soonest first. */
    val items: StateFlow<List<Scheduled>> = _items.asStateFlow()

    /** Saves a message to send at [at] (copying its attachments) and sets an alarm for it. */
    suspend fun add(
        threadId: Long,
        addresses: List<String>,
        text: String,
        attachments: List<Attachment>,
        at: Long,
        subId: Int,
        undo: Boolean,
    ): Scheduled = withContext(Dispatchers.IO) {
        val id = (random.nextLong() and Long.MAX_VALUE).coerceAtLeast(1)
        val dir = File(filesDir, id.toString()).apply { mkdirs() }
        val files = attachments.mapIndexed { i, a ->
            val safe = a.name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "file" }
            val target = File(dir, "${i}_$safe")
            context.contentResolver.openInputStream(a.uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: throw java.io.IOException(a.name)
            StoredFile(target.path, a.mime, a.name)
        }
        val item = Scheduled(id, threadId, addresses, text, files, at, subId, undo)
        synchronized(lock) {
            _items.value = (_items.value + item).sortedBy { it.at }
            write()
        }
        setAlarm(item)
        if (undo) {
            // The alarm is the backup; while TextCtrl runs, this sends it right on time.
            scope.launch {
                delay((at - System.currentTimeMillis()).coerceAtLeast(0))
                fire(id)
            }
        }
        item
    }

    /** Takes a message off the schedule. With [keepFiles], its attachments stay (to edit it). */
    fun cancel(id: Long, keepFiles: Boolean = false): Scheduled? {
        val removed = take(id) ?: return null
        cancelAlarm(id)
        if (!keepFiles) File(filesDir, id.toString()).deleteRecursively()
        return removed
    }

    /** The attachments of [item], to put back in the text field when editing it. */
    fun attachmentsOf(item: Scheduled): List<Attachment> =
        item.files.map { Attachment(Uri.fromFile(File(it.path)), it.mime, it.name) }

    /** Sends a scheduled message now (its time came, or you tapped Send now). */
    suspend fun fire(id: Long) {
        val item = take(id) ?: return // already sent or cancelled
        cancelAlarm(id)
        try {
            messenger.send(item.addresses, item.text, attachmentsOf(item), item.subId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Couldn't even be stored as a failed message (for example TextCtrl isn't the SMS app): say so.
            notifier.notifyFailed(item.threadId)
        } finally {
            withContext(Dispatchers.IO) { File(filesDir, id.toString()).deleteRecursively() }
        }
    }

    /** After a restart or update: alarms are gone, so set them again, and send what's overdue. */
    fun restore() {
        val now = System.currentTimeMillis()
        for (item in _items.value) {
            if (item.at <= now) scope.launch { fire(item.id) } else setAlarm(item)
        }
        // Leftover attachment folders (from edited messages) older than a day.
        scope.launch(Dispatchers.IO) {
            val keep = _items.value.map { it.id.toString() }.toSet()
            filesDir.listFiles()?.filter { it.name !in keep && now - it.lastModified() > 24 * 3_600_000L }?.forEach { it.deleteRecursively() }
        }
    }

    private fun take(id: Long): Scheduled? = synchronized(lock) {
        val item = _items.value.firstOrNull { it.id == id } ?: return null
        _items.value = _items.value - item
        write()
        item
    }

    // ---------------------------------------------------------------- alarms

    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ScheduleReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("textctrl://scheduled/$id"))
            .putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun setAlarm(item: Scheduled) {
        val am = alarms ?: return
        val pi = alarmIntent(item.id)
        runCatching {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
        }
    }

    private fun cancelAlarm(id: Long) {
        runCatching { alarms?.cancel(alarmIntent(id)) }
    }

    /** A daily wake-up for auto-delete. Inexact, so Android can fit it in with other work. */
    fun setDailyCleanup(enabled: Boolean) {
        val am = alarms ?: return
        val pi = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, ScheduleReceiver::class.java).setAction(ACTION_CLEANUP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            if (enabled) am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR, AlarmManager.INTERVAL_DAY, pi)
            else am.cancel(pi)
        }
    }

    // ---------------------------------------------------------------- storage

    private fun read(): List<Scheduled> = runCatching {
        if (!file.exists()) return emptyList()
        val plain = KeystoreCrypto.decrypt(file.readText()) ?: return emptyList()
        json.decodeFromString(ListSerializer(Scheduled.serializer()), plain).sortedBy { it.at }
    }.getOrDefault(emptyList())

    private fun write() {
        val list = _items.value
        if (list.isEmpty()) {
            file.delete()
            return
        }
        val plain = json.encodeToString(ListSerializer(Scheduled.serializer()), list)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(KeystoreCrypto.encrypt(plain))
        tmp.renameTo(file)
    }

    companion object {
        const val ACTION_FIRE = "com.vknxworks.textctrl.SCHEDULED_FIRE"
        const val ACTION_CLEANUP = "com.vknxworks.textctrl.AUTO_DELETE"
        const val EXTRA_ID = "id"
    }
}

/** Wakes TextCtrl to send a scheduled message, or to delete old messages. Not reachable by other apps. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.container
        val pending = goAsync()
        c.appScope.launch {
            try {
                when (intent.action) {
                    Scheduler.ACTION_FIRE -> intent.getLongExtra(Scheduler.EXTRA_ID, 0L).takeIf { it != 0L }?.let { c.scheduler.fire(it) }
                    Scheduler.ACTION_CLEANUP -> c.cleaner.run()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}

/** After the phone restarts or TextCtrl is updated, Android's alarms are gone: set them again. */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // Starting the app (TextCtrlApp.onCreate) restores the schedule and the daily clean-up.
        context.container
    }
}
