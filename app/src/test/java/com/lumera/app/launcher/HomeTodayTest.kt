package com.lumera.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTodayTest {
    private val h = 3_600_000L
    private val lhf = HockeyTeam("LHF", "Luleå", "Luleå Hockey", null)
    private val fbk = HockeyTeam("FBK", "Färjestad", "Färjestad BK", null)

    private fun game(id: String, start: Long, state: String, hs: Int? = null, aws: Int? = null) =
        HockeyGame(id, start, state, lhf, fbk, hs, aws, false, false, null, null)

    @Test
    fun aGameOnNowComesFirst() {
        val now = 100 * h
        val games = listOf(game("a", now - 30 * h, "post-game", 3, 1), game("b", now - h, "live", 1, 0), game("c", now + 24 * h, "pre-game"))
        val pick = HomeHockey.pick(games, "LHF", now)!!
        assertEquals("b", pick.game.id)
        assertEquals(HockeyNow.Mode.Live, pick.mode)
    }

    @Test
    fun aGameStartedWithoutWordFromShlCountsAsLive() {
        val now = 100 * h
        val pick = HomeHockey.pick(listOf(game("b", now - 10 * 60_000L, "pre-game")), "LHF", now)!!
        assertEquals(HockeyNow.Mode.Live, pick.mode)
    }

    @Test
    fun lastNightsResultStaysUntilTheNextDay() {
        val now = 100 * h
        val games = listOf(game("a", now - 12 * h, "post-game", 2, 4), game("c", now + 24 * h, "pre-game"))
        val pick = HomeHockey.pick(games, "LHF", now)!!
        assertEquals("a", pick.game.id)
        assertEquals(HockeyNow.Mode.Result, pick.mode)
        assertEquals(false, pick.game.won("LHF"))
        assertEquals(true, pick.game.won("FBK"))
    }

    @Test
    fun otherwiseTheNextGame() {
        val now = 100 * h
        val games = listOf(game("a", now - 40 * h, "post-game", 2, 1), game("d", now + 48 * h, "pre-game"), game("c", now + 24 * h, "pre-game"))
        val pick = HomeHockey.pick(games, "LHF", now)!!
        assertEquals("c", pick.game.id)
        assertEquals(HockeyNow.Mode.Next, pick.mode)
        assertNull(pick.game.won("LHF"))
    }

    @Test
    fun noGamesNoCard() {
        assertNull(HomeHockey.pick(emptyList(), "LHF", 0L))
    }

    @Test
    fun hueColoursGoThereAndBack() {
        val (x, y) = HomeLights.rgbToXy(0xFFFF0000.toInt())
        val back = HomeLights.xyToRgb(x.toFloat(), y.toFloat())
        val r = (back shr 16) and 0xFF
        val g = (back shr 8) and 0xFF
        val b = back and 0xFF
        assertTrue("red stays red ($r $g $b)", r == 255 && g < 80 && b < 80)
    }

    @Test
    fun warmWhiteIsWarm() {
        val c = HomeLights.kelvinToRgb(2400)
        val r = (c shr 16) and 0xFF
        val b = c and 0xFF
        assertTrue(r == 255 && b < 120)
    }
}
