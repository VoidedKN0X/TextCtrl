package com.vknxworks.textctrl.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.ui.components.copySensitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Reply and Mark as read from a notification. Not exported: only TextCtrl's own notifications reach it. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L).takeIf { it >= 0 } ?: return
        val c = context.container
        if (intent.action == ACTION_COPY_CODE) {
            val code = intent.getStringExtra(EXTRA_CODE)?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: return
            copySensitive(context, context.getString(com.vknxworks.textctrl.R.string.copy_code), code)
            android.widget.Toast.makeText(context, context.getString(com.vknxworks.textctrl.R.string.code_copied), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val reply = if (intent.action == ACTION_REPLY) {
            RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim().orEmpty()
        } else {
            null
        }
        val pending = goAsync()
        c.appScope.launch {
            try {
                if (reply != null && reply.isEmpty()) {
                    // An empty reply: put the notification back as it was, so its reply field closes.
                    c.notifier.show(threadId)
                    return@launch
                }
                if (reply != null) {
                    val to = c.store.threadAddresses(threadId)
                    if (to.isNotEmpty()) c.messenger.send(to, reply, emptyList(), c.settings.settings.value.defaultSim)
                }
                c.store.markRead(threadId)
                c.notifier.cancel(threadId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // A reply that fails stays in the chat as "not sent", with its own notification.
                c.notifier.cancel(threadId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.vknxworks.textctrl.REPLY"
        const val ACTION_MARK_READ = "com.vknxworks.textctrl.MARK_READ"
        const val ACTION_COPY_CODE = "com.vknxworks.textctrl.COPY_CODE"
        const val EXTRA_CODE = "code"
        const val EXTRA_THREAD_ID = "thread_id"
        const val KEY_REPLY = "reply"
    }
}
