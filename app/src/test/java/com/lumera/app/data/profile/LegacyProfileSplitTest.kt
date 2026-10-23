package com.lumera.app.data.profile

import com.google.gson.Gson
import com.lumera.app.data.model.AddonEntity
import com.lumera.app.data.model.CatalogConfigEntity
import com.lumera.app.data.model.HubRowEntity
import com.lumera.app.data.model.HubRowItemEntity
import com.lumera.app.data.model.SeriesNextUpEntity
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.data.model.WatchlistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyProfileSplitTest {

    @Test
    fun signedInProfileKeepsLiveHistoryAndOthersKeepTheirSnapshot() {
        val split = LegacyProfileSplit.personal(
            profileIds = listOf(1, 2, 3),
            owner = 1,
            liveHistory = listOf(history("tt1"), history("tt2")),
            snapshotHistory = mapOf(1 to listOf(history("stale")), 2 to listOf(history("tt3"))),
            sharedWatchlist = emptyList(),
            sharedNextUp = emptyList()
        )
        assertEquals(listOf("tt1", "tt2"), split.history.filter { it.profileId == 1 }.map { it.id })
        assertEquals(listOf("tt3"), split.history.filter { it.profileId == 2 }.map { it.id })
        assertTrue(split.history.none { it.profileId == 3 })
    }

    @Test
    fun withNobodySignedInEveryProfileKeepsItsSnapshot() {
        val split = LegacyProfileSplit.personal(
            profileIds = listOf(1, 2),
            owner = null,
            liveHistory = listOf(history("live")),
            snapshotHistory = mapOf(1 to listOf(history("a")), 2 to listOf(history("b"), history("b"))),
            sharedWatchlist = emptyList(),
            sharedNextUp = emptyList()
        )
        assertEquals(listOf("a" to 1, "b" to 2), split.history.map { it.id to it.profileId })
    }

    @Test
    fun everyoneKeepsTheWatchlistAndNextEpisodesFollowWhoWatched() {
        val split = LegacyProfileSplit.personal(
            profileIds = listOf(1, 2),
            owner = 1,
            liveHistory = listOf(history("tt9")),
            snapshotHistory = mapOf(2 to listOf(history("tt5:1:3", type = "series"))),
            sharedWatchlist = listOf(watchlist("tt7")),
            sharedNextUp = listOf(nextUp("tt5"), nextUp("tt55"))
        )
        assertEquals(listOf(1, 2), split.watchlist.map { it.profileId })
        assertTrue(split.watchlist.all { it.id == "tt7" })
        // "tt55" shares a prefix with "tt5" but is another series
        assertEquals(listOf("tt5" to 2), split.nextUp.map { it.seriesId to it.profileId })
    }

    @Test
    fun brokenSnapshotRowsAreDropped() {
        val snapshot = Gson().fromJson(
            """{"watchHistory":[{"id":"ok","title":"Fine","type":"movie"},{"title":"No id"}]}""",
            ProfileRuntimeSnapshot::class.java
        )
        val split = LegacyProfileSplit.personal(
            profileIds = listOf(4),
            owner = null,
            liveHistory = emptyList(),
            snapshotHistory = mapOf(4 to snapshot.watchHistory),
            sharedWatchlist = emptyList(),
            sharedNextUp = emptyList()
        )
        assertEquals(listOf("ok"), split.history.map { it.id })
    }

    @Test
    fun addonsMissingFromLiveComeInAfterTheLiveSetup() {
        val live = ProfileRuntimeSnapshot(
            addons = listOf(addon("a", sortOrder = 0), addon("b", sortOrder = 1)),
            catalogConfigs = listOf(config("a", "top", home = 0), config("b", "top", home = 1)),
            hubRows = listOf(hub("services", home = 2))
        )
        val other = ProfileRuntimeSnapshot(
            addons = listOf(addon("a", name = "Renamed"), addon("c", sortOrder = 0)),
            catalogConfigs = listOf(
                config("a", "top", home = 0),
                config("c", "new", home = 0),
                config("orphan", "top", home = 1)
            ),
            hubRows = listOf(hub("services", home = 0), hub("genres", home = 1)),
            hubRowItems = listOf(hubItem("services"), hubItem("genres"))
        )
        val missing = LegacyProfileSplit.missingFromLive(live, listOf(other, other))

        assertEquals(listOf("c" to 2), missing.addons.map { it.transportUrl to it.sortOrder })
        assertEquals(listOf("c|new"), missing.catalogConfigs.map { it.uniqueId })
        val config = missing.catalogConfigs.single()
        assertEquals(3, config.homeOrder) // after the hub in place 2
        assertEquals(999, config.moviesOrder) // still not placed on Movies
        assertEquals(listOf("genres" to 4), missing.hubRows.map { it.id to it.homeOrder })
        assertEquals(listOf("genres"), missing.hubRowItems.map { it.hubRowId })
    }

    @Test
    fun incompleteSnapshotAddonsAreSkipped() {
        val other = Gson().fromJson(
            """{"addons":[{"transportUrl":"x"}],"catalogConfigs":[{"uniqueId":"x|top"}],"hubRows":[{"id":"h"}]}""",
            ProfileRuntimeSnapshot::class.java
        )
        val missing = LegacyProfileSplit.missingFromLive(ProfileRuntimeSnapshot(), listOf(other))
        assertTrue(missing.addons.isEmpty())
        assertTrue(missing.catalogConfigs.isEmpty())
        assertTrue(missing.hubRows.isEmpty())
    }

    private fun history(id: String, type: String = "movie") = WatchHistoryEntity(
        id = id, title = id, poster = null, position = 0L, duration = 0L, lastWatched = 0L, type = type
    )

    private fun watchlist(id: String) = WatchlistEntity(id = id, type = "movie", title = id, poster = null, addedAt = 0L)

    private fun nextUp(seriesId: String) = SeriesNextUpEntity(
        seriesId = seriesId, title = seriesId, poster = null, nextSeason = 1, nextEpisode = 4,
        nextEpisodeTitle = null, updatedAt = 0L
    )

    private fun addon(url: String, name: String = url, sortOrder: Int = 999) = AddonEntity(
        transportUrl = url, id = url, name = name, version = "1.0.0", description = null, iconUrl = null,
        sortOrder = sortOrder
    )

    private fun config(url: String, catalog: String, home: Int) = CatalogConfigEntity(
        uniqueId = "$url|$catalog", transportUrl = url, addonName = url, catalogType = "movie",
        catalogId = catalog, showInHome = true, homeOrder = home
    )

    private fun hub(id: String, home: Int) = HubRowEntity(id = id, title = id, shape = "LANDSCAPE", showInHome = true, homeOrder = home)

    private fun hubItem(hubId: String) = HubRowItemEntity(hubRowId = hubId, configUniqueId = "c|new", title = hubId)
}
