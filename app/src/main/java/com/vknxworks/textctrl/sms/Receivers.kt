package com.vknxworks.textctrl.sms

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.vknxworks.textctrl.container
import com.vknxworks.textctrl.sms.mms.PduParser
import com.vknxworks.textctrl.sms.mms.PduType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the broadcast alive until it's done. */
private fun BroadcastReceiver.async(context: Context, block: suspend () -> Unit) {
    val pending = goAsync()
    context.container.appScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Nothing to show here; the message keeps its state in the store.
        } finally {
            pending.finish()
        }
    }
}

/**
 * A text arrived. Android hands it to the default SMS app straight from the mobile network, the moment
 * it's received: no push service is involved, so it's instant and works without Google or any server.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull()?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
        async(context) {
            val c = context.container
            // One delivery is usually one message split into parts; keep senders apart just in case.
            for ((address, pieces) in parts.groupBy { it.displayOriginatingAddress ?: it.originatingAddress.orEmpty() }) {
                val first = pieces.first()
                val body = pieces.joinToString("") { it.displayMessageBody ?: it.messageBody.orEmpty() }
                val uri = c.store.insertIncomingSms(
                    address = address,
                    body = body,
                    sentAt = first.timestampMillis,
                    subId = subId,
                    protocol = first.protocolIdentifier,
                    serviceCenter = first.serviceCenterAddress,
                    replyPath = first.isReplyPathPresent,
                ) ?: continue
                val thread = c.store.threadOf(uri) ?: continue
                c.chats.unarchive(thread)
                c.notifier.onIncoming(thread)
            }
            c.store.touch()
        }
    }
}

/** A picture message waits on the network. Android hands the notice to the default SMS app. */
class MmsPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data") ?: return
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, intent.getIntExtra("subscription", -1))
        val pdu = PduParser(data).parse() ?: return
        if (pdu.type != PduType.NOTIFICATION_IND) return
        async(context) { context.container.messenger.onMmsNotification(pdu, subId) }
    }
}

/** Sent and delivered reports for texts TextCtrl sent. Not reachable by other apps. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = resultCode
        async(context) { context.container.messenger.onSmsStatus(intent, result) }
    }
}

/** Results from Android's MMS service: a picture message sent, or fetched. Not reachable by other apps. */
class MmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = resultCode
        async(context) {
            when (intent.action) {
                Messenger.ACTION_MMS_SENT -> context.container.messenger.onMmsSent(intent, result)
                Messenger.ACTION_MMS_DOWNLOADED -> context.container.messenger.onMmsDownloaded(intent, result)
            }
        }
    }
}

/**
 * "Reply with a message" when you decline a call. Only the phone app can start this
 * (SEND_RESPOND_VIA_MESSAGE); it sends the text you picked there.
 */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        val to = intent?.data?.let { recipientsOf(it) }.orEmpty()
        if (intent?.action != "android.intent.action.RESPOND_VIA_MESSAGE" || text == null || to.isEmpty()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val c = container
        c.appScope.launch {
            try {
                c.messenger.send(to, text, emptyList(), c.settings.settings.value.defaultSim)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // The failed text shows in its chat, with a notification.
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }
}

/** The numbers in an sms:, smsto:, mms: or mmsto: address ("smsto:+3247…,+3248…"). */
fun recipientsOf(uri: android.net.Uri): List<String> {
    val raw = uri.schemeSpecificPart.orEmpty().substringBefore('?')
    return raw.split(',', ';').map { android.net.Uri.decode(it).trim() }.filter { it.isNotEmpty() }
}
