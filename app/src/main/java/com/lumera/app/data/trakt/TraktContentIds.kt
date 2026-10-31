package com.lumera.app.data.trakt

import com.lumera.app.data.model.trakt.TraktIds

/**
 * Stremio content ids as Trakt knows them. Addons name a title by IMDb ("tt123") or with a
 * prefix ("tmdb:123", "tvdb:123"); a playback id adds ":season:episode" and maybe ":streamIndex".
 */
internal object TraktContentIds {
    private val PREFIXED = setOf("tmdb", "tvdb", "kitsu", "mal", "anilist", "anidb")

    /** Trakt ids for a content id, or null when Trakt can't place it (kitsu:, mal:, own videos). */
    fun traktIds(contentId: String): TraktIds? {
        if (contentId.startsWith("tt")) return TraktIds(imdb = contentId)
        val number = contentId.substringAfter(':', "").toIntOrNull() ?: return null
        return when (contentId.substringBefore(':')) {
            "tmdb" -> TraktIds(tmdb = number)
            "tvdb" -> TraktIds(tvdb = number)
            else -> null
        }
    }

    /** Every content id one Trakt title can go by here, IMDb first. */
    fun keys(ids: TraktIds?): List<String> =
        if (ids == null) emptyList()
        else listOfNotNull(ids.imdb, ids.tmdb?.let { "tmdb:$it" }, ids.tvdb?.let { "tvdb:$it" })

    /** "tmdb:1399:2:5:0" is show "tmdb:1399", season 2, episode 5; null for a film's id. */
    fun episode(playbackId: String): Triple<String, Int, Int>? {
        val (contentId, rest) = split(playbackId)
        if (rest.size < 2) return null
        val season = rest[0].toIntOrNull() ?: return null
        val episode = rest[1].toIntOrNull() ?: return null
        return Triple(contentId, season, episode)
    }

    /** The playback id without its stream index: "tt1:2:5:0" is "tt1:2:5". */
    fun normalize(playbackId: String): String {
        val (contentId, rest) = split(playbackId)
        if (rest.size < 3 || rest.take(3).any { it.toIntOrNull() == null }) return playbackId
        return (listOf(contentId) + rest.take(2)).joinToString(":")
    }

    /** The title's own id: the show for an episode, the id itself for a film. */
    fun contentId(playbackId: String): String = split(playbackId).first

    private fun split(playbackId: String): Pair<String, List<String>> {
        val parts = playbackId.split(":")
        val idParts = if (parts.size > 1 && parts[0] in PREFIXED) 2 else 1
        return parts.take(idParts).joinToString(":") to parts.drop(idParts)
    }
}
