package com.lumera.app.launcher

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

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
    private const val MODE = "weather_mode"
    private const val PLACE = "weather_place"
    private const val LAST = "weather_last"

    /** How fresh the weather has to be before it is fetched again. */
    const val FRESH_MS = 30 * 60_000L

    /** Where the weather shows: by the clock and in the picture, by the clock only, or nowhere. */
    enum class Mode { On, ClockOnly, Off }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): Mode =
        Mode.values().getOrNull(prefs(context).getInt(MODE, 0)) ?: Mode.On

    fun setMode(context: Context, mode: Mode) {
        prefs(context).edit().putInt(MODE, mode.ordinal).apply()
    }

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

/**
 * The weather for the home screen while it is in front, asked for again every half hour. A
 * preview can force a [preview] kind, to see how rain or snow looks in a style.
 */
@Composable
internal fun rememberHomeWeather(context: Context, mode: HomeWeather.Mode, preview: WeatherKind?): Weather? {
    var weather by remember { mutableStateOf(HomeWeather.last(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, mode) {
        if (mode == HomeWeather.Mode.Off) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                HomeWeather.now(context)?.let { weather = it }
                delay(HomeWeather.FRESH_MS)
            }
        }
    }
    if (mode == HomeWeather.Mode.Off) return null
    if (preview != null) {
        return (weather ?: Weather(4, preview, false, HomeWeather.zoneCity() ?: "Tokyo", 0L)).copy(kind = preview)
    }
    return weather
}

/** The weather under the clock's date: its mark, how warm it is and where. */
@Composable
internal fun WeatherLine(weather: Weather, modifier: Modifier = Modifier) {
    val look = LocalHomeLook.current
    val color = if (look.glowingText) look.dim else look.text.copy(alpha = 0.82f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        NeonGlyph(HomeWeather.glyph(weather), look.accent, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = "${weather.celsius}°  ${weather.place.uppercase(Locale.getDefault())}",
            maxLines = 1,
            style = TextStyle(
                fontFamily = look.font,
                fontWeight = look.weight,
                fontStyle = look.fontStyle,
                fontSize = 13.sp,
                letterSpacing = 0.6.sp,
                color = color,
                shadow = look.shade?.let { Shadow(it, blurRadius = 10f) }
            )
        )
    }
}

/**
 * The weather outside, in the picture: rain slanting down, drizzle, snow drifting, fog lying low,
 * and in a storm lightning now and then. Drawn from the time alone on a layer of its own, on the
 * slow beat, like [StyleAmbience]. Clear skies and clouds leave the picture as it is.
 */
@Composable
internal fun WeatherAmbience(kind: WeatherKind, modifier: Modifier = Modifier) {
    if (kind == WeatherKind.Clear || kind == WeatherKind.Clouds) return
    val time = rememberWeatherTime()
    Spacer(
        modifier
            .fillMaxSize()
            .graphicsLayer()
            .drawBehind {
                val t = time.value
                when (kind) {
                    WeatherKind.Drizzle -> rain(t, drops = 46, speed = 0.75f, length = 0.6f, strength = 0.65f)
                    WeatherKind.Rain -> rain(t, drops = 96, speed = 1.15f, length = 1f, strength = 1f)
                    WeatherKind.Storm -> {
                        rain(t, drops = 130, speed = 1.45f, length = 1.2f, strength = 1.15f)
                        lightning(t)
                    }
                    WeatherKind.Snow -> snow(t)
                    WeatherKind.Fog -> fog(t)
                    else -> Unit
                }
            }
    )
}

/** Seconds the home screen has been in front, ticking on the [awaitBeat]. */
@Composable
private fun rememberWeatherTime(): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameNanos { it }
            while (true) {
                val now = awaitBeat()
                seconds.floatValue += ((now - last) / 1_000_000_000f).coerceAtMost(0.25f)
                last = now
            }
        }
    }
    return seconds
}

private fun hash(i: Int, k: Int): Float {
    val v = sin(i * 12.9898f + k * 78.233f) * 43758.547f
    return v - floor(v)
}

private fun fract(x: Float) = x - floor(x)

/** Rain on the wind, nearer drops longer, brighter and faster, a few catching the city's neon. */
private fun DrawScope.rain(t: Float, drops: Int, speed: Float, length: Float, strength: Float) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()
    val slant = 0.2f
    for (i in 0 until drops) {
        val near = hash(i, 1)
        val fall = speed * (0.75f + 0.6f * near)
        val p = fract(hash(i, 2) + t * fall)
        val len = (10f + 22f * near) * length * unit
        // Start far enough left that the slant brings drops in over the left edge too
        val x = hash(i, 3) * (w + h * slant) - h * slant * 0.5f
        val y = p * (h + len * 2) - len
        val tint = when (i % 9) {
            0 -> Color(0xFFFF7AD9)
            4 -> Color(0xFF7AE8FF)
            else -> Color(0xFFDCE8FF)
        }
        drawLine(
            tint,
            Offset(x + y * slant, y),
            Offset(x + (y + len) * slant, y + len),
            strokeWidth = unit * (0.7f + 0.8f * near),
            cap = StrokeCap.Round,
            alpha = ((0.08f + 0.2f * near) * strength).coerceAtMost(0.4f)
        )
    }
}

/** Now and then far-off lightning: two quick flickers that light the sky, then dark. */
private fun DrawScope.lightning(t: Float) {
    // One strike in each stretch of 12 s, at a steady random time within it
    val stretch = 12f
    val n = floor(t / stretch).toInt()
    val at = n * stretch + 1f + hash(n, 20) * (stretch - 3f)
    val since = t - at
    if (since < 0f || since > 0.6f) return
    val flash = when {
        since < 0.07f -> 1f
        since < 0.16f -> 0.25f
        since < 0.24f -> 0.8f
        else -> (1f - (since - 0.24f) / 0.36f).coerceIn(0f, 1f) * 0.45f
    }
    drawRect(
        Brush.verticalGradient(0f to Color(0xFFDCE6FF), 0.65f to Color(0xFFDCE6FF).copy(alpha = 0.25f), 1f to Color.Transparent),
        alpha = 0.16f * flash
    )
}

/** Snow drifting down and swaying, nearer flakes bigger and quicker. */
private fun DrawScope.snow(t: Float) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()
    for (i in 0 until 84) {
        val near = hash(i, 1)
        val p = fract(hash(i, 2) + t * (0.035f + 0.06f * near))
        val sway = sin(t * (0.5f + 0.6f * hash(i, 4)) + hash(i, 5) * 6.28f) * (10f + 18f * near) * unit
        val x = hash(i, 3) * w + sway
        val r = (1.1f + 2.4f * near) * unit
        val y = p * (h + r * 4) - r * 2
        drawCircle(Color.White, radius = r * 2.2f, center = Offset(x, y), alpha = 0.06f + 0.06f * near)
        drawCircle(Color(0xFFF4F8FF), radius = r, center = Offset(x, y), alpha = 0.45f + 0.4f * near)
    }
}

/** Fog lying low over the ground, a few soft banks drifting slowly past. */
private fun DrawScope.fog(t: Float) {
    val w = size.width
    val h = size.height
    val mist = Color(0xFFCAD6EA)
    drawRect(
        Brush.verticalGradient(0.35f to Color.Transparent, 1f to mist.copy(alpha = 0.22f), startY = h * 0.35f, endY = h)
    )
    for (i in 0 until 3) {
        val y = h * (0.58f + 0.12f * i)
        val drift = fract(hash(i, 1) + t * (0.006f + 0.004f * i))
        val cx = (drift * 1.6f - 0.3f) * w
        val rx = w * (0.45f + 0.15f * hash(i, 2))
        // A circle of mist squashed flat into a bank, so it fades out on every side
        scale(1f, 0.28f, pivot = Offset(cx, y)) {
            drawCircle(
                Brush.radialGradient(0f to mist.copy(alpha = 0.16f), 1f to Color.Transparent, center = Offset(cx, y), radius = rx),
                radius = rx,
                center = Offset(cx, y)
            )
        }
    }
}
