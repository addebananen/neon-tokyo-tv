package com.lumera.app.launcher

import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.model.SeriesNextUpEntity
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.domain.continueWatching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeWatchingTest {
    private val profiles = listOf(ProfileEntity(id = 1, name = "Adrian"), ProfileEntity(id = 2, name = " Guest "))

    @Test
    fun aHomeProfileGoesWithTheNeonTokyoProfileOfTheSameName() {
        assertEquals(1, HomeWatchingSource.match("Adrian", profiles)?.id)
        assertEquals(1, HomeWatchingSource.match("adrian ", profiles)?.id)
        assertEquals(2, HomeWatchingSource.match("guest", profiles)?.id)
    }

    @Test
    fun noProfileOfThatNameShowsNothing() {
        assertNull(HomeWatchingSource.match("Neon", profiles))
        assertNull(HomeWatchingSource.match("  ", profiles))
    }

    @Test
    fun anEpisodeLeftPartWaySaysWhichItIs() {
        val history = listOf(
            WatchHistoryEntity(id = "tt1:2:5", title = "Show", poster = null, position = 30, duration = 100, lastWatched = 20, type = "series"),
            WatchHistoryEntity(id = "tt9", title = "Film", poster = null, position = 50, duration = 100, lastWatched = 10, type = "movie"),
            WatchHistoryEntity(id = "tt8", title = "Done", poster = null, position = 100, duration = 100, lastWatched = 30, type = "movie", watched = true)
        )
        val entries = continueWatching(history)
        assertEquals(listOf("tt1", "tt9"), entries.map { it.id })
        assertEquals("S2 · E5", episodeLabel(entries[0]))
        assertNull(episodeLabel(entries[1]))
        assertEquals(0.5f, entries[1].progress, 0.001f)
    }

    @Test
    fun theNextEpisodeShowsOnceItHasAired() {
        val nextUp = listOf(
            SeriesNextUpEntity(seriesId = "tt2", title = "Aired", poster = null, nextSeason = 1, nextEpisode = 3, nextEpisodeTitle = null, nextReleased = "2026-01-01", updatedAt = 5),
            SeriesNextUpEntity(seriesId = "tt3", title = "Later", poster = null, nextSeason = 1, nextEpisode = 2, nextEpisodeTitle = null, nextReleased = "2027-01-01", updatedAt = 6)
        )
        val entries = continueWatching(emptyList(), nextUp, today = "2026-10-26")
        assertEquals(listOf("tt2"), entries.map { it.id })
        assertEquals("S1 · E3", episodeLabel(entries[0]))
    }
}
