package com.lumera.app.livetv

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader
import java.text.Normalizer

/** A film or a series in the service's own library ("get_vod_streams", "get_series"). */
data class VodItem(val id: String, val name: String, val year: Int?, val tmdb: String, val ext: String)

/** One episode of a series in the service's library ("get_series_info"). */
data class VodEpisode(val id: String, val season: Int, val episode: Int, val title: String, val ext: String)

/**
 * Reads the films and series an Xtream service has, and finds the ones that are the title on a
 * details page: by TMDB id when both know it, otherwise by name and year, whatever the provider
 * puts around the name ("SE - Dune (2021) 4K", "EN | The Matrix [MULTI-SUB]").
 */
object VodCatalog {

    fun readMovies(reader: Reader): List<VodItem> = readList(reader, "stream_id")

    fun readSeries(reader: Reader): List<VodItem> = readList(reader, "series_id")

    private fun readList(reader: Reader, idField: String): List<VodItem> {
        val out = ArrayList<VodItem>()
        JsonReader(reader).use { json ->
            if (json.peek() != JsonToken.BEGIN_ARRAY) return out
            json.beginArray()
            while (json.hasNext()) {
                if (json.peek() != JsonToken.BEGIN_OBJECT) { json.skipValue(); continue }
                var id: String? = null
                var name = ""
                var tmdb = ""
                var ext = ""
                var year: Int? = null
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        idField -> id = scalar(json)
                        "name" -> name = scalar(json).orEmpty().trim()
                        "tmdb", "tmdb_id" -> scalar(json)?.trim()?.takeIf { it.isNotEmpty() && it != "0" }?.let { tmdb = it }
                        "container_extension" -> ext = scalar(json).orEmpty().trim()
                        "year", "releaseDate", "release_date", "releasedate" ->
                            scalar(json)?.let { yearIn(it) }?.let { if (year == null) year = it }
                        else -> json.skipValue()
                    }
                }
                json.endObject()
                out += VodItem(id ?: continue, name, year ?: nameYear(name), tmdb, ext)
            }
            json.endArray()
        }
        return out
    }

    /** The episodes of one series. Panels send them as {"1": [..], "2": [..]} or as a list of lists. */
    fun readEpisodes(reader: Reader): List<VodEpisode> {
        val out = ArrayList<VodEpisode>()
        JsonReader(reader).use { json ->
            if (json.peek() != JsonToken.BEGIN_OBJECT) return out
            json.beginObject()
            while (json.hasNext()) {
                if (json.nextName() != "episodes") { json.skipValue(); continue }
                when (json.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        json.beginObject()
                        while (json.hasNext()) {
                            val season = json.nextName().toIntOrNull() ?: 0
                            readSeason(json, season, out)
                        }
                        json.endObject()
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        json.beginArray()
                        while (json.hasNext()) readSeason(json, 0, out)
                        json.endArray()
                    }
                    else -> json.skipValue()
                }
            }
            json.endObject()
        }
        return out
    }

    private fun readSeason(json: JsonReader, season: Int, out: MutableList<VodEpisode>) {
        if (json.peek() != JsonToken.BEGIN_ARRAY) { json.skipValue(); return }
        json.beginArray()
        while (json.hasNext()) {
            if (json.peek() != JsonToken.BEGIN_OBJECT) { json.skipValue(); continue }
            var id: String? = null
            var number: Int? = null
            var inSeason: Int? = null
            var title = ""
            var ext = ""
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "id" -> id = scalar(json)
                    "episode_num" -> number = scalar(json)?.toIntOrNull()
                    "season" -> inSeason = scalar(json)?.toIntOrNull()
                    "title" -> title = scalar(json).orEmpty().trim()
                    "container_extension" -> ext = scalar(json).orEmpty().trim()
                    else -> json.skipValue()
                }
            }
            json.endObject()
            out += VodEpisode(id ?: continue, inSeason ?: season, number ?: continue, title, ext)
        }
        json.endArray()
    }

    // ---- Finding a title ----

    /** The library sorted by name and by TMDB id, built once per list. */
    class Index(items: List<VodItem>) {
        internal val byKey = HashMap<String, MutableList<Pair<VodItem, Int?>>>()
        internal val byTmdb = HashMap<String, MutableList<VodItem>>()

        init {
            for (item in items) {
                val key = key(item.name)
                if (key.isNotEmpty()) byKey.getOrPut(key) { ArrayList(1) } += item to item.year
                // "Dune 2021" without brackets: also kept as "dune" from 2021
                trailingYear.find(key)?.let { m ->
                    val short = key.substring(0, m.range.first).trim()
                    if (short.isNotEmpty()) byKey.getOrPut(short) { ArrayList(1) } += item to (item.year ?: m.groupValues[1].toInt())
                }
                if (item.tmdb.isNotEmpty()) byTmdb.getOrPut(item.tmdb) { ArrayList(1) } += item
            }
        }

        val size get() = byKey.size

        /** The entries that are this title: the same TMDB id, or the same name from about the same year. */
        fun find(title: String, year: Int?, tmdb: String?): List<VodItem> {
            val out = LinkedHashSet<VodItem>()
            if (!tmdb.isNullOrEmpty()) byTmdb[tmdb]?.let { out += it }
            val key = key(title)
            if (key.isNotEmpty()) {
                byKey[key]?.forEach { (item, itemYear) ->
                    val sameTime = year == null || itemYear == null || kotlin.math.abs(year - itemYear) <= 1
                    // An entry with another TMDB id is another title that shares the name
                    val otherTitle = !tmdb.isNullOrEmpty() && item.tmdb.isNotEmpty() && item.tmdb != tmdb
                    if (sameTime && !otherTitle) out += item
                }
            }
            return out.toList()
        }
    }

    /** The picture size a provider puts in the name, as the sources panel spells it. */
    fun quality(name: String): String? {
        val n = name.lowercase()
        return when {
            Regex("""(?<![a-z0-9])(4k|uhd|2160p?)(?![a-z0-9])""").containsMatchIn(n) -> "4K"
            Regex("""(?<![a-z0-9])(fhd|1080p?)(?![a-z0-9])""").containsMatchIn(n) -> "1080p"
            Regex("""(?<![a-z0-9])(hd|720p?)(?![a-z0-9])""").containsMatchIn(n) -> "720p"
            else -> null
        }
    }

    /** The first year in "2021", "2021-10-22" or "2008–2013". */
    fun yearIn(text: String): Int? = Regex("""(?<!\d)(19\d{2}|20\d{2})(?!\d)""").find(text)?.value?.toInt()

    /** A year the provider wrote in brackets after the name: "Dune (2021)", "Dune [2021]". */
    private fun nameYear(name: String): Int? =
        Regex("""[(\[]\s*(19\d{2}|20\d{2})\s*[)\]]""").findAll(name).lastOrNull()?.groupValues?.get(1)?.toInt()

    private val marks = Regex("""\p{M}""")
    private val leadingTag = Regex("""^\s*(\[[^\]]{1,16}]|\([^)]{1,16}\)|[A-Z0-9]{2,6}\s*[:\-–]\s+|[^|]{1,16}\|)\s*""")
    private val brackets = Regex("""[(\[][^)\]]*[)\]]""")
    private val noise = Regex(
        """(?<![a-z0-9])(4k|uhd|fhd|hd|sd|2160p|1080p|720p|480p|hevc|x265|x264|h264|h265|hdr|hdr10|dv|multi|multisub|subs?|dual|dubbed|vost|vostfr|remux|web-?dl|bluray|extended|imax)(?![a-z0-9])"""
    )
    private val trailingYear = Regex("""\s(19\d{2}|20\d{2})$""")

    /**
     * A name boiled down to what both sides agree on: no provider tags or bracketed extras, no
     * picture-size marks, no accents or punctuation, and no leading "the".
     */
    fun key(name: String): String {
        var s = name
        // Tags stack: "SE | 4K - Dune"
        repeat(3) { s = s.replace(leadingTag, "") }
        s = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFKD).replace(marks, "")
        s = s.replace(brackets, " ")
            .replace("&", " and ")
            .replace(noise, " ")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
            .removePrefix("the ")
        return s
    }

    private fun scalar(json: JsonReader): String? = when (json.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> json.nextString()
        JsonToken.BOOLEAN -> if (json.nextBoolean()) "1" else "0"
        JsonToken.NULL -> { json.nextNull(); null }
        else -> { json.skipValue(); null }
    }
}
