package com.neontokyo.desktop.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neontokyo.desktop.theme.NeonThemeTokens
import com.neontokyo.desktop.theme.NeonTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import uniffi.neon_core.*
import java.time.LocalDate

/**
 * Everything the Windows app shows, and every change it makes. The rules (rows, watched, next
 * episode, best source) come from the shared Rust core, exactly as on Android and Apple.
 */
class AppModel(private val scope: CoroutineScope) {
    var profiles by mutableStateOf(listOf<Profile>())
        private set
    var activeProfileId by mutableStateOf<String?>(null)
        private set
    var addonUrls by mutableStateOf(listOf<String>())
        private set
    var manifests by mutableStateOf(listOf<AddonManifest>())
        private set
    var rows by mutableStateOf(listOf<HomeRow>())
        private set
    val rowItems = mutableStateMapOf<String, List<MetaItem>>()
    var loadingAddons by mutableStateOf(false)
        private set
    var history by mutableStateOf(listOf<WatchEntry>())
        private set
    var nextUp by mutableStateOf(listOf<SeriesNextUp>())
        private set
    var watchlist by mutableStateOf(listOf<WatchlistItem>())
        private set
    var themeId by mutableStateOf(NeonTokens.neonTokyo.id)
        private set
    var showIntro by mutableStateOf(true)
    var motion by mutableStateOf(com.neontokyo.desktop.ui.MotionLevel.values().firstOrNull { it.key == Store.read("motion")?.trim() } ?: com.neontokyo.desktop.ui.MotionLevel.Full)
        private set
    var sounds by mutableStateOf(Sound.enabled)
        private set

    val accounts = Accounts(this, scope)
    val updates = Updater(scope)

    /** CI screenshots (NEON_DEMO=1): a profile is made and chosen, nothing is looked up on GitHub. */
    val demo = System.getenv("NEON_DEMO") == "1"
    /** CI screenshots: the screen to open at once (`settings`, `details:movie:tt15239678`, `intro`). */
    val launchScreen: String? = System.getenv("NEON_SCREEN")?.trim()?.ifEmpty { null }

    val theme: NeonThemeTokens get() = NeonTokens.themes.firstOrNull { it.id == themeId } ?: NeonTokens.neonTokyo
    val liveProfiles: List<Profile> get() = profiles.filter { !it.deleted }
    val activeProfile: Profile? get() = liveProfiles.firstOrNull { it.id == activeProfileId }

    init {
        profiles = decodeProfiles(Store.read("profiles") ?: "[]")
        activeProfileId = Store.read("active-profile")?.trim()?.ifEmpty { null }
        addonUrls = Store.lines("addons") ?: starterAddonUrls()
        manifests = decodeManifests(Store.read("manifests") ?: "[]")
        rows = homeRows(manifests)
        rows.forEach { row ->
            val cached = decodeMetaItems(Store.read(rowFile(row)) ?: "[]")
            if (cached.isNotEmpty()) rowItems[row.key] = cached
        }
        history = decodeWatchEntries(Store.read("history") ?: "[]")
        nextUp = decodeNextUp(Store.read("next-up") ?: "[]")
        watchlist = decodeWatchlist(Store.read("watchlist") ?: "[]")
        themeId = Store.read("theme")?.trim() ?: NeonTokens.neonTokyo.id
        if (demo) {
            showIntro = launchScreen == null || launchScreen == "intro"
            if (liveProfiles.isEmpty()) createProfile("Neon")
            if (activeProfile == null) activeProfileId = liveProfiles.firstOrNull()?.id
            accounts.load(activeProfileId)
        } else {
            accounts.load(activeProfileId)
            updates.start()
        }
    }

    fun changeMotion(m: com.neontokyo.desktop.ui.MotionLevel) {
        motion = m
        Store.write("motion", m.key)
    }

    fun changeSounds(on: Boolean) {
        sounds = on
        Sound.set(on)
    }

    /** Addons from a Stremio account: new ones are added after this device's own. */
    suspend fun mergeAddons(bases: List<String>) {
        val fresh = bases.filter { it !in addonUrls }
        if (fresh.isEmpty()) return
        addonUrls = addonUrls + fresh
        Store.writeLines("addons", addonUrls)
        refreshAddons().join()
    }

    /** What sync merged: the whole history (and watchlist, when given). */
    fun applySynced(newHistory: List<WatchEntry>, newWatchlist: List<WatchlistItem>?) {
        history = newHistory
        if (newWatchlist != null) watchlist = newWatchlist
        history = trimHistory(history, 2000u, now())
        Store.write("history", encodeWatchEntries(history))
        Store.write("watchlist", encodeWatchlist(watchlist))
    }

    private fun now() = System.currentTimeMillis()
    private fun rowFile(row: HomeRow) = "row-" + cloudRecordName("r", "", row.key)

    private fun saveProfiles() {
        Store.write("profiles", encodeProfiles(profiles))
        Store.write("active-profile", activeProfileId ?: "")
    }

    private fun saveWatch() {
        history = trimHistory(history, 2000u, now())
        Store.write("history", encodeWatchEntries(history))
        Store.write("next-up", encodeNextUp(nextUp))
        Store.write("watchlist", encodeWatchlist(watchlist))
    }

    fun setTheme(id: String) {
        themeId = id
        Store.write("theme", id)
    }

    // Profiles

    fun createProfile(name: String) {
        val p = newProfile(name, profiles, now())
        profiles = profiles + p
        activeProfileId = p.id
        saveProfiles()
    }

    fun select(profile: Profile?) {
        activeProfileId = profile?.id
        saveProfiles()
        accounts.load(activeProfileId)
    }

    fun remove(profile: Profile) {
        profiles = profiles.map { if (it.id == profile.id) it.copy(deleted = true, updatedAtMs = now()) else it }
        if (activeProfileId == profile.id) activeProfileId = null
        saveProfiles()
    }

    // Addons and rows

    fun refreshAddons() = scope.launch {
        loadingAddons = true
        try {
            val cached = manifests
            val fetched = coroutineScope {
                addonUrls.map { base ->
                    async { runCatching { parseManifest(base, Net.text(manifestUrl(base))) }.getOrNull() }
                }.awaitAll()
            }
            manifests = addonUrls.mapIndexedNotNull { i, base -> fetched[i] ?: cached.firstOrNull { it.baseUrl == base } }
            Store.write("manifests", encodeManifests(manifests))
            rows = homeRows(manifests)
            coroutineScope { rows.map { async { loadRow(it) } }.awaitAll() }
        } finally {
            loadingAddons = false
        }
    }

    private suspend fun loadRow(row: HomeRow) {
        val items = runCatching { parseCatalog(Net.text(catalogUrl(row.catalog, null, null, null))) }.getOrNull()
        if (!items.isNullOrEmpty()) {
            rowItems[row.key] = items
            Store.write(rowFile(row), encodeMetaItems(items))
        }
    }

    suspend fun addAddon(input: String) {
        val base = normalizeAddonUrl(input)
        parseManifest(base, Net.text(manifestUrl(base)))
        if (base !in addonUrls) {
            addonUrls = addonUrls + base
            Store.writeLines("addons", addonUrls)
        }
        refreshAddons().join()
    }

    fun removeAddon(base: String) {
        addonUrls = addonUrls - base
        Store.writeLines("addons", addonUrls)
        manifests = manifests.filter { it.baseUrl != base }
        rows = homeRows(manifests)
    }

    // Details, sources, search

    suspend fun meta(kind: String, id: String): MetaDetail {
        var last: Throwable = IllegalStateException("No addon gives details for $id")
        for (base in metaAddons(manifests, kind, id)) {
            try {
                return parseMeta(Net.text(metaUrl(base, kind, id)))
            } catch (e: Throwable) {
                last = e
            }
        }
        throw last
    }

    /** Ranked for a desktop player (mpv opens MKV too), torrents left to the Android app. */
    suspend fun streams(kind: String, videoId: String): List<StreamSource> {
        val names = manifests.associate { it.baseUrl to it.name }
        val all = coroutineScope {
            streamAddons(manifests, kind, videoId).map { base ->
                async { runCatching { parseStreams(names[base] ?: "Addon", Net.text(streamUrl(base, kind, videoId))) }.getOrDefault(emptyList()) }
            }.awaitAll().flatten()
        }
        return rankStreams(all, false, true)
    }

    val hasStreamAddon: Boolean get() = manifests.any { "stream" in it.resources }

    suspend fun search(query: String): List<Pair<CatalogDef, List<MetaItem>>> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val targets = searchCatalogs(manifests)
        val found = coroutineScope {
            targets.map { c -> async { runCatching { parseCatalog(Net.text(catalogUrl(c, null, null, q))) }.getOrDefault(emptyList()) } }.awaitAll()
        }
        return targets.zip(found).filter { it.second.isNotEmpty() }
    }

    // Watching

    val continueItems: List<ContinueEntry>
        get() = activeProfileId?.let { continueWatching(history, nextUp, it, LocalDate.now().toString()) } ?: emptyList()

    fun entry(playbackId: String): WatchEntry? =
        history.firstOrNull { it.profileId == activeProfileId && it.id == playbackId && !it.deleted }

    fun resumePosition(playbackId: String): ULong = resumePositionMs(entry(playbackId))
    fun isWatched(playbackId: String) = entry(playbackId)?.watched == true
    fun progress(playbackId: String): Float {
        val e = entry(playbackId) ?: return 0f
        if (e.watched || e.durationMs == 0uL) return 0f
        return (e.positionMs.toDouble() / e.durationMs.toDouble()).toFloat()
    }

    fun saveProgress(item: PlayItem, positionMs: ULong, durationMs: ULong) {
        val pid = activeProfileId ?: return
        if (durationMs == 0uL) return
        val old = entry(item.playbackId)
        val e = recordProgress(old, pid, item.playbackId, item.kind, item.title, item.poster, positionMs, durationMs, now())
        history = listOf(e) + history.filterNot { it.profileId == pid && it.id == e.id }
        if (e.watched && old?.watched != true && item.kind == "series" && item.seriesId != null) {
            val n = nextUpAfter(pid, item.seriesId, item.seriesTitle ?: item.title, item.poster, item.playbackId, item.episodes, now())
            nextUp = nextUp.filterNot { it.profileId == pid && it.seriesId == item.seriesId } + n
        }
        saveWatch()
    }

    /** From mpv's thread: saved on the window's thread, like every other change. Accounts hear
     *  of it once the player closes, not every ten seconds while it plays. */
    fun playerProgress(item: PlayItem, positionMs: ULong, durationMs: ULong, final: Boolean) {
        scope.launch {
            saveProgress(item, positionMs, durationMs)
            if (final) {
                accounts.scrobbleStop(item.playbackId, item.kind, positionMs, durationMs)
                accounts.changed()
            }
        }
    }

    /** Where to start: a saved time, or (from Trakt, where only the share is known) a share. */
    fun resumeFractionOf(playbackId: String): Double = resumeFraction(entry(playbackId))

    // Watchlist

    val watchlistItems: List<WatchlistItem>
        get() = watchlist.filter { it.profileId == activeProfileId && !it.deleted }.sortedByDescending { it.addedAtMs }

    fun onWatchlist(id: String) = watchlistItems.any { it.id == id }

    fun toggleWatchlist(item: MetaItem) {
        val pid = activeProfileId ?: return
        val t = now()
        val existing = watchlist.firstOrNull { it.profileId == pid && it.id == item.id }
        watchlist = if (existing != null) {
            watchlist.map {
                if (it === existing) it.copy(deleted = !it.deleted, updatedAtMs = t, addedAtMs = if (it.deleted) t else it.addedAtMs) else it
            }
        } else {
            watchlist + WatchlistItem(pid, item.id, item.kind, item.name, item.poster, t, t, false)
        }
        saveWatch()
        accounts.changed()
    }
}

/** What the player needs to know about the film or episode it plays. */
data class PlayItem(
    val playbackId: String,
    val kind: String,
    val title: String,
    val poster: String?,
    val seriesId: String? = null,
    val seriesTitle: String? = null,
    val episodes: List<Video> = emptyList(),
)
