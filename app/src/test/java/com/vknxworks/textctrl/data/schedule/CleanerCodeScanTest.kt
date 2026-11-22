package com.vknxworks.textctrl.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanerCodeScanTest {
    private val day = Cleaner.DAY
    private val cutoff = 100 * day

    @Test
    fun firstRunChecksEverything() {
        assertEquals(0L, Cleaner.codeScanFrom(0L, cutoff))
    }

    @Test
    fun laterRunsStartTwoDaysBeforeTheLastCutoff() {
        assertEquals(97 * day, Cleaner.codeScanFrom(99 * day, cutoff))
    }

    @Test
    fun clockThatWentBackChecksEverything() {
        assertEquals(0L, Cleaner.codeScanFrom(cutoff + 1, cutoff))
    }
}
