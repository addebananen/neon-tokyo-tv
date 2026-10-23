package com.lumera.app.data.profile

import com.lumera.app.data.model.AddonEntity
import com.lumera.app.data.model.CatalogConfigEntity
import com.lumera.app.data.model.HubRowEntity
import com.lumera.app.data.model.HubRowItemEntity
import com.lumera.app.data.model.SeriesNextUpEntity
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.data.model.WatchlistEntity

/**
 * Lumera kept a snapshot per profile and swapped it into the live tables at login, while the
 * watchlist and next episodes were one list for everybody. This works out, once, what each
 * profile keeps now that addons are shared and history is personal.
 */
internal object LegacyProfileSplit {

    class Personal(
        val history: List<WatchHistoryEntity>,
        val watchlist: List<WatchlistEntity>,
        val nextUp: List<SeriesNextUpEntity>
    )

    /**
     * [owner] is the profile whose session the live tables hold, or null when nobody was signed
     * in (the live rows are then a copy of a snapshot). The owner keeps the live history, the
     * freshest there is; every other profile keeps the history in its snapshot. Everybody saw
     * the shared watchlist, so everybody keeps a copy. A next episode goes to the profiles that
     * watched the series.
     */
    fun personal(
        profileIds: List<Int>,
        owner: Int?,
        liveHistory: List<WatchHistoryEntity>,
        snapshotHistory: Map<Int, List<WatchHistoryEntity>>,
        sharedWatchlist: List<WatchlistEntity>,
        sharedNextUp: List<SeriesNextUpEntity>
    ): Personal {
        val history = profileIds.flatMap { id ->
            val rows = if (id == owner) liveHistory else snapshotHistory[id].orEmpty()
            rows.filterNotNull().filter { complete(it.id, it.title, it.type) }
                .distinctBy { it.id }.map { it.copy(profileId = id) }
        }
        val watchlist = profileIds.flatMap { id -> sharedWatchlist.map { it.copy(profileId = id) } }
        val nextUp = profileIds.flatMap { id ->
            val watchedIds = history.filter { it.profileId == id }.map { it.id }
            sharedNextUp
                .filter { entry -> watchedIds.any { it == entry.seriesId || it.startsWith(entry.seriesId + ":") } }
                .map { it.copy(profileId = id) }
        }
        return Personal(history, watchlist, nextUp)
    }

    /**
     * What the other profiles' snapshots have that the live tables don't: addons, their catalogs
     * and hubs. The live setup stays as it is; what comes in is placed after it on each screen.
     */
    fun missingFromLive(live: ProfileRuntimeSnapshot, others: List<ProfileRuntimeSnapshot>): ProfileRuntimeSnapshot {
        val addonUrls = live.addons.mapTo(HashSet()) { it.transportUrl }
        val configIds = live.catalogConfigs.mapTo(HashSet()) { it.uniqueId }
        val hubIds = live.hubRows.mapTo(HashSet()) { it.id }

        var addonOrder = (live.addons.maxOfOrNull { it.sortOrder.coerceAtMost(9_000) } ?: 0) + 1
        val homeBase = nextOrder(live, { it.homeOrder }, { it.homeOrder })
        val moviesBase = nextOrder(live, { it.moviesOrder }, { it.moviesOrder })
        val seriesBase = nextOrder(live, { it.seriesOrder }, { it.seriesOrder })

        val addons = mutableListOf<AddonEntity>()
        val configs = mutableListOf<CatalogConfigEntity>()
        val hubs = mutableListOf<HubRowEntity>()
        val hubItems = mutableListOf<HubRowItemEntity>()
        for (snapshot in others) {
            snapshot.addons.filterNotNull()
                .filter { complete(it.transportUrl, it.id, it.name, it.version, it.catalogsJson, it.typesJson, it.idPrefixesJson) }
                .filter { addonUrls.add(it.transportUrl) }
                .forEach { addons += it.copy(sortOrder = addonOrder++) }
            snapshot.catalogConfigs.filterNotNull()
                .filter { complete(it.uniqueId, it.transportUrl, it.addonName, it.catalogType, it.catalogId) }
                .filter { it.transportUrl in addonUrls && configIds.add(it.uniqueId) }
                .forEach {
                    configs += it.copy(
                        homeOrder = after(homeBase, it.homeOrder),
                        moviesOrder = after(moviesBase, it.moviesOrder),
                        seriesOrder = after(seriesBase, it.seriesOrder)
                    )
                }
            val newHubs = snapshot.hubRows.filterNotNull()
                .filter { complete(it.id, it.title, it.shape) && hubIds.add(it.id) }
            newHubs.forEach {
                hubs += it.copy(
                    homeOrder = after(homeBase, it.homeOrder),
                    moviesOrder = after(moviesBase, it.moviesOrder),
                    seriesOrder = after(seriesBase, it.seriesOrder)
                )
            }
            val newHubIds = newHubs.mapTo(HashSet()) { it.id }
            hubItems += snapshot.hubRowItems.filterNotNull()
                .filter { complete(it.hubRowId, it.configUniqueId, it.title) && it.hubRowId in newHubIds }
        }
        return ProfileRuntimeSnapshot(addons = addons, catalogConfigs = configs, hubRows = hubs, hubRowItems = hubItems)
    }

    // 999 marks "not placed" in Lumera, so it doesn't count as a place on the screen
    private const val UNPLACED = 999

    private fun after(base: Int, order: Int) = if (order >= UNPLACED) order else base + order

    private fun nextOrder(
        live: ProfileRuntimeSnapshot,
        config: (CatalogConfigEntity) -> Int,
        hub: (HubRowEntity) -> Int
    ): Int {
        val placed = live.catalogConfigs.map(config).filter { it < UNPLACED } + live.hubRows.map(hub).filter { it < UNPLACED }
        return (placed.maxOrNull() ?: -1) + 1
    }

    // Snapshots are JSON read by Gson, which leaves a missing field null even where Kotlin says it can't be
    private fun complete(vararg fields: Any?) = fields.none { it == null }
}
