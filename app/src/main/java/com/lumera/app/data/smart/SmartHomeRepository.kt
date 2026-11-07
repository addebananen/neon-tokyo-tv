package com.lumera.app.data.smart

import android.content.Context
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.stremio.MetaItem
import com.lumera.app.data.profile.NeonTokyoStarterPack
import com.lumera.app.data.remote.StremioApiService
import com.lumera.app.data.repository.AddonRepository
import com.lumera.app.data.repository.CatalogUrls
import com.lumera.app.domain.CategoryRow
import com.lumera.app.domain.SmartHome
import com.lumera.app.domain.canonicalSeriesId
import com.lumera.app.domain.isEpisodePlaybackId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** One title of a profile's history, episodes folded into their series. */
private class Title(val id: String, val type: String, var episodes: Int, var finished: Int, var progress: Float, var last: Long)

/** The smart rows of one profile's start page, ready to slot in among its own rows. */
data class SmartRows(
    val top: List<CategoryRow> = emptyList(),
    val genres: List<CategoryRow> = emptyList()
) {
    val isEmpty get() = top.isEmpty() && genres.isEmpty()
}

/**
 * Builds the smart start page per profile: "New episode tonight" from the series the profile
 * follows, "Top this week in Sweden" from Netflix's Swedish top 10, and rows for the genres the
 * profile watches most. Everything is read for the given profile id, so each profile gets its own.
 *
 * Network answers are cached (series episodes for 3 hours, catalogs for 6, genres per title for
 * good on disk), so coming back to Home is instant and the TV stays light.
 */
@Singleton
class SmartHomeRepository @Inject constructor(
    private val api: StremioApiService,
    private val dao: AddonDao,
    private val repository: AddonRepository,
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("smart_home", Context.MODE_PRIVATE)

    private val _version = MutableStateFlow(0)
    /** Bumps when a profile turns its smart rows on or off, so Home rebuilds. */
    val version: StateFlow<Int> = _version

    fun isEnabled(profileId: Int): Boolean = prefs.getBoolean("on.$profileId", true)

    fun setEnabled(profileId: Int, on: Boolean) {
        prefs.edit().putBoolean("on.$profileId", on).apply()
        rowsCache.remove(profileId)
        _version.value++
    }

    private data class Timed<T>(val at: Long, val value: T)
    private val seriesCache = ConcurrentHashMap<String, Timed<MetaItem?>>()
    private val catalogCache = ConcurrentHashMap<String, Timed<List<MetaItem>>>()
    private val rowsCache = ConcurrentHashMap<Int, Pair<Long, Timed<SmartRows>>>()
    private val gate = Semaphore(6)

    /**
     * The last rows built for a profile, if they are fresh enough to show straight away.
     * With [historyStamp] (the newest lastWatched in its history) they also have to predate
     * nothing the profile watched since, so a just-watched episode leaves "New episode tonight".
     */
    fun cached(profileId: Int, historyStamp: Long? = null): SmartRows? {
        val (stamp, rows) = rowsCache[profileId] ?: return null
        if (System.currentTimeMillis() - rows.at >= ROWS_FRESH_MS) return null
        if (historyStamp != null && historyStamp != stamp) return null
        return rows.value
    }

    suspend fun build(profileId: Int): SmartRows = withContext(Dispatchers.IO) {
        if (!isEnabled(profileId)) return@withContext SmartRows()
        coroutineScope {
            val history = dao.getHistoryOf(profileId)
            val watchedIds = history.filter { it.watched }.map { it.id }.toSet()
            val seenTitles = history.map { if (it.type == "series") canonicalSeriesId(it.id) else it.id }.toSet()

            val newEpisodes = async { runCatching { newEpisodesRow(profileId, history, watchedIds) }.getOrNull() }
            val topSweden = async { runCatching { topSwedenRow() }.getOrNull() }
            val genres = async { runCatching { genreRows(history, seenTitles) }.getOrDefault(emptyList()) }

            SmartRows(
                top = listOfNotNull(newEpisodes.await(), topSweden.await()),
                genres = genres.await()
            ).also {
                val stamp = history.maxOfOrNull { h -> h.lastWatched } ?: 0L
                rowsCache[profileId] = stamp to Timed(System.currentTimeMillis(), it)
            }
        }
    }

    // ── New episode tonight ─────────────────────────────────────────────────────

    private suspend fun newEpisodesRow(
        profileId: Int,
        history: List<com.lumera.app.data.model.WatchHistoryEntity>,
        watchedIds: Set<String>
    ): CategoryRow? = coroutineScope {
        // The series this profile follows, most recently active first
        val activity = LinkedHashMap<String, Long>()
        fun note(id: String, at: Long) { if (at > (activity[id] ?: Long.MIN_VALUE)) activity[id] = at }
        history.filter { it.type == "series" }.forEach { note(canonicalSeriesId(it.id), it.lastWatched) }
        dao.getSeriesNextUpOf(profileId).forEach { note(it.seriesId, it.updatedAt) }
        dao.getWatchlistOf(profileId).filter { it.type == "series" }.forEach { note(it.id, it.addedAt) }
        val followed = activity.entries.sortedByDescending { it.value }.map { it.key }.take(MAX_FOLLOWED)
        if (followed.isEmpty()) return@coroutineScope null

        val metas = followed.map { id -> async { gate.withPermit { seriesMeta(id) } } }.awaitAll()
            .filterNotNull()
            .associateBy { it.id }
        val now = ZonedDateTime.now()
        val (window, hits) = SmartHome.pickNewEpisodes(
            series = followed.mapNotNull { id -> metas[id]?.let { id to it.videos } },
            watchedPlaybackIds = watchedIds,
            now = now
        ) ?: return@coroutineScope null

        val items = hits.mapNotNull { hit ->
            val meta = metas[hit.seriesId] ?: return@mapNotNull null
            meta.copy(
                videos = null, // the card only needs the series; keeps the row light
                badge = SmartHome.episodeBadge(hit, now)
            )
        }
        if (items.isEmpty()) return@coroutineScope null
        CategoryRow(
            id = SmartHome.NEW_EPISODES_ID,
            title = SmartHome.newEpisodesTitle(window, items.size),
            order = Int.MIN_VALUE,
            items = items
        )
    }

    /** A series' meta with its episode list, Cinemeta first since it carries air times. */
    private suspend fun seriesMeta(id: String): MetaItem? {
        seriesCache[id]?.takeIf { System.currentTimeMillis() - it.at < SERIES_FRESH_MS }?.let { return it.value }
        val meta = withTimeoutOrNull(8_000) {
            val cinemeta = if (id.startsWith("tt")) runCatching {
                api.getMeta("${NeonTokyoStarterPack.CINEMETA_URL}/meta/series/$id.json").meta
            }.getOrNull() else null
            cinemeta?.takeIf { !it.videos.isNullOrEmpty() }
                ?: runCatching { repository.resolveMetaDetails("series", id) }.getOrNull()
        }
        seriesCache[id] = Timed(System.currentTimeMillis(), meta)
        return meta
    }

    // ── Top this week in Sweden ─────────────────────────────────────────────────

    /**
     * Netflix's weekly top 10 for Sweden, series and films taken in turn so both #1s lead.
     * Read from the profile's own Streaming Catalogs addon when it has one, else straight
     * from the same addon Neon Tokyo starts with.
     */
    private suspend fun topSwedenRow(): CategoryRow? = coroutineScope {
        val catalogId = "netflix-top10-${NeonTokyoStarterPack.COUNTRY}"
        val configs = (dao.getAllCatalogConfigs().firstOrNull() ?: emptyList()).filter { it.catalogId == catalogId }
        fun urlFor(type: String): String {
            val base = configs.firstOrNull { it.catalogType == type }?.transportUrl ?: NeonTokyoStarterPack.STREAMING_URL
            return CatalogUrls.catalog(base, type, catalogId)
        }
        val series = async { catalog(urlFor("series")) }
        val films = async { catalog(urlFor("movie")) }
        val ranked = SmartHome.interleave(
            listOf(
                series.await().take(10).mapIndexed { i, m -> m.copy(badge = "#${i + 1} series") },
                films.await().take(10).mapIndexed { i, m -> m.copy(badge = "#${i + 1} film") }
            )
        ) { "${it.type}:${it.id}" }
        if (ranked.isEmpty()) return@coroutineScope null
        CategoryRow(
            id = SmartHome.TOP_SWEDEN_ID,
            title = "Top this week in Sweden",
            order = Int.MIN_VALUE + 1,
            items = ranked
        )
    }

    /** Catalog ids of the rows "Top this week in Sweden" stands in for on the start page. */
    fun isTopSwedenCatalog(configId: String): Boolean =
        configId.endsWith("/netflix-top10-${NeonTokyoStarterPack.COUNTRY}")

    @Suppress("SENSELESS_COMPARISON")
    private suspend fun catalog(url: String): List<MetaItem> {
        catalogCache[url]?.takeIf { System.currentTimeMillis() - it.at < CATALOG_FRESH_MS }?.let { return it.value }
        val metas = withTimeoutOrNull(10_000) {
            runCatching { api.getCatalog(url).metas.orEmpty() }.getOrNull()
        }.orEmpty()
            .filter { it.id != null && it.name != null && it.type != null && it.poster != null }
        if (metas.isNotEmpty()) catalogCache[url] = Timed(System.currentTimeMillis(), metas)
        return metas
    }

    // ── Most-watched genres ─────────────────────────────────────────────────────

    private suspend fun genreRows(
        history: List<com.lumera.app.data.model.WatchHistoryEntity>,
        seenTitles: Set<String>
    ): List<CategoryRow> = coroutineScope {
        // Fold episodes into their series
        val titles = LinkedHashMap<String, Title>()
        history.sortedByDescending { it.lastWatched }.forEach { h ->
            val series = h.type == "series"
            val id = if (series) canonicalSeriesId(h.id) else h.id
            val t = titles.getOrPut(id) { Title(id, if (series) "series" else h.type, 0, 0, 0f, h.lastWatched) }
            if (series && isEpisodePlaybackId(h.id) || !series) t.episodes++
            if (h.watched) t.finished++
            t.progress = maxOf(t.progress, h.progress())
            t.last = maxOf(t.last, h.lastWatched)
        }
        val recent = titles.values.take(MAX_GENRE_TITLES)
        if (recent.size < 2) return@coroutineScope emptyList()

        val genresById = recent.map { t -> async { gate.withPermit { t.id to genresOf(t.type, t.id) } } }.awaitAll().toMap()
        val watched = recent.map { t ->
            val amount = if (t.type == "series") SmartHome.seriesAmount(t.episodes, t.finished)
            else if (t.finished > 0) 1f else 0.3f + 0.7f * t.progress
            SmartHome.Watched(t.id, genresById[t.id].orEmpty(), amount, t.last)
        }
        val top = SmartHome.topGenres(watched, System.currentTimeMillis())
        // Which kind the profile watches more, per genre, goes first in that genre's row
        val seriesShare = top.associateWith { g ->
            val inGenre = recent.filter { t -> genresById[t.id].orEmpty().flatMap(SmartHome::normalizeGenre).contains(g) }
            if (inGenre.isEmpty()) 0.5f else inGenre.count { it.type == "series" }.toFloat() / inGenre.size
        }

        val shown = HashSet<String>(seenTitles)
        val lists = top.map { g ->
            async {
                val extra = CatalogUrls.genreExtra(g)
                val movies = async { catalog(CatalogUrls.catalog(NeonTokyoStarterPack.CINEMETA_URL, "movie", "top", extra)) }
                val series = async { catalog(CatalogUrls.catalog(NeonTokyoStarterPack.CINEMETA_URL, "series", "top", extra)) }
                val order = if ((seriesShare[g] ?: 0.5f) > 0.5f) listOf(series.await(), movies.await()) else listOf(movies.await(), series.await())
                g to SmartHome.interleave(order) { "${it.type}:${it.id}" }
            }
        }.awaitAll()

        // Each title shows once across the genre rows, and nothing the profile has already seen
        lists.mapIndexedNotNull { rank, (genre, items) ->
            val fresh = items.filter { shown.add(it.id) }.take(GENRE_ROW_SIZE)
            if (fresh.size < 6) null else CategoryRow(
                id = SmartHome.genreRowId(genre),
                title = SmartHome.genreRowTitle(genre, rank),
                order = Int.MIN_VALUE + 2 + rank,
                items = fresh.map { it.copy(addonBaseUrl = NeonTokyoStarterPack.CINEMETA_URL) }
            )
        }
    }

    /** A title's genres, kept on disk for good once known: genres don't change. */
    private suspend fun genresOf(type: String, id: String): List<String> {
        val key = "g.$id"
        prefs.getString(key, null)?.let { return if (it.isEmpty()) emptyList() else it.split('|') }
        val meta = withTimeoutOrNull(8_000) {
            runCatching { repository.resolveMetaDetails(type, id) }.getOrNull()
        } ?: return emptyList() // not cached: try again next time
        val genres = meta.genres.orEmpty().filter { it.isNotBlank() && '|' !in it }
        prefs.edit().putString(key, genres.joinToString("|")).apply()
        return genres
    }

    companion object {
        private const val MAX_FOLLOWED = 24
        private const val MAX_GENRE_TITLES = 40
        private const val GENRE_ROW_SIZE = 30
        private const val SERIES_FRESH_MS = 3 * 60 * 60 * 1000L
        private const val CATALOG_FRESH_MS = 6 * 60 * 60 * 1000L
        private const val ROWS_FRESH_MS = 20 * 60 * 1000L
    }
}
