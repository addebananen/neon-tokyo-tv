package com.lumera.app.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.lumera.app.MainActivity
import com.lumera.app.livetv.LiveTv
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** The everyday cards a profile can have in the Today row on Neon Home. */
enum class TodayCard(val title: String) {
    Weather("Weather"),
    Hockey("Hockey"),
    Music("Now playing"),
    Lights("Lights")
}

/**
 * Which Today cards each home profile shows. All of them are on until a profile turns one off:
 * a card with nothing to say (no lights at home, say) keeps out of the row by itself.
 */
object HomeToday {
    private const val PREFS = "neon_home_today"
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun cards(context: Context, profileId: String): Set<TodayCard> = runCatching {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(profileId, null) ?: return TodayCard.entries.toSet()
        val off = JSONArray(text).let { a -> (0 until a.length()).mapNotNull { i -> runCatching { TodayCard.valueOf(a.getString(i)) }.getOrNull() } }
        TodayCard.entries.toSet() - off.toSet()
    }.getOrDefault(TodayCard.entries.toSet())

    fun set(context: Context, profileId: String, cards: Set<TodayCard>) {
        val off = TodayCard.entries.filter { it !in cards }.map { it.name }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(profileId, JSONArray(off).toString()).apply()
        _version.update { it + 1 }
    }

    fun changed() = _version.update { it + 1 }

    /** Made-up weather, game, song and lights, for the emulator test and its screenshots. */
    var demo by androidx.compose.runtime.mutableStateOf(false)
}

/** What the demo row shows: the same every run, so the test can look for it. */
private object TodayDemo {
    private const val H = 3_600_000L
    fun forecast(now: Long): Forecast {
        val hour = now - now % H
        val kinds = listOf(WeatherKind.Snow, WeatherKind.Snow, WeatherKind.Clouds, WeatherKind.Clouds, WeatherKind.Clear, WeatherKind.Clear)
        return Forecast(
            now = Weather(-3, WeatherKind.Snow, true, "Luleå", now),
            feels = -8, wind = 4,
            hours = (1..14).map { i -> WeatherHour(hour + i * H, -3 - i / 3, kinds[i % kinds.size], i < 3, if (i < 3) 70 else 10) },
            days = (0..6).map { d -> WeatherDay(now + d * 24 * H, -1 - d % 3, -7 - d % 4, kinds[(d + 1) % kinds.size], if (d % 3 == 0) 60 else 10) },
            sunrise = hour + 2 * H, sunset = hour + 5 * H
        )
    }
    private val lhf = HockeyTeam("LHF", "Luleå", "Luleå Hockey", "https://sportality.cdn.s8y.se/team-logos/lhf1_lhf.svg")
    private val fbk = HockeyTeam("FBK", "Färjestad", "Färjestad BK", null)
    private val fhc = HockeyTeam("FHC", "Frölunda", "Frölunda HC", null)
    private val saik = HockeyTeam("SAIK", "Skellefteå", "Skellefteå AIK", null)
    fun games(now: Long): List<HockeyGame> = listOf(
        HockeyGame("d1", now - 72 * H, "post-game", saik, lhf, 2, 3, true, false, "Skellefteå Kraft Arena", 14),
        HockeyGame("d2", now - 40 * 60_000L, "live", lhf, fbk, 2, 1, false, false, "Coop Norrbotten Arena", 15),
        HockeyGame("d3", now + 48 * H, "pre-game", fhc, lhf, null, null, false, false, "Scandinavium", 16),
        HockeyGame("d4", now + 96 * H, "pre-game", lhf, saik, null, null, false, false, "Coop Norrbotten Arena", 17)
    )
    fun track(): Track = Track(
        "Night Drive", "Neon Tokyo", HomeCatalog.Spotify.packages.first(), true,
        album = "City Lights", duration = 240_000L, position = 80_000L, positionAt = SystemClock.elapsedRealtime()
    )
    val lights = listOf(
        HomeLight(LightHub.Hue, "1", "Living room", true, 0.8f, 0xFFFFB46B.toInt(), colorful = true, room = true),
        HomeLight(LightHub.Hue, "2", "Kitchen", false, 1f, null, room = true),
        HomeLight(LightHub.Hue, "3", "Bedroom", true, 0.4f, 0xFFFF2E9A.toInt(), colorful = true, room = true)
    )
}

private val TodayHeight = 92.dp
private val LiveRed = Color(0xFFFF3B4E)
private val LuleaRed = Color(0xFFE5243B)

private fun weatherColor(kind: WeatherKind, day: Boolean): Color = when (kind) {
    WeatherKind.Clear -> if (day) Color(0xFFFFC857) else Color(0xFF9AA8FF)
    WeatherKind.Clouds -> Color(0xFFB8C6E6)
    WeatherKind.Fog -> Color(0xFFC9CED6)
    WeatherKind.Drizzle, WeatherKind.Rain -> Color(0xFF5EC8FF)
    WeatherKind.Snow -> Color(0xFFDDF1FF)
    WeatherKind.Storm -> Color(0xFFB48CFF)
}

private fun appColor(pkg: String?, fallback: Color): Color = when (pkg) {
    in HomeCatalog.Spotify.packages -> HomeCatalog.Spotify.color
    in HomeCatalog.YtMusic.packages -> HomeCatalog.YtMusic.color
    in HomeCatalog.YouTube.packages -> HomeCatalog.YouTube.color
    else -> fallback
}

private fun appName(context: Context, pkg: String?): String? = when (pkg) {
    null -> null
    in HomeCatalog.Spotify.packages -> "Spotify"
    in HomeCatalog.YtMusic.packages -> "YT Music"
    in HomeCatalog.YouTube.packages -> "YouTube"
    else -> runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
}

private fun clock(context: Context, time: Long): String =
    java.text.SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(time)

// ---- What the cards read, kept fresh while Home shows ----

/** A clock that ticks each minute, for "Tonight 19:00" and the like to roll over. */
@Composable
private fun rememberMinute(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - now % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun rememberForecast(on: Boolean): Forecast? {
    val context = LocalContext.current
    var forecast by remember { mutableStateOf(HomeWeather.keptForecast(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val version by HomeToday.version.collectAsState()
    LaunchedEffect(on, lifecycle, version) {
        if (!on) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                HomeWeather.forecast(context)?.let { forecast = it }
                delay(HomeWeather.FRESH_MS)
            }
        }
    }
    return forecast
}

private class HockeyState(val games: List<HockeyGame>, val team: String)

@Composable
private fun rememberHockey(on: Boolean): HockeyState? {
    val context = LocalContext.current
    var state by remember { mutableStateOf(HomeHockey.kept(context)?.let { HockeyState(it.first, HomeHockey.team(context)) }) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val version by HomeToday.version.collectAsState()
    LaunchedEffect(on, lifecycle, version) {
        if (!on) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val team = HomeHockey.team(context)
                val games = HomeHockey.games(context)
                if (games != null) state = HockeyState(games, team)
                val now = System.currentTimeMillis()
                val hot = games?.any { it.live(now) || (it.coming && it.start - now in 0..15 * 60_000L) } == true
                // Each minute while a game is on for a fresh score, else now and then
                delay(if (hot) HomeHockey.LIVE_FRESH_MS else 15 * 60_000L)
            }
        }
    }
    return state
}

@Composable
private fun rememberMusic(on: Boolean): Pair<NowPlaying, Track?> {
    val context = LocalContext.current
    val now = remember { NowPlaying(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(on, lifecycle) {
        if (!on) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                // The switch can be flipped while Home is away; look again on each return
                while (true) {
                    now.start()
                    delay(30_000L)
                }
            } finally {
                now.stop()
            }
        }
    }
    val track by now.track.collectAsState()
    return now to track
}

private class LightsState(val lights: List<HomeLight>, val linked: Boolean, val bridge: String?)

@Composable
private fun rememberLights(on: Boolean, bump: Int): LightsState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(LightsState(emptyList(), HomeLights.linked(context), null)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val version by HomeLights.version.collectAsState()
    LaunchedEffect(on, lifecycle, version, bump) {
        if (!on) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!HomeLights.linked(context)) {
                // No hub yet: a Hue bridge on the network is offered once a session
                val found = TodayCache.bridge ?: HomeLights.findHueBridge(context)?.also { TodayCache.bridge = it }
                state = LightsState(emptyList(), false, found)
                return@repeatOnLifecycle
            }
            while (true) {
                val all = HomeLights.all(context)
                TodayCache.allLights = all
                state = LightsState(HomeLights.forRow(context, all), true, null)
                // Someone may switch a lamp by the wall: look again now and then
                delay(15_000L)
            }
        }
    }
    return state
}

/** What outlives one visit to Home: the bridge found on the network and the lights last read. */
internal object TodayCache {
    @Volatile var bridge: String? = null
    @Volatile var allLights: List<HomeLight> = emptyList()
}

private sealed class TodayPanel {
    data object Weather : TodayPanel()
    data object Hockey : TodayPanel()
    data object Music : TodayPanel()
    data object Listen : TodayPanel()
    data class Light(val key: String) : TodayPanel()
    data class HuePair(val ip: String) : TodayPanel()
}

// ---- The row ----

/**
 * Today: the weather, the followed team's next game, what plays, and the home's lights, as cards
 * in a row under the apps. Each card is quiet until it has something to say, and only the card
 * in focus moves.
 */
@Composable
internal fun TodayRow(
    profileId: String,
    shape: RoundedCornerShape,
    onFocusColor: (String?, String, Color) -> Unit,
    onSpot: (Spot) -> Unit
) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    val version by HomeToday.version.collectAsState()
    val cards = remember(version, profileId) { HomeToday.cards(context, profileId) }
    if (cards.isEmpty()) return
    var panel by remember { mutableStateOf<TodayPanel?>(null) }
    // A closed panel gives the focus back to the card that opened it, or it would land nowhere
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    var opener by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(panel) {
        if (panel != null) return@LaunchedEffect
        val back = opener ?: return@LaunchedEffect
        opener = null
        withFrameNanos { }
        runCatching { requesters[back]?.requestFocus() }
    }
    fun open(key: String, next: TodayPanel) {
        opener = key
        panel = next
    }
    var lightsBump by remember { mutableStateOf(0) }
    val minute = rememberMinute()
    val demo = HomeToday.demo
    val demoNow = remember(demo) { System.currentTimeMillis() }
    val forecast = if (demo) remember(demoNow) { TodayDemo.forecast(demoNow) } else rememberForecast(TodayCard.Weather in cards)
    val hockey = if (demo) remember(demoNow) { HockeyState(TodayDemo.games(demoNow), HomeHockey.DEFAULT_TEAM) } else rememberHockey(TodayCard.Hockey in cards)
    val (player, realTrack) = rememberMusic(TodayCard.Music in cards && !demo)
    val track = if (demo) remember(demoNow) { TodayDemo.track() } else realTrack
    val lights = if (demo) remember { LightsState(TodayDemo.lights, true, null) } else rememberLights(TodayCard.Lights in cards, lightsBump)
    // Lights changed here show at once, before the hub answers
    val switched = remember { mutableStateOf(mapOf<String, HomeLight>()) }
    LaunchedEffect(lights) { switched.value = emptyMap() }
    val scope = rememberCoroutineScope()
    val musicAllowed = demo || remember(track, minute) { NowPlaying.allowed(context) }
    val spotify = remember { HomeApps.isInstalled(context, HomeCatalog.Spotify) }

    val hockeyNow = hockey?.let { HomeHockey.pick(it.games, it.team, System.currentTimeMillis()) }
    val showWeather = TodayCard.Weather in cards && forecast != null
    val showHockey = TodayCard.Hockey in cards && hockeyNow != null
    val showMusic = TodayCard.Music in cards && (track != null || spotify || !musicAllowed)
    val showLights = TodayCard.Lights in cards && (lights.lights.isNotEmpty() || lights.bridge != null)
    if (!showWeather && !showHockey && !showMusic && !showLights) return

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        RowTitle("Today")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(end = Side),
            horizontalArrangement = Arrangement.spacedBy(Gap)
        ) {
            if (showWeather && forecast != null) key("weather") {
                val color = weatherColor(forecast.now.kind, forecast.now.day)
                NeonTile(
                    width = 236.dp, height = TodayHeight, shape = shape, color = color,
                    onClick = { open("weather", TodayPanel.Weather) },
                    focusRequester = requester("weather"),
                    onFocus = { onFocusColor(null, "Weather", color) },
                    onFocusRect = { onSpot(Spot(it, color)) }
                ) { focused -> WeatherCard(forecast, color, focused, minute) }
            }
            if (showHockey && hockeyNow != null) key("hockey") {
                val color = if (hockeyNow.team == HomeHockey.DEFAULT_TEAM) LuleaRed else look.accent
                NeonTile(
                    width = 252.dp, height = TodayHeight, shape = shape, color = color,
                    onClick = { open("hockey", TodayPanel.Hockey) },
                    focusRequester = requester("hockey"),
                    onFocus = { onFocusColor(null, hockeyNow.game.side(hockeyNow.team).long, color) },
                    onFocusRect = { onSpot(Spot(it, color)) }
                ) { focused -> HockeyCard(hockeyNow, color, focused, minute) }
            }
            if (showMusic) key("music") {
                val color = appColor(track?.packageName, look.accent)
                NeonTile(
                    width = 252.dp, height = TodayHeight, shape = shape, color = color,
                    onClick = {
                        when {
                            !musicAllowed -> open("music", TodayPanel.Listen)
                            track != null -> player.toggle()
                            else -> HomeApps.open(context, HomeCatalog.Spotify)
                        }
                    },
                    onLongClick = { if (track != null) open("music", TodayPanel.Music) else if (!musicAllowed) open("music", TodayPanel.Listen) },
                    focusRequester = requester("music"),
                    onFocus = { onFocusColor(track?.packageName, track?.title ?: "Now playing", color) },
                    onFocusRect = { onSpot(Spot(it, color)) }
                ) { focused -> MusicCard(context, track, musicAllowed, color, focused) }
            }
            if (showLights) {
                if (lights.lights.isEmpty() && lights.bridge != null) key("hue") {
                    val color = Color(0xFFFFC07A)
                    NeonTile(
                        width = 196.dp, height = TodayHeight, shape = shape, color = color,
                        onClick = { open("hue", TodayPanel.HuePair(lights.bridge)) },
                        focusRequester = requester("hue"),
                        onFocus = { onFocusColor(null, "Lights", color) },
                        onFocusRect = { onSpot(Spot(it, color)) }
                    ) { focused -> HueFoundCard(color, focused) }
                }
                lights.lights.forEach { base ->
                    key(base.key) {
                        val light = switched.value[base.key] ?: base
                        val color = lightColor(light)
                        NeonTile(
                            width = 140.dp, height = TodayHeight, shape = shape, color = color,
                            onClick = {
                                if (!light.reachable) return@NeonTile
                                val next = light.copy(on = !light.on)
                                switched.value = switched.value + (light.key to next)
                                if (!demo) scope.launch { HomeLights.set(context, light, on = next.on) }
                            },
                            onLongClick = { if (light.reachable) open(light.key, TodayPanel.Light(light.key)) },
                            focusRequester = requester(light.key),
                            onFocus = { onFocusColor(null, light.name, color) },
                            onFocusRect = { onSpot(Spot(it, color)) }
                        ) { focused -> LightCard(light, color, focused) }
                    }
                }
            }
        }
    }

    when (val open = panel) {
        TodayPanel.Weather -> forecast?.let { WeatherPanel(it, onDismiss = { panel = null }) }
        TodayPanel.Hockey -> hockey?.let { HockeyPanel(it.games, it.team, onDismiss = { panel = null }) }
        TodayPanel.Music -> MusicPanel(player, track, onDismiss = { panel = null })
        TodayPanel.Listen -> ListenPanel(onDismiss = { panel = null })
        is TodayPanel.Light -> {
            val light = switched.value[open.key] ?: TodayCache.allLights.firstOrNull { it.key == open.key } ?: lights.lights.firstOrNull { it.key == open.key }
            if (light != null) LightPanel(light, demo, onChange = { next -> switched.value = switched.value + (next.key to next) }, onDismiss = { panel = null })
        }
        is TodayPanel.HuePair -> HuePairPanel(open.ip, onDone = { panel = null; lightsBump++ })
        null -> Unit
    }
}

private fun lightColor(light: HomeLight): Color = when {
    !light.reachable || !light.on -> Color(0xFF8C8FA3)
    light.color != null -> Color(light.color)
    else -> Color(0xFFFFC07A)
}

// ---- Words ----

@Composable
private fun Words(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    color: Color? = null,
    bold: Boolean = false,
    glow: Color? = null
) {
    val look = LocalHomeLook.current
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = look.font,
            fontWeight = if (bold) FontWeight.SemiBold else look.weight,
            fontSize = size,
            color = (color ?: look.text).copy(alpha = alpha),
            shadow = when {
                glow != null && look.glowingText -> Shadow(glow.copy(alpha = 0.7f), blurRadius = 14f)
                look.shade != null -> Shadow(look.shade, blurRadius = 8f)
                else -> null
            }
        )
    )
}

// ---- Weather ----

@Composable
private fun BoxScope.WeatherCard(forecast: Forecast, color: Color, focused: Boolean, minute: Long) {
    val now = forecast.now
    if (focused) Sky(now.kind, now.day, color)
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        NeonGlyph(HomeWeather.glyph(now), color, Modifier.size(40.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Words("${now.celsius}°", 26.sp, glow = color)
                Spacer(Modifier.width(7.dp))
                Words(HomeWeather.words(now.kind, now.day), 12.sp, Modifier.padding(bottom = 4.dp), alpha = 0.85f)
            }
            val today = forecast.today
            Words(listOfNotNull(now.place, today?.let { "H ${it.high}°  L ${it.low}°" }).joinToString("  ·  "), 10.5.sp, alpha = 0.7f)
            // The next few hours, small, along the foot
            val coming = forecast.hours.filter { it.at > minute }.take(4)
            if (coming.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    coming.forEach { hour ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Words(java.text.SimpleDateFormat("HH", java.util.Locale.getDefault()).format(hour.at), 9.5.sp, alpha = 0.55f)
                            Spacer(Modifier.width(2.dp))
                            NeonGlyph(HomeWeather.glyph(hour.kind, hour.day), weatherColor(hour.kind, hour.day), Modifier.size(11.dp), plain = true)
                            Spacer(Modifier.width(2.dp))
                            Words("${hour.celsius}°", 9.5.sp, alpha = 0.8f)
                        }
                    }
                }
            }
        }
    }
}

/** The sky on the card in focus: the sun breathing, rain or snow falling, stars, or cloud drifting. */
@Composable
private fun BoxScope.Sky(kind: WeatherKind, day: Boolean, color: Color) {
    val time by rememberInfiniteTransition(label = "Sky").animateFloat(
        0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "SkyTime"
    )
    val come by animateFloatAsState(1f, tween(500), label = "SkyIn")
    Box(
        Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(LocalHomeLook.current.corner))
            .drawBehind {
                val t = time
                val w = size.width
                val h = size.height
                val a = come
                when (kind) {
                    WeatherKind.Clear -> if (day) {
                        val pulse = 0.75f + 0.25f * sin(t * 2 * PI.toFloat())
                        drawCircle(
                            Brush.radialGradient(listOf(color.copy(alpha = 0.32f * a * pulse), Color.Transparent), center = Offset(w * 0.12f, h * 0.2f), radius = h * 1.3f),
                            radius = h * 1.3f, center = Offset(w * 0.12f, h * 0.2f)
                        )
                    } else {
                        for (i in 0 until 14) {
                            val x = ((i * 73) % 100) / 100f * w
                            val y = ((i * 41) % 100) / 100f * h
                            val tw = 0.5f + 0.5f * sin((t * 2 + i * 0.37f) * 2 * PI.toFloat())
                            drawCircle(Color.White.copy(alpha = 0.55f * tw * a), radius = 1.1.dp.toPx(), center = Offset(x, y))
                        }
                    }
                    WeatherKind.Rain, WeatherKind.Drizzle, WeatherKind.Storm -> {
                        val drops = if (kind == WeatherKind.Drizzle) 12 else 22
                        for (i in 0 until drops) {
                            val x = ((i * 37) % 100) / 100f * w + 6.dp.toPx()
                            val y = (((i * 53) % 100) / 100f + t * 3f) % 1f * (h + 20f) - 10f
                            drawLine(color.copy(alpha = 0.35f * a), Offset(x, y), Offset(x - 3.dp.toPx(), y + 9.dp.toPx()), strokeWidth = 1.dp.toPx())
                        }
                        if (kind == WeatherKind.Storm) {
                            val flash = ((t * 6f) % 1f).let { if (it < 0.05f) 1f - it / 0.05f else 0f }
                            drawRect(Color.White.copy(alpha = 0.12f * flash * a))
                        }
                    }
                    WeatherKind.Snow -> for (i in 0 until 18) {
                        val drift = sin((t * 2 + i * 0.29f) * 2 * PI.toFloat()) * 5.dp.toPx()
                        val x = ((i * 37) % 100) / 100f * w + drift
                        val y = (((i * 53) % 100) / 100f + t) % 1f * (h + 10f) - 5f
                        drawCircle(Color.White.copy(alpha = 0.6f * a), radius = (1 + i % 3 * 0.6f).dp.toPx(), center = Offset(x, y))
                    }
                    WeatherKind.Clouds, WeatherKind.Fog -> for (i in 0 until 3) {
                        val x = ((t + i / 3f) % 1f) * (w + h * 2) - h
                        drawCircle(
                            Brush.radialGradient(listOf(color.copy(alpha = 0.16f * a), Color.Transparent), center = Offset(x, h * (0.3f + 0.25f * i)), radius = h * 0.9f),
                            radius = h * 0.9f, center = Offset(x, h * (0.3f + 0.25f * i))
                        )
                    }
                }
            }
    )
}

@Composable
private fun WeatherPanel(forecast: Forecast, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    val now = forecast.now
    val color = weatherColor(now.kind, now.day)
    val first = remember { FocusRequester() }
    TodayDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonGlyph(HomeWeather.glyph(now), color, Modifier.size(64.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Words("${now.celsius}°  ${HomeWeather.words(now.kind, now.day)}", 30.sp, glow = color)
                Words(
                    listOfNotNull(
                        now.place,
                        "Feels like ${forecast.feels}°",
                        "Wind ${forecast.wind} m/s",
                        forecast.sunset?.takeIf { it > System.currentTimeMillis() }?.let { "Sunset ${clock(context, it)}" }
                            ?: forecast.sunrise?.takeIf { it > System.currentTimeMillis() }?.let { "Sunrise ${clock(context, it)}" }
                    ).joinToString("  ·  "),
                    12.sp, alpha = 0.75f
                )
            }
        }
        // The next twelve hours
        val hours = forecast.hours.filter { it.at > System.currentTimeMillis() - 30 * 60_000L }.take(12)
        if (hours.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                hours.forEachIndexed { i, hour ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Words(if (i == 0) "Now" else java.text.SimpleDateFormat("HH", java.util.Locale.getDefault()).format(hour.at), 11.sp, alpha = 0.65f)
                        NeonGlyph(HomeWeather.glyph(hour.kind, hour.day), weatherColor(hour.kind, hour.day), Modifier.size(22.dp))
                        Words("${hour.celsius}°", 13.sp)
                        Words(if (hour.wet >= 20) "${hour.wet}%" else " ", 9.5.sp, color = Color(0xFF5EC8FF))
                    }
                }
            }
        }
        // The week: each day's low to high along the week's own span
        val days = forecast.days
        if (days.isNotEmpty()) {
            val low = days.minOf { it.low }
            val high = days.maxOf { it.high }.coerceAtLeast(low + 1)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                days.forEachIndexed { i, day ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Words(if (i == 0) "Today" else java.text.SimpleDateFormat("EEE", java.util.Locale.ENGLISH).format(day.at), 13.sp, Modifier.width(60.dp))
                        NeonGlyph(HomeWeather.glyph(day.kind, true), weatherColor(day.kind, true), Modifier.size(20.dp), plain = true)
                        Words(if (day.wet >= 20) "${day.wet}%" else "", 10.sp, Modifier.width(44.dp).padding(start = 8.dp), color = Color(0xFF5EC8FF))
                        Words("${day.low}°", 13.sp, Modifier.width(36.dp), alpha = 0.6f)
                        Box(Modifier.weight(1f).height(5.dp).background(look.text.copy(alpha = 0.1f), RoundedCornerShape(3.dp))) {
                            val from = (day.low - low).toFloat() / (high - low)
                            val to = (day.high - low).toFloat() / (high - low)
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(to.coerceIn(0.05f, 1f))
                                    .drawBehind {
                                        val start = size.width * (from / to.coerceAtLeast(0.05f))
                                        drawRoundRect(
                                            Brush.horizontalGradient(listOf(Color(0xFF5EC8FF), Color(0xFFFFC857)), startX = start, endX = size.width),
                                            topLeft = Offset(start, 0f), size = Size(size.width - start, size.height), cornerRadius = CornerRadius(size.height / 2)
                                        )
                                    }
                            )
                        }
                        Words("${day.high}°", 13.sp, Modifier.width(40.dp).padding(start = 10.dp))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelButton("Done", look, primary = true, modifier = Modifier.focusRequester(first), onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            PanelNote("Open-Meteo · the town is set in the profile menu, Today", look)
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
}

// ---- Hockey ----

@Composable
private fun BoxScope.HockeyCard(now: HockeyNow, color: Color, focused: Boolean, minute: Long) {
    val game = now.game
    val live = now.mode == HockeyNow.Mode.Live
    if (focused) Ice(color)
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonGlyph(Glyph.Stick, color, Modifier.size(13.dp), plain = true)
            Spacer(Modifier.width(5.dp))
            Words(listOfNotNull("SHL", game.round?.let { "Round $it" }).joinToString(" · "), 9.5.sp, Modifier.weight(1f), alpha = 0.6f)
            when (now.mode) {
                HockeyNow.Mode.Live -> LiveDot()
                HockeyNow.Mode.Result -> Words(
                    listOfNotNull("Final", "OT".takeIf { game.overtime && !game.shootout }, "SO".takeIf { game.shootout }).joinToString(" · ") +
                        "  " + HomeHockey.dayText(game.start, minute),
                    9.5.sp, alpha = 0.75f
                )
                HockeyNow.Mode.Next -> Words(HomeHockey.whenText(game.start, minute), 10.5.sp, color = color, bold = true)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamBadge(game.home, Modifier.size(28.dp))
            Spacer(Modifier.width(6.dp))
            Words(game.home.short, 13.sp, Modifier.weight(1f), bold = game.home.code == now.team)
            val score = if (game.homeScore != null && game.awayScore != null) "${game.homeScore} – ${game.awayScore}" else "vs"
            AnimatedContent(
                targetState = score,
                transitionSpec = { (slideInVertically { -it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut()) },
                label = "Score"
            ) { text ->
                Words(text, if (text == "vs") 12.sp else 20.sp, Modifier.padding(horizontal = 6.dp), alpha = if (text == "vs") 0.6f else 1f, glow = color, bold = text != "vs")
            }
            Words(game.away.short, 13.sp, Modifier.weight(1f).padding(start = 6.dp), bold = game.away.code == now.team)
            TeamBadge(game.away, Modifier.size(28.dp))
        }
        val foot = when (now.mode) {
            HockeyNow.Mode.Next -> game.venue ?: "SHL"
            HockeyNow.Mode.Live -> listOfNotNull("On now", game.venue).joinToString(" · ")
            HockeyNow.Mode.Result -> when (game.won(now.team)) {
                true -> "${game.side(now.team).short} won" + (game.venue?.let { " · $it" } ?: "")
                false -> "${game.other(now.team).short} won" + (game.venue?.let { " · $it" } ?: "")
                null -> game.venue ?: ""
            }
        }
        Words(foot, 10.sp, alpha = 0.6f)
    }
}

/** A cold light sweeping along the ice, on the card in focus. */
@Composable
private fun BoxScope.Ice(color: Color) {
    val sweep by rememberInfiniteTransition(label = "Ice").animateFloat(
        -0.3f, 1.3f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing)), label = "IceSweep"
    )
    Box(
        Modifier.matchParentSize().clip(RoundedCornerShape(LocalHomeLook.current.corner)).drawBehind {
            val x = sweep * size.width
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.14f), Color.Transparent), startX = x - size.width * 0.25f, endX = x + size.width * 0.25f))
        }
    )
}

@Composable
private fun LiveDot() {
    val pulse by rememberInfiniteTransition(label = "Live").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "LivePulse"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).graphicsLayer { alpha = pulse }.background(LiveRed, CircleShape))
        Spacer(Modifier.width(5.dp))
        Words("LIVE", 10.sp, color = LiveRed, bold = true)
    }
}

@Composable
private fun TeamBadge(team: HockeyTeam, modifier: Modifier) {
    val context = LocalContext.current
    var failed by remember(team.icon) { mutableStateOf(team.icon == null) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!failed) {
            val request = remember(team.icon) {
                ImageRequest.Builder(context).data(team.icon).decoderFactory(SvgDecoder.Factory()).crossfade(true).build()
            }
            AsyncImage(
                model = request,
                contentDescription = team.long,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true }
            )
        } else {
            Box(Modifier.fillMaxSize().border(1.dp, LocalHomeLook.current.text.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
                Words(team.code.take(3), 8.sp, alpha = 0.8f, bold = true)
            }
        }
    }
}

@Composable
private fun HockeyPanel(games: List<HockeyGame>, team: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    val now = System.currentTimeMillis()
    val first = remember { FocusRequester() }
    val mine = games.firstOrNull()?.side(team)
    val liveTv = remember { LiveTv.source(context) != null }
    val current = HomeHockey.pick(games, team, now)
    TodayDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mine != null) TeamBadge(mine, Modifier.size(54.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                PanelTitle(mine?.long ?: "Hockey", look)
                val played = games.filter { it.played }
                val wins = played.count { it.won(team) == true }
                PanelNote("SHL 2026/27  ·  ${played.size} played  ·  $wins won", look)
            }
        }
        val coming = games.filter { it.coming && it.start > now - HomeHockey.GAME_MS }.take(5)
        val played = games.filter { it.played }.takeLast(5).asReversed()
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelNote("COMING", look)
                if (coming.isEmpty()) Words("No games left this season", 12.sp, alpha = 0.6f)
                coming.forEach { game ->
                    val home = game.home.code == team
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Words(HomeHockey.whenText(game.start, now), 12.sp, Modifier.width(118.dp), color = if (game.live(now)) LiveRed else null, alpha = 0.85f)
                        TeamBadge(game.other(team), Modifier.size(20.dp))
                        Spacer(Modifier.width(7.dp))
                        Words((if (home) "vs " else "@ ") + game.other(team).short, 13.sp, Modifier.weight(1f))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelNote("PLAYED", look)
                played.forEach { game ->
                    val won = game.won(team)
                    val mineScore = if (game.home.code == team) game.homeScore else game.awayScore
                    val theirs = if (game.home.code == team) game.awayScore else game.homeScore
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(18.dp).background((if (won == true) Color(0xFF34D27B) else LiveRed).copy(alpha = 0.85f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Words(if (won == true) "W" else "L", 9.sp, color = Color.White, bold = true) }
                        Spacer(Modifier.width(8.dp))
                        Words("$mineScore – $theirs", 13.sp, Modifier.width(54.dp), bold = true)
                        Words(
                            (if (game.home.code == team) "vs " else "@ ") + game.other(team).short +
                                (if (game.shootout) "  SO" else if (game.overtime) "  OT" else ""),
                            12.sp, Modifier.weight(1f)
                        )
                        Words(HomeHockey.dayText(game.start, now), 10.5.sp, alpha = 0.55f)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (liveTv && current != null) {
                PanelButton(if (current.mode == HockeyNow.Mode.Live) "● Watch now" else "Find it on Live TV", look, primary = true, modifier = Modifier.focusRequester(first)) {
                    watchGame(context, current.game)
                    onDismiss()
                }
                PanelButton("Done", look, onClick = onDismiss)
            } else {
                PanelButton("Done", look, primary = true, modifier = Modifier.focusRequester(first), onClick = onDismiss)
            }
            Spacer(Modifier.weight(1f))
            PanelNote("From shl.se · the team is set in the profile menu, Today", look)
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
}

/** Opens Live TV on the channel whose guide has [game], or on the hockey channels when none says so. */
private fun watchGame(context: Context, game: HockeyGame) {
    val words = listOf(game.home.short, game.away.short, game.home.long, game.away.long).distinct()
    LiveTv.want(LiveTv.MATCH_PREFIX + words.joinToString("|"))
    HomeApps.start(context, Intent(context, MainActivity::class.java))
}

// ---- Music ----

@Composable
private fun BoxScope.MusicCard(context: Context, track: Track?, allowed: Boolean, color: Color, focused: Boolean) {
    Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                track?.art != null -> ArtImage(track.art, Modifier.fillMaxSize())
                track?.artUri != null -> AsyncImage(model = track.artUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                track == null && allowed -> NeonGlyph(Glyph.Spotify, color, Modifier.size(38.dp))
                else -> NeonGlyph(Glyph.Note, color, Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when {
                !allowed -> {
                    Words("Now playing", 13.sp, bold = true)
                    Words("Show Spotify on Home", 11.sp, alpha = 0.7f)
                    Words("OK to turn on", 10.sp, color = color, alpha = 0.9f)
                }
                track == null -> {
                    Words("Spotify", 13.sp, bold = true)
                    Words("Nothing playing", 11.sp, alpha = 0.7f)
                    Words("OK opens Spotify", 10.sp, color = color, alpha = 0.9f)
                }
                else -> {
                    Words(track.title, 13.sp, bold = true)
                    Words(track.artist ?: appName(context, track.packageName) ?: "", 11.sp, alpha = 0.72f)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Equalizer(track.playing && focused, track.playing, color, Modifier.size(width = 14.dp, height = 11.dp))
                        Spacer(Modifier.width(7.dp))
                        TrackLine(track, color, Modifier.weight(1f).height(3.dp))
                    }
                    Words(if (track.playing) "OK pauses · hold for more" else "OK plays · hold for more", 9.sp, Modifier.padding(top = 2.dp), alpha = if (focused) 0.6f else 0f)
                }
            }
        }
    }
}

@Composable
private fun ArtImage(art: Bitmap, modifier: Modifier) {
    val image = remember(art) { art.asImageBitmap() }
    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

/** Three bars dancing while it plays, standing still when paused or out of focus. */
@Composable
private fun Equalizer(moving: Boolean, playing: Boolean, color: Color, modifier: Modifier) {
    val time = if (moving) {
        rememberInfiniteTransition(label = "Eq").animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "EqTime")
    } else null
    Box(
        modifier.drawBehind {
            val t = time?.value ?: 0f
            val bar = size.width / 5f
            for (i in 0 until 3) {
                val level = when {
                    !playing -> 0.25f
                    time == null -> floatArrayOf(0.6f, 1f, 0.45f)[i]
                    else -> 0.3f + 0.7f * (0.5f + 0.5f * sin((t * 2 + i * 0.31f) * 2 * PI.toFloat() * (1 + i * 0.4f)))
                }
                val h = size.height * level
                drawRoundRect(color, topLeft = Offset(i * bar * 2, size.height - h), size = Size(bar, h), cornerRadius = CornerRadius(bar / 2))
            }
        }
    )
}

/** How far into the track, moved on in the draw alone so the card never recomposes for it. */
@Composable
private fun TrackLine(track: Track, color: Color, modifier: Modifier) {
    val look = LocalHomeLook.current
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(track.playing, track.position) {
        while (track.playing) {
            delay(1000)
            tick = SystemClock.elapsedRealtime()
        }
    }
    Box(
        modifier.drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(look.text.copy(alpha = 0.16f), cornerRadius = r)
            if (track.duration > 0 && track.position >= 0) {
                val at = track.positionNow(tick).toFloat() / track.duration
                drawRoundRect(color, size = Size(size.width * at.coerceIn(0f, 1f), size.height), cornerRadius = r)
            }
        }
    )
}

@Composable
private fun MusicPanel(player: NowPlaying, track: Track?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    val first = remember { FocusRequester() }
    val color = appColor(track?.packageName, look.accent)
    TodayDialog(onDismiss, width = 640.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(150.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                when {
                    track?.art != null -> ArtImage(track.art, Modifier.fillMaxSize())
                    track?.artUri != null -> AsyncImage(model = track.artUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else -> NeonGlyph(Glyph.Note, color, Modifier.size(60.dp))
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PanelNote((appName(context, track?.packageName) ?: "Now playing").uppercase(), look)
                Words(track?.title ?: "Nothing playing", 22.sp, glow = color)
                track?.artist?.let { Words(it, 14.sp, alpha = 0.8f) }
                track?.album?.let { Words(it, 12.sp, alpha = 0.55f) }
                if (track != null) {
                    Spacer(Modifier.height(8.dp))
                    TrackLine(track, color, Modifier.fillMaxWidth().height(4.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelButton("⏮  Back", look) { player.previous() }
            PanelButton(if (track?.playing == true) "⏸  Pause" else "▶  Play", look, primary = true, modifier = Modifier.focusRequester(first)) { player.toggle() }
            PanelButton("Next  ⏭", look) { player.next() }
            Spacer(Modifier.weight(1f))
            track?.packageName?.let { pkg -> PanelButton("Open ${appName(context, pkg) ?: "app"}", look) { onDismiss(); HomeApps.open(context, pkg) } }
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
}

/** Turning on what plays: a press when the TV lets Neon Tokyo, else the TV's page, else the PC line. */
@Composable
private fun ListenPanel(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    val first = remember { FocusRequester() }
    val canSelf = remember { context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    var note by remember { mutableStateOf<String?>(null) }
    TodayDialog(onDismiss, width = 640.dp) {
        PanelTitle("Show what plays", look)
        PanelNote("Spotify, YT Music and the rest tell Android what they play. The TV shares it once it says yes.", look)
        Words(
            if (canSelf) "One press and the card shows the song, the cover and a play button."
            else "Turn on Neon Tokyo under Notification access. If this TV has no such page, run this once from the PC:",
            13.sp, alpha = 0.85f
        )
        if (!canSelf) {
            Box(Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp)).padding(10.dp)) {
                Words("adb shell cmd notification allow_listener ${context.packageName}/${NowPlayingListener::class.java.name}", 11.sp, alpha = 0.9f)
            }
        }
        note?.let { Words(it, 12.sp, color = look.accent) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelButton(if (canSelf) "Turn on" else "Open the TV's page", look, primary = true, modifier = Modifier.focusRequester(first)) {
                if (canSelf) {
                    if (NowPlaying.allow(context)) { HomeToday.changed(); onDismiss() } else note = "The TV said no"
                } else if (!NowPlaying.openSettings(context)) note = "This TV has no such page: use the line above from the PC"
            }
            PanelButton("Not now", look, onClick = onDismiss)
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
}

// ---- Lights ----

@Composable
private fun BoxScope.LightCard(light: HomeLight, color: Color, focused: Boolean) {
    val glow by animateFloatAsState(if (light.on && light.reachable) 1f else 0f, tween(450), label = "Lamp")
    Box(
        Modifier.matchParentSize().clip(RoundedCornerShape(LocalHomeLook.current.corner)).drawBehind {
            if (glow > 0f) drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.34f * glow), Color.Transparent), center = Offset(size.width * 0.22f, size.height * 0.32f), radius = size.height),
                radius = size.height, center = Offset(size.width * 0.22f, size.height * 0.32f)
            )
        }
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonGlyph(Glyph.Bulb, { lerpColor(Color(0xFF8C8FA3), color, glow) }, Modifier.size(26.dp))
            Spacer(Modifier.weight(1f))
            Switch(light.on && light.reachable, color)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Words(light.name, 12.5.sp, bold = true)
            Words(
                when {
                    !light.reachable -> "Unreachable"
                    !light.on -> "Off"
                    light.dimmable -> "On · ${(light.brightness * 100).roundToInt()}%"
                    else -> "On"
                },
                10.sp, alpha = 0.65f
            )
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

/** A little switch, its knob sliding over as the lamp goes on. */
@Composable
private fun Switch(on: Boolean, color: Color) {
    val at by animateFloatAsState(if (on) 1f else 0f, tween(260, easing = FastOutSlowInEasing), label = "Switch")
    val look = LocalHomeLook.current
    Box(
        Modifier.size(width = 26.dp, height = 15.dp).drawBehind {
            drawRoundRect(lerpColor(look.text.copy(alpha = 0.18f), color.copy(alpha = 0.85f), at), cornerRadius = CornerRadius(size.height / 2))
            val r = size.height / 2 - 2.dp.toPx()
            drawCircle(Color.White, radius = r, center = Offset(size.height / 2 + (size.width - size.height) * at, size.height / 2))
        }
    )
}

@Composable
private fun BoxScope.HueFoundCard(color: Color, focused: Boolean) {
    Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        NeonGlyph(Glyph.Bulb, color, Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Words("Hue bridge found", 13.sp, bold = true)
            Words("Your lights, here on Home", 10.5.sp, alpha = 0.7f)
            Words("OK to link", 10.sp, color = color)
        }
    }
}

@Composable
private fun LightPanel(light: HomeLight, demo: Boolean, onChange: (HomeLight) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var current by remember(light.key) { mutableStateOf(light) }
    var sliderFocused by remember { mutableStateOf(false) }
    fun change(next: HomeLight, on: Boolean? = null, brightness: Float? = null, tone: LightTone? = null) {
        current = next
        onChange(next)
        if (!demo) scope.launch { HomeLights.set(context, light, on, brightness, tone) }
    }
    val color = lightColor(current)
    TodayDialog(onDismiss, width = 620.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonGlyph(Glyph.Bulb, color, Modifier.size(52.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                PanelTitle(current.name, look)
                PanelNote(if (current.hub == LightHub.Hue) (if (current.room) "Hue room" else "Hue") else "Home Assistant", look)
            }
        }
        if (current.dimmable) {
            // Left and right dim and brighten in tens; the bar fills as it goes
            val shown by animateFloatAsState(if (current.on) current.brightness else 0f, tween(220), label = "Bri")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Words("☀", 14.sp, Modifier.width(22.dp), alpha = 0.6f)
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .focusRequester(first)
                        .onFocusChanged { sliderFocused = it.isFocused }
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val step = when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> -0.1f
                                KeyEvent.KEYCODE_DPAD_RIGHT -> 0.1f
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    change(current.copy(on = !current.on), on = !current.on)
                                    return@onKeyEvent true
                                }
                                else -> return@onKeyEvent false
                            }
                            val from = if (current.on) current.brightness else 0f
                            val to = ((from + step) * 10).roundToInt() / 10f
                            if (to <= 0f) change(current.copy(on = false), on = false)
                            else change(current.copy(on = true, brightness = to.coerceAtMost(1f)), brightness = to.coerceAtMost(1f))
                            true
                        }
                        .focusable()
                        .drawBehind {
                            val r = CornerRadius(size.height / 2)
                            drawRoundRect(look.text.copy(alpha = 0.12f), cornerRadius = r)
                            drawRoundRect(
                                Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)),
                                size = Size(size.width * shown.coerceIn(0f, 1f), size.height), cornerRadius = r
                            )
                            if (sliderFocused) drawRoundRect(Color.White, cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Words(if (current.on) "${(current.brightness * 100).roundToInt()}%" else "Off", 13.sp, bold = true)
                }
            }
            PanelNote("◀ ▶ dims and brightens · OK switches it on or off", look)
        }
        if (current.colorful || current.dimmable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LightTone.entries.filter { current.colorful || it.kelvin != null }.forEach { tone ->
                    ToneDot(tone) { change(current.copy(on = true, color = tone.argb), tone = tone) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelButton(if (current.on) "Turn off" else "Turn on", look, primary = !current.dimmable, modifier = if (current.dimmable) Modifier else Modifier.focusRequester(first)) {
                change(current.copy(on = !current.on), on = !current.on)
            }
            PanelButton("Done", look, onClick = onDismiss)
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
}

@Composable
private fun ToneDot(tone: LightTone, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (focused) 1.2f else 1f, tween(160), label = "Tone")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(30.dp)
                .graphicsLayer { scaleX = grow; scaleY = grow }
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyUp && (e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                        onClick(); true
                    } else false
                }
                .focusable()
                .background(Color(tone.argb), CircleShape)
                .border(if (focused) 2.dp else 0.5.dp, Color.White.copy(alpha = if (focused) 1f else 0.3f), CircleShape)
        )
        Spacer(Modifier.height(4.dp))
        Words(tone.title, 9.sp, alpha = if (focused) 0.95f else 0.5f)
    }
}

/** Linking the Hue bridge: press its round button; the TV keeps asking for half a minute. */
@Composable
private fun HuePairPanel(ip: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    val first = remember { FocusRequester() }
    var state by remember { mutableStateOf<HuePairing?>(null) }
    var tries by remember { mutableStateOf(0) }
    val left = remember { Animatable(1f) }
    LaunchedEffect(tries) {
        state = null
        left.snapTo(1f)
        launch { left.animateTo(0f, tween(30_000, easing = LinearEasing)) }
        val until = SystemClock.elapsedRealtime() + 30_000L
        while (SystemClock.elapsedRealtime() < until) {
            val answer = HomeLights.pairHue(context, ip)
            if (answer == HuePairing.Paired) {
                state = answer
                left.snapTo(0f)
                delay(1400)
                onDone()
                return@LaunchedEffect
            }
            if (answer == HuePairing.Unreachable) state = answer
            delay(2000)
        }
        state = HuePairing.PressButton
    }
    TodayDialog(onDone, width = 560.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                val ring = look.accent
                Box(Modifier.fillMaxSize().drawBehind {
                    drawCircle(ring.copy(alpha = 0.15f), style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                    drawArc(ring, -90f, 360f * left.value, false, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                })
                NeonGlyph(if (state == HuePairing.Paired) Glyph.Check else Glyph.Bulb, Color(0xFFFFC07A), Modifier.size(34.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PanelTitle(if (state == HuePairing.Paired) "Linked" else "Press the bridge's button", look)
                Words(
                    when (state) {
                        HuePairing.Paired -> "Your rooms are coming to Home."
                        HuePairing.Unreachable -> "The bridge at $ip does not answer. Is it on?"
                        else -> "The big round button on top of the Hue bridge. The TV links by itself within half a minute."
                    },
                    12.sp, alpha = 0.8f
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelButton("Try again", look, primary = left.value == 0f && state != HuePairing.Paired, modifier = Modifier.focusRequester(first)) { tries++ }
            PanelButton("Not now", look, onClick = onDone)
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
}

// ---- Panels ----

@Composable
private fun TodayDialog(onDismiss: () -> Unit, width: Dp = 760.dp, content: @Composable () -> Unit) {
    val look = LocalHomeLook.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = width)
                    .fillMaxWidth()
                    .panelIn().homePanel(look, RoundedCornerShape(18.dp))
                    .padding(horizontal = 30.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) { content() }
        }
    }
}

// ---- The profile menu's Today page ----

private val WeatherTowns = listOf("Luleå", "Stockholm", "Göteborg", "Malmö", "Uppsala", "Umeå")

/**
 * The profile menu's page for the Today row: which cards show, the town for the weather, the team
 * to follow, and the home's lights (a Hue bridge found and paired here, Home Assistant from the
 * phone). The town and team are the TV's; the cards are the profile's own.
 */
@Composable
internal fun HomeTodayDialog(profile: HomeProfile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var cards by remember(profile.id) { mutableStateOf(HomeToday.cards(context, profile.id)) }
    var place by remember { mutableStateOf(HomeWeather.place(context)?.name) }
    var team by remember { mutableStateOf(HomeHockey.team(context)) }
    var phone by remember { mutableStateOf(false) }
    var pairing by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val version by HomeLights.version.collectAsState()
    val hue = remember(version) { HomeLights.hueLinked(context) }
    val ha = remember(version) { HomeLights.homeAssistantLinked(context) }
    var all by remember { mutableStateOf(TodayCache.allLights) }
    var shown by remember(version) { mutableStateOf(HomeLights.shown(context)) }
    LaunchedEffect(version) { if (hue || ha) all = HomeLights.all(context).also { TodayCache.allLights = it } }

    Dialog(onDismissRequest = { if (phone) phone = false else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(860.dp)
                    .panelIn().homePanel(look, RoundedCornerShape(18.dp))
                    .padding(horizontal = 30.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                PanelTitle("Today for ${profile.name}", look)
                PanelNote("Everyday cards under the apps on Home. A card with nothing to say keeps out of the way.", look)
                TodayOption("CARDS", look) {
                    TodayCard.entries.forEachIndexed { i, card ->
                        PanelButton((if (card in cards) "✓ " else "") + card.title, look, primary = card in cards, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) {
                            cards = if (card in cards) cards - card else cards + card
                            HomeToday.set(context, profile.id, cards)
                        }
                    }
                }
                TodayOption("WEATHER IN", look) {
                    PanelButton("TV's time zone", look, primary = place == null) {
                        HomeWeather.setPlace(context, null); place = null; HomeToday.changed()
                    }
                    WeatherTowns.forEach { town ->
                        PanelButton(town, look, primary = place == town) {
                            scope.launch {
                                val found = HomeWeather.find(town, 1).firstOrNull()
                                if (found != null) {
                                    HomeWeather.setPlace(context, found); place = found.name; HomeToday.changed()
                                } else note = "No network to look up $town"
                            }
                        }
                    }
                    if (place != null && place !in WeatherTowns) PanelButton(place!!, look, primary = true) {}
                    PanelButton("Other…", look) { phone = true }
                }
                TodayOption("HOCKEY TEAM", look) {
                    HomeHockey.teams.forEach { (code, name) ->
                        PanelButton(name, look, primary = team == code) {
                            HomeHockey.setTeam(context, code); team = code; HomeToday.changed()
                        }
                    }
                }
                TodayOption("LIGHTS", look) {
                    if (hue) PanelButton("Hue linked  ✕", look) { HomeLights.forget(context, LightHub.Hue) }
                    else PanelButton(if (searching) "Looking…" else "Find Hue bridge", look, primary = true) {
                        if (searching) return@PanelButton
                        searching = true
                        scope.launch {
                            val ip = TodayCache.bridge ?: HomeLights.findHueBridge(context)?.also { TodayCache.bridge = it }
                            searching = false
                            if (ip == null) note = "No Hue bridge on this network" else pairing = ip
                        }
                    }
                    if (ha) PanelButton("Home Assistant linked  ✕", look) { HomeLights.forget(context, LightHub.HomeAssistant) }
                    else PanelButton("Home Assistant…", look) { phone = true }
                }
                if (all.isNotEmpty()) {
                    TodayOption("ON HOME", look) {
                        PanelButton("First ${HomeLights.MAX_SHOWN}", look, primary = shown.isEmpty()) { HomeLights.setShown(context, emptyList()) }
                        all.forEach { light ->
                            val on = light.key in shown
                            PanelButton((if (on) "✓ " else "") + light.name, look, primary = on) {
                                val next = if (on) shown - light.key else (shown + light.key).take(HomeLights.MAX_SHOWN)
                                HomeLights.setShown(context, next)
                            }
                        }
                    }
                }
                note?.let { PanelNote(it, look) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PanelButton("Done", look, primary = true, onClick = onDismiss)
                    PanelButton("Set up from phone", look) { phone = true }
                    Spacer(Modifier.weight(1f))
                    PanelNote("Back closes", look)
                }
            }
        }
    }
    pairing?.let { ip -> HuePairPanel(ip, onDone = { pairing = null; HomeToday.changed() }) }
    if (phone) TodayPhoneDialog(onDismiss = { phone = false; place = HomeWeather.place(context)?.name })
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
}

@Composable
private fun TodayOption(label: String, look: HomeLook, choices: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PanelNote(label, look, Modifier.width(110.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) { choices() }
    }
}

/** The QR code for the phone page: the town, and Home Assistant's address and token. */
@Composable
private fun TodayPhoneDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    val manager = remember { TodaySetupServer.Manager() }
    var url by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val info = manager.start(context.applicationContext) {
            saved++
            HomeToday.changed()
        }
        if (info == null) failed = true
        else {
            url = info.url
            qr = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.lumera.app.ui.profiles.qrCode(info.url) }?.asImageBitmap()
        }
    }
    DisposableEffect(Unit) { onDispose { manager.stop() } }
    TodayDialog(onDismiss, width = 640.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(190.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                qr?.let { Image(it, contentDescription = "QR code", modifier = Modifier.fillMaxSize()) }
                    ?: Words(if (failed) "No network" else "…", 12.sp, color = Color.Black)
            }
            Spacer(Modifier.width(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelTitle("Set up from your phone", look)
                Words("Scan with the phone's camera, on the same Wi-Fi as the TV.", 12.sp, alpha = 0.8f)
                Words("The town for the weather, and Home Assistant's address and token.", 12.sp, alpha = 0.8f)
                url?.let { Words(it, 10.sp, alpha = 0.5f) }
                if (saved > 0) Words("✓ Saved on the TV", 12.sp, color = look.accent)
            }
        }
        PanelButton("Done", look, primary = true, modifier = Modifier.focusRequester(first), onClick = onDismiss)
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
}
