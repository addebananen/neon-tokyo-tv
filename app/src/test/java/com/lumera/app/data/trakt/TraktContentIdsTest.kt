package com.lumera.app.data.trakt

import com.lumera.app.data.model.trakt.TraktIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TraktContentIdsTest {

    @Test
    fun imdbTmdbAndTvdbIdsReachTrakt() {
        assertEquals(TraktIds(imdb = "tt0944947"), TraktContentIds.traktIds("tt0944947"))
        assertEquals(TraktIds(tmdb = 1399), TraktContentIds.traktIds("tmdb:1399"))
        assertEquals(TraktIds(tvdb = 121361), TraktContentIds.traktIds("tvdb:121361"))
    }

    @Test
    fun idsTraktCannotPlaceStayLocal() {
        assertNull(TraktContentIds.traktIds("kitsu:1376"))
        assertNull(TraktContentIds.traktIds("tmdb:"))
        assertNull(TraktContentIds.traktIds("own_video_1"))
    }

    @Test
    fun episodesSplitAfterAPrefixedId() {
        assertEquals(Triple("tmdb:1399", 2, 5), TraktContentIds.episode("tmdb:1399:2:5"))
        assertEquals(Triple("tmdb:1399", 2, 5), TraktContentIds.episode("tmdb:1399:2:5:0"))
        assertEquals(Triple("tt0944947", 2, 5), TraktContentIds.episode("tt0944947:2:5:1"))
        assertNull(TraktContentIds.episode("tmdb:603"))
    }

    @Test
    fun normalizeDropsOnlyTheStreamIndex() {
        assertEquals("tmdb:1399:2:5", TraktContentIds.normalize("tmdb:1399:2:5"))
        assertEquals("tmdb:1399:2:5", TraktContentIds.normalize("tmdb:1399:2:5:0"))
        assertEquals("tt0944947:2:5", TraktContentIds.normalize("tt0944947:2:5:3"))
        assertEquals("tt0133093", TraktContentIds.normalize("tt0133093"))
    }

    @Test
    fun keysListEveryIdImdbFirst() {
        assertEquals(
            listOf("tt0944947", "tmdb:1399", "tvdb:121361"),
            TraktContentIds.keys(TraktIds(imdb = "tt0944947", tmdb = 1399, tvdb = 121361))
        )
        assertEquals(listOf("tmdb:603"), TraktContentIds.keys(TraktIds(tmdb = 603)))
    }
}
