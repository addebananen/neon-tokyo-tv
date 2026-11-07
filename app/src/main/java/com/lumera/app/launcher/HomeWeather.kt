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

/** One hour ahead: how warm, what the sky does, and how likely rain or snow is (0-100). */
data class WeatherHour(val at: Long, val celsius: Int, val kind: WeatherKind, val day: Boolean, val wet: Int)

/** One day ahead: the warmest and coldest it gets and what it mostly does. */
data class WeatherDay(val at: Long, val high: Int, val low: Int, val kind: WeatherKind, val wet: Int)

/** The weather now and what comes: the next hours and the week, for the Today card and its panel. */
data class Forecast(
    val now: Weather,
    val feels: Int,
    /** Wind in metres a second, as Swedes say it. */
    val wind: Int,
    val hours: List<WeatherHour>,
    val days: List<WeatherDay>,
    val sunrise: Long?,
    val sunset: Long?
) {
    /** Today's high and low, or null before the days have come. */
    val today: WeatherDay? get() = days.firstOrNull()
}

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
    private const val FORECAST = "weather_forecast"

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
            remove(FORECAST)
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
    suspend fun now(context: Context): Weather? = forecast(context)?.now ?: last(context)

    /** The forecast kept from the last fetch, at once and without the network, or null. */
    fun keptForecast(context: Context): Forecast? =
        prefs(context).getString(FORECAST, null)?.let { text ->
            runCatching {
                val o = JSONObject(text)
                parse(o.getString("json"), o.getString("p"), o.getLong("t"))
            }.getOrNull()
        }

    /** The forecast: kept if fresh, else fetched. Null when there is no place or no network. */
    suspend fun forecast(context: Context): Forecast? {
        val kept = keptForecast(context)
        if (kept != null && System.currentTimeMillis() - kept.now.at < FRESH_MS) return kept
        val place = place(context) ?: zoneCity()?.let { find(it, 1).firstOrNull() } ?: return kept
        val fetched = fetch(context, place) ?: return kept
        keep(context, fetched.now)
        return fetched
    }

    private suspend fun fetch(context: Context, place: WeatherPlace): Forecast? = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,is_day,precipitation_probability" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
                "&forecast_days=7&forecast_hours=26&wind_speed_unit=ms&timezone=auto",
            place.latitude, place.longitude
        )
        val json = get(url) ?: return@withContext null
        val at = System.currentTimeMillis()
        val forecast = runCatching { parse(json, place.name, at) }.getOrNull() ?: return@withContext null
        prefs(context).edit().putString(FORECAST, JSONObject().put("json", json).put("p", place.name).put("t", at).toString()).apply()
        forecast
    }

    /** Open-Meteo's answer read into a [Forecast]; its times are the place's own clock, so the offset is taken off. */
    internal fun parse(json: String, place: String, at: Long): Forecast {
        val root = JSONObject(json)
        val offset = root.optLong("utc_offset_seconds", 0L) * 1000L
        fun time(text: String): Long {
            // "2026-11-07T14:00" or "2026-11-07", in the place's own time
            val date = text.substringBefore('T').split('-').map { it.toInt() }
            val clock = text.substringAfter('T', "00:00").split(':').map { it.toInt() }
            val days = java.time.LocalDate.of(date[0], date[1], date[2]).toEpochDay()
            return days * 86_400_000L + clock[0] * 3_600_000L + clock.getOrElse(1) { 0 } * 60_000L - offset
        }
        val current = root.getJSONObject("current")
        val now = Weather(
            celsius = current.getDouble("temperature_2m").roundToInt(),
            kind = kindOf(current.getInt("weather_code")),
            day = current.optInt("is_day", 1) == 1,
            place = place,
            at = at
        )
        val hours = root.optJSONObject("hourly")?.let { h ->
            val times = h.getJSONArray("time")
            val temps = h.getJSONArray("temperature_2m")
            val codes = h.getJSONArray("weather_code")
            val days = h.optJSONArray("is_day")
            val wet = h.optJSONArray("precipitation_probability")
            (0 until times.length()).mapNotNull { i ->
                if (temps.isNull(i) || codes.isNull(i)) return@mapNotNull null
                WeatherHour(
                    at = time(times.getString(i)),
                    celsius = temps.getDouble(i).roundToInt(),
                    kind = kindOf(codes.getInt(i)),
                    day = days?.optInt(i, 1) != 0,
                    wet = wet?.optInt(i, 0) ?: 0
                )
            }
        }.orEmpty()
        val daily = root.optJSONObject("daily")
        val days = daily?.let { d ->
            val times = d.getJSONArray("time")
            val highs = d.getJSONArray("temperature_2m_max")
            val lows = d.getJSONArray("temperature_2m_min")
            val codes = d.getJSONArray("weather_code")
            val wet = d.optJSONArray("precipitation_probability_max")
            (0 until times.length()).mapNotNull { i ->
                if (highs.isNull(i) || lows.isNull(i) || codes.isNull(i)) return@mapNotNull null
                WeatherDay(time(times.getString(i)), highs.getDouble(i).roundToInt(), lows.getDouble(i).roundToInt(), kindOf(codes.getInt(i)), wet?.optInt(i, 0) ?: 0)
            }
        }.orEmpty()
        fun first(key: String): Long? = daily?.optJSONArray(key)?.optString(0)?.takeIf { it.contains('T') }?.let { runCatching { time(it) }.getOrNull() }
        return Forecast(
            now = now,
            feels = current.optDouble("apparent_temperature", current.getDouble("temperature_2m")).roundToInt(),
            wind = current.optDouble("wind_speed_10m", 0.0).roundToInt(),
            hours = hours,
            days = days,
            sunrise = first("sunrise"),
            sunset = first("sunset")
        )
    }

    /** What the sky does, in a word or two. */
    fun words(kind: WeatherKind, day: Boolean = true): String = when (kind) {
        WeatherKind.Clear -> if (day) "Sunny" else "Clear"
        WeatherKind.Clouds -> "Cloudy"
        WeatherKind.Fog -> "Fog"
        WeatherKind.Drizzle -> "Drizzle"
        WeatherKind.Rain -> "Rain"
        WeatherKind.Snow -> "Snow"
        WeatherKind.Storm -> "Thunder"
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

    fun glyph(weather: Weather): Glyph = glyph(weather.kind, weather.day)

    fun glyph(kind: WeatherKind, day: Boolean): Glyph = when (kind) {
        WeatherKind.Clear -> if (day) Glyph.Sun else Glyph.Moon
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
