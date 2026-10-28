package com.lumera.app.launcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
/** The sky outside, as the home screen shows it. */
enum class WeatherKind { Clear, Clouds, Fog, Drizzle, Rain, Snow, Storm }

/** The weather at [place] at [at]: what it is doing and how warm it is. */
data class Weather(val celsius: Int, val kind: WeatherKind, val day: Boolean, val place: String, val at: Long)

/** A place to show the weather for, as the weather service found it. */
data class WeatherPlace(val name: String, val region: String?, val latitude: Double, val longitude: Double)

/**
 * The weather outside for the home screen, from Open-Meteo (free, no account and no key). The
 * place is the one picked in Settings, or else the city of the TV's time zone. What was last
 * fetched is kept, so the home screen shows it at once and only asks again every half hour.
 */
internal object HomeWeather {
    private const val PREFS = "neon_home"
    private const val PLACE = "weather_place"
    private const val LAST = "weather_last"

    /** How fresh the weather has to be before it is fetched again. */
    const val FRESH_MS = 30 * 60_000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The place picked in Settings, or null to go by the TV's time zone. */
    fun place(context: Context): WeatherPlace? =
        prefs(context).getString(PLACE, null)?.let { runCatching { placeFrom(JSONObject(it)) }.getOrNull() }

    fun setPlace(context: Context, place: WeatherPlace?) {
        prefs(context).edit().apply {
            if (place == null) remove(PLACE) else putString(PLACE, placeJson(place).toString())
            // The weather kept is for the old place
            remove(LAST)
        }.apply()
    }

    /** The city of the TV's time zone, "Stockholm" for Europe/Stockholm. */
    fun zoneCity(zone: String = TimeZone.getDefault().id): String? =
        zone.substringAfterLast('/', "").replace('_', ' ').takeIf { it.isNotBlank() && zone.contains('/') }

    fun last(context: Context): Weather? =
        prefs(context).getString(LAST, null)?.let { text ->
            runCatching {
                val o = JSONObject(text)
                Weather(o.getInt("c"), WeatherKind.valueOf(o.getString("k")), o.getBoolean("d"), o.getString("p"), o.getLong("t"))
            }.getOrNull()
        }

    private fun keep(context: Context, weather: Weather) {
        val o = JSONObject()
            .put("c", weather.celsius)
            .put("k", weather.kind.name)
            .put("d", weather.day)
            .put("p", weather.place)
            .put("t", weather.at)
        prefs(context).edit().putString(LAST, o.toString()).apply()
    }

    /** Places called [name], best match first, in the TV's language where the service has it. */
    suspend fun find(name: String, count: Int = 5): List<WeatherPlace> = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode(name.trim(), "UTF-8")
        val language = Locale.getDefault().language.ifBlank { "en" }
        val json = get("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=$count&language=$language&format=json")
            ?: return@withContext emptyList()
        val results = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: return@withContext emptyList()
        (0 until results.length()).mapNotNull { i ->
            val r = results.optJSONObject(i) ?: return@mapNotNull null
            val region = listOfNotNull(r.optString("admin1").takeIf { it.isNotBlank() }, r.optString("country").takeIf { it.isNotBlank() })
                .distinct().joinToString(", ").ifBlank { null }
            WeatherPlace(r.optString("name"), region, r.optDouble("latitude"), r.optDouble("longitude"))
                .takeIf { it.name.isNotBlank() && !it.latitude.isNaN() && !it.longitude.isNaN() }
        }
    }

    /** The weather now: kept if fresh, else fetched. Null when there is no place or no network. */
    suspend fun now(context: Context): Weather? {
        val kept = last(context)
        if (kept != null && System.currentTimeMillis() - kept.at < FRESH_MS) return kept
        val place = place(context) ?: zoneCity()?.let { find(it, 1).firstOrNull() } ?: return kept
        val fetched = fetch(place) ?: return kept
        keep(context, fetched)
        return fetched
    }

    private suspend fun fetch(place: WeatherPlace): Weather? = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&timezone=auto",
            place.latitude, place.longitude
        )
        val json = get(url) ?: return@withContext null
        runCatching {
            val current = JSONObject(json).getJSONObject("current")
            Weather(
                celsius = current.getDouble("temperature_2m").roundToInt(),
                kind = kindOf(current.getInt("weather_code")),
                day = current.optInt("is_day", 1) == 1,
                place = place.name,
                at = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    /** What a WMO weather code, as Open-Meteo gives it, looks like out of the window. */
    fun kindOf(code: Int): WeatherKind = when (code) {
        0, 1 -> WeatherKind.Clear
        2, 3 -> WeatherKind.Clouds
        45, 48 -> WeatherKind.Fog
        in 51..57 -> WeatherKind.Drizzle
        in 61..67, in 80..82 -> WeatherKind.Rain
        in 71..77, 85, 86 -> WeatherKind.Snow
        in 95..99 -> WeatherKind.Storm
        else -> WeatherKind.Clouds
    }

    fun glyph(weather: Weather): Glyph = when (weather.kind) {
        WeatherKind.Clear -> if (weather.day) Glyph.Sun else Glyph.Moon
        WeatherKind.Clouds -> Glyph.Cloud
        WeatherKind.Fog -> Glyph.Fog
        WeatherKind.Drizzle, WeatherKind.Rain -> Glyph.Rain
        WeatherKind.Snow -> Glyph.Snow
        WeatherKind.Storm -> Glyph.Storm
    }

    private fun get(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        try {
            if (connection.responseCode != 200) null else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun placeJson(place: WeatherPlace) = JSONObject()
        .put("n", place.name)
        .put("r", place.region ?: "")
        .put("la", place.latitude)
        .put("lo", place.longitude)

    private fun placeFrom(o: JSONObject) =
        WeatherPlace(o.getString("n"), o.optString("r").ifBlank { null }, o.getDouble("la"), o.getDouble("lo"))
}
