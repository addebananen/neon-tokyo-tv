package com.lumera.app.livetv

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader

/**
 * The few calls of the Xtream Codes API ("player_api.php") that live TV needs. Answers are read
 * as a stream: a big provider's list of live streams runs to many megabytes.
 */
object XtreamApi {

    class Account(val ok: Boolean, val status: String, val formats: List<String>, val expires: Long?)

    fun apiUrl(source: IptvSource.Xtream, action: String? = null): String {
        val query = "username=${enc(source.username)}&password=${enc(source.password)}"
        return "${source.base}/player_api.php?$query" + (action?.let { "&action=$it" } ?: "")
    }

    fun guideUrl(source: IptvSource.Xtream) =
        "${source.base}/xmltv.php?username=${enc(source.username)}&password=${enc(source.password)}"

    /** Where a live stream plays from, in the format the account allows (TS when it can). */
    fun streamUrl(source: IptvSource.Xtream, streamId: String, format: String) =
        "${source.base}/live/${enc(source.username)}/${enc(source.password)}/$streamId.$format"

    fun pickFormat(account: Account): String =
        if (account.formats.isEmpty() || "ts" in account.formats) "ts" else if ("m3u8" in account.formats) "m3u8" else account.formats.first()

    /** Reads the answer to the bare API address: whether the login works, and what it may play. */
    fun readAccount(reader: Reader): Account {
        var ok = false
        var status = ""
        val formats = ArrayList<String>()
        var expires: Long? = null
        JsonReader(reader).use { json ->
            if (json.peek() != JsonToken.BEGIN_OBJECT) return Account(false, "", formats, null)
            json.beginObject()
            while (json.hasNext()) {
                if (json.nextName() != "user_info" || json.peek() != JsonToken.BEGIN_OBJECT) { json.skipValue(); continue }
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "auth" -> ok = scalar(json) == "1"
                        "status" -> status = scalar(json).orEmpty()
                        "exp_date" -> expires = scalar(json)?.toLongOrNull()?.times(1000)
                        "allowed_output_formats" -> if (json.peek() == JsonToken.BEGIN_ARRAY) {
                            json.beginArray()
                            while (json.hasNext()) scalar(json)?.let { formats += it.lowercase() }
                            json.endArray()
                        } else json.skipValue()
                        else -> json.skipValue()
                    }
                }
                json.endObject()
            }
            json.endObject()
        }
        return Account(ok && !status.equals("Disabled", true) && !status.equals("Banned", true), status, formats, expires)
    }

    /** Category id to its name, in the provider's order. */
    fun readCategories(reader: Reader): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        JsonReader(reader).use { json ->
            if (json.peek() != JsonToken.BEGIN_ARRAY) return out
            json.beginArray()
            while (json.hasNext()) {
                var id: String? = null
                var name: String? = null
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "category_id" -> id = scalar(json)
                        "category_name" -> name = scalar(json)
                        else -> json.skipValue()
                    }
                }
                json.endObject()
                if (id != null) out[id] = name?.trim().orEmpty().ifBlank { "Other" }
            }
            json.endArray()
        }
        return out
    }

    fun readStreams(
        reader: Reader,
        categories: Map<String, String>,
        onProgress: ((Int) -> Unit)? = null,
        url: (streamId: String) -> String
    ): List<IptvChannel> {
        val out = ArrayList<IptvChannel>()
        JsonReader(reader).use { json ->
            if (json.peek() != JsonToken.BEGIN_ARRAY) return out
            json.beginArray()
            while (json.hasNext()) {
                var id: String? = null
                var name = ""
                var logo = ""
                var epg = ""
                var category = ""
                var number: Int? = null
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "stream_id" -> id = scalar(json)
                        "name" -> name = scalar(json).orEmpty().trim()
                        "stream_icon" -> logo = scalar(json).orEmpty()
                        "epg_channel_id" -> epg = scalar(json).orEmpty()
                        "category_id" -> category = scalar(json).orEmpty()
                        "num" -> number = scalar(json)?.toIntOrNull()
                        else -> json.skipValue()
                    }
                }
                json.endObject()
                val streamId = id ?: continue
                out += IptvChannel(
                    id = "x$streamId",
                    name = name.ifBlank { "Channel $streamId" },
                    group = categories[category] ?: "Other",
                    logo = logo,
                    url = url(streamId),
                    epgId = epg,
                    number = number ?: (out.size + 1)
                )
                if (onProgress != null && out.size % 2000 == 0) onProgress(out.size)
            }
            json.endArray()
        }
        return out
    }

    /** A value as text, whether the server sent it as a string, a number, a boolean or null. */
    private fun scalar(json: JsonReader): String? = when (json.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> json.nextString()
        JsonToken.BOOLEAN -> if (json.nextBoolean()) "1" else "0"
        JsonToken.NULL -> { json.nextNull(); null }
        else -> { json.skipValue(); null }
    }

    private fun enc(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
}
