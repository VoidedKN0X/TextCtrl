package com.vknxworks.textctrl.sms

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A blocked number, as stored in Android's own block list. */
data class BlockedNumber(val id: Long, val number: String)

/**
 * Android's block list, shared with the phone app: texts and calls from these numbers never reach you.
 * Only the default SMS and phone apps may change it.
 */
class Blocklist(private val context: Context) {

    suspend fun all(): List<BlockedNumber> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ID, BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null, null, null,
            )?.use { c -> buildList { while (c.moveToNext()) add(BlockedNumber(c.getLong(0), c.getString(1).orEmpty())) } }
        }.getOrNull().orEmpty()
    }

    suspend fun isBlocked(number: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false)
    }

    suspend fun block(number: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
        context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
        Unit
    }

    suspend fun unblock(number: String) = withContext(Dispatchers.IO) {
        BlockedNumberContract.unblock(context, number)
        Unit
    }

    val canBlock: Boolean
        get() = runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)
}
