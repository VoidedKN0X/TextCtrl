package com.vknxworks.textctrl.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheFilesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun deletesOnlyFilesOlderThanTheMaximumAge() {
        val dir = tmp.newFolder("open")
        val now = 10_000_000L
        val old = File(dir, "old.jpg").apply { writeText("x"); setLastModified(now - CacheFiles.OPEN_MAX_AGE - 1_000) }
        val fresh = File(dir, "fresh.jpg").apply { writeText("x"); setLastModified(now - 1_000) }
        val sub = File(dir, "sub").apply { mkdirs(); setLastModified(now - CacheFiles.OPEN_MAX_AGE - 1_000) }

        assertEquals(1, CacheFiles.deleteOlderThan(dir, CacheFiles.OPEN_MAX_AGE, now))
        assertFalse(old.exists())
        assertTrue(fresh.exists())
        assertTrue(sub.exists())
    }

    @Test
    fun missingFolderIsFine() {
        assertEquals(0, CacheFiles.deleteOlderThan(File(tmp.root, "nope"), 1L, 0L))
    }
}
