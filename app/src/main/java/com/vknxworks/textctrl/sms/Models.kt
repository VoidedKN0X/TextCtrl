package com.vknxworks.textctrl.sms

import android.net.Uri

/** One chat: everything sent to and from the same person, or the same group. */
data class Conversation(
    val threadId: Long,
    val addresses: List<String>,
    val snippet: String,
    /** Milliseconds since 1970. */
    val date: Long,
    val unread: Int,
    val hasAttachment: Boolean,
    /** A message in it couldn't be sent. */
    val hasError: Boolean,
    /** You've sent something in it (so its sender isn't a stranger). */
    val hasOutgoing: Boolean = false,
) {
    val isGroup: Boolean get() = addresses.size > 1
}

enum class SendStatus { NONE, SENDING, SENT, DELIVERED, NOT_DELIVERED, FAILED }

/** A text (SMS) or picture message (MMS). */
data class Message(
    val id: Long,
    val isMms: Boolean,
    val threadId: Long,
    /** The sender of an incoming message (also in groups), or the recipient of a sent text. */
    val address: String,
    val body: String,
    /** Milliseconds since 1970. */
    val date: Long,
    val incoming: Boolean,
    val read: Boolean,
    val status: SendStatus,
    val subId: Int,
    val subject: String? = null,
    val parts: List<MmsPart> = emptyList(),
    /** A picture message that is still waiting on the network to be fetched. */
    val notDownloaded: Boolean = false,
    /** Size of a picture message that isn't fetched yet, in bytes. */
    val mmsSize: Long = 0,
    /** Milliseconds since 1970 when a waiting picture message expires, or 0. */
    val mmsExpiry: Long = 0,
) {
    /** Unique across texts and picture messages. */
    val key: String get() = (if (isMms) "m" else "s") + id

    val uri: Uri get() = Uri.parse(if (isMms) "content://mms/$id" else "content://sms/$id")

    val attachments: List<MmsPart> get() = parts.filter { !it.isText && !it.isSmil }
}

/** One part of a picture message, read from Android's message store. */
data class MmsPart(
    val id: Long,
    val contentType: String,
    val name: String?,
    val text: String?,
) {
    val uri: Uri get() = Uri.parse("content://mms/part/$id")
    val isText: Boolean get() = contentType.equals("text/plain", ignoreCase = true)
    val isSmil: Boolean get() = contentType.equals("application/smil", ignoreCase = true)
    val isImage: Boolean get() = contentType.startsWith("image/", ignoreCase = true)
    val isVideo: Boolean get() = contentType.startsWith("video/", ignoreCase = true)
    val isAudio: Boolean get() = contentType.startsWith("audio/", ignoreCase = true)
    val isContactCard: Boolean get() = contentType.contains("vcard", ignoreCase = true)
}

/** A message found by search. */
data class SearchHit(
    val threadId: Long,
    val messageKey: String,
    val address: String,
    val body: String,
    val date: Long,
    val incoming: Boolean,
)

/** A file to send in a picture message. */
data class Attachment(val uri: Uri, val mime: String, val name: String)
