package com.vknxworks.textctrl.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** A contact on the phone, found by number. */
data class Contact(
    val name: String?,
    val photo: Uri?,
    val lookupUri: Uri?,
)

/** A phone number from your contacts, for starting a chat. */
data class ContactPhone(
    val name: String,
    val number: String,
    val label: String,
    val photo: Uri?,
)

/** Names and pictures from the phone's contacts. Read only, on the phone; nothing leaves it. */
class Contacts(private val context: Context) {
    private val cache = ConcurrentHashMap<String, Contact>()
    private val none = Contact(null, null, null)

    private val _changes = MutableStateFlow(0)
    /** Goes up when contacts change, so names and pictures are looked up again. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private var observing = false

    val granted: Boolean
        get() = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Call after the contacts permission is granted, to start following changes. */
    fun refresh() {
        cache.clear()
        if (granted && !observing) {
            observing = runCatching {
                context.contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI, true,
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            cache.clear()
                            _changes.value++
                        }
                    },
                )
            }.isSuccess
        }
        _changes.value++
    }

    /** The contact for [number] (blocking: call off the main thread). */
    fun lookup(number: String): Contact {
        if (number.isBlank() || !granted) return none
        cache[number]?.let { return it }
        val found = runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI, ContactsContract.PhoneLookup.LOOKUP_KEY, ContactsContract.PhoneLookup._ID),
                null, null, null,
            )?.use { c ->
                if (!c.moveToFirst()) null
                else Contact(
                    name = c.getString(0),
                    photo = c.getString(1)?.let(Uri::parse),
                    lookupUri = c.getString(2)?.let { key -> ContactsContract.Contacts.getLookupUri(c.getLong(3), key) },
                )
            }
        }.getOrNull() ?: none
        cache[number] = found
        return found
    }

    /** A name for [number]: the contact's name, or the number written nicely. */
    fun displayName(number: String): String = lookup(number).name ?: formatNumber(number)

    /** Phone numbers in your contacts that match [query] (all of them when it's empty). */
    suspend fun search(query: String): List<ContactPhone> = withContext(Dispatchers.IO) {
        if (!granted) return@withContext emptyList()
        val q = query.trim()
        val uri = if (q.isEmpty()) ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        else Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(q))
        val out = ArrayList<ContactPhone>()
        val seen = HashSet<String>()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < 500) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    val key = name + "|" + number.filter { it.isDigit() || it == '+' }
                    if (!seen.add(key)) continue
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, c.getInt(2), c.getString(3)).toString()
                    out += ContactPhone(name, number, label, c.getString(4)?.let(Uri::parse))
                }
            }
        }
        out
    }

    companion object {
        /** A number as people write it in their country, e.g. "+32 470 12 34 56". */
        fun formatNumber(number: String): String {
            if (number.any { it.isLetter() } || number.contains('@')) return number
            val country = Locale.getDefault().country.ifEmpty { "US" }
            return PhoneNumberUtils.formatNumber(number, country) ?: number
        }

        /** Same phone number, written differently ("0470…" and "+32470…"). */
        fun same(a: String, b: String): Boolean =
            a == b || (!a.contains('@') && !b.contains('@') && PhoneNumberUtils.compare(a, b))
    }
}
