package com.lumera.app.handoff

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * "Fortsätt på TV:n": how a phone (the web app, the iPhone app, the PC app) hands what it is
 * playing to a TV, through the profile's Stremio account. See docs/handoff.md.
 *
 * Two hidden items in the account's library carry it. Both are type "other", removed and temp,
 * with no progress, so Stremio's own apps never show them; the message itself is JSON in `name`.
 *  - [MAILBOX_ID]: the last thing sent to a TV. The TV that takes it writes its id back.
 *  - [PRESENCE_ID]: the TVs that are on, so the phone can name them and pick one.
 */
object HandoffProtocol {
    const val MAILBOX_ID = "neontokyo:handoff"
    const val PRESENCE_ID = "neontokyo:tvs"
    const val ANY_TV = "*"

    /** A message older than this is not acted on: the person has moved on. */
    const val MAX_AGE_MS = 5 * 60_000L

    /** A TV seen within this is listed as on. */
    const val PRESENCE_FRESH_MS = 10 * 60_000L

    data class Message(
        val nonce: String,
        val to: String,
        val from: String,
        val profile: String,
        val sentAt: Long,
        val id: String,
        val type: String,
        val video: String,
        val name: String,
        val title: String,
        val poster: String,
        val background: String,
        val logo: String,
        val positionMs: Long,
        val durationMs: Long,
        val takenBy: String?,
        val takenName: String?,
        val takenAt: Long
    ) {
        fun isFor(tvId: String) = to == ANY_TV || to == tvId
        fun isFresh(now: Long) = sentAt in (now - MAX_AGE_MS)..(now + 60_000L)
    }

    data class Tv(val id: String, val name: String, val seenAt: Long, val on: Boolean)

    private fun JsonObject.str(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.long(key: String): Long =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asDouble?.toLong() }.getOrNull() ?: 0L

    private fun parseObject(text: String?): JsonObject? =
        runCatching { JsonParser.parseString(text ?: return null).takeIf { it.isJsonObject }?.asJsonObject }.getOrNull()

    /** The message in a mailbox item's name, or null when it isn't one this version reads. */
    fun parseMessage(name: String?): Message? {
        val o = parseObject(name) ?: return null
        if (o.long("v") != 1L) return null
        val id = o.str("id").ifBlank { return null }
        val nonce = o.str("nonce").ifBlank { return null }
        val type = o.str("type").ifBlank { "movie" }
        return Message(
            nonce = nonce,
            to = o.str("to").ifBlank { ANY_TV },
            from = o.str("from"),
            profile = o.str("profile"),
            sentAt = o.long("sentAt"),
            id = id,
            type = type,
            video = o.str("video").ifBlank { id },
            name = o.str("name"),
            title = o.str("title"),
            poster = o.str("poster"),
            background = o.str("background"),
            logo = o.str("logo"),
            positionMs = o.long("positionMs").coerceAtLeast(0L),
            durationMs = o.long("durationMs").coerceAtLeast(0L),
            takenBy = o.str("takenBy").ifBlank { null },
            takenName = o.str("takenName").ifBlank { null },
            takenAt = o.long("takenAt")
        )
    }

    /** [name] with the message marked as taken by this TV, every other field kept as sent. */
    fun markTaken(name: String, tvId: String, tvName: String, now: Long): String? {
        val o = parseObject(name) ?: return null
        o.addProperty("takenBy", tvId)
        o.addProperty("takenName", tvName)
        o.addProperty("takenAt", now)
        return o.toString()
    }

    fun parsePresence(name: String?): List<Tv> {
        val tvs = parseObject(name)?.get("tvs")?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
        return tvs.entrySet().mapNotNull { (id, v) ->
            val o = v.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            Tv(id, o.str("name").ifBlank { "TV" }, o.long("seenAt"), o.get("on")?.let { runCatching { it.asBoolean }.getOrNull() } ?: false)
        }
    }

    /** The presence list with this TV in it, and TVs gone for a week left out. */
    fun withTv(name: String?, me: Tv, now: Long): String {
        val tvs = parsePresence(name).filter { it.id != me.id && now - it.seenAt < 7 * 24 * 3_600_000L } + me
        val map = JsonObject()
        tvs.forEach { tv ->
            map.add(tv.id, JsonObject().apply {
                addProperty("name", tv.name)
                addProperty("seenAt", tv.seenAt)
                addProperty("on", tv.on)
            })
        }
        return JsonObject().apply { addProperty("v", 1); add("tvs", map) }.toString()
    }

    /**
     * A hidden library item: valid for Stremio's own apps (they read every item in the library)
     * yet never shown in their Library or Continue watching.
     */
    fun hiddenItem(id: String, name: String, now: Long): JsonObject {
        val iso = isoTime(now)
        return JsonObject().apply {
            addProperty("_id", id)
            addProperty("name", name)
            addProperty("type", "other")
            addProperty("poster", "")
            addProperty("posterShape", "poster")
            addProperty("removed", true)
            addProperty("temp", true)
            addProperty("_ctime", iso)
            addProperty("_mtime", iso)
            add("state", JsonObject().apply {
                addProperty("lastWatched", iso)
                addProperty("timeWatched", 0)
                addProperty("timeOffset", 0)
                addProperty("overallTimeWatched", 0)
                addProperty("timesWatched", 0)
                addProperty("flaggedWatched", 0)
                addProperty("duration", 0)
                addProperty("video_id", id)
                addProperty("watched", "")
                addProperty("noNotif", false)
            })
            add("behaviorHints", JsonObject().apply {
                add("defaultVideoId", null)
                add("featuredVideoId", null)
                addProperty("hasScheduledVideos", false)
            })
        }
    }

    fun isoTime(ms: Long): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return f.format(java.util.Date(ms))
    }

    fun parseIso(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        return runCatching { java.time.Instant.parse(text).toEpochMilli() }.getOrNull() ?: 0L
    }

    /** The TV profile a message is for: the one with the same name, else the one watching now. */
    fun <P> pickProfile(profileName: String, profiles: List<P>, nameOf: (P) -> String, current: P?): P? {
        val wanted = profileName.trim()
        if (wanted.isNotEmpty()) {
            profiles.firstOrNull { nameOf(it).trim().equals(wanted, ignoreCase = true) }?.let { return it }
        }
        return current ?: profiles.firstOrNull()
    }
}
