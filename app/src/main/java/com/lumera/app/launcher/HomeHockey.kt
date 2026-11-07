package com.lumera.app.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** A team as SHL names it: "LHF", "Luleå", "Luleå Hockey", and its badge. */
data class HockeyTeam(val code: String, val short: String, val long: String, val icon: String?)

/** One SHL game: who meets whom, when, where, and how it went or goes. */
data class HockeyGame(
    val id: String,
    val start: Long,
    val state: String,
    val home: HockeyTeam,
    val away: HockeyTeam,
    val homeScore: Int?,
    val awayScore: Int?,
    val overtime: Boolean,
    val shootout: Boolean,
    val venue: String?,
    val round: Int?
) {
    val played: Boolean get() = state == "post-game"
    val coming: Boolean get() = state == "pre-game"

    /** On now: SHL says so, or the puck has dropped and no result has come yet. */
    fun live(now: Long): Boolean = when {
        played -> false
        !coming -> true
        else -> now >= start && now < start + HomeHockey.GAME_MS
    }

    fun side(team: String): HockeyTeam = if (home.code.equals(team, true)) home else away
    fun other(team: String): HockeyTeam = if (home.code.equals(team, true)) away else home

    /** Whether [team] won, lost or (not yet decided) neither; null before the end. */
    fun won(team: String): Boolean? {
        val h = homeScore ?: return null
        val a = awayScore ?: return null
        if (!played || h == a) return null
        return if (home.code.equals(team, true)) h > a else a > h
    }
}

/** What the Today card shows for the followed team: a game on now, one just played, or the next. */
data class HockeyNow(val game: HockeyGame, val team: String, val mode: Mode) {
    enum class Mode { Live, Result, Next }
}

/**
 * The followed SHL team's season from shl.se's own open schedule (the one its web pages read; no
 * account or key). Adrian's household follows Luleå Hockey, so that is the team until another is
 * picked. The season is asked for again every few hours, and every minute while a game is on.
 */
internal object HomeHockey {
    private const val TAG = "HomeHockey"
    private const val PREFS = "neon_home"
    private const val TEAM = "hockey_team"
    private const val KEPT = "hockey_games"
    const val DEFAULT_TEAM = "LHF"
    private const val BASE = "https://www.shl.se/api/sports-v2"

    /** A game with overtime and a shootout is over well inside three hours. */
    const val GAME_MS = 3 * 60 * 60_000L
    /** How long a result stays on the card after the game. */
    private const val RESULT_MS = 14 * 60 * 60_000L
    const val FRESH_MS = 4 * 60 * 60_000L
    const val LIVE_FRESH_MS = 60_000L

    /** The SHL teams of 2026/27, for the team picker; the season's own list wins when it has come. */
    val teams = listOf(
        "LHF" to "Luleå Hockey", "FHC" to "Frölunda HC", "FBK" to "Färjestad BK", "SAIK" to "Skellefteå AIK",
        "VLH" to "Växjö Lakers", "RBK" to "Rögle BK", "HV71" to "HV71", "LIF" to "Leksands IF", "MIF" to "Malmö Redhawks",
        "TIK" to "Timrå IK", "OHK" to "Örebro Hockey", "BIF" to "Brynäs IF", "LHC" to "Linköping HC", "DIF" to "Djurgården"
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun team(context: Context): String = prefs(context).getString(TEAM, null) ?: DEFAULT_TEAM

    fun setTeam(context: Context, code: String) {
        prefs(context).edit().putString(TEAM, code).remove(KEPT).apply()
    }

    /** The team's games kept from the last fetch, and when they were fetched. */
    fun kept(context: Context): Pair<List<HockeyGame>, Long>? = runCatching {
        val o = JSONObject(prefs(context).getString(KEPT, null) ?: return null)
        if (o.optString("team") != team(context)) return null
        val list = o.getJSONArray("games")
        (0 until list.length()).map { gameFrom(list.getJSONObject(it)) } to o.getLong("at")
    }.getOrNull()

    /**
     * The team's games this season, kept if fresh enough for [now]: a game on (or about to be)
     * wants a fresh score each minute, otherwise a few hours is plenty.
     */
    suspend fun games(context: Context, now: Long = System.currentTimeMillis()): List<HockeyGame>? {
        val team = team(context)
        val kept = kept(context)
        if (kept != null) {
            val (list, at) = kept
            val hot = list.any { it.live(now) || (it.coming && it.start - now in 0..15 * 60_000L) }
            if (now - at < if (hot) LIVE_FRESH_MS else FRESH_MS) return list
        }
        val fetched = fetch(team) ?: return kept?.first
        prefs(context).edit().putString(
            KEPT,
            JSONObject().put("team", team).put("at", now).put("games", JSONArray(fetched.map(::gameJson))).toString()
        ).apply()
        return fetched
    }

    /** What the card shows at [now]: a game on, else a result from the last hours, else the next game. */
    fun pick(games: List<HockeyGame>, team: String, now: Long): HockeyNow? {
        games.firstOrNull { it.live(now) }?.let { return HockeyNow(it, team, HockeyNow.Mode.Live) }
        games.filter { it.played && now - it.start in 0..(GAME_MS + RESULT_MS) }.maxByOrNull { it.start }
            ?.let { return HockeyNow(it, team, HockeyNow.Mode.Result) }
        games.filter { it.coming && it.start > now - GAME_MS }.minByOrNull { it.start }
            ?.let { return HockeyNow(it, team, HockeyNow.Mode.Next) }
        // The season is over: the last game played
        return games.filter { it.played }.maxByOrNull { it.start }?.let { HockeyNow(it, team, HockeyNow.Mode.Result) }
    }

    /** "Tonight 19:00", "Tomorrow 15:15", "Sat 19:00", or "Sat 14 Nov" further off. */
    fun whenText(start: Long, now: Long = System.currentTimeMillis()): String {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(start)
        val days = dayNumber(start) - dayNumber(now)
        val hour = Calendar.getInstance().apply { timeInMillis = start }.get(Calendar.HOUR_OF_DAY)
        return when {
            days == 0L -> (if (hour >= 17) "Tonight" else "Today") + " $time"
            days == 1L -> "Tomorrow $time"
            days in 2..6 -> SimpleDateFormat("EEE", Locale.ENGLISH).format(start) + " $time"
            else -> SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(start) + " · $time"
        }
    }

    /** "Today", "Yesterday", "Sat" or "Sat 14 Nov" for a game already played. */
    fun dayText(start: Long, now: Long = System.currentTimeMillis()): String {
        val days = dayNumber(now) - dayNumber(start)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days in 2..6 -> SimpleDateFormat("EEEE", Locale.ENGLISH).format(start)
            else -> SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(start)
        }
    }

    private fun dayNumber(time: Long): Long {
        val zone = TimeZone.getDefault()
        return Math.floorDiv(time + zone.getOffset(time), 86_400_000L)
    }

    private suspend fun fetch(team: String): List<HockeyGame>? = withContext(Dispatchers.IO) {
        runCatching {
            // Which season is on, and the kinds of games SHL plays in it (the regular season, then
            // the playoffs), as shl.se itself starts its schedule page
            val filter = JSONObject(get("$BASE/season-series-game-types-filter") ?: return@runCatching null)
            val pick = filter.getJSONObject("defaultSsgtFilter")
            val season = pick.getString("season")
            val series = pick.getString("series")
            val first = pick.getString("gameType")
            val types = filter.optJSONArray("gameType")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("uuid")?.takeIf(String::isNotBlank) } }.orEmpty()
            val all = ArrayList<HockeyGame>()
            for (type in listOf(first) + types.filter { it != first }) {
                val json = get("$BASE/game-schedule?seasonUuid=$season&seriesUuid=$series&gameTypeUuid=$type&gamePlace=all&played=all") ?: continue
                val list = JSONObject(json).optJSONArray("gameInfo") ?: continue
                for (i in 0 until list.length()) {
                    val g = list.optJSONObject(i) ?: continue
                    val game = runCatching { gameFromShl(g) }.getOrNull() ?: continue
                    if (game.home.code.equals(team, true) || game.away.code.equals(team, true)) all += game
                }
                // The regular season is enough while it has games to come
                if (type == first && all.any { it.coming }) break
            }
            all.distinctBy { it.id }.sortedBy { it.start }
        }.onFailure { Log.w(TAG, "SHL schedule failed", it) }.getOrNull()
    }

    internal fun gameFromShl(g: JSONObject): HockeyGame {
        fun team(o: JSONObject): HockeyTeam {
            val names = o.optJSONObject("names")
            val code = o.optString("code").ifBlank { names?.optString("code").orEmpty() }
            return HockeyTeam(
                code = code,
                short = names?.optString("short")?.takeIf { it.isNotBlank() } ?: code,
                long = names?.optString("long")?.takeIf { it.isNotBlank() } ?: names?.optString("full")?.takeIf { it.isNotBlank() } ?: code,
                icon = o.optString("icon").takeIf { it.startsWith("http") }
            )
        }
        val home = g.getJSONObject("homeTeamInfo")
        val away = g.getJSONObject("awayTeamInfo")
        val raw = g.getString("rawStartDateTime")
        val start = java.time.Instant.parse(raw).toEpochMilli()
        val state = g.optString("state").ifBlank { "pre-game" }
        val scored = state != "pre-game"
        return HockeyGame(
            id = g.getString("uuid"),
            start = start,
            state = state,
            home = team(home),
            away = team(away),
            homeScore = if (scored && home.has("score") && !home.isNull("score")) home.optInt("score") else null,
            awayScore = if (scored && away.has("score") && !away.isNull("score")) away.optInt("score") else null,
            overtime = g.optBoolean("overtime"),
            shootout = g.optBoolean("shootout"),
            venue = g.optJSONObject("venueInfo")?.optString("name")?.takeIf { it.isNotBlank() },
            round = g.optInt("roundNumber", 0).takeIf { it > 0 }
        )
    }

    private fun gameJson(g: HockeyGame): JSONObject = JSONObject()
        .put("id", g.id).put("start", g.start).put("state", g.state)
        .put("home", teamJson(g.home)).put("away", teamJson(g.away))
        .put("hs", g.homeScore ?: JSONObject.NULL).put("as", g.awayScore ?: JSONObject.NULL)
        .put("ot", g.overtime).put("so", g.shootout)
        .put("venue", g.venue ?: "").put("round", g.round ?: 0)

    private fun teamJson(t: HockeyTeam) = JSONObject().put("c", t.code).put("s", t.short).put("l", t.long).put("i", t.icon ?: "")

    private fun gameFrom(o: JSONObject): HockeyGame {
        fun team(t: JSONObject) = HockeyTeam(t.getString("c"), t.getString("s"), t.getString("l"), t.optString("i").ifBlank { null })
        return HockeyGame(
            id = o.getString("id"),
            start = o.getLong("start"),
            state = o.getString("state"),
            home = team(o.getJSONObject("home")),
            away = team(o.getJSONObject("away")),
            homeScore = if (o.isNull("hs")) null else o.getInt("hs"),
            awayScore = if (o.isNull("as")) null else o.getInt("as"),
            overtime = o.optBoolean("ot"),
            shootout = o.optBoolean("so"),
            venue = o.optString("venue").ifBlank { null },
            round = o.optInt("round", 0).takeIf { it > 0 }
        )
    }

    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "NeonTokyo/1.0 (Android TV)")
        return try {
            if (connection.responseCode != 200) null else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
