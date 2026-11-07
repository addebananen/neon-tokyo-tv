package com.lumera.app.domain

import com.lumera.app.data.model.stremio.MetaVideo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The thinking behind the smart start page: which of a profile's series have a new episode
 * tonight, which genres the profile watches most, and where the smart rows sit among the
 * rows the profile set up itself. Pure functions, so the rules are tested without a TV.
 */
object SmartHome {

    /** Row ids start with this, so nothing mistakes a smart row for an addon catalog. */
    const val ROW_PREFIX = "smart:"
    const val NEW_EPISODES_ID = "${ROW_PREFIX}new-episodes"
    const val TOP_SWEDEN_ID = "${ROW_PREFIX}top-sweden"
    fun genreRowId(genre: String) = "${ROW_PREFIX}genre:$genre"

    // ── New episode tonight ─────────────────────────────────────────────────────

    /** An episode of a series the profile follows that came out lately or comes out later today. */
    data class EpisodeHit(
        val seriesId: String,
        val season: Int,
        val episode: Int,
        val title: String?,
        val releasedAt: Instant
    ) {
        val playbackId: String get() = "$seriesId:$season:$episode"
    }

    enum class EpisodeWindow { TONIGHT, THIS_WEEK }

    /**
     * When an episode counts as tonight's: it came out within the last 30 hours (an American
     * evening premiere lands in the Swedish small hours, a streaming drop in the morning) or
     * it comes out before midnight today.
     */
    fun tonightRange(now: ZonedDateTime): ClosedRange<Instant> {
        val endOfToday = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant()
        return now.toInstant().minusSeconds(30 * 3600L)..endOfToday.minusMillis(1)
    }

    /** The fallback window when nothing is new tonight: out within the last seven days. */
    fun weekRange(now: ZonedDateTime): ClosedRange<Instant> =
        now.toInstant().minusSeconds(7 * 24 * 3600L)..now.toInstant()

    /** Stremio "released" values: full ISO instants, or just a date. */
    fun parseReleased(released: String?): Instant? {
        if (released.isNullOrBlank()) return null
        return runCatching { Instant.parse(released) }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(released).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(released.take(10)).atStartOfDay(ZoneId.of("UTC")).toInstant() }.getOrNull()
    }

    /**
     * The newest unwatched episode of one series inside [range], or null. Specials (season 0)
     * never count, and an episode the profile already watched is not news.
     */
    fun latestEpisodeIn(
        seriesId: String,
        videos: List<MetaVideo>?,
        watchedPlaybackIds: Set<String>,
        range: ClosedRange<Instant>
    ): EpisodeHit? {
        if (videos.isNullOrEmpty()) return null
        return videos.asSequence()
            .filter { it.season > 0 && it.episode > 0 }
            .mapNotNull { v ->
                val at = parseReleased(v.released) ?: return@mapNotNull null
                if (at !in range) return@mapNotNull null
                EpisodeHit(seriesId, v.season, v.episode, v.title.takeIf { it.isNotBlank() && it != "Episode" }, at)
            }
            .filter { it.playbackId !in watchedPlaybackIds }
            // The first new one is the one to watch tonight; a double bill shows its first part
            .minWithOrNull(compareBy<EpisodeHit>({ it.releasedAt }, { it.season }, { it.episode }))
    }

    /**
     * Tonight's episodes when there are any, else this week's, with which window it was.
     * Out already comes before later tonight, newest first; a later one goes by its time.
     */
    fun pickNewEpisodes(
        series: List<Pair<String, List<MetaVideo>?>>,
        watchedPlaybackIds: Set<String>,
        now: ZonedDateTime
    ): Pair<EpisodeWindow, List<EpisodeHit>>? {
        val nowInstant = now.toInstant()
        val tonight = series.mapNotNull { (id, videos) -> latestEpisodeIn(id, videos, watchedPlaybackIds, tonightRange(now)) }
        if (tonight.isNotEmpty()) {
            val (out, later) = tonight.partition { !it.releasedAt.isAfter(nowInstant) }
            return EpisodeWindow.TONIGHT to (out.sortedByDescending { it.releasedAt } + later.sortedBy { it.releasedAt })
        }
        val week = series.mapNotNull { (id, videos) -> latestEpisodeIn(id, videos, watchedPlaybackIds, weekRange(now)) }
        if (week.isNotEmpty()) return EpisodeWindow.THIS_WEEK to week.sortedByDescending { it.releasedAt }
        return null
    }

    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    /** The corner label on an episode card: "S2 E5", plus the time when it is still to come. */
    fun episodeBadge(hit: EpisodeHit, now: ZonedDateTime): String {
        val code = "S${hit.season} E${hit.episode}"
        if (!hit.releasedAt.isAfter(now.toInstant())) return code
        val local = hit.releasedAt.atZone(now.zone)
        // Date-only releases sit at midnight UTC and carry no real time to show
        val hasTime = hit.releasedAt.atZone(ZoneId.of("UTC")).toLocalTime() != java.time.LocalTime.MIDNIGHT
        return if (hasTime) "$code · ${local.format(clock)}" else code
    }

    fun newEpisodesTitle(window: EpisodeWindow, count: Int): String = when (window) {
        EpisodeWindow.TONIGHT -> if (count == 1) "New episode tonight" else "New episodes tonight"
        EpisodeWindow.THIS_WEEK -> if (count == 1) "New episode this week" else "New episodes this week"
    }

    // ── Most-watched genres ─────────────────────────────────────────────────────

    /** Genres Cinemeta can filter by, so every genre row can be filled. */
    val ROW_GENRES = listOf(
        "Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family",
        "Fantasy", "History", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller", "War", "Western"
    )

    /**
     * Genre names from Cinemeta, TMDB and the anime addons, folded onto [ROW_GENRES].
     * "Sci-Fi & Fantasy" counts for both; "Biography" and the like count for nothing.
     */
    fun normalizeGenre(raw: String): List<String> {
        val g = raw.trim().lowercase()
        return when (g) {
            "science fiction", "sci-fi", "scifi", "science-fiction" -> listOf("Sci-Fi")
            "sci-fi & fantasy" -> listOf("Sci-Fi", "Fantasy")
            "action & adventure" -> listOf("Action", "Adventure")
            "war & politics" -> listOf("War")
            "kids" -> listOf("Family")
            "anime" -> listOf("Animation")
            "suspense", "psychological" -> listOf("Thriller")
            "documentaries" -> listOf("Documentary")
            "historical" -> listOf("History")
            else -> ROW_GENRES.filter { it.equals(g, ignoreCase = true) }
        }
    }

    /** One title in a profile's history, folded to the series level, with what it says about taste. */
    data class Watched(
        val id: String,
        val genres: List<String>,
        /** How much of it was seen: 1 for a finished film, more for a series with many episodes. */
        val amount: Float,
        val lastWatched: Long
    )

    /**
     * The profile's favourite genres, best first. A title counts by how much of it was seen,
     * a series grows with the square root of its episodes so one long show can't own the page,
     * and taste fades with a 45-day half-life. A genre needs two titles to count, and Drama,
     * which covers half of everything, needs to clearly lead before it gets a row.
     */
    fun topGenres(watched: List<Watched>, nowMillis: Long, max: Int = 3): List<String> {
        val score = mutableMapOf<String, Float>()
        val titles = mutableMapOf<String, Int>()
        for (w in watched) {
            val genres = w.genres.flatMap(::normalizeGenre).distinct()
            if (genres.isEmpty()) continue
            val ageDays = ((nowMillis - w.lastWatched).coerceAtLeast(0L)) / 86_400_000.0
            val weight = (w.amount * 0.5.pow(ageDays / 45.0)).toFloat()
            // A title with many genres spreads its weight a little, so precise genres win
            val share = weight / sqrt(genres.size.toFloat())
            genres.forEach { g ->
                score[g] = (score[g] ?: 0f) + share
                titles[g] = (titles[g] ?: 0) + 1
            }
        }
        val ranked = score.entries
            .filter { (titles[it.key] ?: 0) >= 2 }
            .sortedByDescending { it.value }
        if (ranked.isEmpty()) return emptyList()
        val runnerUp = ranked.firstOrNull { it.key != "Drama" }?.value ?: 0f
        val dramaLeads = ranked.first().key == "Drama" && ranked.first().value >= runnerUp * 1.3f
        return ranked
            .filter { it.key != "Drama" || dramaLeads }
            .take(max)
            .map { it.key }
    }

    /** How much of a series an amount of watched episodes is worth, see [topGenres]. */
    fun seriesAmount(episodes: Int, finished: Int): Float =
        min(3f, sqrt(episodes.coerceAtLeast(1).toFloat())) * if (finished > 0) 1f else 0.6f

    fun genreRowTitle(genre: String, rank: Int): String = when (rank) {
        0 -> "Because you love $genre"
        1 -> "More $genre for you"
        else -> "Your kind of $genre"
    }

    // ── Where the smart rows go ─────────────────────────────────────────────────

    /**
     * New episodes, then this week's top, at the top of the page; the genre rows spread out
     * after the profile's second, fourth and sixth own rows, so its own catalog stays near.
     */
    fun <T> place(base: List<T>, top: List<T>, genreRows: List<T>): List<T> {
        if (top.isEmpty() && genreRows.isEmpty()) return base
        val out = ArrayList<T>(base.size + top.size + genreRows.size)
        out += top
        var g = 0
        base.forEachIndexed { i, row ->
            out += row
            if (i % 2 == 1 && g < genreRows.size) out += genreRows[g++]
        }
        while (g < genreRows.size) out += genreRows[g++]
        return out
    }

    /** Takes one from each list in turn, skipping ids already taken. */
    fun <T> interleave(lists: List<List<T>>, key: (T) -> String): List<T> {
        val seen = HashSet<String>()
        val out = ArrayList<T>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) for (l in lists) {
            val item = l.getOrNull(i) ?: continue
            if (seen.add(key(item))) out += item
        }
        return out
    }
}
