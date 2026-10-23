package com.lumera.app.launcher

import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.lumera.app.projectivy.NeonWallpaper
import com.lumera.app.ui.components.NeonPalette
import com.lumera.app.ui.components.NeonText
import com.lumera.app.ui.theme.KanitFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HomeTab { Home, Apps }

private val TileShape = RoundedCornerShape(10.dp)
private val SmallTileShape = RoundedCornerShape(8.dp)
private val TileFill = Brush.verticalGradient(listOf(Color(0xEB2A0F4F), Color(0xEB0D0721)))
private val Dim = Color(0xFFB9A6D6)
private val Pane = Color(0xCC0D0721)

private val BigTile = 190.dp to 82.dp
private val SmallTile = 116.dp to 69.dp
private val Gap = 13.dp
private val Side = 55.dp

/** The keys a TV Surface clicks on. */
private val SelectKeys = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)

/**
 * The Neon Tokyo home screen: the clock, Search, Inputs and Settings at the top, and two tabs.
 * Home has the big tiles for YouTube, YT Music, Spotify and Stremio and a row of consoles, each
 * with the HDMI number Adrian gave it; All apps has every app on the TV with its neon icon.
 * A console that is switched on gets a glowing outline that breathes and a small white star
 * that twinkles in its corner. The skyline behind takes the colour of the tile in focus.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NeonHome(homePresses: Int, onLeave: (() -> Unit)? = null) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val inputs = remember { TvInputs(appContext) }
    val links = remember { ConsoleLinks(appContext) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var installed by remember { mutableStateOf(HomeCatalog.favourites.toSet()) }
    var appsVersion by remember { mutableIntStateOf(0) }
    var resumes by remember { mutableIntStateOf(0) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    inputs.start()
                    appsVersion++
                }
                Lifecycle.Event.ON_RESUME -> resumes++
                Lifecycle.Event.ON_STOP -> inputs.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            inputs.stop()
        }
    }
    // Apps put on or taken off the TV while the home screen is open
    DisposableEffect(appContext) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                appsVersion++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { appContext.unregisterReceiver(receiver) }
    }
    LaunchedEffect(appsVersion) {
        if (appsVersion == 0) return@LaunchedEffect
        val (all, favourites) = withContext(Dispatchers.IO) {
            HomeApps.load(appContext) to HomeCatalog.favourites.filter { HomeApps.isInstalled(appContext, it) }.toSet()
        }
        apps = all
        installed = favourites
    }

    val inputList by inputs.inputs.collectAsState()
    val states by inputs.states.collectAsState()
    val linkVersion by links.version.collectAsState()
    val consoleInputs = remember(inputList, linkVersion) {
        HomeCatalog.consoles.associateWith { links.inputFor(it, inputList) }
    }

    var tab by rememberSaveable { mutableStateOf(HomeTab.Home) }
    var wallpaper by remember { mutableStateOf(NeonWallpaper.TOKYO) }
    var linking by remember { mutableStateOf<Console?>(null) }
    var showInputs by remember { mutableStateOf(false) }
    val firstFavourite = remember { FocusRequester() }
    val firstApp = remember { FocusRequester() }
    val homeTab = remember { FocusRequester() }
    val appsTab = remember { FocusRequester() }
    // Goes up to send the focus to the first tile of the open tab
    var focusFirst by remember { mutableIntStateOf(1) }
    LaunchedEffect(focusFirst) {
        // A tab that just opened is only there after a frame or two
        repeat(10) {
            withFrameNanos { }
            val target = if (tab == HomeTab.Home) firstFavourite else firstApp
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    // Home pressed while the home screen shows: back to the start, as on other TV home screens
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        linking = null
        showInputs = false
        tab = HomeTab.Home
        focusFirst++
    }
    BackHandler {
        if (tab == HomeTab.Home && onLeave != null) {
            onLeave()
        } else {
            tab = HomeTab.Home
            focusFirst++
        }
    }

    fun openConsole(console: Console) {
        val input = consoleInputs[console]
        if (input == null) {
            linking = console
        } else {
            HomeApps.start(context, TvInputs.openIntent(input.id))
        }
    }

    fun pickInput(input: TvInput?) {
        showInputs = false
        if (input != null) HomeApps.start(context, TvInputs.openIntent(input.id))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tabsTop = maxHeight * 0.415f
        val rowsTop = maxHeight * 0.485f
        SkylineBackdrop(wallpaper)

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = 30.dp),
            verticalAlignment = Alignment.Top
        ) {
            HomeClock(resumes)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 6.dp)) {
                Pill("Search", onClick = { openSearch(context) }, onFocus = { wallpaper = NeonWallpaper.TOKYO })
                Pill("Inputs", onClick = { showInputs = true }, onFocus = { wallpaper = NeonWallpaper.OCEAN })
                Pill("Settings", onClick = { HomeApps.start(context, Intent(Settings.ACTION_SETTINGS)) }, onFocus = { wallpaper = NeonWallpaper.TOKYO })
            }
        }

        Row(
            modifier = Modifier
                .padding(start = Side, top = tabsTop)
                // Up from the tiles lands on the open tab, never on the other one, which would open it
                .focusProperties { enter = { if (tab == HomeTab.Home) homeTab else appsTab } }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TabPill("Home", tab == HomeTab.Home, homeTab, onFocus = { tab = HomeTab.Home }, onClick = { focusFirst++ })
            TabPill("All apps", tab == HomeTab.Apps, appsTab, onFocus = { tab = HomeTab.Apps }, onClick = { focusFirst++ })
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(180, delayMillis = 40)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 14 })
                    .togetherWith(fadeOut(tween(110)))
            },
            label = "HomeTab",
            modifier = Modifier.padding(top = rowsTop).fillMaxSize()
        ) { shown ->
            when (shown) {
                HomeTab.Home -> HomeRows(
                    installed = installed,
                    live = { console -> TvInputs.isOn(consoleInputs[console]?.let { states[it.id] }) },
                    firstFavourite = firstFavourite,
                    onFocusColor = { pkg, title, color -> wallpaper = NeonWallpaper.forCard(pkg, title, color.toArgb()) },
                    onOpenApp = { HomeApps.open(context, it) },
                    onOpenConsole = { openConsole(it) },
                    onLinkConsole = { linking = it }
                )
                HomeTab.Apps -> AllApps(
                    apps = apps,
                    firstApp = firstApp,
                    onFocusColor = { pkg, title, color -> wallpaper = NeonWallpaper.forCard(pkg, title, color.toArgb()) },
                    onOpen = { HomeApps.open(context, it.packageName) }
                )
            }
        }
    }

    linking?.let { console ->
        InputPicker(
            title = "Which input is ${console.name} on?",
            hint = "Consoles that share an input through an HDMI switch light up together.",
            inputs = inputList,
            states = states,
            selected = consoleInputs[console]?.id,
            labels = { input -> HomeCatalog.consoles.filter { it != console && consoleInputs[it]?.id == input.id }.map { it.name } },
            noneLabel = "Not plugged in",
            onPick = { input ->
                links.link(console, input)
                linking = null
            },
            onDismiss = { linking = null }
        )
    }
    if (showInputs) {
        InputPicker(
            title = "Inputs",
            hint = null,
            inputs = inputList,
            states = states,
            selected = null,
            labels = { input -> HomeCatalog.consoles.filter { consoleInputs[it]?.id == input.id }.map { it.name } },
            noneLabel = null,
            onPick = { pickInput(it) },
            onDismiss = { showInputs = false }
        )
    }
}

@Composable
private fun HomeRows(
    installed: Set<KnownApp>,
    live: (Console) -> Boolean,
    firstFavourite: FocusRequester,
    onFocusColor: (String?, String, Color) -> Unit,
    onOpenApp: (KnownApp) -> Unit,
    onOpenConsole: (Console) -> Unit,
    onLinkConsole: (Console) -> Unit
) {
    Column(modifier = Modifier.padding(start = Side), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        RowTitle("Watch & listen")
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            HomeCatalog.favourites.forEachIndexed { index, app ->
                NeonTile(
                    width = BigTile.first,
                    height = BigTile.second,
                    shape = TileShape,
                    color = app.color,
                    onClick = { onOpenApp(app) },
                    onFocus = { onFocusColor(app.packages.first(), app.name, app.color) },
                    focusRequester = if (index == 0) firstFavourite else null,
                    // Not on the TV yet: dimmed, and opening it goes to its Play Store page
                    dimmed = app !in installed
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeonGlyph(app.glyph, app.color, Modifier.size(42.dp))
                        Spacer(Modifier.width(11.dp))
                        TileName(app.name, 15.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        RowTitle("Consoles")
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            HomeCatalog.consoles.forEach { console ->
                val light = rememberLiveLight(live(console))
                NeonTile(
                    width = SmallTile.first,
                    height = SmallTile.second,
                    shape = SmallTileShape,
                    color = console.color,
                    onClick = { onOpenConsole(console) },
                    onLongClick = { onLinkConsole(console) },
                    onFocus = { onFocusColor(null, console.name, console.color) },
                    behind = { LiveHalo(light, console.color, 8.dp) },
                    front = { LiveEdge(light, live(console), console.color, 8.dp) }
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp)) {
                        NeonGlyph(console.glyph, console.color, Modifier.size(26.dp))
                        Spacer(Modifier.weight(1f))
                        TileName(console.name, 11.5.sp)
                        BasicText(
                            text = "HDMI ${console.port}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.5.sp,
                                letterSpacing = 0.4.sp,
                                color = console.color
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllApps(
    apps: List<InstalledApp>,
    firstApp: FocusRequester,
    onFocusColor: (String?, String, Color) -> Unit,
    onOpen: (InstalledApp) -> Unit
) {
    // The grid leaves room around the tiles for a focused tile's glow; the offset keeps the
    // tiles themselves in line with the rest of the screen
    val room = 12.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalArrangement = Arrangement.spacedBy(Gap),
        contentPadding = PaddingValues(start = room, end = room, top = 7.dp + room, bottom = 48.dp),
        modifier = Modifier
            // The tab holder hands down the full screen width, which would stretch the six
            // columns past the right edge; the grid keeps the width of six small tiles
            .wrapContentWidth(Alignment.Start)
            .offset(x = Side - room, y = -room)
            .width(SmallTile.first * 6 + Gap * 5 + room * 2)
            .fillMaxHeight()
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            val known = app.known
            val color = known?.color ?: HomeCatalog.monogramColor(app.label)
            NeonTile(
                width = SmallTile.first,
                height = SmallTile.second,
                shape = SmallTileShape,
                color = color,
                onClick = { onOpen(app) },
                onFocus = { onFocusColor(app.packageName, app.name, color) },
                focusRequester = if (index == 0) firstApp else null
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp)) {
                    if (known != null) {
                        NeonGlyph(known.glyph, known.color, Modifier.size(26.dp))
                    } else {
                        Monogram(app.label, color, Modifier.size(26.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    TileName(app.name, 11.5.sp)
                }
            }
        }
    }
}

/** A tile on the home screen. It grows a little on focus, and [behind] and [front] can draw past its edges. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NeonTile(
    width: Dp,
    height: Dp,
    shape: RoundedCornerShape,
    color: Color,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    dimmed: Boolean = false,
    behind: @Composable BoxScope.() -> Unit = {},
    front: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 1.03f
            focused -> 1.07f
            else -> 1f
        },
        animationSpec = tween(durationMillis = if (focused && !pressed) 90 else 140, easing = FastOutSlowInEasing),
        label = "TileScale"
    )
    Box(
        modifier = modifier
            .size(width, height)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        behind()
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .matchParentSize()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                },
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = NeonPalette.Text,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = NeonPalette.Text,
                pressedContainerColor = Color.Transparent,
                pressedContentColor = NeonPalette.Text
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(0.5.dp, color.copy(alpha = 0.45f)), shape = shape),
                focusedBorder = Border(BorderStroke(1.5.dp, color), shape = shape),
                pressedBorder = Border(BorderStroke(1.5.dp, color), shape = shape)
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = color.copy(alpha = 0.6f), elevation = 14.dp)
            ),
            interactionSource = interaction
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TileFill, shape)
                    .graphicsLayer { alpha = if (dimmed) 0.5f else 1f }
            ) { content() }
        }
        front()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Pill(text: String, onClick: () -> Unit, onFocus: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x990D0721),
            contentColor = NeonPalette.Text,
            focusedContainerColor = Color(0xCC1A0B36),
            focusedContentColor = NeonPalette.Text
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.5.dp, NeonPalette.Text.copy(alpha = 0.18f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, NeonPalette.Magenta), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = NeonPalette.Magenta.copy(alpha = 0.55f), elevation = 12.dp))
    ) {
        BasicText(
            text = text,
            style = TextStyle(fontFamily = KanitFamily, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, color = NeonPalette.Text),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
        )
    }
}

/** Home or All apps. Pointing at a tab opens it, as on other TV home screens. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TabPill(text: String, selected: Boolean, focusRequester: FocusRequester, onFocus: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val line by animateFloatAsState(if (selected) 1f else 0f, tween(170, easing = FastOutSlowInEasing), label = "TabLine")
    Surface(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Pane,
            contentColor = NeonPalette.Text,
            focusedContainerColor = Pane,
            focusedContentColor = NeonPalette.Text
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, NeonPalette.Magenta), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = NeonPalette.Magenta.copy(alpha = 0.55f), elevation = 12.dp))
    ) {
        BasicText(
            text = text.uppercase(),
            style = TextStyle(
                fontFamily = KanitFamily,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = if (selected) NeonPalette.Text else Dim,
                shadow = if (selected) Shadow(NeonPalette.Magenta.copy(alpha = 0.55f), blurRadius = 12f) else null
            ),
            modifier = Modifier
                .padding(start = 11.dp, end = 11.dp, top = 4.dp, bottom = 7.dp)
                .drawWithCache {
                    // The neon line under the open tab, drawn from its middle outwards
                    val y = size.height + 2.dp.toPx()
                    val thick = 1.5.dp.toPx()
                    onDrawBehind {
                        if (line <= 0f) return@onDrawBehind
                        val half = size.width / 2f * line
                        val from = Offset(size.width / 2f - half, y)
                        val to = Offset(size.width / 2f + half, y)
                        drawLine(NeonPalette.Magenta.copy(alpha = 0.25f), from, to, strokeWidth = thick * 5f, cap = StrokeCap.Round)
                        drawLine(NeonPalette.Magenta, from, to, strokeWidth = thick, cap = StrokeCap.Round)
                    }
                }
        )
    }
}

@Composable
private fun RowTitle(text: String) {
    NeonText(
        text = text.uppercase(),
        style = TextStyle(
            fontFamily = KanitFamily,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
            color = NeonPalette.Text,
            shadow = Shadow(NeonPalette.Cyan.copy(alpha = 0.6f), blurRadius = 12f)
        )
    )
}

@Composable
private fun TileName(text: String, size: TextUnit) {
    BasicText(
        text = text,
        style = TextStyle(fontFamily = KanitFamily, fontWeight = FontWeight.Medium, fontSize = size, color = NeonPalette.Text),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** For an app without an icon of its own: its first letter in a neon ring. */
@Composable
private fun Monogram(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        NeonGlyph(Glyph.Ring, color, Modifier.fillMaxSize())
        BasicText(
            text = label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
            style = TextStyle(
                fontFamily = KanitFamily,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = color,
                shadow = Shadow(color.copy(alpha = 0.7f), blurRadius = 8f)
            )
        )
    }
}

/**
 * The glow around a console that is switched on. When it turns on, the light flickers to life
 * like a neon tube, then breathes slowly. Drawn behind the tile so only the part outside shows.
 */
@Composable
private fun BoxScope.LiveHalo(light: Animatable<Float, AnimationVector1D>, color: Color, corner: Dp) {
    Spacer(
        Modifier
            .matchParentSize()
            .drawWithCache {
                val radius = CornerRadius(corner.toPx())
                // Wider and fainter strokes around the edge, which read as one soft glow
                val halo = listOf(3f to 0.34f, 6f to 0.2f, 10f to 0.11f, 15f to 0.06f, 21f to 0.03f)
                    .map { (width, alpha) -> Stroke(width.dp.toPx()) to alpha }
                onDrawBehind {
                    val a = light.value
                    if (a <= 0f) return@onDrawBehind
                    halo.forEach { (stroke, alpha) ->
                        drawRoundRect(color, cornerRadius = radius, style = stroke, alpha = alpha * a)
                    }
                }
            }
    )
}

/** The lit edge of a console that is switched on, with its small white star in the corner. */
@Composable
private fun BoxScope.LiveEdge(light: Animatable<Float, AnimationVector1D>, on: Boolean, color: Color, corner: Dp) {
    val edge = remember(color) { lerp(color, Color.White, 0.2f) }
    Spacer(
        Modifier
            .matchParentSize()
            .drawWithCache {
                val radius = CornerRadius(corner.toPx())
                val line = Stroke(1.dp.toPx())
                val inner = Stroke(7.dp.toPx())
                onDrawBehind {
                    val a = light.value
                    if (a <= 0f) return@onDrawBehind
                    // A little light inside the edge too, as a tube lights the glass around it
                    drawRoundRect(color, cornerRadius = radius, style = inner, alpha = 0.12f * a)
                    drawRoundRect(edge, cornerRadius = radius, style = line, alpha = a)
                }
            }
    )
    LiveStar(on, Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp))
}

/** 0 when the console is off; when it comes on, the flicker of a neon tube and then a slow breath. */
@Composable
private fun rememberLiveLight(on: Boolean): Animatable<Float, AnimationVector1D> {
    val light = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (on) {
            light.animateTo(1f, keyframes {
                durationMillis = 700
                1f at 84
                0.25f at 154
                1f at 252
                0.6f at 364
                1f at 700
            })
            while (true) {
                light.animateTo(0.55f, tween(1400, easing = FastOutSlowInEasing))
                light.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
            }
        } else {
            light.animateTo(0f, tween(300))
        }
    }
    return light
}

/** A small four pointed white star that pops in a moment after the outline lights, then twinkles. */
@Composable
private fun LiveStar(on: Boolean, modifier: Modifier = Modifier) {
    val appear = remember { Animatable(0f) }
    // Where the star is in its twinkle, from 0 to 1
    val twinkle = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (on) {
            delay(350)
            launch { appear.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 420f)) }
            delay(1000)
            while (true) {
                twinkle.snapTo(0f)
                twinkle.animateTo(1f, tween(3400, easing = LinearEasing))
            }
        } else {
            twinkle.snapTo(0f)
            appear.animateTo(0f, tween(220))
        }
    }
    Spacer(
        modifier
            .size(15.dp)
            .graphicsLayer {
                val p = appear.value
                val t = twinkle.value
                // The twinkle: brightest, biggest and turned a little at 40%, back down by the end
                val grow: Float
                val turn: Float
                val glow: Float
                when {
                    t < 0.4f -> {
                        val e = ease(t / 0.4f)
                        grow = 0.88f + 0.2f * e; turn = 12f * e; glow = 0.8f + 0.2f * e
                    }
                    t < 0.6f -> {
                        val e = ease((t - 0.4f) / 0.2f)
                        grow = 1.08f - 0.08f * e; turn = 12f * (1f - e); glow = 1f - 0.05f * e
                    }
                    else -> {
                        val e = ease((t - 0.6f) / 0.4f)
                        grow = 1f - 0.12f * e; turn = 0f; glow = 0.95f - 0.15f * e
                    }
                }
                alpha = (p * 2.5f).coerceIn(0f, 1f) * glow
                val s = (0.3f + 0.7f * p) * grow
                scaleX = s
                scaleY = s
                rotationZ = -40f * (1f - p) + turn
            }
            .drawWithCache {
                val star = starPath(size)
                val glow = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.6f),
                    0.35f to Color.White.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension * 0.85f
                )
                onDrawBehind {
                    drawCircle(glow, radius = size.minDimension * 0.85f)
                    drawPath(star, Color.White)
                }
            }
    )
}

private fun ease(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The star from the prototype, on its 24 by 24 grid, fitted to [size]. */
private fun starPath(size: Size): Path {
    val k = size.minDimension / 24f
    fun x(v: Float) = v * k
    return Path().apply {
        moveTo(x(12f), x(1.5f))
        quadraticTo(x(13.4f), x(10.6f), x(22.5f), x(12f))
        quadraticTo(x(13.4f), x(13.4f), x(12f), x(22.5f))
        quadraticTo(x(10.6f), x(13.4f), x(1.5f), x(12f))
        quadraticTo(x(10.6f), x(10.6f), x(12f), x(1.5f))
        close()
    }
}

/** The time in big neon letters and the date under it, kept right by the TV's minute tick. */
@Composable
private fun HomeClock(resumes: Int) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(resumes) { now = System.currentTimeMillis() }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                now = System.currentTimeMillis()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val locale = Locale.getDefault()
    val is24 = DateFormat.is24HourFormat(context)
    val time = remember(now, is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", locale).format(Date(now)) }
    val date = remember(now, locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(Date(now)).uppercase(locale)
    }
    Column {
        NeonText(
            text = time,
            style = TextStyle(
                fontFamily = KanitFamily,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                fontSize = 60.sp,
                letterSpacing = (-0.5).sp,
                color = Color(0xFFFFF1F8),
                shadow = Shadow(NeonPalette.Magenta.copy(alpha = 0.85f), blurRadius = 26f)
            )
        )
        BasicText(
            text = date,
            style = TextStyle(
                fontFamily = KanitFamily,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                letterSpacing = 0.6.sp,
                color = Dim
            )
        )
    }
}

/**
 * The skyline in the colour of the tile in focus. The next colour is drawn over the one showing
 * and fades in, so the picture never dims on the way. Only a few colours are kept in memory.
 */
@Composable
private fun SkylineBackdrop(wallpaper: NeonWallpaper) {
    val context = LocalContext.current
    var below by remember { mutableStateOf<ImageBitmap?>(null) }
    var above by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(wallpaper) {
        val bitmap = SkylineCache.cached(wallpaper) ?: run {
            // Running along a row only loads the colour where it stops
            delay(140)
            withContext(Dispatchers.Default) { SkylineCache.load(context, wallpaper) }
        } ?: return@LaunchedEffect
        if (bitmap === above) return@LaunchedEffect
        // An unfinished fade leaves whichever picture shows most underneath
        below = if (fade.value >= 0.5f) above else below
        above = bitmap
        fade.snapTo(0f)
        fade.animateTo(1f, tween(480, easing = LinearEasing))
        below = null
    }
    Box(Modifier.fillMaxSize()) {
        below?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        above?.let {
            Image(
                it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }
            )
        }
    }
}

/** The skylines last shown, decoded once. Each is a full screen picture, so only three are kept. */
private object SkylineCache {
    private val pictures = object : LinkedHashMap<NeonWallpaper, ImageBitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<NeonWallpaper, ImageBitmap>?): Boolean = size > 3
    }

    @Synchronized
    fun cached(wallpaper: NeonWallpaper): ImageBitmap? = pictures[wallpaper]

    fun load(context: Context, wallpaper: NeonWallpaper): ImageBitmap? {
        cached(wallpaper)?.let { return it }
        val bitmap = runCatching {
            android.graphics.BitmapFactory.decodeResource(context.resources, wallpaper.drawable)
        }.getOrNull() ?: return null
        val image = bitmap.asImageBitmap()
        synchronized(this) { pictures[wallpaper] = image }
        return image
    }
}

/**
 * A neon panel listing the TV's inputs, each with a light that shows when something on it is
 * switched on. For a console it picks where the console is plugged in; from Inputs it opens one.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InputPicker(
    title: String,
    hint: String?,
    inputs: List<TvInput>,
    states: Map<String, Int>,
    selected: String?,
    labels: (TvInput) -> List<String>,
    noneLabel: String?,
    onPick: (TvInput?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
        val start = inputs.indexOfFirst { it.id == selected }.takeIf { it >= 0 } ?: 0
        // A long press on a console opens this while OK is still held, and letting go of it
        // must not pick a row: only a press that starts here counts
        val pressedHere = remember { booleanArrayOf(false) }
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.keyCode !in SelectKeys) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) pressedHere[0] = true
                    !pressedHere[0]
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .background(Color(0xF2160A2E), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NeonText(
                    text = title,
                    style = TextStyle(
                        fontFamily = KanitFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Italic,
                        fontSize = 18.sp,
                        color = NeonPalette.Text,
                        shadow = Shadow(NeonPalette.Magenta.copy(alpha = 0.6f), blurRadius = 16f)
                    )
                )
                if (inputs.isEmpty()) {
                    BasicText(
                        "This TV shows no inputs to pick from.",
                        style = TextStyle(fontFamily = KanitFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Dim)
                    )
                }
                inputs.forEachIndexed { index, input ->
                    val names = labels(input)
                    PickerRow(
                        title = input.label,
                        detail = names.joinToString(", ").ifEmpty { null },
                        on = TvInputs.isOn(states[input.id]),
                        child = input.parentId != null,
                        chosen = input.id == selected,
                        onClick = { onPick(input) },
                        modifier = if (index == start) Modifier.focusRequester(first) else Modifier
                    )
                }
                if (noneLabel != null) {
                    PickerRow(
                        title = noneLabel,
                        detail = null,
                        on = null,
                        child = false,
                        chosen = selected == null,
                        onClick = { onPick(null) },
                        modifier = if (inputs.isEmpty()) Modifier.focusRequester(first) else Modifier
                    )
                }
                if (hint != null) {
                    BasicText(
                        hint,
                        style = TextStyle(fontFamily = KanitFamily, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, color = Dim),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PickerRow(
    title: String,
    detail: String?,
    on: Boolean?,
    child: Boolean,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(9.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(38.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x14F6EAFF),
            contentColor = NeonPalette.Text,
            focusedContainerColor = Color(0x33FF3D9A),
            focusedContentColor = NeonPalette.Text
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, NeonPalette.Magenta), shape = shape)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = if (child) 26.dp else 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (on != null) {
                // Lit white when something on this input is switched on
                Box(
                    Modifier
                        .size(7.dp)
                        .background(if (on) Color.White else Dim.copy(alpha = 0.35f), CircleShape)
                )
                Spacer(Modifier.width(10.dp))
            }
            // The input's name gets all the room the names of the consoles on it leave
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = title,
                    style = TextStyle(fontFamily = KanitFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = NeonPalette.Text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (detail != null) {
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = detail,
                        style = TextStyle(fontFamily = KanitFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Dim),
                        maxLines = 1
                    )
                }
            }
            if (chosen) {
                Spacer(Modifier.width(8.dp))
                BasicText("✓", style = TextStyle(fontSize = 14.sp, color = NeonPalette.Magenta))
            }
        }
    }
}

/** Google's search where the TV has it, otherwise the assistant. */
private fun openSearch(context: Context) {
    val tries = listOf(
        Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH),
        Intent(Intent.ACTION_ASSIST),
        Intent(Intent.ACTION_WEB_SEARCH)
    )
    for (intent in tries) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: android.content.ActivityNotFoundException) {
            // Try the next one
        } catch (e: SecurityException) {
            // Try the next one
        }
    }
    Toast.makeText(context, "Search isn't available on this TV", Toast.LENGTH_SHORT).show()
}
