package com.lumera.app.control

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlRulesTest {
    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP

    @Test
    fun `a short press of home is held back and then given to the tv`() {
        val hold = HomeHold()
        assertEquals(HomeStep.Swallow, hold.onKey(down, 0, 1_000))
        assertFalse(hold.due(1_300))
        assertEquals(HomeStep.PressHome, hold.onKey(up, 0, 1_120))
        assertFalse(hold.holding)
    }

    @Test
    fun `holding home opens once and keeps the rest of the hold`() {
        val hold = HomeHold()
        hold.onKey(down, 0, 0)
        assertEquals(HomeHold.HOLD_MS, hold.dueAt())
        assertFalse(hold.due(HomeHold.HOLD_MS - 1))
        assertTrue(hold.due(HomeHold.HOLD_MS))
        assertFalse(hold.due(HomeHold.HOLD_MS + 500))
        // Repeats and the release belong to the hold, not the TV
        assertEquals(HomeStep.Swallow, hold.onKey(down, 12, 900))
        assertEquals(HomeStep.Swallow, hold.onKey(up, 0, 1_400))
        assertEquals(-1L, hold.dueAt())
    }

    @Test
    fun `a release long after the press counts as a hold even if no timer ran`() {
        val hold = HomeHold()
        hold.onKey(down, 0, 0)
        assertEquals(HomeStep.Swallow, hold.onKey(up, 0, 2_000))
    }

    @Test
    fun `a hold that began before the service listened is the tv's`() {
        val hold = HomeHold()
        assertEquals(HomeStep.Pass, hold.onKey(down, 3, 500))
        assertEquals(HomeStep.Pass, hold.onKey(up, 0, 900))
    }

    @Test
    fun `the remote moves round the front page as the eye expects`() {
        assertEquals(CcItem.Sleep, CcLayout.move(CcItem.Audio, Direction.Right))
        assertEquals(CcItem.Power, CcLayout.move(CcItem.Audio, Direction.Up))
        assertEquals(CcItem.Screensaver, CcLayout.move(CcItem.Audio, Direction.Down))
        assertEquals(CcItem.Wifi, CcLayout.move(CcItem.Power, Direction.Right))
        assertEquals(CcItem.Power, CcLayout.move(CcItem.Quiet, Direction.Left))
        assertEquals(CcItem.Sleep, CcLayout.move(CcItem.Quiet, Direction.Down))
        assertEquals(CcItem.Profile, CcLayout.move(CcItem.Wifi, Direction.Up))
        assertEquals(CcItem.Profile, CcLayout.move(CcItem.Power, Direction.Up))
        assertEquals(CcItem.Wifi, CcLayout.move(CcItem.Profile, Direction.Down))
        assertEquals(CcItem.Gaming, CcLayout.move(CcItem.Sleep, Direction.Down))
        assertEquals(CcItem.Sleep, CcLayout.move(CcItem.Settings, Direction.Up))
        assertEquals(CcItem.Audio, CcLayout.move(CcItem.Diagnostics, Direction.Up))
        assertEquals(CcItem.Gaming, CcLayout.move(CcItem.Diagnostics, Direction.Right))
        assertNull(CcLayout.move(CcItem.Profile, Direction.Up))
        assertNull(CcLayout.move(CcItem.Settings, Direction.Right))
        assertNull(CcLayout.move(CcItem.Screensaver, Direction.Down))
    }

    @Test
    fun `every button can be reached from the first one`() {
        val seen = mutableSetOf(CcLayout.FIRST)
        var frontier = listOf(CcLayout.FIRST)
        while (frontier.isNotEmpty()) {
            frontier = frontier.flatMap { from -> Direction.entries.mapNotNull { CcLayout.move(from, it) } }.filter { seen.add(it) }
        }
        assertEquals(CcItem.entries.toSet(), seen)
    }

    @Test
    fun `list focus skips the headings`() {
        val rows = listOf(true, false, true, true, false, true)
        assertEquals(2, nextRow(rows, 0, 1))
        assertEquals(5, nextRow(rows, 3, 1))
        assertEquals(5, nextRow(rows, 5, 1))
        assertEquals(0, nextRow(rows, 2, -1))
        assertEquals(2, firstRow(listOf(false, false, true)))
        assertEquals(3, firstRow(rows, 3))
        assertEquals(0, firstRow(rows, 1))
    }

    @Test
    fun `networks are listed once each, the one in use first`() {
        val scans = listOf(
            WifiScan("link3", -70, true, 2_437),
            WifiScan("link3", -48, true, 5_180),
            WifiScan("Granne", -40, true, 2_412),
            WifiScan("", -30, false, 2_412),
            WifiScan("Cafe", -80, false, 2_462)
        )
        val list = NetRules.networks(scans, current = "link3")
        assertEquals(listOf("link3", "Granne", "Cafe"), list.map { it.ssid })
        assertEquals(-48, list.first().rssi)
    }

    @Test
    fun `network names and strengths read as the tv shows them`() {
        assertEquals("link3", NetRules.ssid("\"link3\""))
        assertNull(NetRules.ssid("<unknown ssid>"))
        assertNull(NetRules.ssid(null))
        assertEquals(4, NetRules.bars(-50))
        assertEquals(2, NetRules.bars(-75))
        assertEquals(0, NetRules.bars(-95))
        assertEquals("5 GHz", NetRules.band(5_180))
        assertEquals("2.4 GHz", NetRules.band(2_437))
        assertEquals("6 GHz", NetRules.band(6_115))
    }

    @Test
    fun `sleep timer labels and countdown`() {
        assertEquals("Off", SleepRules.label(0))
        assertEquals("45 min", SleepRules.label(45))
        assertEquals("1 h", SleepRules.label(60))
        assertEquals("1 h 30 min", SleepRules.label(90))
        assertEquals("0:59", SleepRules.left(58_200))
        assertEquals("42:10", SleepRules.left(2_530_000))
        assertEquals("1:05:00", SleepRules.left(3_900_000))
        assertEquals("0:00", SleepRules.left(-5))
    }
}
