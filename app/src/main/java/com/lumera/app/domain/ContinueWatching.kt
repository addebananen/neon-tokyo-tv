package com.lumera.app.domain

import com.lumera.app.data.model.SeriesNextUpEntity
import com.lumera.app.data.model.WatchHistoryEntity

/**
 * One title to carry on with: a film or episode left part way, or the next episode of a series.
 * [id] is the film's or series' own id, as the details screen opens it.
 */
data class ContinueEntry(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val logo: String?,
    val progress: Float,
    val hasNewEpisode: Boolean,
    val season: Int?,
    val episode: Int?,
    val lastActivity: Long
)

/**
 * What to carry on watching, most recent first: what was left part way, then the next episode of
 * series whose episodes so far are all watched, once that episode has aired. The app's own home
 * and Neon Home both show this.
 */
fun continueWatching(
    history: List<WatchHistoryEntity>,
    seriesNextUp: List<SeriesNextUpEntity> = emptyList(),
    today: String = java.time.LocalDate.now().toString()
): List<ContinueEntry> {
    val result = mutableListOf<ContinueEntry>()
    val seriesIdsIncluded = mutableSetOf<String>()

    // 1. In-progress items (partially watched, not completed)
    val inProgress = history.filter { !it.watched }

    val seriesByCanonicalId = mutableMapOf<String, MutableList<WatchHistoryEntity>>()
    val movieById = mutableMapOf<String, WatchHistoryEntity>()

    inProgress.forEach { entry ->
        if (entry.type == "series") {
            val canonicalId = canonicalSeriesId(entry.id)
            seriesByCanonicalId.getOrPut(canonicalId) { mutableListOf() }.add(entry)
        } else {
            movieById.putIfAbsent(entry.id, entry)
        }
    }

    val chosenSeries = mutableMapOf<String, WatchHistoryEntity>()
    seriesByCanonicalId.forEach { (canonicalId, entries) ->
        val preferred = entries.firstOrNull { isEpisodePlaybackId(it.id) } ?: entries.firstOrNull()
        if (preferred != null) {
            chosenSeries[canonicalId] = preferred
        }
    }

    inProgress.forEach { entry ->
        if (entry.type == "series") {
            val canonicalId = canonicalSeriesId(entry.id)
            val chosen = chosenSeries[canonicalId] ?: return@forEach
            if (chosen.id != entry.id) return@forEach
            seriesIdsIncluded.add(canonicalId)
            val parts = chosen.id.split(":")
            val episodeId = isEpisodePlaybackId(chosen.id)
            result.add(
                ContinueEntry(
                    id = canonicalId,
                    type = entry.type,
                    name = entry.title,
                    poster = entry.poster,
                    background = chosen.background,
                    logo = chosen.logo,
                    progress = chosen.progress(),
                    hasNewEpisode = false,
                    season = if (episodeId) parts[parts.lastIndex - 1].toIntOrNull() else null,
                    episode = if (episodeId) parts.last().toIntOrNull() else null,
                    lastActivity = chosen.lastWatched
                )
            )
        } else {
            val chosen = movieById[entry.id] ?: return@forEach
            if (chosen.id != entry.id) return@forEach
            result.add(
                ContinueEntry(
                    id = entry.id,
                    type = entry.type,
                    name = entry.title,
                    poster = entry.poster,
                    background = entry.background,
                    logo = entry.logo,
                    progress = entry.progress(),
                    hasNewEpisode = false,
                    season = null,
                    episode = null,
                    lastActivity = entry.lastWatched
                )
            )
        }
    }

    // 2. Next-up entries: series where all in-progress episodes are watched,
    //    but there's a next episode available. Only include if the episode has aired.
    for (nextUp in seriesNextUp) {
        if (nextUp.seriesId in seriesIdsIncluded) continue // already shown as in-progress

        // Check if the next episode has aired (null = assume aired)
        val released = nextUp.nextReleased
        if (released != null && released > today) continue

        // Complete with no next episode date = truly done, skip
        if (nextUp.isComplete && released == null) continue

        // +1 badge: show was complete (user was caught up) and episode has now aired
        val isReturning = nextUp.isComplete || nextUp.isNewEpisode

        result.add(
            ContinueEntry(
                id = nextUp.seriesId,
                type = "series",
                name = nextUp.title,
                poster = nextUp.poster,
                background = null,
                logo = null,
                progress = 0f,
                hasNewEpisode = isReturning,
                season = nextUp.nextSeason,
                episode = nextUp.nextEpisode,
                lastActivity = nextUp.updatedAt
            )
        )
    }

    // Sort all items by most recent activity
    return result.sortedByDescending { it.lastActivity }
}

/** The series' own id from an episode's playback id ("tt123:2:5" is "tt123"). */
fun canonicalSeriesId(playbackId: String): String {
    if (!isEpisodePlaybackId(playbackId)) return playbackId
    val parts = playbackId.split(":")
    return parts.dropLast(2).joinToString(":")
}

/** Whether [playbackId] ends in a season and an episode number, as an episode's does. */
fun isEpisodePlaybackId(playbackId: String): Boolean {
    val parts = playbackId.split(":")
    if (parts.size < 3) return false
    return parts[parts.lastIndex - 1].toIntOrNull() != null && parts.last().toIntOrNull() != null
}
