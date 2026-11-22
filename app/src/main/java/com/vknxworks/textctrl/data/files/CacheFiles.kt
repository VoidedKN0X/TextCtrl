package com.vknxworks.textctrl.data.files

import android.content.Context
import java.io.File

/**
 * Copies TextCtrl leaves in its cache: attachments opened in another app (`cache/open`), contact cards made
 * to attach (`cache/vcards`) and picture message files for Android's MMS service (`cache/mms`). Each is only
 * needed for a short while; this removes the ones that are old enough to be left over.
 */
object CacheFiles {
    /** An attachment another app opened: long enough for that app to have read it. */
    const val OPEN_MAX_AGE = 60 * 60_000L
    /** A contact card: a scheduled message keeps its own copy, so a day is plenty. */
    const val VCARD_MAX_AGE = 24 * 60 * 60_000L
    /** A picture message file the MMS service never answered for (normally deleted at once). */
    const val MMS_MAX_AGE = 24 * 60 * 60_000L

    /** Deletes the files in [dir] last changed more than [maxAgeMs] before [now]; returns how many. */
    fun deleteOlderThan(dir: File, maxAgeMs: Long, now: Long = System.currentTimeMillis()): Int {
        var deleted = 0
        dir.listFiles()?.forEach { f ->
            if (f.isFile && now - f.lastModified() > maxAgeMs && f.delete()) deleted++
        }
        return deleted
    }

    /** Removes left-over copies. Call off the main thread (at start-up and when the app locks). */
    fun clearLeftovers(context: Context) {
        val cache = context.cacheDir
        runCatching { deleteOlderThan(File(cache, "open"), OPEN_MAX_AGE) }
        runCatching { deleteOlderThan(File(cache, "vcards"), VCARD_MAX_AGE) }
        runCatching { deleteOlderThan(File(cache, "mms"), MMS_MAX_AGE) }
    }
}
