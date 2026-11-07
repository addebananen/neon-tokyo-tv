package com.lumera.app.handoff

import android.content.Context
import android.content.Intent
import android.util.Log
import com.lumera.app.MainActivity
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.launcher.HomeWatchingEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Opens a title on this TV where another device left it, and plays it: "Fortsätt på TV:n".
 * The one way in for every channel (the Stremio mailbox, the phone remote on the home network),
 * so they all behave the same.
 */
object HandoffOpen {
    private const val TAG = "HandoffOpen"

    data class Request(
        /** The title's id (the series' id for an episode). */
        val id: String,
        /** "movie" or "series". */
        val type: String,
        /** What plays: the episode's id for a series ("tt…:1:2"), else the title's id. */
        val videoId: String = id,
        val name: String = "",
        /** The line the player shows, like "S1:E2 - Show"; falls back to [name]. */
        val title: String = "",
        val poster: String = "",
        val background: String = "",
        val logo: String = "",
        /** Where the other device was; 0 starts from the beginning (or this TV's own spot). */
        val positionMs: Long = 0L,
        /** Its length there; 0 or [WatchHistoryEntity.PERCENT_DURATION_MS] when unknown. */
        val durationMs: Long = 0L,
        /** The profile's name on the other device; this TV's profile of the same name plays it. */
        val profileName: String = "",
        /** Shown on the TV: "Fortsätter från iPhone". */
        val fromDevice: String = ""
    )

    /** Saves where to pick up for the matching profile, then opens and plays. Returns the profile used. */
    suspend fun start(context: Context, request: Request): Int? {
        val app = context.applicationContext
        val dao = EntryPointAccessors.fromApplication(app, HomeWatchingEntryPoint::class.java).dao()
        val profileId = withContext(Dispatchers.IO) {
            val profiles = dao.getProfiles().first()
            val current = dao.getActiveProfileId()?.let { id -> profiles.firstOrNull { it.id == id } }
            val profile = HandoffProtocol.pickProfile(request.profileName, profiles, { it.name }, current)
                ?: return@withContext null
            remember(dao, profile.id, request)
            profile.id
        } ?: run {
            Log.w(TAG, "No profile on this TV to play ${request.id} for")
            return null
        }

        val type = if (request.type == "series") "series" else "movie"
        val intent = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(MainActivity.EXTRA_OPEN_PROFILE, profileId)
            .putExtra(MainActivity.EXTRA_OPEN_TYPE, type)
            .putExtra(MainActivity.EXTRA_OPEN_ID, request.id)
            .putExtra(MainActivity.EXTRA_OPEN_NAME, request.name)
            .putExtra(MainActivity.EXTRA_OPEN_POSTER, request.poster)
            .putExtra(MainActivity.EXTRA_OPEN_BACKGROUND, request.background)
            .putExtra(MainActivity.EXTRA_OPEN_LOGO, request.logo)
            .putExtra(MainActivity.EXTRA_OPEN_VIDEO, request.videoId.ifBlank { request.id })
            .putExtra(MainActivity.EXTRA_OPEN_AUTOPLAY, true)
            .putExtra(MainActivity.EXTRA_OPEN_FROM, request.fromDevice)
        return try {
            withContext(Dispatchers.Main) { app.startActivity(intent) }
            profileId
        } catch (e: Exception) {
            Log.w(TAG, "Could not open ${request.id}", e)
            null
        }
    }

    /** The other device's spot, as this profile's newest progress on the title. */
    private suspend fun remember(dao: com.lumera.app.data.local.AddonDao, profileId: Int, request: Request) {
        if (request.positionMs <= 0L) return
        val video = request.videoId.ifBlank { request.id }
        val existing = dao.getHistoryItemOf(profileId, video)
        val share = WatchHistoryEntity.PERCENT_DURATION_MS
        val localLength = existing?.duration?.takeIf { it > 0L && !existing.isShare }
        val (position, duration) = when {
            // The other device knew the length: its spot as it is
            request.durationMs > 0L && request.durationMs != share -> request.positionMs to request.durationMs
            // It only knew the share watched: that share of the length known here, else kept a share
            request.durationMs == share -> if (localLength != null) {
                (request.positionMs.toDouble() / share * localLength).toLong() to localLength
            } else {
                request.positionMs to share
            }
            // A spot with no length at all
            else -> request.positionMs to (localLength ?: (request.positionMs + 1L))
        }.let { (p, d) -> p.coerceIn(0L, d) to d }
        val type = if (request.type == "series") "series" else "movie"
        dao.insertHistoryRows(
            listOf(
                WatchHistoryEntity(
                    id = video,
                    title = request.title.ifBlank { existing?.title ?: request.name },
                    poster = request.poster.ifBlank { existing?.poster.orEmpty() }.ifBlank { null },
                    background = request.background.ifBlank { existing?.background.orEmpty() }.ifBlank { null },
                    logo = request.logo.ifBlank { existing?.logo.orEmpty() }.ifBlank { null },
                    position = position,
                    duration = duration,
                    lastWatched = System.currentTimeMillis(),
                    type = type,
                    watched = false,
                    scrobbled = existing?.scrobbled ?: false,
                    profileId = profileId
                )
            )
        )
    }
}
