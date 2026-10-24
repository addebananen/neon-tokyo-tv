package com.lumera.app.ui.watchlist

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistCountTest {

    @Test
    fun countsBothKinds() {
        assertEquals("WATCHLIST  ·  2 MOVIES  ·  1 SERIES", watchlistCount(2, 1))
    }

    @Test
    fun oneMovieIsSingular() {
        assertEquals("WATCHLIST  ·  1 MOVIE", watchlistCount(1, 0))
    }

    @Test
    fun leavesOutAnEmptyKind() {
        assertEquals("WATCHLIST  ·  3 SERIES", watchlistCount(0, 3))
    }
}
