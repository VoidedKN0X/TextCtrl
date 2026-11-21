package com.vknxworks.textctrl.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFileTest {
    private val snippets = Snippets("Axel", listOf("Onderweg", "Bel je zo terug"))

    @Test fun roundTrip() {
        val mine = AppSettings(
            bubbleColor = BubbleColor.MINT,
            textSize = TextSize.LARGE,
            swipeLeft = SwipeAction.DELETE,
            hideScreenshots = true,
            notificationContent = NotificationContent.NAME_ONLY,
            undoSend = UndoSend.OFF,
            plainSms = true,
        )
        val plan = SettingsFile.plan(SettingsFile.export(mine, snippets), AppSettings())
        assertNotNull(plan)
        assertEquals(mine, plan!!.settings)
        assertEquals(snippets, plan.snippets)
        assertTrue(plan.refused.isEmpty())
        assertEquals(7, plan.changes.size)
    }

    @Test fun nothingAboutTheLockAutoDeleteOrSimIsInTheFile() {
        val text = SettingsFile.export(
            AppSettings(lockTimeout = LockTimeout.HOUR_1, autoDelete = AutoDelete.DAYS_30, deleteCodes = true, defaultSim = 3),
            Snippets(),
        )
        assertFalse(text.contains("lock"))
        assertFalse(text.contains("auto_delete"))
        assertFalse(text.contains("delete_codes"))
        assertFalse(text.contains("sim"))
        // And a file that has them anyway changes none of them.
        val forged = """{"app":"TextCtrl","format":1,"settings":{"lock_timeout":"RESTART","auto_delete":"DAYS_30","delete_codes":"true","default_sim":"9"}}"""
        val plan = SettingsFile.plan(forged, AppSettings())!!
        assertEquals(AppSettings(), plan.settings)
        assertTrue(plan.changes.isEmpty())
    }

    @Test fun anImportNeverMakesTextCtrlLessPrivate() {
        val careful = AppSettings(hideScreenshots = true, hideRecents = true, warnLinks = true, notificationContent = NotificationContent.NOTHING)
        val loose = AppSettings(hideScreenshots = false, hideRecents = false, warnLinks = false, notificationContent = NotificationContent.NAME_AND_MESSAGE)
        val plan = SettingsFile.plan(SettingsFile.export(loose, Snippets()), careful)!!
        assertEquals(careful, plan.settings)
        assertTrue(plan.changes.isEmpty())
        assertEquals(
            setOf(SettingField.HIDE_SCREENSHOTS, SettingField.HIDE_RECENTS, SettingField.WARN_LINKS, SettingField.NOTIFICATION_CONTENT),
            plan.refused.toSet(),
        )
        // The other way round is fine.
        val stricter = SettingsFile.plan(SettingsFile.export(careful, Snippets()), loose)!!
        assertEquals(careful, stricter.settings)
        assertTrue(stricter.refused.isEmpty())
    }

    @Test fun otherFilesAndUnknownValuesAreRefusedOrSkipped() {
        assertNull(SettingsFile.plan("not json", AppSettings()))
        assertNull(SettingsFile.plan("""{"app":"CallCtrl","format":1,"settings":{}}""", AppSettings()))
        assertNull(SettingsFile.plan("""{"app":"TextCtrl","format":2,"settings":{}}""", AppSettings()))
        val odd = """{"app":"TextCtrl","format":1,"settings":{"bubble_colour":"PLAID","copy_codes":"maybe","text_size":"LARGER","extra":"x"}}"""
        val plan = SettingsFile.plan(odd, AppSettings())!!
        assertEquals(AppSettings(textSize = TextSize.LARGER), plan.settings)
        assertEquals(listOf(SettingChange(SettingField.TEXT_SIZE, "DEFAULT", "LARGER")), plan.changes)
        assertNull(plan.snippets)
    }

    @Test fun snippetsAreCleanedAndSigned() {
        val long = "x".repeat(5000)
        val cleaned = Snippets("  $long ", listOf(" a ", "", "a", long)).cleaned()
        assertEquals(Snippets.MAX_SIGNATURE, cleaned.signature.length)
        assertEquals(listOf("a", "x".repeat(Snippets.MAX_TEMPLATE)), cleaned.templates)
        assertEquals("Tot straks\nAxel", Snippets.sign("Tot straks ", "Axel"))
        assertEquals("Tot straks\nAxel", Snippets.sign("Tot straks\nAxel", "Axel"))
        assertEquals("", Snippets.sign("", "Axel"))
        assertEquals("Hoi", Snippets.sign("Hoi", "  "))
        assertEquals("Tot straks", Snippets.unsign("Tot straks\nAxel", "Axel"))
        assertEquals("Axel", Snippets.unsign("Axel", "Axel"))
    }
}
