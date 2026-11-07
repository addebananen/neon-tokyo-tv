package com.lumera.app.handoff

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.lumera.app.data.auth.StremioAuthManager
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.WatchHistoryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Continue watching kept the same as the Stremio account's, for the one TV profile it belongs
 * to (the TV shares one Stremio account between profiles for addons; history is per profile).
 * Stremio's app, the web app, the iPhone app and the PC app read and write the same items, so
 * a film paused on the phone shows here at that spot, and the other way round.
 * The rule is the shared core's: per title, the newest change wins.
 */
@Singleton
class StremioWatchSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: StremioAuthManager,
    private val datastore: StremioDatastore,
    private val dao: AddonDao
) {
    private val mutex = Mutex()
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /** Whether the profile watching now is the one the account's Continue watching belongs to. */
    suspend fun appliesNow(): Boolean {
        val active = dao.getActiveProfileId() ?: return false
        return auth.getStoredAuthKey() != null && auth.watchSyncProfileId(dao) == active
    }

    /** Pull what changed in the account, then push what changed here. Quietly does nothing when off. */
    suspend fun sync(pullAll: Boolean = false) = mutex.withLock {
        val key = auth.getStoredAuthKey() ?: return@withLock
        val profileId = dao.getActiveProfileId() ?: return@withLock
        if (auth.watchSyncProfileId(dao) != profileId) return@withLock
        val stamp = "${profileId}_${key.takeLast(8)}"
        val startedAt = System.currentTimeMillis()
        try {
            val items = datastore.get(key, emptyList())
            val since = if (pullAll) 0L else prefs.getLong("pull_$stamp", 0L)
            val pulled = absorb(items, since)
            val pushed = push(key, items, prefs.getLong("push_$stamp", 0L))
            prefs.edit().putLong("pull_$stamp", startedAt).putLong("push_$stamp", startedAt).apply()
            if (pulled + pushed > 0) Log.i(TAG, "Stremio Continue watching: $pulled in, $pushed out")
        } catch (e: StremioDatastore.AuthException) {
            Log.w(TAG, "Stremio sign-in no longer works: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Stremio sync failed: ${e.message}")
        }
    }

    /** Stremio items with progress, changed since [since], into this profile's history. */
    private suspend fun absorb(items: List<JsonObject>, since: Long): Int {
        var n = 0
        val oldest = System.currentTimeMillis() - MAX_PULL_AGE_MS
        for (item in items) {
            val type = item.str("type")
            if (type != "movie" && type != "series") continue
            val itemId = item.str("_id").takeIf { it.isNotBlank() } ?: continue
            val state = item.get("state")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val lastWatched = HandoffProtocol.parseIso(state.str("lastWatched")).takeIf { it > 0L }
                ?: HandoffProtocol.parseIso(item.str("_mtime"))
            if (lastWatched <= since || lastWatched < oldest) continue
            val video = state.str("video_id").ifBlank { if (type == "movie") itemId else "" }
            if (video.isBlank() || (type == "series" && !isEpisodeId(video))) continue
            val offset = state.num("timeOffset").coerceAtLeast(0L)
            val duration = state.num("duration").coerceAtLeast(0L)
            val flagged = state.num("flaggedWatched") > 0L
            if (offset <= 0L && !flagged) continue
            val local = dao.getHistoryItem(video)
            if (local != null && local.lastWatched >= lastWatched - SAME_CHANGE_MS) continue
            val name = item.str("name")
            val title = if (type == "series") {
                val p = video.split(':')
                "S${p[p.size - 2]}:E${p[p.size - 1]} - $name"
            } else name
            val length = when {
                duration > 0L -> duration
                local != null && local.duration > 0L && !local.isShare -> local.duration
                else -> offset + 1L
            }
            dao.upsertHistory(
                WatchHistoryEntity(
                    id = video,
                    title = local?.title ?: title,
                    poster = item.str("poster").ifBlank { local?.poster.orEmpty() }.ifBlank { null },
                    background = local?.background,
                    logo = local?.logo,
                    position = if (flagged && offset == 0L) length else offset.coerceAtMost(length),
                    duration = length,
                    lastWatched = lastWatched,
                    type = type,
                    watched = flagged && offset == 0L,
                    scrobbled = local?.scrobbled ?: false
                )
            )
            n++
        }
        return n
    }

    /** This profile's history changed since [since] as Stremio items, merged into the account's own. */
    private suspend fun push(key: String, items: List<JsonObject>, since: Long): Int {
        val remote = items.associateBy { it.str("_id") }
        val out = LinkedHashMap<String, JsonObject>()
        val changed = dao.getAllWatchHistoryOnce()
            .filter { it.lastWatched > since && !it.isShare && it.duration > 0L && (it.type == "movie" || it.type == "series") }
            .sortedBy { it.lastWatched }
        for (e in changed) {
            val video = e.id
            if (e.type == "series" && !isEpisodeId(video)) continue
            val itemId = if (e.type == "series") seriesId(video) else video
            val old = out[itemId] ?: remote[itemId]
            val oldState = old?.get("state")?.takeIf { it.isJsonObject }?.asJsonObject
            val remoteAt = HandoffProtocol.parseIso(oldState?.str("lastWatched"))
            if (out[itemId] == null && remoteAt >= e.lastWatched - SAME_CHANGE_MS) continue // the account is as new
            val it = old?.deepCopy() ?: HandoffProtocol.hiddenItem(itemId, seriesName(e), e.lastWatched).apply {
                addProperty("type", e.type)
                addProperty("poster", e.poster.orEmpty())
            }
            val st = it.getAsJsonObject("state")
            st.addProperty("lastWatched", HandoffProtocol.isoTime(e.lastWatched))
            st.addProperty("video_id", video)
            st.addProperty("duration", e.duration)
            st.addProperty("timeOffset", if (e.watched) 0L else e.position)
            st.addProperty("flaggedWatched", if (e.watched) 1 else 0)
            if (e.watched) st.addProperty("timesWatched", (oldState?.num("timesWatched") ?: 0L) + 1L)
            if (e.type == "series") {
                val p = video.split(':')
                st.addProperty("season", p[p.size - 2].toIntOrNull() ?: 0)
                st.addProperty("episode", p[p.size - 1].toIntOrNull() ?: 0)
            }
            it.addProperty("_mtime", HandoffProtocol.isoTime(e.lastWatched))
            out[itemId] = it
        }
        if (out.isNotEmpty()) datastore.put(key, out.values.toList())
        return out.size
    }

    private fun seriesName(e: WatchHistoryEntity): String =
        if (e.type == "series") e.title.substringAfter(" - ", e.title) else e.title

    private fun JsonObject.str(k: String): String = get(k)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    private fun JsonObject.num(k: String): Long =
        runCatching { get(k)?.takeIf { it.isJsonPrimitive }?.asDouble?.toLong() }.getOrNull() ?: 0L

    companion object {
        private const val TAG = "StremioWatchSync"
        private const val PREFS = "stremio_watch_sync"
        /** Two saves of the same change, here and there, are this close. */
        private const val SAME_CHANGE_MS = 5_000L
        /** On a first sync, only what was watched in the last two months comes in. */
        private const val MAX_PULL_AGE_MS = 60L * 24 * 3_600_000L

        fun isEpisodeId(id: String): Boolean {
            val p = id.split(':')
            return p.size >= 3 && p[p.size - 1].all(Char::isDigit) && p[p.size - 2].all(Char::isDigit) &&
                p[p.size - 1].isNotEmpty() && p[p.size - 2].isNotEmpty()
        }

        fun seriesId(id: String): String = if (isEpisodeId(id)) id.split(':').dropLast(2).joinToString(":") else id
    }
}
