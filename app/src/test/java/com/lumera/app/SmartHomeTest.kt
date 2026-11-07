package com.lumera.app

import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.domain.SmartHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SmartHomeTest {

    private val stockholm = ZoneId.of("Europe/Stockholm")
    // Friday 6 Nov 2026, 19:30 in Sweden
    private val now = ZonedDateTime.of(2026, 11, 6, 19, 30, 0, 0, stockholm)

    private fun ep(season: Int, episode: Int, released: String?) =
        MetaVideo(id = "x:$season:$episode", title = "Ep $episode", released = released, season = season, episode = episode)

    @Test
    fun usPremiereInTheSmallHoursCountsAsTonight() {
        // Aired 21:00 in New York on Thursday = 03:00 Friday in Sweden
        val videos = listOf(ep(2, 4, "2026-10-30T01:00:00.000Z"), ep(2, 5, "2026-11-06T02:00:00.000Z"))
        val (window, hits) = SmartHome.pickNewEpisodes(listOf("tt1" to videos), emptySet(), now)!!
        assertEquals(SmartHome.EpisodeWindow.TONIGHT, window)
        assertEquals(5, hits.single().episode)
        assertEquals("S2 E5", SmartHome.episodeBadge(hits.single(), now))
    }

    @Test
    fun laterTonightShowsItsLocalTime() {
        val videos = listOf(ep(1, 3, "2026-11-06T20:00:00.000Z")) // 21:00 in Sweden
        val (_, hits) = SmartHome.pickNewEpisodes(listOf("tt1" to videos), emptySet(), now)!!
        assertEquals("S1 E3 · 21:00", SmartHome.episodeBadge(hits.single(), now))
    }

    @Test
    fun watchedEpisodesAndSpecialsAreNotNews() {
        val videos = listOf(ep(0, 1, "2026-11-06T10:00:00.000Z"), ep(3, 1, "2026-11-06T10:00:00.000Z"))
        assertNull(SmartHome.pickNewEpisodes(listOf("tt9" to videos), setOf("tt9:3:1"), now))
    }

    @Test
    fun fallsBackToThisWeekWhenNothingIsNewTonight() {
        val videos = listOf(ep(1, 1, "2026-11-03"), ep(1, 2, "2026-11-20"))
        val (window, hits) = SmartHome.pickNewEpisodes(listOf("tt2" to videos), emptySet(), now)!!
        assertEquals(SmartHome.EpisodeWindow.THIS_WEEK, window)
        assertEquals(1, hits.single().episode)
        assertEquals("New episode this week", SmartHome.newEpisodesTitle(window, 1))
    }

    @Test
    fun outAlreadyComesBeforeLaterTonight() {
        val series = listOf(
            "later" to listOf(ep(1, 1, "2026-11-06T21:00:00.000Z")),
            "out" to listOf(ep(1, 1, "2026-11-06T08:00:00.000Z"))
        )
        val (_, hits) = SmartHome.pickNewEpisodes(series, emptySet(), now)!!
        assertEquals(listOf("out", "later"), hits.map { it.seriesId })
    }

    @Test
    fun genresFoldOntoCinemetaNames() {
        assertEquals(listOf("Sci-Fi", "Fantasy"), SmartHome.normalizeGenre("Sci-Fi & Fantasy"))
        assertEquals(listOf("Sci-Fi"), SmartHome.normalizeGenre("Science Fiction"))
        assertEquals(listOf("Thriller"), SmartHome.normalizeGenre("thriller"))
        assertTrue(SmartHome.normalizeGenre("Biography").isEmpty())
    }

    @Test
    fun topGenresFollowWhatTheProfileWatches() {
        val day = 86_400_000L
        val t = 100 * day
        val watched = listOf(
            SmartHome.Watched("a", listOf("Crime", "Drama", "Thriller"), 1f, t),
            SmartHome.Watched("b", listOf("Crime", "Thriller"), 3f, t),
            SmartHome.Watched("c", listOf("Crime", "Drama"), 1f, t),
            SmartHome.Watched("d", listOf("Comedy"), 1f, t - 300 * day), // long ago
            SmartHome.Watched("e", listOf("Comedy"), 1f, t - 300 * day)
        )
        val top = SmartHome.topGenres(watched, t)
        assertEquals("Crime", top.first())
        assertEquals("Thriller", top[1])
        // Drama doesn't clearly lead, so it gets no row of its own
        assertTrue("Drama" !in top)
    }

    @Test
    fun oneTitleIsNotATaste() {
        val watched = listOf(SmartHome.Watched("a", listOf("Horror"), 3f, 0L))
        assertTrue(SmartHome.topGenres(watched, 0L).isEmpty())
    }

    @Test
    fun smartRowsSitAtTheTopAndGenresSpreadOut() {
        val placed = SmartHome.place(listOf("a", "b", "c", "d", "e"), listOf("new", "top"), listOf("g1", "g2", "g3"))
        assertEquals(listOf("new", "top", "a", "b", "g1", "c", "d", "g2", "e", "g3"), placed)
    }

    @Test
    fun interleaveTakesTurnsWithoutRepeats() {
        val merged = SmartHome.interleave(listOf(listOf("s1", "s2"), listOf("f1", "s1", "f2"))) { it }
        assertEquals(listOf("s1", "f1", "s2", "f2"), merged)
    }
}
