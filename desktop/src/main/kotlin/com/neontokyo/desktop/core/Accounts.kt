package com.neontokyo.desktop.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uniffi.neon_core.*

/** A profile's Stremio sign-in. The password is never kept. */
data class StremioLink(val authKey: String, val email: String, val lastSync: Long)

/** A profile's Trakt sign-in and how far sync got. */
data class TraktLink(val token: TraktToken, val stamp: String, val pushedAt: Long)

/**
 * Stremio and Trakt, per profile, on the PC: signing in, and keeping addons, Continue watching,
 * watched marks and the watchlist the same as on the TV, the phone and other apps. The rules
 * are the shared core's; this only moves the bytes and keeps the keys.
 */
class Accounts(private val model: AppModel, private val scope: CoroutineScope) {
    var stremio by mutableStateOf<StremioLink?>(null)
        private set
    var trakt by mutableStateOf<TraktLink?>(null)
        private set
    /** A short line for Settings: "Synced 12 titles", "Signed out of Stremio…". */
    var stremioStatus by mutableStateOf("")
        private set
    var traktStatus by mutableStateOf("")
        private set
    var syncing by mutableStateOf(false)
        private set
    /** The code to type on trakt.tv/activate while signing in. */
    var traktCode by mutableStateOf<TraktDeviceCode?>(null)
        private set

    val traktAvailable: Boolean get() = AppInfo.traktClientId.isNotEmpty()

    private val lock = Mutex()
    private var loop: Job? = null
    private var pairing: Job? = null
    private var pushSoon: Job? = null

    private fun now() = System.currentTimeMillis()

    // ---------- keeping the keys ----------

    fun load(profileId: String?) {
        traktCode = null
        pairing?.cancel()
        stremio = profileId?.let { pid ->
            Store.lines("stremio-$pid")?.takeIf { it.size >= 2 }?.let { StremioLink(it[0], it[1], it.getOrNull(2)?.toLongOrNull() ?: 0L) }
        }
        trakt = profileId?.let { pid ->
            Store.lines("trakt-$pid")?.takeIf { it.size >= 3 }?.let {
                TraktLink(
                    TraktToken(it[0], it[1], it[2].toLongOrNull() ?: 0L, it.getOrNull(3)?.takeIf { u -> u != "-" }),
                    it.getOrNull(4)?.takeIf { s -> s != "-" } ?: "",
                    it.getOrNull(5)?.toLongOrNull() ?: 0L,
                )
            }
        }
        stremioStatus = stremio?.let { "Signed in as ${it.email}" } ?: ""
        traktStatus = trakt?.let { "Signed in" + (it.token.username?.let { u -> " as $u" } ?: "") } ?: ""
        startLoop()
    }

    private fun saveStremio(pid: String, link: StremioLink?) {
        stremio = link
        if (link == null) Store.delete("stremio-$pid") else Store.writeLines("stremio-$pid", listOf(link.authKey, link.email, link.lastSync.toString()))
    }

    private fun saveTrakt(pid: String, link: TraktLink?) {
        trakt = link
        if (link == null) Store.delete("trakt-$pid")
        else Store.writeLines(
            "trakt-$pid",
            listOf(link.token.accessToken, link.token.refreshToken, link.token.expiresAtMs.toString(), link.token.username ?: "-",
                link.stamp.ifEmpty { "-" }, link.pushedAt.toString()),
        )
    }

    /** Syncs now, then every five minutes while the app is open. */
    private fun startLoop() {
        loop?.cancel()
        if (stremio == null && trakt == null) return
        loop = scope.launch {
            while (isActive) {
                syncAll()
                delay(5 * 60_000L)
            }
        }
    }

    /** Called after something changes here; sends it a few seconds later, in one go. */
    fun changed() {
        if (stremio == null && trakt == null) return
        pushSoon?.cancel()
        pushSoon = scope.launch {
            delay(6_000)
            syncAll()
        }
    }

    suspend fun syncAll(full: Boolean = false) {
        val pid = model.activeProfileId ?: return
        lock.withLock {
            syncing = true
            try {
                if (stremio != null) runCatching { syncStremio(pid, full) }.onFailure { stremioStatus = message(it, "Stremio") }
                if (trakt != null) runCatching { syncTrakt(pid) }.onFailure { traktStatus = message(it, "Trakt") }
            } finally {
                syncing = false
            }
        }
    }

    private fun message(e: Throwable, who: String) = when (e) {
        is CoreException.SignedOut -> "Signed out of $who, sign in again"
        is java.io.IOException, is java.net.http.HttpTimeoutException -> "$who could not be reached"
        is CoreException.BadResponse -> e.reason
        else -> e.message ?: "$who sync failed"
    }

    // ---------- Stremio ----------

    suspend fun stremioSignIn(email: String, password: String) {
        val pid = model.activeProfileId ?: return
        val a = Net.call(stremioUrl("login"), stremioLoginBody(email, password))
        val auth = parseStremioLogin(a.body, email)
        saveStremio(pid, StremioLink(auth.authKey, auth.email, 0L))
        stremioStatus = "Signed in as ${auth.email}"
        syncAll(full = true)
        startLoop()
    }

    fun stremioSignOut() {
        val pid = model.activeProfileId ?: return
        val link = stremio ?: return
        scope.launch { runCatching { Net.call(stremioUrl("logout"), stremioLogoutBody(link.authKey)) } }
        saveStremio(pid, null)
        stremioStatus = ""
    }

    private suspend fun syncStremio(pid: String, full: Boolean) {
        val link = stremio ?: return
        val started = now()
        if (full || link.lastSync == 0L) {
            val addons = parseStremioAddons(Net.call(stremioUrl("addonCollectionGet"), stremioAddonsBody(link.authKey)).body)
            model.mergeAddons(addons)
        }
        val lib = Net.call(stremioUrl("datastoreGet"), stremioLibraryBody(link.authKey))
        try {
            val m = stremioLibraryMerge(lib.body, link.authKey, model.history, model.watchlist, pid, link.lastSync, started)
            model.applySynced(m.history, m.watchlist)
            m.uploadBody?.let { body ->
                val put = Net.call(stremioUrl("datastorePut"), body)
                if (!put.ok) error("Stremio did not take the changes (${put.status})")
            }
            saveStremio(pid, link.copy(lastSync = started))
            stremioStatus = "Synced " + (if (m.received + m.sent > 0u) "${m.received} in, ${m.sent} out" else "and up to date") + " · ${clock()}"
        } catch (e: CoreException.SignedOut) {
            saveStremio(pid, null)
            throw e
        }
    }

    // ---------- Trakt ----------

    private fun headers(token: String?): Map<String, String> =
        traktHeaders(AppInfo.traktClientId, token).associate { it.name to it.value }

    /** Shows a code; when it is typed on trakt.tv/activate the profile is signed in. */
    fun traktSignIn() {
        val pid = model.activeProfileId ?: return
        if (!traktAvailable) {
            traktStatus = "This build has no Trakt key"
            return
        }
        pairing?.cancel()
        pairing = scope.launch {
            try {
                val a = Net.call(traktUrl("oauth/device/code"), traktDeviceCodeBody(AppInfo.traktClientId), headers(null))
                val code = parseTraktDeviceCode(a.body, now())
                traktCode = code
                traktStatus = ""
                var wait = code.intervalMs.toLong()
                while (isActive && now() < code.expiresAtMs) {
                    delay(wait)
                    val p = Net.call(traktUrl("oauth/device/token"), traktTokenBody(AppInfo.traktClientId, null, code.deviceCode), headers(null))
                    when (val r = parseTraktPoll(p.status.toUShort(), p.body, now())) {
                        is TraktPoll.Done -> {
                            val name = runCatching { parseTraktUsername(Net.call(traktUrl("users/settings"), null, headers(r.token.accessToken)).body) }.getOrNull()
                            saveTrakt(pid, TraktLink(r.token.copy(username = name), "", 0L))
                            traktCode = null
                            traktStatus = "Signed in" + (name?.let { " as $it" } ?: "")
                            syncAll()
                            startLoop()
                            return@launch
                        }
                        TraktPoll.Waiting -> {}
                        TraktPoll.SlowDown -> wait += 1000
                        is TraktPoll.Failed -> {
                            traktCode = null
                            traktStatus = r.reason
                            return@launch
                        }
                    }
                }
                traktCode = null
                traktStatus = "The code ran out, try again"
            } catch (e: Exception) {
                traktCode = null
                traktStatus = message(e, "Trakt")
            }
        }
    }

    fun traktCancel() {
        pairing?.cancel()
        traktCode = null
    }

    fun traktSignOut() {
        val pid = model.activeProfileId ?: return
        val link = trakt ?: return
        scope.launch { runCatching { Net.call(traktUrl("oauth/revoke"), traktRevokeBody(AppInfo.traktClientId, link.token.accessToken), headers(link.token.accessToken)) } }
        saveTrakt(pid, null)
        traktStatus = ""
    }

    private suspend fun freshToken(pid: String): TraktLink? {
        var link = trakt ?: return null
        if (traktNeedsRefresh(link.token, now())) {
            val r = Net.call(traktUrl("oauth/token"), traktRefreshBody(AppInfo.traktClientId, null, link.token.refreshToken), headers(null))
            if (r.status == 400 || r.status == 401) {
                saveTrakt(pid, null)
                throw CoreException.SignedOut("Trakt sign-in ran out")
            }
            val t = parseTraktToken(r.body, now())
            link = link.copy(token = t.copy(username = link.token.username))
            saveTrakt(pid, link)
        }
        return link
    }

    private suspend fun syncTrakt(pid: String) {
        var link = freshToken(pid) ?: return
        val h = headers(link.token.accessToken)
        val act = Net.call(traktUrl("sync/last_activities"), null, h)
        if (act.status == 401) {
            saveTrakt(pid, null)
            throw CoreException.SignedOut("Trakt")
        }
        val stamp = traktActivityStamp(act.body)
        var changed = 0u
        if (stamp != link.stamp) {
            val playback = Net.call(traktUrl("sync/playback?limit=60"), null, h)
            if (playback.ok) {
                val m = traktMergePlayback(playback.body, model.history, pid)
                model.applySynced(m.history, null)
                changed += m.changed
            }
            val movies = Net.call(traktUrl("sync/watched/movies"), null, h)
            val shows = Net.call(traktUrl("sync/watched/shows"), null, h)
            if (movies.ok && shows.ok) {
                val m = traktMergeWatched(movies.body, shows.body, model.history, pid, 120u)
                model.applySynced(m.history, null)
                changed += m.changed
            }
        }
        // What was watched here and Trakt has not heard of
        val pending = traktPendingHistory(model.history, pid, link.pushedAt)
        val started = now()
        var sent = 0
        traktHistoryBody(pending)?.let { body ->
            val r = Net.call(traktUrl("sync/history"), body, h)
            if (r.ok) sent = pending.size
        }
        link = link.copy(stamp = stamp, pushedAt = if (pending.isEmpty() || sent > 0) started else link.pushedAt)
        saveTrakt(pid, link)
        traktStatus = "Synced " + (if (changed + sent.toUInt() > 0u) "$changed in, $sent out" else "and up to date") + " · ${clock()}"
    }

    /** When the player closes: tells Trakt where it stopped (past 80 % counts as watched). */
    fun scrobbleStop(playbackId: String, kind: String, positionMs: ULong, durationMs: ULong) {
        val link = trakt ?: return
        if (durationMs == 0uL) return
        val pct = positionMs.toDouble() / durationMs.toDouble() * 100.0
        if (pct < 1.0) return
        val body = traktScrobbleBody(playbackId, kind, pct) ?: return
        scope.launch {
            runCatching {
                val pid = model.activeProfileId ?: return@runCatching
                val fresh = freshToken(pid) ?: link
                Net.call(traktUrl("scrobble/stop"), body, headers(fresh.token.accessToken))
            }
        }
    }

    private fun clock(): String = java.time.LocalTime.now().withNano(0).withSecond(0).toString()
}
