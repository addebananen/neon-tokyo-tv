package com.lumera.app.livetv

import android.content.Context
import android.util.Log
import com.lumera.app.data.model.stremio.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.lang.ref.SoftReference
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The films and series of the household's own IPTV service, offered as sources on a title's
 * details page next to the addons'. The service's lists are kept on the TV for a day; the
 * episodes of a series are asked for when that series is opened.
 *
 * Addresses carry the login, so they are never logged.
 */
object VodLibrary {

    /** Marks the sources that come from the service, so they can be told from the addons'. */
    const val TRANSPORT = "neon-tokyo://iptv"
    const val LABEL = "[IPTV]"

    private const val TAG = "VodLibrary"
    private const val FRESH_MS = 24 * 60 * 60_000L

    private class Library(val signature: String, val index: VodCatalog.Index)

    private val lock = Mutex()
    // Soft, so a big library gives its memory back when the TV needs it; the lists stay on disk
    private var movies: SoftReference<Library>? = null
    private var series: SoftReference<Library>? = null
    private val episodes = HashMap<String, List<VodEpisode>>()

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * The service's copies of one title: the film itself, or for an episode id ("tt0944947:1:2")
     * that episode of the series. Empty when there is no service, it has no such title, or it
     * does not answer.
     */
    suspend fun streams(
        context: Context,
        type: String,
        videoId: String,
        title: String,
        year: Int?,
        tmdb: String?,
        seasonEpisode: Pair<Int, Int>? = null
    ): List<Stream> = withContext(Dispatchers.IO) {
        val source = LiveTv.source(context) as? IptvSource.Xtream ?: return@withContext emptyList()
        if (title.isBlank()) return@withContext emptyList()
        try {
            when (type) {
                "movie" -> {
                    val index = library(context, source, movie = true) ?: return@withContext emptyList()
                    val found = index.find(title, year, tmdb)
                    Log.i(TAG, "${found.size} films on the service for \"$title\" ($year)")
                    found.map { item ->
                        val ext = item.ext.ifBlank { "mp4" }
                        stream(item.name, ext, XtreamApi.vodUrl(source, "movie", item.id, ext))
                    }
                }
                "series" -> {
                    val (season, episode) = seasonEpisode ?: episodeOf(videoId) ?: run {
                        Log.i(TAG, "No season and episode in the episode id")
                        return@withContext emptyList()
                    }
                    val index = library(context, source, movie = false) ?: return@withContext emptyList()
                    val shows = index.find(title, year, tmdb)
                    Log.i(TAG, "${shows.size} series on the service for \"$title\" ($year), looking for S${season}E$episode")
                    shows.flatMap { show ->
                        val all = episodesOf(source, show.id)
                        if (all.none { it.season == season && it.episode == episode }) {
                            Log.i(TAG, "\"${show.name}\" has ${all.size} episodes on the service, not S${season}E$episode")
                        }
                        all.filter { it.season == season && it.episode == episode }
                            .map { ep ->
                                val ext = ep.ext.ifBlank { "mp4" }
                                val name = "${show.name} " + String.format(Locale.US, "S%02dE%02d", season, episode) +
                                    ep.title.takeIf { it.isNotBlank() && it != show.name }?.let { " · $it" }.orEmpty()
                                stream(name, ext, XtreamApi.vodUrl(source, "series", ep.id, ext))
                            }
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "The service's library did not answer: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    /** Season and episode from "tt0944947:1:2" or "tmdb:1399:1:2". */
    fun episodeOf(videoId: String): Pair<Int, Int>? {
        val parts = videoId.split(':')
        if (parts.size < 3) return null
        val season = parts[parts.size - 2].toIntOrNull() ?: return null
        val episode = parts.last().toIntOrNull() ?: return null
        return season to episode
    }

    private fun stream(name: String, ext: String, url: String) = Stream(
        name = listOfNotNull(LABEL, VodCatalog.quality(name)).joinToString(" "),
        // No file type in the text: the sources filter reads "TS" as a camera copy
        title = "$name\nfrom your TV service",
        url = url,
        addonTransportUrl = TRANSPORT
    )

    private suspend fun library(context: Context, source: IptvSource.Xtream, movie: Boolean): VodCatalog.Index? = lock.withLock {
        val signature = "${source.base}|${source.username}"
        val kept = (if (movie) movies else series)?.get()
        if (kept != null && kept.signature == signature) return@withLock kept.index

        val dir = File(context.applicationContext.filesDir, "livetv").apply { mkdirs() }
        val file = File(dir, if (movie) "vod-movies.json" else "vod-series.json")
        val signatureFile = File(dir, "vod.signature")
        val sameService = signatureFile.takeIf { it.exists() }?.readText() == signature.hashCode().toString()
        val fresh = sameService && file.exists() && System.currentTimeMillis() - file.lastModified() < FRESH_MS
        if (!fresh) {
            try {
                download(XtreamApi.apiUrl(source, if (movie) "get_vod_streams" else "get_series"), file)
                signatureFile.writeText(signature.hashCode().toString())
            } catch (e: Exception) {
                // An old list still finds most titles
                Log.w(TAG, "Could not fetch the service's ${if (movie) "films" else "series"}: ${e.javaClass.simpleName}")
                if (!sameService || !file.exists()) return@withLock null
            }
        }
        val items = file.bufferedReader().use { if (movie) VodCatalog.readMovies(it) else VodCatalog.readSeries(it) }
        val index = VodCatalog.Index(items)
        Log.i(TAG, "${items.size} ${if (movie) "films" else "series"} from the service")
        val library = Library(signature, index)
        if (movie) movies = SoftReference(library) else { series = SoftReference(library); synchronized(episodes) { episodes.clear() } }
        index
    }

    private fun episodesOf(source: IptvSource.Xtream, seriesId: String): List<VodEpisode> {
        synchronized(episodes) { episodes[seriesId]?.let { return it } }
        val request = Request.Builder().url(XtreamApi.apiUrl(source, "get_series_info") + "&series_id=$seriesId")
            .header("User-Agent", "NeonTokyoTV").build()
        val list = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.charStream()?.use { VodCatalog.readEpisodes(it) }.orEmpty()
        }
        synchronized(episodes) { episodes[seriesId] = list }
        return list
    }

    private fun download(url: String, into: File) {
        val request = Request.Builder().url(url).header("User-Agent", "NeonTokyoTV").build()
        val part = File(into.parentFile, into.name + ".part")
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty")
            part.outputStream().use { out -> body.byteStream().use { it.copyTo(out, 64 * 1024) } }
        }
        if (!part.renameTo(into)) { into.delete(); part.renameTo(into) }
    }

    /** Forgets what was fetched, for a service that was changed or removed. */
    fun forget() {
        movies = null
        series = null
        synchronized(episodes) { episodes.clear() }
    }
}
