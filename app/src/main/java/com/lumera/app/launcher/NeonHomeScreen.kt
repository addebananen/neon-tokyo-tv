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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

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
    // The light on the floor under the tile in focus, off while the focus is anywhere else
    var spot by remember { mutableStateOf<Spot?>(null) }
    var spotOn by remember { mutableStateOf(false) }
    // The lights come on once when the home screen first shows: the clock flickers to life like
    // a neon sign and the tabs and tiles rise into place
    val ignite = remember { Animatable(0f) }
    val clockLight = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            clockLight.animateTo(1f, keyframes {
                durationMillis = 780
                0f at 90
                0.9f at 150
                0.12f at 215
                1f at 300
                0.5f at 400
                1f at 540
            })
        }
        ignite.animateTo(1f, tween(640, delayMillis = 160, easing = FastOutSlowInEasing))
    }
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
        SkySparkles()
        FloorLight(spot, spotOn, ignite = { ignite.value })

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = 30.dp),
            verticalAlignment = Alignment.Top
        ) {
            HomeClock(resumes, light = { clockLight.value }, ignite = { ignite.value })
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = ignite.value }
            ) {
                Pill("Search", Glyph.Search, onClick = { openSearch(context) }, onFocus = {
                    wallpaper = NeonWallpaper.TOKYO
                    spotOn = false
                })
                Pill("Inputs", Glyph.Hdmi, onClick = { showInputs = true }, onFocus = {
                    wallpaper = NeonWallpaper.OCEAN
                    spotOn = false
                })
                Pill("Settings", Glyph.Gear, onClick = { HomeApps.start(context, Intent(Settings.ACTION_SETTINGS)) }, onFocus = {
                    wallpaper = NeonWallpaper.TOKYO
                    spotOn = false
                })
            }
        }

        Row(
            modifier = Modifier
                .padding(start = Side, top = tabsTop)
                .graphicsLayer {
                    alpha = ignite.value
                    translationY = (1f - ignite.value) * 14.dp.toPx()
                }
                // Up from the tiles lands on the open tab, never on the other one, which would open it
                .focusProperties { enter = { if (tab == HomeTab.Home) homeTab else appsTab } }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TabPill("Home", tab == HomeTab.Home, homeTab, onFocus = {
                tab = HomeTab.Home
                spotOn = false
            }, onClick = { focusFirst++ })
            TabPill("All apps", tab == HomeTab.Apps, appsTab, onFocus = {
                tab = HomeTab.Apps
                spotOn = false
            }, onClick = { focusFirst++ })
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(180, delayMillis = 40)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 14 })
                    .togetherWith(fadeOut(tween(110)))
            },
            label = "HomeTab",
            modifier = Modifier
                .padding(top = rowsTop)
                .fillMaxSize()
                // Rising by layout rather than by layer, so the light under the focused tile rises with it
                .offset { IntOffset(0, ((1f - ignite.value) * 26.dp.toPx()).roundToInt()) }
                .graphicsLayer { alpha = ignite.value }
        ) { shown ->
            when (shown) {
                HomeTab.Home -> HomeRows(
                    installed = installed,
                    live = { console -> TvInputs.isOn(consoleInputs[console]?.let { states[it.id] }) },
                    firstFavourite = firstFavourite,
                    onFocusColor = { pkg, title, color -> wallpaper = NeonWallpaper.forCard(pkg, title, color.toArgb()) },
                    onSpot = {
                        spot = it
                        spotOn = true
                    },
                    onOpenApp = { HomeApps.open(context, it) },
                    onOpenConsole = { openConsole(it) },
                    onLinkConsole = { linking = it }
                )
                HomeTab.Apps -> AllApps(
                    apps = apps,
                    firstApp = firstApp,
                    onFocusColor = { pkg, title, color ->
                        wallpaper = NeonWallpaper.forCard(pkg, title, color.toArgb())
                        spotOn = false
                    },
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
    onSpot: (Spot) -> Unit,
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
                    dimmed = app !in installed,
                    onFocusRect = { onSpot(Spot(it, app.color)) }
                ) { focused ->
                    TileScene(app, focused, TileShape)
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
                    onFocusRect = { onSpot(Spot(it, console.color)) },
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

/**
 * A tile on the home screen. It pops forward on focus and its edge lights up like a neon tube;
 * [behind] and [front] can draw past its edges, and [onFocusRect] hears where it is on the
 * screen when the focus lands on it.
 */
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
    onFocusRect: ((Rect) -> Unit)? = null,
    behind: @Composable BoxScope.() -> Unit = {},
    front: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // A spring that overshoots a touch, so the tile pops when the focus lands on it
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 1.03f
            focused -> 1.07f
            else -> 1f
        },
        animationSpec = if (focused && !pressed) spring(dampingRatio = 0.55f, stiffness = 650f) else spring(stiffness = 900f),
        label = "TileScale"
    )
    val tube by animateFloatAsState(if (focused) 1f else 0f, tween(if (focused) 150 else 240), label = "TileTube")
    // Where the tile was laid out and last said it was, kept out of state so that moving it
    // never recomposes anything. A focused tile that moves, as its row slides in, says so again.
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val told = remember { arrayOfNulls<Rect>(1) }
    fun tellWhere(always: Boolean) {
        val coordinates = placed[0] ?: return
        if (onFocusRect == null || !coordinates.isAttached) return
        val bounds = coordinates.boundsInRoot()
        if (always || bounds != told[0]) {
            told[0] = bounds
            onFocusRect(bounds)
        }
    }
    Box(
        modifier = modifier
            .size(width, height)
            .zIndex(if (focused) 1f else 0f)
            .then(
                if (onFocusRect != null) {
                    Modifier.onGloballyPositioned {
                        placed[0] = it
                        if (focused) tellWhere(always = false)
                    }
                } else {
                    Modifier
                }
            )
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
                    if (it.isFocused) {
                        onFocus()
                        tellWhere(always = true)
                    }
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
            ) { content(focused) }
        }
        front()
        TubeCore(shape, color, lit = { tube })
    }
}

/**
 * The white hot core of a focused tile's edge, drawn down the middle of its coloured line, so
 * the edge reads as a lit neon tube rather than a painted border.
 */
@Composable
private fun BoxScope.TubeCore(shape: RoundedCornerShape, color: Color, lit: () -> Float) {
    val core = remember(color) { lerp(color, Color.White, 0.62f) }
    Spacer(
        Modifier
            .matchParentSize()
            .drawWithCache {
                // The tile's border is 1.5 dp wide and drawn inside its edge
                val inset = 0.75.dp.toPx()
                val radius = (shape.topStart.toPx(size, this) - inset).coerceAtLeast(0f)
                val line = Stroke(0.7.dp.toPx())
                val topLeft = Offset(inset, inset)
                val inner = Size(size.width - 2 * inset, size.height - 2 * inset)
                onDrawBehind {
                    val a = lit()
                    if (a <= 0f) return@onDrawBehind
                    drawRoundRect(core, topLeft, inner, CornerRadius(radius), style = line, alpha = 0.9f * a)
                }
            }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Pill(text: String, glyph: Glyph, onClick: () -> Unit, onFocus: () -> Unit) {
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
        Row(
            modifier = Modifier.padding(start = 9.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonGlyph(glyph, NeonPalette.Cyan, Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            BasicText(
                text = text,
                style = TextStyle(fontFamily = KanitFamily, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, color = NeonPalette.Text)
            )
        }
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
    Row(verticalAlignment = Alignment.CenterVertically) {
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
        Spacer(Modifier.width(10.dp))
        // A thin neon rule that runs out from the title and fades away
        Spacer(
            Modifier
                .width(150.dp)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(NeonPalette.Cyan.copy(alpha = 0.55f), Color.Transparent)))
        )
    }
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

/** How long a tile has been in focus, in seconds, counting on from where it stopped last time. */
@Composable
private fun rememberFocusClock(focused: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                seconds.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    return seconds
}

private enum class Scene { Video, Record, Equalizer, Film }

/**
 * A little scene behind a big tile's name about what the app is for: a video playing for
 * YouTube, a record turning for YT Music, an equalizer for Spotify and a film strip for Stremio.
 * It holds still while the tile rests and plays while it is in focus. Only the drawing changes,
 * so it never recomposes or lays anything out again.
 */
@Composable
private fun BoxScope.TileScene(app: KnownApp, focused: Boolean, shape: RoundedCornerShape) {
    val scene = when (app) {
        HomeCatalog.YouTube -> Scene.Video
        HomeCatalog.YtMusic -> Scene.Record
        HomeCatalog.Spotify -> Scene.Equalizer
        HomeCatalog.Stremio -> Scene.Film
        else -> return
    }
    val clock = rememberFocusClock(focused)
    val lit = animateFloatAsState(if (focused) 1f else 0f, tween(320, easing = FastOutSlowInEasing), label = "SceneLight")
    val time = { clock.value }
    val light = { lit.value }
    Spacer(
        Modifier
            .matchParentSize()
            .clip(shape)
            .drawWithCache {
                when (scene) {
                    Scene.Video -> videoScene(app.color, time, light)
                    Scene.Record -> recordScene(app.color, time, light)
                    Scene.Equalizer -> equalizerScene(app.color, time, light)
                    Scene.Film -> filmScene(app.color, time, light)
                }
            }
    )
}

/** How strongly a scene shows: half lit while its tile rests, fully lit in focus. */
private fun sceneStrength(lit: Float) = 0.55f + 0.45f * lit

/** A screen's glow with scanlines drifting down it, and the progress bar of the video playing. */
private fun CacheDrawScope.videoScene(color: Color, time: () -> Float, lit: () -> Float): DrawResult {
    val w = size.width
    val h = size.height
    val glow = Brush.radialGradient(
        0f to color.copy(alpha = 0.28f),
        1f to Color.Transparent,
        center = Offset(w * 0.9f, h * 0.42f),
        radius = h * 1.05f
    )
    // The scanlines fade out towards the name on the left
    val scanline = Brush.horizontalGradient(0.42f to Color.Transparent, 1f to color.copy(alpha = 0.17f), startX = 0f, endX = w)
    val gap = 4.dp.toPx()
    val drift = 5.dp.toPx()
    val from = w * 0.42f
    val to = w - 12.dp.toPx()
    val barY = h - 10.dp.toPx()
    val thick = 2.dp.toPx()
    val head = lerp(color, Color.White, 0.5f)
    return onDrawBehind {
        val strength = sceneStrength(lit())
        val t = time()
        drawRect(glow, alpha = strength)
        var y = (t * drift) % gap
        while (y < h) {
            drawLine(scanline, Offset(0f, y), Offset(w, y), strokeWidth = 1f, alpha = strength)
            y += gap
        }
        // A seven second video on a loop, a third of the way in when it first shows
        val played = (0.32f + t / 7f) % 1f
        val x = from + (to - from) * played
        drawLine(Color.White, Offset(from, barY), Offset(to, barY), thick, StrokeCap.Round, alpha = 0.16f * strength)
        drawLine(color, Offset(from, barY), Offset(x, barY), thick, StrokeCap.Round, alpha = strength)
        drawCircle(color, radius = 6.dp.toPx(), center = Offset(x, barY), alpha = 0.3f * strength)
        drawCircle(head, radius = 3.dp.toPx(), center = Offset(x, barY), alpha = strength)
    }
}

/** A record at the right edge with the light catching its grooves, turning at 33 rpm. */
private fun CacheDrawScope.recordScene(color: Color, time: () -> Float, lit: () -> Float): DrawResult {
    val w = size.width
    val h = size.height
    val radius = h * 0.66f
    val center = Offset(w - radius * 0.15f, h * 0.5f)
    val groove = Stroke(0.7.dp.toPx())
    val step = 3.2.dp.toPx()
    val grooves = generateSequence(radius * 0.42f) { it + step }.takeWhile { it < radius - 1.dp.toPx() }.toList()
    val vinyl = Brush.radialGradient(0f to Color(0xFF1C0C30), 1f to Color(0xFF0B0516), center = center, radius = radius)
    val glow = Brush.radialGradient(0f to color.copy(alpha = 0.32f), 1f to Color.Transparent, center = center, radius = radius * 1.4f)
    val disc = Size(radius * 2f, radius * 2f)
    val corner = Offset(center.x - radius, center.y - radius)
    val rim = lerp(color, Color.White, 0.35f)
    val label = Stroke(1.dp.toPx())
    val hole = 2.dp.toPx()
    return onDrawBehind {
        val strength = sceneStrength(lit())
        drawCircle(glow, radius = radius * 1.4f, center = center, alpha = strength)
        drawCircle(vinyl, radius = radius, center = center)
        grooves.forEach { r -> drawCircle(color, radius = r, center = center, style = groove, alpha = 0.15f * strength) }
        // Two soft highlights opposite each other, turning with the record
        val turn = time() * 200f
        for (side in 0..1) {
            val start = turn + side * 180f
            drawArc(Color.White, start - 14f, 28f, useCenter = true, topLeft = corner, size = disc, alpha = 0.045f * strength)
            drawArc(Color.White, start - 6f, 12f, useCenter = true, topLeft = corner, size = disc, alpha = 0.06f * strength)
        }
        drawCircle(color, radius = radius * 0.3f, center = center, alpha = 0.78f * strength)
        drawCircle(rim, radius = radius * 0.3f, center = center, style = label, alpha = strength)
        drawCircle(Color(0xFF0B0516), radius = hole, center = center)
    }
}

/** An equalizer whose bars dance while the tile is in focus and settle down when it rests. */
private fun CacheDrawScope.equalizerScene(color: Color, time: () -> Float, lit: () -> Float): DrawResult {
    val w = size.width
    val h = size.height
    val bars = 9
    val barW = 3.5.dp.toPx()
    val gapW = 3.dp.toPx()
    val right = w - 13.dp.toPx()
    val left = right - bars * barW - (bars - 1) * gapW
    val base = h - 15.dp.toPx()
    val tallest = h * 0.6f
    val fill = Brush.verticalGradient(
        0f to lerp(color, Color.White, 0.3f),
        1f to color.copy(alpha = 0.35f),
        startY = base - tallest,
        endY = base
    )
    val rest = floatArrayOf(0.3f, 0.48f, 0.66f, 0.85f, 0.72f, 0.56f, 0.78f, 0.44f, 0.28f)
    val round = CornerRadius(barW / 2f)
    val gapBelow = 2.dp.toPx()
    return onDrawBehind {
        val l = lit()
        val strength = sceneStrength(l)
        val t = time()
        for (i in 0 until bars) {
            // Every bar on two beats of its own
            val beat = abs(sin(t * (2.1f + i * 0.37f) + i * 1.3f)) * (0.62f + 0.38f * sin(t * (0.9f + i * 0.23f) + i))
            val dance = (0.22f + 0.78f * beat).coerceIn(0.12f, 1f)
            val level = rest[i] + (dance - rest[i]) * l
            val barH = tallest * level
            val x = left + i * (barW + gapW)
            drawRoundRect(fill, Offset(x, base - barH), Size(barW, barH), round, alpha = strength)
            // Its reflection on the glass under it
            drawRoundRect(color, Offset(x, base + gapBelow), Size(barW, barH * 0.22f), round, alpha = 0.16f * strength)
        }
    }
}

/** A film strip running up through a projector's beam. */
private fun CacheDrawScope.filmScene(color: Color, time: () -> Float, lit: () -> Float): DrawResult {
    val w = size.width
    val h = size.height
    val band = 24.dp.toPx()
    val pivot = Offset(w * 0.8f, h * 0.52f)
    val from = w * 0.44f
    val to = w * 1.25f
    val top = pivot.y - band / 2f
    val beam = Brush.radialGradient(0f to color.copy(alpha = 0.3f), 1f to Color.Transparent, center = Offset(w, 0f), radius = h * 1.1f)
    val strip = Brush.horizontalGradient(0f to Color.Transparent, 0.22f to Color(0xF20E0619), startX = from, endX = to)
    val edge = Brush.horizontalGradient(0f to Color.Transparent, 0.3f to color.copy(alpha = 0.6f), startX = from, endX = to)
    val hole = Size(2.6.dp.toPx(), 3.2.dp.toPx())
    val holeStep = 6.5.dp.toPx()
    val holeRound = CornerRadius(0.8.dp.toPx())
    val holeInset = 2.dp.toPx()
    val frame = Size(14.dp.toPx(), band - 14.dp.toPx())
    val frameStep = 17.dp.toPx()
    val frameRound = CornerRadius(1.5.dp.toPx())
    val frameTop = top + 7.dp.toPx()
    val speed = 16.dp.toPx()
    val edgeLine = 0.8.dp.toPx()
    // The left end of the strip fades out towards the name
    val fadeWidth = w * 0.22f
    return onDrawBehind {
        val strength = sceneStrength(lit())
        val run = time() * speed
        drawRect(beam, alpha = strength)
        rotate(-16f, pivot) {
            drawRect(strip, Offset(from, top), Size(to - from, band))
            drawLine(edge, Offset(from, top), Offset(to, top), strokeWidth = edgeLine, alpha = strength)
            drawLine(edge, Offset(from, top + band), Offset(to, top + band), strokeWidth = edgeLine, alpha = strength)
            var x = from - run % holeStep
            while (x < to) {
                val a = 0.6f * ((x - from) / fadeWidth).coerceIn(0f, 1f) * strength
                if (a > 0f) {
                    drawRoundRect(color, Offset(x, top + holeInset), hole, holeRound, alpha = a)
                    drawRoundRect(color, Offset(x, top + band - holeInset - hole.height), hole, holeRound, alpha = a)
                }
                x += holeStep
            }
            // Every third frame is lit, and the frames keep their places as the strip runs
            val first = (run / frameStep).toInt()
            var k = 0
            var fx = from - run % frameStep
            while (fx < to) {
                val a = (if ((first + k) % 3 == 0) 0.36f else 0.15f) * ((fx - from) / fadeWidth).coerceIn(0f, 1f) * strength
                if (a > 0f) drawRoundRect(color, Offset(fx, frameTop), frame, frameRound, alpha = a)
                fx += frameStep
                k++
            }
        }
    }
}

/** The tile in focus on the Home tab: where it is on the screen, and its colour. */
private data class Spot(val bounds: Rect, val color: Color)

/**
 * The light the tile in focus throws on the floor of the skyline, and its reflection, the way
 * a neon sign lights a wet street at night. It glides from tile to tile with the focus.
 */
@Composable
private fun FloorLight(spot: Spot?, on: Boolean, ignite: () -> Float) {
    val target = spot ?: return
    val center by animateOffsetAsState(
        Offset(target.bounds.center.x, target.bounds.bottom),
        spring(dampingRatio = 0.86f, stiffness = 240f),
        label = "LightSpot"
    )
    val width by animateFloatAsState(target.bounds.width, spring(dampingRatio = 0.86f, stiffness = 240f), label = "LightWidth")
    val color by animateColorAsState(target.color, tween(360), label = "LightColor")
    val strength by animateFloatAsState(if (on) 1f else 0f, tween(if (on) 240 else 420), label = "LightOn")
    Spacer(
        Modifier
            .fillMaxSize()
            // A layer of its own, so a gliding light redraws only itself
            .graphicsLayer()
            .drawBehind {
                val a = strength * ignite()
                if (a <= 0f) return@drawBehind
                val c = center
                val pool = Offset(c.x, c.y + 12.dp.toPx())
                val rx = width * 0.72f
                val ry = 20.dp.toPx()
                // A round pool of light squashed flat, as it lies on the floor
                scale(scaleX = 1f, scaleY = ry / rx, pivot = pool) {
                    drawCircle(
                        Brush.radialGradient(
                            0f to color.copy(alpha = 0.34f),
                            0.55f to color.copy(alpha = 0.12f),
                            1f to Color.Transparent,
                            center = pool,
                            radius = rx
                        ),
                        radius = rx,
                        center = pool,
                        alpha = a
                    )
                }
                // The tile's reflection, fading down the floor
                val top = c.y + 3.dp.toPx()
                val depth = 32.dp.toPx()
                val side = 6.dp.toPx()
                drawRect(
                    Brush.verticalGradient(0f to color.copy(alpha = 0.16f), 1f to Color.Transparent, startY = top, endY = top + depth),
                    topLeft = Offset(c.x - width / 2f + side, top),
                    size = Size((width - 2 * side).coerceAtLeast(0f), depth),
                    alpha = a
                )
            }
    )
}

/**
 * Every few seconds a star in the sky twinkles, and once in a while a shooting star crosses it.
 * In between nothing moves, so the home screen asks for no frames while it rests.
 */
@Composable
private fun SkySparkles() {
    val twinkle = remember { Animatable(0f) }
    val shooting = remember { Animatable(0f) }
    // Where they are, as fractions of the screen
    var star by remember { mutableStateOf(Offset(0.5f, 0.1f)) }
    var streak by remember { mutableStateOf(Offset(0.5f, 0.06f)) }
    LaunchedEffect(Unit) {
        val random = Random(System.nanoTime())
        launch {
            while (true) {
                delay(1800L + random.nextLong(3600L))
                star = skySpot(random)
                twinkle.snapTo(0f)
                twinkle.animateTo(1f, tween(1500, easing = LinearEasing))
            }
        }
        launch {
            delay(8000L + random.nextLong(10000L))
            while (true) {
                // From high in the middle of the sky, down to the left between the clock and the sun
                streak = Offset(0.4f + random.nextFloat() * 0.22f, 0.03f + random.nextFloat() * 0.09f)
                shooting.snapTo(0f)
                shooting.animateTo(1f, tween(950, easing = LinearEasing))
                delay(24000L + random.nextLong(36000L))
            }
        }
    }
    Spacer(
        Modifier
            .fillMaxSize()
            // A layer of its own, so a twinkle redraws only itself
            .graphicsLayer()
            .drawWithCache {
                val starSize = 11.dp.toPx()
                val shape = starPath(Size(starSize, starSize))
                val middle = Offset(starSize / 2f, starSize / 2f)
                val travel = 270.dp.toPx()
                val tail = 80.dp.toPx()
                val direction = Offset(-0.9f, 0.42f)
                val trail = 1.6.dp.toPx()
                val halo = 3.dp.toPx()
                val spark = 1.2.dp.toPx()
                onDrawBehind {
                    val t = twinkle.value
                    if (t > 0f && t < 1f) {
                        val p = sin(t * PI.toFloat())
                        val c = Offset(star.x * size.width, star.y * size.height)
                        drawCircle(
                            Brush.radialGradient(0f to Color.White.copy(alpha = 0.5f), 1f to Color.Transparent, center = c, radius = starSize * 1.4f),
                            radius = starSize * 1.4f,
                            center = c,
                            alpha = p
                        )
                        withTransform({
                            translate(c.x - middle.x, c.y - middle.y)
                            scale(0.4f + 0.6f * p, 0.4f + 0.6f * p, pivot = middle)
                            rotate(t * 40f, pivot = middle)
                        }) {
                            drawPath(shape, Color(0xFFFFF4FB), alpha = p)
                        }
                    }
                    val s = shooting.value
                    if (s > 0f && s < 1f) {
                        val rising = (s / 0.15f).coerceAtMost(1f)
                        val a = rising * ((1f - s) / 0.4f).coerceAtMost(1f)
                        val start = Offset(streak.x * size.width, streak.y * size.height)
                        val head = start + direction * (travel * s)
                        val end = head - direction * (tail * (0.4f + 0.6f * rising))
                        drawLine(
                            Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f)), start = end, end = head),
                            end,
                            head,
                            strokeWidth = trail,
                            cap = StrokeCap.Round,
                            alpha = a
                        )
                        drawCircle(Color.White, radius = halo, center = head, alpha = 0.35f * a)
                        drawCircle(Color.White, radius = spark, center = head, alpha = a)
                    }
                }
            }
    )
}

/** A place in the open sky: clear of the clock, the buttons at the top and the sun. */
private fun skySpot(random: Random): Offset {
    while (true) {
        val x = 0.3f + random.nextFloat() * 0.66f
        val y = 0.03f + random.nextFloat() * 0.3f
        val buttons = x > 0.72f && y < 0.15f
        val sun = x > 0.55f && y > 0.2f
        if (!buttons && !sun) return Offset(x, y)
    }
}

/**
 * The time in big neon letters and the date under it, kept right by the TV's minute tick.
 * [light] is the sign's power, which flickers on when the home screen first shows.
 */
@Composable
private fun HomeClock(resumes: Int, light: () -> Float, ignite: () -> Float) {
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
            ),
            // An unlit tube still shows a little. The alpha goes on each stroke rather than on an
            // offscreen copy of the text, which would cut the glow off at the edges of the text
            modifier = Modifier.graphicsLayer {
                alpha = 0.14f + 0.86f * light()
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
            glow = light
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
            ),
            modifier = Modifier.graphicsLayer { alpha = ignite() }
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
