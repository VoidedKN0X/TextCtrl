package com.vknxworks.textctrl.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CopyAtMostTest {
    @Test fun copiesEverythingWhenSmallerThanTheMaximum() {
        val out = ByteArrayOutputStream()
        assertEquals(10L, Scheduler.copyAtMost(ByteArrayInputStream(ByteArray(10)), out, 100))
        assertEquals(10, out.size())
    }

    @Test fun stopsAtTheMaximum() {
        val out = ByteArrayOutputStream()
        assertEquals(300_001L, Scheduler.copyAtMost(ByteArrayInputStream(ByteArray(1_000_000)), out, 300_001))
        assertEquals(300_001, out.size())
    }
}
