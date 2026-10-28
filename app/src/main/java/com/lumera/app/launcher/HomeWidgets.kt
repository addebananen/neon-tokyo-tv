package com.lumera.app.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/** The small things a profile can put under the clock. */
enum class WidgetKind(val title: String) {
    Weather("Weather"),
    Calendar("Week"),
    Music("Now playing"),
    Network("Network"),
    Shortcuts("Shortcuts")
}

/** A profile's widgets: which show, its shortcuts, and whether the screensaver shows them too. */
data class WidgetSetup(
    val kinds: Set<WidgetKind> = emptySet(),
    val shortcuts: List<String> = emptyList(),
    val onDream: Boolean = true
) {
    /** In the order they sit, left to right. */
    val shown: List<WidgetKind> get() = WidgetKind.values().filter { it in kinds && (it != WidgetKind.Shortcuts || shortcuts.isNotEmpty()) }
}

/**
 * Each home profile's widgets, kept in the app's preferences. None is on until it is picked: the
 * styles stay as they are drawn, and the widgets are an extra on top.
 */
object HomeWidgets {
    private const val PREFS = "neon_home_widgets"
    const val MAX_SHORTCUTS = 4
    private val _version = MutableStateFlow(0)
    /** Goes up on every change, for the home screen to read the setup again. */
    val version: StateFlow<Int> = _version.asStateFlow()

    @Volatile
    private var previewed: WidgetSetup? = null

    /** Widgets a preview of the home screen shows instead of the profile's own; null ends it. */
    fun preview(setup: WidgetSetup?) {
        if (setup == previewed) return
        previewed = setup
        _version.update { it + 1 }
    }

    /** What the home screen shows for [profileId]: a preview's widgets, else the profile's. */
    fun shown(context: Context, profileId: String): WidgetSetup = previewed ?: get(context, profileId)

    fun get(context: Context, profileId: String): WidgetSetup = runCatching {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(profileId, null) ?: return WidgetSetup()
        val o = JSONObject(text)
        val kinds = o.optJSONArray("k")?.let { a -> (0 until a.length()).mapNotNull { i -> runCatching { WidgetKind.valueOf(a.getString(i)) }.getOrNull() } }.orEmpty()
        val shortcuts = o.optJSONArray("s")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        WidgetSetup(kinds.toSet(), shortcuts.take(MAX_SHORTCUTS), o.optBoolean("d", true))
    }.getOrDefault(WidgetSetup())

    fun set(context: Context, profileId: String, setup: WidgetSetup) {
        val o = JSONObject()
            .put("k", JSONArray(setup.kinds.map { it.name }))
            .put("s", JSONArray(setup.shortcuts.take(MAX_SHORTCUTS)))
            .put("d", setup.onDream)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(profileId, o.toString()).apply()
        _version.update { it + 1 }
    }

    /** Week numbers as Sweden counts them: ISO, Monday first, week 1 holds the first Thursday. */
    fun week(now: Long = System.currentTimeMillis()): Int =
        Calendar.getInstance(Locale.ROOT).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = now
        }.get(Calendar.WEEK_OF_YEAR)
}

/** How the TV is online: the kind of link and, on Wi‑Fi, its strength from 0 to 3. */
data class NetLink(val kind: Kind, val level: Int = 3) {
    enum class Kind { Wifi, Ethernet, Other, Offline }
}

internal fun netLink(caps: NetworkCapabilities?): NetLink {
    caps ?: return NetLink(NetLink.Kind.Offline, 0)
    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetLink(NetLink.Kind.Offline, 0)
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetLink(NetLink.Kind.Ethernet)
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            val dbm = if (Build.VERSION.SDK_INT >= 29) caps.signalStrength else Int.MIN_VALUE
            NetLink(NetLink.Kind.Wifi, wifiLevel(dbm))
        }
        else -> NetLink(NetLink.Kind.Other)
    }
}

/** Bars for a Wi‑Fi signal in dBm, as the TV's own settings count them roughly. */
internal fun wifiLevel(dbm: Int): Int = when {
    dbm == Int.MIN_VALUE || dbm >= -60 -> 3
    dbm >= -70 -> 2
    dbm >= -80 -> 1
    else -> 0
}

@Composable
private fun rememberNetLink(on: Boolean): NetLink? {
    val context = LocalContext.current
    var link by remember { mutableStateOf<NetLink?>(null) }
    DisposableEffect(on) {
        if (!on) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(ConnectivityManager::class.java)
        link = netLink(manager?.getNetworkCapabilities(manager.activeNetwork))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                link = netLink(caps)
            }
            override fun onLost(network: Network) {
                link = NetLink(NetLink.Kind.Offline, 0)
            }
        }
        val registered = runCatching { manager?.registerDefaultNetworkCallback(callback) }.isSuccess
        onDispose { if (registered) runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
    return link
}

@Composable
private fun rememberWeatherNow(on: Boolean): Weather? {
    val context = LocalContext.current
    var weather by remember { mutableStateOf(HomeWeather.last(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(on, lifecycle) {
        if (!on) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                HomeWeather.now(context)?.let { weather = it }
                delay(HomeWeather.FRESH_MS)
            }
        }
    }
    return weather
}

@Composable
private fun rememberTrack(on: Boolean): Track? {
    val context = LocalContext.current
    val now = remember { NowPlaying(context.applicationContext) }
    DisposableEffect(on) {
        if (on) now.start()
        onDispose { now.stop() }
    }
    val track by now.track.collectAsState()
    return track
}

@Composable
private fun rememberWeek(on: Boolean): Int {
    var week by remember { mutableStateOf(HomeWidgets.week()) }
    LaunchedEffect(on) {
        while (on) {
            delay(15 * 60_000L)
            week = HomeWidgets.week()
        }
    }
    return week
}

private fun weatherGlyph(weather: Weather): Glyph = when (weather.kind) {
    WeatherKind.Clear -> if (weather.day) Glyph.Sun else Glyph.Moon
    WeatherKind.Clouds -> Glyph.Cloud
    WeatherKind.Fog -> Glyph.Fog
    WeatherKind.Drizzle, WeatherKind.Rain -> Glyph.Rain
    WeatherKind.Snow -> Glyph.Snow
    WeatherKind.Storm -> Glyph.Storm
}

/**
 * The widgets in a slim row under the clock: small glass chips that come in one after another
 * and change their words with a soft roll. [interactive] makes the shortcuts pressable; on the
 * screensaver nothing is.
 */
@Composable
internal fun WidgetStrip(
    setup: WidgetSetup,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    apps: List<InstalledApp> = emptyList(),
    onFocus: () -> Unit = {}
) {
    val shown = setup.shown
    if (shown.isEmpty()) return
    val context = LocalContext.current
    val weather = rememberWeatherNow(WidgetKind.Weather in shown)
    val week = rememberWeek(WidgetKind.Calendar in shown)
    val track = rememberTrack(WidgetKind.Music in shown)
    val net = rememberNetLink(WidgetKind.Network in shown)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { index, kind ->
            Arrive(index) {
                when (kind) {
                    WidgetKind.Weather -> weather?.let { w -> InfoChip(weatherGlyph(w), "${w.celsius}°", w.place) }
                        ?: InfoChip(Glyph.Cloud, "–", null)
                    WidgetKind.Calendar -> InfoChip(Glyph.Calendar, "Week $week", null)
                    WidgetKind.Music -> track?.let { t -> InfoChip(Glyph.Note, t.title, t.artist, dim = !t.playing, wide = true) }
                        ?: InfoChip(Glyph.Note, "Nothing playing", null, dim = true)
                    WidgetKind.Network -> NetChip(net)
                    WidgetKind.Shortcuts -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        setup.shortcuts.forEach { pkg ->
                            val app = apps.firstOrNull { it.packageName == pkg }
                            ShortcutChip(pkg, app, interactive, onClick = { HomeApps.open(context, pkg) }, onFocus = onFocus)
                        }
                    }
                }
            }
        }
    }
}

/** A chip slides up and lights as it comes, a beat after the one before it. */
@Composable
private fun Arrive(index: Int, content: @Composable () -> Unit) {
    val come = remember { Animatable(0f) }
    LaunchedEffect(Unit) { come.animateTo(1f, tween(420, delayMillis = 120 + index * 70, easing = FastOutSlowInEasing)) }
    Box(Modifier.graphicsLayer {
        alpha = come.value
        translationY = (1f - come.value) * 8.dp.toPx()
    }) { content() }
}

@Composable
private fun Modifier.chip(dim: Boolean = false): Modifier {
    val look = LocalHomeLook.current
    val shape = RoundedCornerShape(50)
    return this
        .background(look.pill.copy(alpha = if (dim) 0.32f else 0.46f), shape)
        .border(0.5.dp, look.text.copy(alpha = 0.14f), shape)
}

@Composable
private fun InfoChip(glyph: Glyph, text: String, sub: String?, dim: Boolean = false, wide: Boolean = false) {
    val look = LocalHomeLook.current
    Row(
        Modifier.chip(dim).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeonGlyph(glyph, look.accent2.copy(alpha = if (dim) 0.6f else 1f), Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        AnimatedContent(
            targetState = text to sub,
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 2 })
                    .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 })
                    .using(SizeTransform(clip = false))
            },
            label = "WidgetText"
        ) { (main, small) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    main,
                    modifier = if (wide) Modifier.widthIn(max = 170.dp) else Modifier,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 10.5.sp, color = look.text.copy(alpha = if (dim) 0.7f else 1f))
                )
                if (small != null) {
                    Spacer(Modifier.width(5.dp))
                    BasicText(
                        small,
                        modifier = Modifier.widthIn(max = if (wide) 110.dp else 90.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 9.5.sp, color = look.dim)
                    )
                }
            }
        }
    }
}

@Composable
private fun NetChip(net: NetLink?) {
    val look = LocalHomeLook.current
    val kind = net?.kind ?: NetLink.Kind.Other
    val (glyph, label) = when (kind) {
        NetLink.Kind.Wifi -> Glyph.Wifi to "Wi‑Fi"
        NetLink.Kind.Ethernet -> Glyph.Ethernet to "Ethernet"
        NetLink.Kind.Offline -> Glyph.Offline to "Offline"
        NetLink.Kind.Other -> Glyph.Globe to "Online"
    }
    Row(
        Modifier.chip(kind == NetLink.Kind.Offline).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (kind == NetLink.Kind.Offline) Color(0xFFFF5A6E) else look.accent2
        NeonGlyph(glyph, tint, Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        BasicText(label, style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 10.5.sp, color = look.text))
        if (kind == NetLink.Kind.Wifi) {
            Spacer(Modifier.width(6.dp))
            SignalBars(net?.level ?: 3, look.accent2, look.text.copy(alpha = 0.2f))
        }
    }
}

/** Three little bars, lit up to [level]. */
@Composable
private fun SignalBars(level: Int, on: Color, off: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp), verticalAlignment = Alignment.Bottom) {
        for (bar in 1..3) {
            Spacer(
                Modifier
                    .size(width = 2.5.dp, height = (3 + bar * 2.5f).dp)
                    .background(if (bar <= level) on else off, RoundedCornerShape(1.dp))
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShortcutChip(pkg: String, app: InstalledApp?, interactive: Boolean, onClick: () -> Unit, onFocus: () -> Unit) {
    val look = LocalHomeLook.current
    val known = app?.known
    val shape = RoundedCornerShape(50)
    val face: @Composable () -> Unit = {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (known != null) {
                NeonGlyph(known.glyph, look.tint(known.color), Modifier.size(15.dp))
            } else {
                BasicText(
                    (app?.name ?: pkg.substringAfterLast('.')).take(1).uppercase(),
                    style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 11.sp, color = look.text)
                )
            }
        }
    }
    if (!interactive) {
        Box(Modifier.chip()) { face() }
        return
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = look.pill.copy(alpha = 0.46f),
            contentColor = look.text,
            focusedContainerColor = look.pillFocused,
            focusedContentColor = look.text
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.5.dp, look.text.copy(alpha = 0.14f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, look.accent), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = look.accent.copy(alpha = 0.5f), elevation = look.glow * 0.7f))
    ) { face() }
}

/** The screensaver's copy of the active profile's widgets, if it wants them there. */
@Composable
internal fun DreamWidgets(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val setup = remember {
        val profile = HomeProfiles.get(context).active()
        HomeWidgets.get(context, profile.id).let { it.copy(kinds = it.kinds - WidgetKind.Shortcuts) }
    }
    if (!setup.onDream) return
    WidgetStrip(setup, interactive = false, modifier = modifier)
}

/**
 * Picking a profile's widgets: each one on or off, up to four app shortcuts, and whether the
 * screensaver shows them too. The row at the top shows them live as they are picked.
 */
@Composable
internal fun HomeWidgetsDialog(profile: HomeProfile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val look = LocalHomeLook.current
    var setup by remember(profile.id) { mutableStateOf(HomeWidgets.get(context, profile.id)) }
    var picking by remember { mutableStateOf(false) }
    val apps = remember { HomeApps.load(context) }
    val first = remember { FocusRequester() }
    fun save(next: WidgetSetup) {
        setup = next
        HomeWidgets.set(context, profile.id, next)
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Dialog(onDismissRequest = { if (picking) picking = false else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(820.dp)
                    .homePanel(look, RoundedCornerShape(18.dp))
                    .padding(horizontal = 30.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PanelTitle("Widgets for ${profile.name}", look)
                PanelNote("Small and quiet, under the clock. Only this profile sees them.", look)
                Box(
                    Modifier
                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (setup.shown.isEmpty()) PanelNote("Nothing yet: pick some below", look)
                    else WidgetStrip(setup.copy(onDream = true), interactive = false, apps = apps)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PanelNote("SHOW", look)
                    Spacer(Modifier.width(4.dp))
                    WidgetKind.values().forEachIndexed { index, kind ->
                        PanelButton(
                            (if (kind in setup.kinds) "✓ " else "") + kind.title,
                            look,
                            primary = kind in setup.kinds,
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier
                        ) {
                            val kinds = if (kind in setup.kinds) setup.kinds - kind else setup.kinds + kind
                            save(setup.copy(kinds = kinds))
                            if (kind == WidgetKind.Shortcuts && kind in kinds && setup.shortcuts.isEmpty()) picking = true
                        }
                    }
                }
                if (WidgetKind.Shortcuts in setup.kinds) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PanelNote("SHORTCUTS", look)
                        Spacer(Modifier.width(4.dp))
                        setup.shortcuts.forEach { pkg ->
                            val name = apps.firstOrNull { it.packageName == pkg }?.name ?: pkg
                            PanelButton("$name  ✕", look) { save(setup.copy(shortcuts = setup.shortcuts - pkg)) }
                        }
                        if (setup.shortcuts.size < HomeWidgets.MAX_SHORTCUTS) {
                            PanelButton(if (picking) "Pick below" else "+ Add app", look, primary = picking) { picking = !picking }
                        }
                    }
                    if (picking) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(apps.filter { it.packageName !in setup.shortcuts && it.packageName != context.packageName }, key = { it.packageName }) { app ->
                                PanelButton(app.name, look) {
                                    val next = (setup.shortcuts + app.packageName).take(HomeWidgets.MAX_SHORTCUTS)
                                    save(setup.copy(shortcuts = next))
                                    if (next.size >= HomeWidgets.MAX_SHORTCUTS) picking = false
                                }
                            }
                        }
                    }
                }
                if (WidgetKind.Music in setup.kinds && !NowPlaying.allowed(context)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            PanelNote("Now playing needs a one-time yes from the TV. From the PC:", look)
                            PanelNote("adb shell cmd notification allow_listener ${context.packageName}/${NowPlayingListener::class.java.name}", look)
                        }
                        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                            PanelButton("Allow", look, primary = true) {
                                NowPlaying.allow(context)
                                save(setup)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PanelNote("ON THE SCREENSAVER", look)
                    Spacer(Modifier.width(4.dp))
                    PanelButton("Show", look, primary = setup.onDream) { save(setup.copy(onDream = true)) }
                    PanelButton("Hide", look, primary = !setup.onDream) { save(setup.copy(onDream = false)) }
                    Spacer(Modifier.weight(1f))
                    PanelButton("Done", look, primary = true, onClick = onDismiss)
                }
            }
        }
    }
}
