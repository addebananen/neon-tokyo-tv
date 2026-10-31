package com.lumera.app.launcher

import android.app.ActivityOptions
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.lumera.app.domain.ContinueEntry
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
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
import com.lumera.app.data.update.UpdateState
import com.lumera.app.projectivy.NeonWallpaper
import com.lumera.app.overlay.ConsoleSession
import com.lumera.app.ui.components.NeonPalette
import com.lumera.app.ui.components.NeonText
import com.lumera.app.ui.components.neonPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

private enum class HomeTab { Home, Apps, Sources }


private val BigTile = 190.dp to 82.dp
private val SmallTile = 116.dp to 69.dp
private val Gap = 13.dp
private val Side = 55.dp

// On glass: the wide tiles on Home, square icons as on Apple TV, consoles on Home, and the room
// for an app's name under its tile
private val GlassWide = 176.dp to 94.dp
private val GlassSquare = 88.dp to 88.dp
private val HomeConsole = 128.dp to 68.dp
private val CaptionRoom = 24.dp
private fun GlassGap(square: Boolean): Dp = if (square) 22.dp else 15.dp

/** The light of a console that is on, on glass. */
private val LiveGreen = Color(0xFF34D27B)

/** The light across glass: quick off the mark, gliding out. */
private val SheenEasing = CubicBezierEasing(0.3f, 0.6f, 0.3f, 1f)

/** The made-up device a preview's Bluetooth card shows. */
private const val PREVIEW_DEVICE = "preview"

/** The keys a TV Surface clicks on. */
private val SelectKeys = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
private val ArrowKeys = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)

/** Columns of the All apps grid; Up and Down move a lifted app by a whole row. */
private const val AppColumns = 6

/** Columns of the consoles on the Sources tab, which Up and Down move a lifted console across. */
private const val ConsoleColumns = 3

// Holding OK lifts a tile to move it: the light starts round its edge after [HoldShowsMs] and
// has gone all the way round at [HoldMs]
private const val HoldShowsMs = 400L
private const val HoldMs = 3_000L

/** The tile that is lifted to be moved, by its row and key (an app's package, a console's id). */
private data class Lifted(val row: HomeOrder.Row, val key: String)

/**
 * The Neon Tokyo home screen: the clock, Search, Inputs and Settings at the top, and three tabs.
 * Home has the big tiles for YouTube, YT Music, Spotify and Neon Tokyo; All apps has every app on
 * the TV with its neon icon; Källor (Sources) has the consoles, each with the HDMI number Adrian
 * gave it.
 * A console that is switched on gets a glowing outline that breathes and a small white star
 * that twinkles in its corner. The skyline behind takes the colour of the tile in focus.
 * Holding OK on a tile for three seconds lifts it: the arrows move it along its row, or round
 * the grid of All apps or Källor, and OK or Back puts it down there, where it stays.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NeonHome(homePresses: Int, switcherOpens: Int = 0, onLeave: (() -> Unit)? = null, previewStyle: HomeStyle? = null, previewIcons: IconPack? = null, previewTint: IconTint? = null, previewTiles: List<String>? = null, previewGlass: HomeGlass? = null, previewLayout: Set<String>? = null, previewFocus: GlassFocus? = null, previewWake: String? = null, previewBluetooth: String? = null, previewBluetoothList: String? = null, previewSwitcher: String? = null) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val homeProfiles = remember { HomeProfiles.get(appContext) }
    val profileList by homeProfiles.profiles.collectAsState()
    val activeProfileId by homeProfiles.activeId.collectAsState()
    val profile = homeProfiles.active(profileList, activeProfileId)
    val style = previewStyle ?: profile.style
    // A preview of another style shows it as it comes; the profile's own choices are for its own
    val own = previewStyle == null || previewStyle == profile.style
    val glass = previewGlass ?: if (own) profile.glassLook else style.glass
    val watchDown = previewLayout?.let { "down" in it } ?: if (own) profile.watchDown else style.rowsDown
    val consolesOnHome = previewLayout?.let { "consoles" in it } ?: if (own) profile.showConsoles else style.consolesOnHome
    val squareIcons = previewLayout?.let { "square" in it } ?: if (own) profile.square else style.squareIcons
    val namesAlways = previewLayout?.let { "names" in it } ?: if (own) profile.namesAlways else style.namesAlways
    val focusMotion = previewFocus ?: if (own) profile.focusMotion else GlassFocus.Glow
    val look = remember(style, glass, focusMotion) { style.look.inGlass(glass).copy(focusMotion = focusMotion) }
    val inputs = remember { TvInputs(appContext) }
    val links = remember { ConsoleLinks(appContext) }
    val sounds = remember { HomeSounds(appContext) }
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    val updates = remember { homeUpdates(appContext) }
    val bt = remember { HomeBluetooth(appContext) }
    // What the Neon Tokyo profile of the same name has left to watch, for the row under the apps
    val watching = rememberHomeWatching(appContext, profile.name)
    val updateState by updates.state.collectAsState()
    var showUpdates by remember { mutableStateOf(false) }
    var showMotion by remember { mutableStateOf(false) }
    remember { HomeMotion.load(appContext) }
    // A quiet look for a newer version once the home screen is up; the pill at the top says so
    LaunchedEffect(Unit) {
        delay(4000)
        if (updates.state.value is UpdateState.Idle) updates.checkForUpdate()
    }
    // Only a move the remote made ticks: not the focus the screen sets itself as it opens
    val lastArrow = remember { longArrayOf(0L) }
    fun focusMoved() {
        if (SystemClock.uptimeMillis() - lastArrow[0] < 400) sounds.move()
    }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var installed by remember { mutableStateOf(HomeCatalog.favourites.toSet()) }
    var appsVersion by remember { mutableIntStateOf(0) }
    var resumes by remember { mutableIntStateOf(0) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        // Back from an app, the screen comes first: Bluetooth and the list of apps, which ask the
        // TV a lot and only matter a moment later, wait until the home screen has settled
        val later = android.os.Handler(android.os.Looper.getMainLooper())
        var firstStart = true
        val settled = Runnable {
            bt.start()
            appsVersion++
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    inputs.start()
                    if (firstStart) {
                        firstStart = false
                        settled.run()
                    } else {
                        later.postDelayed(settled, RETURN_SETTLE_MS)
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    resumes++
                }
                Lifecycle.Event.ON_STOP -> {
                    later.removeCallbacks(settled)
                    inputs.stop()
                    bt.stop()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            later.removeCallbacks(settled)
            lifecycle.removeObserver(observer)
            inputs.stop()
            bt.stop()
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
    // Where each tab lies in its row, and whether the focus is up on them, for the mark that glides
    val tabSpots = remember { HomeTab.entries.associateWith { mutableStateOf<TabSpot?>(null) } }
    var onTabs by remember { mutableStateOf(false) }
    // How tall the rows of Home are, and how far down they and the tabs sit
    var homeRowsHeight by remember { mutableIntStateOf(0) }
    val drop = remember { Animatable(0f) }
    val dropSettled = remember { booleanArrayOf(false) }
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
            if (!look.glowingText) {
                clockLight.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                return@launch
            }
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
    var showProfiles by remember { mutableStateOf(false) }

    val order = remember { HomeOrder(appContext) }
    // The Home tiles: any apps Adrian picks, by package, in his order; the four favourites until then
    var homeKeys by remember { mutableStateOf(order.load(HomeOrder.Row.Favourites)) }
    var consoleOrder by remember { mutableStateOf(order.load(HomeOrder.Row.Consoles)) }
    var appOrder by remember { mutableStateOf(order.load(HomeOrder.Row.Apps)) }
    var hiddenApps by remember { mutableStateOf(order.hidden()) }
    var showHidden by remember { mutableStateOf(false) }
    val slots = remember(homeKeys, apps, previewTiles) { homeSlots(previewTiles ?: homeKeys, apps) }
    val consoles = remember(consoleOrder) { HomeOrder.arrange(HomeCatalog.consoles, consoleOrder) { it.id } }
    // Hidden apps stay on the TV but out of the list, and out of the apps to put on Home
    val orderedApps = remember(apps, appOrder, hiddenApps) { HomeOrder.arrange(apps.filter { it.packageName !in hiddenApps }, appOrder) { it.packageName } }
    val hiddenList = remember(apps, hiddenApps) { apps.filter { it.packageName in hiddenApps } }
    // A console switched on while the home screen shows gets its card; those already on when
    // the inputs are first read, or still on from before an app was opened, do not
    var woke by remember { mutableStateOf<Console?>(null) }
    val wasOn = remember { arrayOfNulls<Set<String>>(1) }
    LaunchedEffect(states, consoleInputs) {
        if (inputList.isEmpty()) return@LaunchedEffect
        val on = HomeCatalog.consoles.filter { TvInputs.isOn(consoleInputs[it]?.let { input -> states[input.id] }) }.map { it.id }.toSet()
        val before = wasOn[0]
        wasOn[0] = on
        val fresh = before?.let { ConsoleWake.woke(consoles.map { it.id }, it, on) }
        when {
            fresh != null -> woke = HomeCatalog.consoles.first { it.id == fresh }
            woke?.id?.let { it !in on } == true -> woke = null
        }
    }
    LaunchedEffect(woke) { if (woke != null) sounds.wake() }

    // A paired device of this profile switched on near the TV gets its card in the corner; one
    // nobody here took as theirs never does, so a sister's home screen doesn't offer his headphones
    val btDevices by bt.devices.collectAsState()
    val btNearby by bt.nearby.collectAsState()
    val btOwners by bt.owners.collectAsState()
    val btMine = remember(btOwners, profile.id) { BtRules.mine(btOwners, profile.id) }
    LaunchedEffect(btMine) { bt.watch(btMine) }
    var btCard by remember { mutableStateOf<BtDevice?>(null) }
    val btOn by bt.on.collectAsState()
    val btPermitted by bt.permitted.collectAsState()
    // The Bluetooth corner: this profile's devices with Connect, from Down in the app switcher
    var btCorner by remember { mutableStateOf(false) }
    // Home pressed twice opens the app switcher, as on Apple TV
    var switcher by remember { mutableStateOf(false) }
    val previewCorner = remember(previewBluetoothList) {
        previewBluetoothList?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.mapIndexed { i, name ->
            BtDevice("$PREVIEW_DEVICE$i", name, BtRules.previewKind(name), connected = false)
        }
    }
    LaunchedEffect(previewCorner) { if (previewCorner != null) btCorner = true }
    val ownCorner = remember(btDevices, btOwners, profile.id) { BtRules.corner(btDevices, btOwners, profile.id) }
    val cornerDevices = previewCorner ?: ownCorner
    var showBluetooth by remember { mutableStateOf(false) }
    val btBefore = remember { arrayOf<Set<String>>(emptySet()) }
    LaunchedEffect(btNearby, btMine, btDevices) {
        val connected = btDevices.filter { it.connected }.map { it.address }.toSet()
        val fresh = BtRules.arrived(btDevices.map { it.address }, btBefore[0], btNearby, btMine, connected, bt.snoozes(), bt.clock())
        btBefore[0] = btNearby
        val shown = btCard
        when {
            // Do Not Disturb in the Control Center keeps the card away
            fresh != null && !com.lumera.app.control.Quiet.on(context) -> btCard = btDevices.first { it.address == fresh }
            shown != null && shown.address != PREVIEW_DEVICE &&
                (shown.address !in btMine || shown.address in connected || shown.address !in btNearby) -> btCard = null
        }
    }
    LaunchedEffect(btCard?.address) { if (btCard != null) sounds.wake() }
    LaunchedEffect(previewBluetooth) {
        previewBluetooth?.let { btCard = BtDevice(PREVIEW_DEVICE, it, BtRules.previewKind(it), connected = false) }
    }
    fun connectBluetooth(device: BtDevice) {
        if (device.address.startsWith(PREVIEW_DEVICE)) return
        val result = bt.connect(device.address)
        btConnectNote(result, device.name)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    fun disconnectBluetooth(device: BtDevice) {
        if (device.address.startsWith(PREVIEW_DEVICE)) return
        val result = bt.connect(device.address, disconnect = true)
        btConnectNote(result, device.name, disconnect = true)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(previewWake) {
        previewWake?.let { id -> HomeCatalog.consoles.firstOrNull { it.id == id }?.let { woke = it } }
    }

    var lifted by remember { mutableStateOf<Lifted?>(null) }
    // OK or Back puts a lifted tile down when let go, once it was pressed after the lift: the
    // OK that lifted it is still held when the lift starts
    val dropArmed = remember { booleanArrayOf(false) }

    fun keysOf(row: HomeOrder.Row): List<String> = when (row) {
        HomeOrder.Row.Favourites -> slots.map { it.key }
        HomeOrder.Row.Consoles -> consoles.map { it.id }
        HomeOrder.Row.Apps -> orderedApps.map { it.packageName }
    }

    fun lift(row: HomeOrder.Row, key: String) {
        dropArmed[0] = false
        lifted = Lifted(row, key)
    }

    fun moveLifted(step: Int) {
        val current = lifted ?: return
        val keys = keysOf(current.row)
        val from = keys.indexOf(current.key)
        val moved = HomeOrder.move(keys, from, from + step)
        if (moved === keys) return
        when (current.row) {
            HomeOrder.Row.Favourites -> homeKeys = moved
            HomeOrder.Row.Consoles -> consoleOrder = moved
            HomeOrder.Row.Apps -> appOrder = moved
        }
    }

    var pinning by remember { mutableStateOf<InstalledApp?>(null) }
    var changing by remember { mutableStateOf<Int?>(null) }

    // Puts the app with [key] on the Home tile at [index]; one already on Home trades places
    fun placeOnHome(key: String, index: Int) {
        val keys = slots.map { it.key }.toMutableList()
        if (index !in keys.indices) return
        val already = keys.indexOf(key)
        if (already >= 0) keys[already] = keys[index]
        keys[index] = key
        homeKeys = keys
        order.save(HomeOrder.Row.Favourites, keys)
    }

    fun resetHome() {
        homeKeys = emptyList()
        order.save(HomeOrder.Row.Favourites, emptyList())
    }

    fun drop() {
        val current = lifted ?: return
        order.save(current.row, keysOf(current.row))
        lifted = null
    }
    val firstFavourite = remember { FocusRequester() }
    val firstApp = remember { FocusRequester() }
    val firstConsole = remember { FocusRequester() }
    val homeTab = remember { FocusRequester() }
    val appsTab = remember { FocusRequester() }
    val sourcesTab = remember { FocusRequester() }
    // Goes up to send the focus to the first tile of the open tab
    var focusFirst by remember { mutableIntStateOf(1) }
    LaunchedEffect(focusFirst) {
        // A tab that just opened is only there after a frame or two
        repeat(10) {
            withFrameNanos { }
            // A console's or a device's card that just came has the focus, and keeps it
            if (woke != null || btCard != null || btCorner || switcher) return@LaunchedEffect
            val target = when (tab) {
                HomeTab.Home -> firstFavourite
                HomeTab.Apps -> firstApp
                HomeTab.Sources -> firstConsole
            }
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    // A panel that closes gives the focus back to the pill that opened it; without this the
    // focus is left nowhere once the panel's window goes and the first arrow press is lost
    val profilePill = remember { FocusRequester() }
    val inputsPill = remember { FocusRequester() }
    val versionPill = remember { FocusRequester() }
    val bluetoothPill = remember { FocusRequester() }
    val motionPill = remember { FocusRequester() }
    var reopen by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(showProfiles, showInputs, showUpdates, showMotion, showBluetooth) {
        val back = reopen ?: return@LaunchedEffect
        if (showProfiles || showInputs || showUpdates || showMotion || showBluetooth) return@LaunchedEffect
        reopen = null
        repeat(10) {
            withFrameNanos { }
            if (runCatching { back.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    // Home pressed while the home screen shows: back to the start, as on other TV home screens
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        btCorner = false
        switcher = false
        reopen = null
        drop()
        linking = null
        woke = null
        btCard = null
        showBluetooth = false
        showUpdates = false
        showMotion = false
        showInputs = false
        showProfiles = false
        pinning = null
        changing = null
        tab = HomeTab.Home
        focusFirst++
    }
    // The second of two Home presses: the app switcher comes over the home screen just reset
    LaunchedEffect(switcherOpens) {
        if (switcherOpens == 0) return@LaunchedEffect
        withFrameNanos { }
        switcher = true
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
            val open = TvInputs.openIntent(input.id)
            if (HomeApps.start(context, open)) {
                ConsoleSession.started(context, console.name, console.color.toArgb().toLong() and 0xFFFFFFFFL, input.id, input.label, open)
            }
        }
    }

    fun pickInput(input: TvInput?) {
        showInputs = false
        if (input != null) {
            val open = TvInputs.openIntent(input.id)
            if (HomeApps.start(context, open)) {
                // A console linked to this input lends its name and colour
                val console = HomeCatalog.consoles.firstOrNull { consoleInputs[it]?.id == input.id }
                ConsoleSession.started(
                    context,
                    console?.name ?: input.label,
                    (console?.color ?: NeonPalette.Magenta).toArgb().toLong() and 0xFFFFFFFFL,
                    input.id,
                    input.label,
                    open
                )
            }
        }
    }

    CompositionLocalProvider(LocalHomeLook provides look, LocalIconPack provides (previewIcons ?: profile.icons), LocalIconTint provides (previewTint ?: profile.tint)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // The arrows the remote presses, for the tick as the focus moves, and a blip on OK
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val code = event.nativeKeyEvent.keyCode
                        if (code in ArrowKeys) lastArrow[0] = SystemClock.uptimeMillis()
                        if (code in SelectKeys && event.nativeKeyEvent.repeatCount == 0 && lifted == null) sounds.select()
                    }
                    false
                }
                // While a tile is lifted every key goes to moving it, so the focus stays on it
                .onPreviewKeyEvent { event ->
                    val current = lifted ?: return@onPreviewKeyEvent false
                    val native = event.nativeKeyEvent
                    val code = native.keyCode
                    val putsDown = code in SelectKeys || code == KeyEvent.KEYCODE_BACK
                    when (event.type) {
                        KeyEventType.KeyDown -> when {
                            code == KeyEvent.KEYCODE_DPAD_LEFT -> moveLifted(-1)
                            code == KeyEvent.KEYCODE_DPAD_RIGHT -> moveLifted(1)
                            code == KeyEvent.KEYCODE_DPAD_UP && columns(current.row) > 0 -> moveLifted(-columns(current.row))
                            code == KeyEvent.KEYCODE_DPAD_DOWN && columns(current.row) > 0 -> moveLifted(columns(current.row))
                            putsDown && native.repeatCount == 0 -> { dropArmed[0] = true }
                        }
                        KeyEventType.KeyUp -> if (putsDown && dropArmed[0]) drop()
                        else -> Unit
                    }
                    true
                }
        ) {
            // As far as the Motion panel says: by default a gentle few dp
            val leanX = with(LocalDensity.current) { HomeMotion.lean.across.dp.toPx() }
            val leanY = with(LocalDensity.current) { HomeMotion.lean.down.dp.toPx() }
            val tabsTop = maxHeight * 0.415f
            val rowsTop = maxHeight * 0.485f
            // With Watch & listen down at the foot, the tabs and the rows of Home sit low under the
            // picture, and rise to their usual place for the other tabs, which need the room
            val density = LocalDensity.current
            val lowest = with(density) { (maxHeight - 30.dp - (rowsTop - tabsTop)).toPx() }
            val lowTabs = (lowest - homeRowsHeight).coerceAtLeast(with(density) { (maxHeight * 0.3f).toPx() })
            val dropTo = if (watchDown && tab == HomeTab.Home && homeRowsHeight > 0) lowTabs - with(density) { tabsTop.toPx() } else 0f
            LaunchedEffect(dropTo) {
                // Where the rows open at shows at once; only a move after that is seen
                if (dropSettled[0]) drop.animateTo(dropTo, spring(dampingRatio = 0.86f, stiffness = 240f))
                else drop.snapTo(dropTo)
                if (homeRowsHeight > 0 || !watchDown) dropSettled[0] = true
            }
            FocusBackdrop(
                style = style,
                picture = profile.background,
                leanX = leanX,
                leanY = leanY,
                screen = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
                wallpaper = { wallpaper },
                spot = { spot },
                spotOn = { spotOn },
                ignite = { ignite.value }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = 30.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    HomeClock({ resumes }, light = { clockLight.value }, ignite = { ignite.value })
                    // The profile's own widgets, if it picked any: none of the styles has them
                    val widgetsVersion by HomeWidgets.version.collectAsState()
                    val widgets = remember(profile.id, widgetsVersion) { HomeWidgets.shown(appContext, profile.id) }
                    WidgetStrip(
                        widgets,
                        interactive = true,
                        apps = apps,
                        modifier = Modifier.padding(top = 9.dp).graphicsLayer { alpha = ignite.value },
                        onFocus = {
                            focusMoved()
                            wallpaper = NeonWallpaper.TOKYO
                            spotOn = false
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = ignite.value }
                ) {
                    fun up(paper: NeonWallpaper) {
                        focusMoved()
                        wallpaper = paper
                        spotOn = false
                    }
                    RoundButton(profile.name, namesAlways, Modifier.focusRequester(profilePill), onClick = {
                        reopen = profilePill
                        showProfiles = true
                    }, onFocus = { up(NeonWallpaper.TOKYO) }, round = true) {
                        ProfileFace(profile, Modifier.size(24.dp), letterSize = 11.sp)
                    }
                    RoundButton("Search", namesAlways, onClick = { openSearch(context) }, onFocus = { up(NeonWallpaper.TOKYO) }) { ink ->
                        ButtonGlyph(Glyph.Search, ink)
                    }
                    RoundButton("AirPlay", namesAlways, onClick = { startAirPlay(context) }, onFocus = { up(NeonWallpaper.OCEAN) }) { ink ->
                        ButtonGlyph(Glyph.AirPlay, ink)
                    }
                    RoundButton("Inputs", namesAlways, Modifier.focusRequester(inputsPill), onClick = {
                        reopen = inputsPill
                        showInputs = true
                    }, onFocus = { up(NeonWallpaper.OCEAN) }) { ink ->
                        ButtonGlyph(Glyph.Hdmi, ink)
                    }
                    RoundButton("Bluetooth", namesAlways, Modifier.focusRequester(bluetoothPill), onClick = {
                        reopen = bluetoothPill
                        showBluetooth = true
                    }, onFocus = { up(NeonWallpaper.OCEAN) }) { ink ->
                        ButtonGlyph(Glyph.Bluetooth, ink)
                    }
                    // A newer version says so in words, whatever the profile's buttons show
                    val newer = updateState is UpdateState.UpdateAvailable
                    RoundButton(
                        if (newer) "Update" else shortVersion(),
                        namesAlways || newer,
                        Modifier.focusRequester(versionPill),
                        onClick = {
                            reopen = versionPill
                            showUpdates = true
                        },
                        onFocus = { up(NeonWallpaper.TOKYO) }
                    ) { ink ->
                        ButtonGlyph(Glyph.Update, ink)
                    }
                    RoundButton("Motion", namesAlways, Modifier.focusRequester(motionPill), onClick = {
                        reopen = motionPill
                        showMotion = true
                    }, onFocus = { up(NeonWallpaper.TOKYO) }) { ink ->
                        ButtonGlyph(Glyph.Sparkle, ink)
                    }
                    RoundButton("Settings", namesAlways, onClick = { HomeApps.start(context, Intent(Settings.ACTION_SETTINGS)) }, onFocus = { up(NeonWallpaper.TOKYO) }) { ink ->
                        ButtonGlyph(Glyph.Gear, ink)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .padding(start = Side, top = tabsTop)
                    .offset { IntOffset(0, drop.value.roundToInt()) }
                    .graphicsLayer {
                        alpha = ignite.value
                        translationY = (1f - ignite.value) * 14.dp.toPx()
                    }
                    .onFocusChanged { onTabs = it.hasFocus }
                    // Up from the tiles lands on the open tab, never on the other one, which would open it
                    .focusProperties {
                        enter = {
                            when (tab) {
                                HomeTab.Home -> homeTab
                                HomeTab.Apps -> appsTab
                                HomeTab.Sources -> sourcesTab
                            }
                        }
                    }
                    .focusGroup()
            ) {
                // The glass lies behind the words, the neon line over the pills
                if (look.glassy) TabGlide(tabSpots.getValue(tab).value, focused = { onTabs })
                Row(horizontalArrangement = Arrangement.spacedBy(if (look.glassy) 4.dp else 6.dp)) {
                    fun placed(of: HomeTab) = Modifier.onPlaced {
                        tabSpots.getValue(of).value = TabSpot(it.positionInParent().x, it.positionInParent().y, it.size.width.toFloat(), it.size.height.toFloat())
                    }
                    TabPill("Home", tab == HomeTab.Home, homeTab, placed(HomeTab.Home), onFocus = {
                        focusMoved()
                        tab = HomeTab.Home
                        spotOn = false
                    }, onClick = { focusFirst++ })
                    TabPill("All apps", tab == HomeTab.Apps, appsTab, placed(HomeTab.Apps), onFocus = {
                        focusMoved()
                        tab = HomeTab.Apps
                        spotOn = false
                    }, onClick = { focusFirst++ })
                    TabPill("Källor", tab == HomeTab.Sources, sourcesTab, placed(HomeTab.Sources), onFocus = {
                        focusMoved()
                        tab = HomeTab.Sources
                        spotOn = false
                    }, onClick = { focusFirst++ })
                }
                if (!look.glassy) TabGlide(tabSpots.getValue(tab).value, focused = { onTabs })
            }

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    // The next tab comes in from the side it lies on, and the last one gives way
                    val way = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(200, delayMillis = 40)) + slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { way * it / 18 })
                        .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(tween(160, easing = FastOutSlowInEasing)) { -way * it / 30 })
                },
                label = "HomeTab",
                modifier = Modifier
                    .padding(top = rowsTop)
                    .fillMaxSize()
                    // Rising by layout rather than by layer, so the light under the focused tile rises with it
                    .offset { IntOffset(0, ((1f - ignite.value) * 26.dp.toPx() + drop.value).roundToInt()) }
                    .graphicsLayer { alpha = ignite.value }
            ) { shown ->
                when (shown) {
                    HomeTab.Home -> HomeRows(
                        slots = slots,
                        lifted = lifted,
                        onLift = { row, key -> lift(row, key) },
                        isInstalled = { slot ->
                            val known = slot.known
                            if (known != null && known in HomeCatalog.favourites) known in installed
                            else apps.isEmpty() || apps.any { it.packageName == slot.key }
                        },
                        onChange = { changing = it },
                        firstFavourite = firstFavourite,
                        onFocusColor = { pkg, title, color -> focusMoved(); wallpaper = NeonWallpaper.forCard(pkg, title, color.toArgb()) },
                        onSpot = {
                            spot = it
                            spotOn = true
                        },
                        onOpenApp = { slot ->
                            val known = slot.known
                            if (known != null && known in HomeCatalog.favourites) HomeApps.open(context, known) else HomeApps.open(context, slot.key)
                        },
                        watching = watching,
                        onOpenWatching = { profileId, entry -> HomeWatchingSource.open(context, profileId, entry) },
                        square = squareIcons,
                        namesAlways = namesAlways,
                        down = watchDown,
                        onHeight = { homeRowsHeight = it },
                        consoleRow = if (!consolesOnHome) null else ({
                            ConsoleRow(
                                consoles = consoles,
                                square = squareIcons,
                                namesAlways = namesAlways,
                                live = { console -> TvInputs.isOn(consoleInputs[console]?.let { states[it.id] }) },
                                onFocusColor = { title, color -> focusMoved(); wallpaper = NeonWallpaper.forCard(null, title, color.toArgb()) },
                                onSpot = {
                                    spot = it
                                    spotOn = true
                                },
                                onOpen = { openConsole(it) },
                                onLink = { linking = it }
                            )
                        })
                    )
                    HomeTab.Sources -> Sources(
                        consoles = consoles,
                        lifted = lifted?.takeIf { it.row == HomeOrder.Row.Consoles }?.key,
                        onLift = { lift(HomeOrder.Row.Consoles, it) },
                        live = { console -> TvInputs.isOn(consoleInputs[console]?.let { states[it.id] }) },
                        firstConsole = firstConsole,
                        onFocusColor = { title, color -> focusMoved(); wallpaper = NeonWallpaper.forCard(null, title, color.toArgb()) },
                        onSpot = {
                            spot = it
                            spotOn = true
                        },
                        onOpen = { openConsole(it) },
                        onLink = { linking = it }
                    )
                    HomeTab.Apps -> AllApps(
                        apps = orderedApps,
                        lifted = lifted?.takeIf { it.row == HomeOrder.Row.Apps }?.key,
                        onLift = { lift(HomeOrder.Row.Apps, it) },
                        firstApp = firstApp,
                        onFocusColor = { pkg, title, color ->
                            focusMoved()
                            wallpaper = NeonWallpaper.forCard(pkg, title, color.toArgb())
                            spotOn = false
                        },
                        onOpen = { HomeApps.open(context, it.packageName) },
                        onPin = { pinning = it },
                        namesAlways = namesAlways,
                        hiddenCount = hiddenList.size,
                        onHidden = { showHidden = true }
                    )
                }
            }

            AnimatedVisibility(
                visible = lifted != null,
                enter = fadeIn(tween(220)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 2 },
                exit = fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)
            ) {
                MoveHint(grid = lifted?.let { columns(it.row) > 0 } == true)
            }

            // The card of a console that was just switched on, kept on screen while it slides away
            val wakeShown = remember { arrayOfNulls<Console>(1) }
            woke?.let { wakeShown[0] = it }
            AnimatedVisibility(
                visible = woke != null && lifted == null && linking == null && !showInputs && !showProfiles && !showUpdates && !showBluetooth && !showMotion && pinning == null && changing == null,
                enter = fadeIn(tween(260)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { -it / 2 },
                exit = fadeOut(tween(220)) + slideOutVertically(tween(260)) { -it / 3 },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = Side)
            ) {
                val console = wakeShown[0] ?: return@AnimatedVisibility
                key(console.id) {
                    ConsoleWakeCard(
                        console = console,
                        input = consoleInputs[console]?.label,
                        onPlay = {
                            woke = null
                            openConsole(console)
                        },
                        onDismiss = { hadFocus ->
                            woke = null
                            if (hadFocus) focusFirst++
                        }
                    )
                }
            }

            // The card of a Bluetooth device that was just switched on, in the bottom corner;
            // a console's card goes first
            val btShown = remember { arrayOfNulls<BtDevice>(1) }
            btCard?.let { btShown[0] = it }
            AnimatedVisibility(
                visible = btCard != null && !btCorner && woke == null && lifted == null && linking == null && !showInputs && !showProfiles && !showUpdates && !showBluetooth && !showMotion && pinning == null && changing == null,
                enter = fadeIn(tween(260)) + slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { it / 2 },
                exit = fadeOut(tween(220)) + slideOutHorizontally(tween(260)) { it / 3 },
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 34.dp, end = Side)
            ) {
                val device = btShown[0] ?: return@AnimatedVisibility
                key(device.address) {
                    BluetoothCard(
                        device = device,
                        onConnect = {
                            btCard = null
                            bt.snooze(device.address, BtRules.QUIET_MS)
                            connectBluetooth(device)
                            focusFirst++
                        },
                        onDismiss = { hadFocus, pressed ->
                            btCard = null
                            bt.snooze(device.address, if (pressed) BtRules.SNOOZE_MS else BtRules.QUIET_MS)
                            if (hadFocus) focusFirst++
                        }
                    )
                }
            }

            // The Bluetooth corner, after Home pressed twice
            AnimatedVisibility(
                visible = btCorner && woke == null && lifted == null && linking == null && !showInputs && !showProfiles && !showUpdates && !showBluetooth && !showMotion && pinning == null && changing == null,
                enter = fadeIn(tween(240)) + slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutHorizontally(tween(240)) { it / 3 },
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 34.dp, end = Side)
            ) {
                BluetoothCorner(
                    devices = cornerDevices,
                    nearby = btNearby,
                    note = when {
                        previewCorner != null -> null
                        !bt.available -> "This TV has no Bluetooth."
                        !btPermitted -> "Open All devices to let Neon Tokyo see them."
                        !btOn -> "Bluetooth is off on the TV."
                        cornerDevices.isEmpty() -> "No headphones or speakers of yours yet."
                        else -> null
                    },
                    onConnect = { device ->
                        btCorner = false
                        if (device.address.startsWith(PREVIEW_DEVICE)) return@BluetoothCorner
                        if (btOwners[device.address].isNullOrEmpty()) bt.take(device.address, profile.id, true)
                        // A device that is connected lets go, and the sound comes back to the TV
                        if (device.connected) disconnectBluetooth(device)
                        else connectBluetooth(device)
                        focusFirst++
                    },
                    onAll = {
                        btCorner = false
                        reopen = bluetoothPill
                        showBluetooth = true
                    },
                    onDismiss = { hadFocus ->
                        btCorner = false
                        if (hadFocus) focusFirst++
                    }
                )
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
        if (showProfiles) {
            HomeProfilesDialog(homeProfiles, onDismiss = { showProfiles = false })
        }
        if (showUpdates) {
            HomeUpdatesDialog(updates, onDismiss = { showUpdates = false })
        }
        if (showBluetooth) {
            HomeBluetoothDialog(bt, profile, profileList, onConnect = { connectBluetooth(it) }, onDismiss = { showBluetooth = false })
        }
        if (showMotion) {
            HomeMotionDialog(appContext, onDismiss = { showMotion = false })
        }
        pinning?.let { app ->
            PinToHomeDialog(app, slots, onPick = { index ->
                placeOnHome(app.packageName, index)
                pinning = null
            }, onHide = {
                hiddenApps = hiddenApps + app.packageName
                order.setHidden(hiddenApps)
                pinning = null
            }, onDismiss = { pinning = null })
        }
        if (showHidden) {
            HiddenAppsDialog(hiddenList, onShow = { app ->
                hiddenApps = hiddenApps - app.packageName
                order.setHidden(hiddenApps)
            }, onDismiss = { showHidden = false })
        }
        changing?.let { index ->
            slots.getOrNull(index)?.let { slot ->
                ChangeHomeTileDialog(
                    slot = slot,
                    apps = orderedApps,
                    onPick = { app ->
                        placeOnHome(app.packageName, index)
                        changing = null
                        focusFirst++
                    },
                    onReset = {
                        resetHome()
                        changing = null
                        focusFirst++
                    },
                    onDismiss = { changing = null }
                )
            }
        }
        if (switcher) {
            AppSwitcher(
                onDone = {
                    switcher = false
                    focusFirst++
                },
                onBluetooth = { btCorner = true },
                onMove = { sounds.move() },
                preview = previewSwitcher?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
            )
        }
    }
}

@Composable
private fun HomeRows(
    slots: List<HomeSlot>,
    lifted: Lifted?,
    onLift: (HomeOrder.Row, String) -> Unit,
    isInstalled: (HomeSlot) -> Boolean,
    onChange: (Int) -> Unit,
    firstFavourite: FocusRequester,
    onFocusColor: (String?, String, Color) -> Unit,
    onSpot: (Spot) -> Unit,
    onOpenApp: (HomeSlot) -> Unit,
    watching: HomeWatching?,
    onOpenWatching: (Int, ContinueEntry) -> Unit,
    square: Boolean = false,
    namesAlways: Boolean = true,
    down: Boolean = false,
    onHeight: (Int) -> Unit = {},
    consoleRow: (@Composable () -> Unit)? = null
) {
    val look = LocalHomeLook.current
    val pack = LocalIconPack.current
    val tileShape = RoundedCornerShape(if (look.glassy && square) 22.dp else look.corner)
    val continues = watching != null && watching.entries.isNotEmpty()
    // With the consoles and Continue watching the rows outgrow the screen: they scroll as the
    // focus goes down, with room at the top for a focused tile's glow
    val scrolls = consoleRow != null && continues
    val room = 16.dp
    val watch: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            RowTitle("Watch & listen")
            WatchRow(slots, lifted, onLift, isInstalled, onChange, firstFavourite, onFocusColor, onSpot, onOpenApp, tileShape, square, namesAlways, pack)
        }
    }
    val continueRow: @Composable () -> Unit = {
        if (watching != null) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { ContinueRow(watching, RoundedCornerShape(look.corner), onFocusColor, onSpot, onOpenWatching) }
        }
    }
    val rows = listOfNotNull(watch, consoleRow, continueRow.takeIf { continues })
    Column(
        modifier = Modifier
            .then(if (scrolls) Modifier.offset(y = -room).verticalScroll(rememberScrollState()).padding(top = room, bottom = room) else Modifier)
            .padding(start = Side)
    ) {
        // Down at the foot, Watch & listen is the lowest row and the others stack up over it
        Column(
            modifier = Modifier.onSizeChanged { onHeight(it.height) },
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            (if (down) rows.asReversed() else rows).forEach { row -> row() }
        }
    }
}

/** The apps on Home, as wide tiles or, on glass, square icons; the first has the focus to start. */
@Composable
private fun WatchRow(
    slots: List<HomeSlot>,
    lifted: Lifted?,
    onLift: (HomeOrder.Row, String) -> Unit,
    isInstalled: (HomeSlot) -> Boolean,
    onChange: (Int) -> Unit,
    firstFavourite: FocusRequester,
    onFocusColor: (String?, String, Color) -> Unit,
    onSpot: (Spot) -> Unit,
    onOpenApp: (HomeSlot) -> Unit,
    tileShape: RoundedCornerShape,
    square: Boolean,
    namesAlways: Boolean,
    pack: IconPack
) {
    val look = LocalHomeLook.current
    val size = when {
        !look.glassy -> BigTile
        square -> GlassSquare
        else -> GlassWide
    }
    Row(horizontalArrangement = Arrangement.spacedBy(if (look.glassy) GlassGap(square) else Gap)) {
        slots.forEachIndexed { index, slot ->
            val appKey = slot.key
            // Keyed, so a tile that moves keeps its focus and slides over to its new place
            key(appKey) {
                val color = look.tint(slot.color)
                val known = slot.known
                NeonTile(
                    width = size.first,
                    height = size.second,
                    shape = tileShape,
                    color = color,
                    onClick = { onOpenApp(slot) },
                    onFocus = { onFocusColor(appKey, slot.name, slot.color) },
                    modifier = Modifier.animatePlacement(),
                    focusRequester = if (index == 0) firstFavourite else null,
                    // A short hold changes the app on the tile; the full three seconds lifts it
                    onLongClick = { onChange(index) },
                    onHold = { onLift(HomeOrder.Row.Favourites, appKey) },
                    lift = liftOf(lifted, HomeOrder.Row.Favourites, appKey),
                    // Not on the TV yet: dimmed, and opening it goes to its Play Store page
                    dimmed = !isInstalled(slot),
                    onFocusRect = { onSpot(Spot(it, slot.color)) },
                    caption = slot.name,
                    captionAlways = namesAlways,
                    behind = { if (known == HomeCatalog.NeonTokyo) NeonAura(tileShape, it, outer = true) },
                    front = { if (known == HomeCatalog.NeonTokyo) NeonAura(tileShape, it, outer = false) }
                ) { focused ->
                    if (look.glassy) {
                        val lit = rememberLit(focused)
                        GlassFace(pack, known?.packages ?: listOf(appKey), slot.name, tileShape, slot.color, if (square) 50.dp else 46.dp, lit = { lit.value }) { ink ->
                            val sign = Modifier.align(Alignment.Center).size(if (square) 44.dp else 40.dp)
                            if (known != null) NeonGlyph(known.glyph, ink, sign, plain = true) else Monogram(slot.label, ink(), sign)
                        }
                        return@NeonTile
                    }
                    if (known != null && pack != IconPack.Banner) TileScene(known, color, focused, tileShape)
                    AppArtFace(pack, known?.packages ?: listOf(appKey), slot.name, tileShape, color, focused, big = true, nameSize = 15.sp, motion = IconMotion.of(known?.name ?: slot.name)) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (known != null) NeonGlyph(known.glyph, color, Modifier.size(42.dp)) else Monogram(slot.label, color, Modifier.size(42.dp))
                            Spacer(Modifier.width(11.dp))
                            TileName(slot.name, 15.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The consoles on Home, in a row under the apps or over them, when the profile wants them there
 * as well as on Källor. A short hold asks which input one is on.
 */
@Composable
private fun ConsoleRow(
    consoles: List<Console>,
    square: Boolean,
    namesAlways: Boolean,
    live: (Console) -> Boolean,
    onFocusColor: (String, Color) -> Unit,
    onSpot: (Spot) -> Unit,
    onOpen: (Console) -> Unit,
    onLink: (Console) -> Unit
) {
    val look = LocalHomeLook.current
    val glassSquare = look.glassy && square
    val shape = RoundedCornerShape(if (glassSquare) 22.dp else look.corner)
    val size = if (glassSquare) GlassSquare else HomeConsole
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        RowTitle("Consoles")
        Row(horizontalArrangement = Arrangement.spacedBy(if (look.glassy) GlassGap(square) else Gap)) {
            consoles.forEach { console ->
                key(console.id) {
                    val on = live(console)
                    val light = rememberLiveLight(on)
                    val color = look.tint(console.color)
                    NeonTile(
                        width = size.first,
                        height = size.second,
                        shape = shape,
                        color = color,
                        onClick = { onOpen(console) },
                        onLongClick = { onLink(console) },
                        onFocus = { onFocusColor(console.name, console.color) },
                        onFocusRect = { onSpot(Spot(it, console.color)) },
                        caption = console.name,
                        captionAlways = namesAlways,
                        behind = { LiveHalo(light, color, look.corner) },
                        front = { LiveEdge(light, on, color, look.corner) }
                    ) { focused ->
                        if (look.glassy) {
                            GlassConsole(console, on, focused, square)
                            return@NeonTile
                        }
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NeonGlyph(console.glyph, color, Modifier.size(26.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                TileName(console.name, 12.sp)
                                HdmiLabel(console, color, Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A console on glass: its sign in the middle, its HDMI number and whether it is on at the edges. */
@Composable
private fun BoxScope.GlassConsole(console: Console, on: Boolean, focused: Boolean, square: Boolean) {
    val look = LocalHomeLook.current
    val lit = rememberLit(focused)
    val ink = { lerp(Color.White.copy(alpha = 0.94f), console.color, if (look.brandInFocus) lit.value else 0f) }
    val note = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 8.sp, color = look.text.copy(alpha = 0.72f))
    NeonGlyph(console.glyph, ink, Modifier.align(Alignment.Center).size(if (square) 38.dp else 32.dp), plain = true)
    BasicText(
        "HDMI ${console.port}",
        style = note,
        modifier = Modifier.align(if (square) Alignment.TopCenter else Alignment.TopStart).padding(start = if (square) 0.dp else 10.dp, top = 6.dp)
    )
    if (on) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(if (square) Alignment.BottomCenter else Alignment.TopEnd).padding(end = if (square) 0.dp else 9.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Box(Modifier.size(5.dp).background(LiveGreen, CircleShape))
            Spacer(Modifier.width(4.dp))
            BasicText("On", style = note.copy(color = look.text))
        }
    }
}

/** A console's HDMI number, small, in its colour. */
@Composable
private fun HdmiLabel(console: Console, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text = "HDMI ${console.port}",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 0.5.sp,
            color = color
        ),
        modifier = modifier
    )
}

/** How lit a glass tile is, from 0 at rest to 1 in focus, read where it is drawn. */
@Composable
private fun rememberLit(focused: Boolean): State<Float> =
    animateFloatAsState(if (focused) 1f else 0f, tween(if (focused) 220 else 320, easing = FastOutSlowInEasing), label = "GlassLit")

/**
 * Continue watching: what the Neon Tokyo profile with the same name as the home profile left part
 * way, or the next episode of a series it follows. OK opens it in Neon Tokyo, logged in as that
 * profile, on its page, where it plays on from where it was.
 */
@Composable
private fun ContinueRow(
    watching: HomeWatching,
    shape: RoundedCornerShape,
    onFocusColor: (String?, String, Color) -> Unit,
    onSpot: (Spot) -> Unit,
    onOpen: (Int, ContinueEntry) -> Unit
) {
    val look = LocalHomeLook.current
    val color = look.accent
    RowTitle("Continue watching")
    Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
        watching.entries.forEach { entry ->
            key(entry.type, entry.id) {
                NeonTile(
                    width = BigTile.first,
                    height = WatchTileHeight,
                    shape = shape,
                    color = color,
                    onClick = { onOpen(watching.profileId, entry) },
                    onFocus = { onFocusColor(null, entry.name, color) },
                    modifier = Modifier.animatePlacement(),
                    onFocusRect = { onSpot(Spot(it, color)) }
                ) { focused ->
                    val picture = entry.background?.takeIf { it.isNotBlank() } ?: entry.poster
                    if (picture != null) {
                        AsyncImage(
                            model = picture,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(shape).graphicsLayer { alpha = if (focused) 0.95f else 0.7f }
                        )
                    }
                    // Dark at the foot, so the name reads on any picture
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f)), shape)
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, end = 10.dp, bottom = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        TileName(entry.name, 13.sp)
                        val episode = episodeLabel(entry)
                        val note = when {
                            entry.hasNewEpisode -> listOfNotNull("New episode", episode).joinToString("  ·  ")
                            else -> episode
                        }
                        if (note != null) {
                            BasicText(
                                text = note,
                                maxLines = 1,
                                style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 10.5.sp, color = look.text.copy(alpha = 0.75f))
                            )
                        }
                    }
                    if (entry.progress > 0f) {
                        // How far in: a lit line along the foot of the tile
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(look.text.copy(alpha = 0.18f), RoundedCornerShape(1.dp))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(entry.progress.coerceIn(0.03f, 1f))
                                    .background(color, RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

private val WatchTileHeight = 92.dp

/**
 * Källor, the consoles and the PC by their HDMI numbers, as big tiles three to a row. One that
 * is switched on glows and has its star; a short hold asks which input it is on.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Sources(
    consoles: List<Console>,
    lifted: String?,
    onLift: (String) -> Unit,
    live: (Console) -> Boolean,
    firstConsole: FocusRequester,
    onFocusColor: (String, Color) -> Unit,
    onSpot: (Spot) -> Unit,
    onOpen: (Console) -> Unit,
    onLink: (Console) -> Unit
) {
    // Room round the tiles for a focused tile's glow, as on All apps
    val room = 12.dp
    val look = LocalHomeLook.current
    val tileShape = RoundedCornerShape(look.corner)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.padding(start = Side)) { RowTitle("Consoles") }
        LazyVerticalGrid(
            columns = GridCells.Fixed(ConsoleColumns),
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalArrangement = Arrangement.spacedBy(Gap),
            contentPadding = PaddingValues(room),
            userScrollEnabled = false,
            modifier = Modifier
                // Down from the tabs lands on the first console, not on whichever is under the tab
                .focusProperties { enter = { firstConsole } }
                .focusGroup()
                .wrapContentWidth(Alignment.Start)
                .offset(x = Side - room, y = -room)
                .width(BigTile.first * ConsoleColumns + Gap * (ConsoleColumns - 1) + room * 2)
                .fillMaxHeight()
        ) {
            itemsIndexed(consoles, key = { _, console -> console.id }) { index, console ->
                val light = rememberLiveLight(live(console))
                val color = look.tint(console.color)
                NeonTile(
                    width = BigTile.first,
                    height = BigTile.second,
                    shape = tileShape,
                    color = color,
                    onClick = { onOpen(console) },
                    // A short hold asks which input it is on; the full three seconds lifts it
                    onLongClick = { onLink(console) },
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    focusRequester = if (index == 0) firstConsole else null,
                    onHold = { onLift(console.id) },
                    lift = when (lifted) {
                        null -> Lift.None
                        console.id -> Lift.Lifted
                        else -> Lift.Other
                    },
                    onFocus = { onFocusColor(console.name, console.color) },
                    onFocusRect = { onSpot(Spot(it, console.color)) },
                    behind = { LiveHalo(light, color, look.corner) },
                    front = { LiveEdge(light, live(console), color, look.corner) }
                ) { focused ->
                    val lit = rememberLit(focused)
                    val ink = if (look.glassy) ({ lerp(Color.White.copy(alpha = 0.94f), console.color, if (look.brandInFocus) lit.value else 0f) }) else ({ color })
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeonGlyph(console.glyph, ink, Modifier.size(if (look.glassy) 34.dp else 38.dp), plain = look.glassy)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            TileName(console.name, 15.sp)
                            HdmiLabel(console, if (look.glassy) look.text.copy(alpha = 0.72f) else color, Modifier.padding(top = 3.dp))
                        }
                    }
                    if (look.glassy && live(console)) {
                        Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(6.dp).background(LiveGreen, CircleShape))
                    }
                }
            }
        }
    }
}

/** Grid columns of a row, which Up and Down move a lifted tile across; 0 for a single row. */
private fun columns(row: HomeOrder.Row): Int = when (row) {
    HomeOrder.Row.Favourites -> 0
    HomeOrder.Row.Consoles -> ConsoleColumns
    HomeOrder.Row.Apps -> AppColumns
}

/** How a tile shows a lift: none going on, it is the one lifted, or another one is. */
private enum class Lift { None, Lifted, Other }

private fun liftOf(lifted: Lifted?, row: HomeOrder.Row, key: String): Lift = when {
    lifted == null -> Lift.None
    lifted.row == row && lifted.key == key -> Lift.Lifted
    else -> Lift.Other
}

@Composable
private fun AllApps(
    apps: List<InstalledApp>,
    lifted: String?,
    onLift: (String) -> Unit,
    firstApp: FocusRequester,
    onFocusColor: (String?, String, Color) -> Unit,
    onOpen: (InstalledApp) -> Unit,
    onPin: (InstalledApp) -> Unit,
    hiddenCount: Int,
    onHidden: () -> Unit,
    namesAlways: Boolean = true
) {
    // The grid leaves room around the tiles for a focused tile's glow; the offset keeps the
    // tiles themselves in line with the rest of the screen
    val room = 12.dp
    val look = LocalHomeLook.current
    val pack = LocalIconPack.current
    val tileShape = RoundedCornerShape(look.corner * 0.8f)
    val grid = rememberLazyGridState()
    // A lifted app moved past the top or bottom of the screen brings its row into view
    val liftedIndex = lifted?.let { key -> apps.indexOfFirst { it.packageName == key } } ?: -1
    LaunchedEffect(liftedIndex) {
        if (liftedIndex < 0) return@LaunchedEffect
        val info = grid.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == liftedIndex }
        val fits = item != null && item.offset.y >= 0 && item.offset.y + item.size.height <= info.viewportEndOffset - info.afterContentPadding
        if (!fits) grid.animateScrollToItem((liftedIndex / AppColumns - 1).coerceAtLeast(0) * AppColumns)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(AppColumns),
        state = grid,
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
            val ownColor = known?.color ?: HomeCatalog.monogramColor(app.label)
            val color = look.tint(ownColor)
            NeonTile(
                width = SmallTile.first,
                height = SmallTile.second,
                shape = tileShape,
                color = color,
                onClick = { onOpen(app) },
                onFocus = { onFocusColor(app.packageName, app.name, ownColor) },
                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                focusRequester = if (index == 0) firstApp else null,
                // A short hold puts the app on Home; the full three seconds lifts it
                onLongClick = { onPin(app) },
                onHold = { onLift(app.packageName) },
                lift = when (lifted) {
                    null -> Lift.None
                    app.packageName -> Lift.Lifted
                    else -> Lift.Other
                },
                caption = app.name,
                captionAlways = namesAlways,
                behind = { if (known == HomeCatalog.NeonTokyo) NeonAura(tileShape, it, outer = true) },
                front = { if (known == HomeCatalog.NeonTokyo) NeonAura(tileShape, it, outer = false) }
            ) { focused ->
                if (look.glassy) {
                    val lit = rememberLit(focused)
                    GlassFace(pack, listOf(app.packageName), app.name, tileShape, ownColor, 34.dp, lit = { lit.value }) { ink ->
                        val sign = Modifier.align(Alignment.Center).size(30.dp)
                        if (known != null) NeonGlyph(known.glyph, ink, sign, plain = true) else Monogram(app.label, ink(), sign)
                    }
                    return@NeonTile
                }
                AppArtFace(pack, listOf(app.packageName), app.name, tileShape, color, focused, big = false, nameSize = 11.5.sp, motion = IconMotion.of(known?.name ?: app.name)) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp)) {
                        if (known != null) {
                            NeonGlyph(known.glyph, color, Modifier.size(26.dp))
                        } else {
                            Monogram(app.label, color, Modifier.size(26.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        TileName(app.name, 11.5.sp)
                    }
                }
            }
        }
        // The way back to the apps hidden from the list
        if (hiddenCount > 0) {
            item(key = "hidden") {
                val color = look.tint(look.dim)
                NeonTile(
                    width = SmallTile.first,
                    height = SmallTile.second,
                    shape = tileShape,
                    color = color,
                    onClick = onHidden,
                    onFocus = { onFocusColor(null, "Hidden", look.dim) },
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    caption = "Hidden · $hiddenCount",
                    captionAlways = true
                ) {
                    if (look.glassy) {
                        NeonGlyph(Glyph.Layers, Color.White.copy(alpha = 0.9f), Modifier.align(Alignment.Center).size(28.dp), plain = true)
                        return@NeonTile
                    }
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp)) {
                        NeonGlyph(Glyph.Layers, color, Modifier.size(26.dp))
                        Spacer(Modifier.weight(1f))
                        TileName("Hidden · $hiddenCount", 11.5.sp)
                    }
                }
            }
        }
    }
}

/**
 * A tile on the home screen. It pops forward on focus and its edge lights up like a neon tube;
 * [behind] and [front] can draw past its edges, and [onFocusRect] hears where it is on the
 * screen when the focus lands on it.
 *
 * With [onHold], holding OK draws a white hot light round its edge, and once it has gone all the
 * way round, three seconds in, [onHold] lifts the tile. Let go sooner and it is a click, or,
 * after a long press, [onLongClick]; let go once the light has started without one and nothing
 * happens. A lifted tile stands out with arrows at its sides while the others dim.
 *
 * On glass the tile is a pane of the look's glass instead, with no light at its edge: it comes
 * forward a little in focus with the light sweeping across it, and [caption], the app's name,
 * shows under it, all the time with [captionAlways] or else as the focus lands.
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
    onHold: (() -> Unit)? = null,
    lift: Lift = Lift.None,
    dimmed: Boolean = false,
    onFocusRect: ((Rect) -> Unit)? = null,
    caption: String? = null,
    captionAlways: Boolean = false,
    behind: @Composable BoxScope.(focused: Boolean) -> Unit = {},
    front: @Composable BoxScope.(focused: Boolean) -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    val look = LocalHomeLook.current
    val glass = look.glassy
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val surfacePressed by interaction.collectIsPressedAsState()
    var keyHeld by remember { mutableStateOf(false) }
    val pressed = surfacePressed || keyHeld
    val isLifted = lift == Lift.Lifted
    // A spring that overshoots a touch, so the tile pops when the focus lands on it; frosted glass
    // comes forward by less, as far as the Motion panel's pop says (4 % at its liveliest), and
    // slower, and liquid glass grows by more (10 %) and settles without a wobble
    val liquid = look.glassMode == HomeGlass.Liquid
    // What liquid glass plays as the focus lands on it, as the profile chose
    val motion = if (liquid) look.focusMotion else null
    val scale by animateFloatAsState(
        targetValue = when {
            isLifted -> if (glass) 1.12f else 1.13f
            pressed -> if (liquid) 1.06f else if (glass) 0.985f else 1.03f
            focused -> when {
                motion == GlassFocus.Layers -> HomeMotion.pop.scale
                liquid -> 1f + (HomeMotion.pop.scale - 1f) * 1.43f
                glass -> 1f + (HomeMotion.pop.scale - 1f) * 0.57f
                else -> HomeMotion.pop.scale
            }
            else -> 1f
        },
        animationSpec = when {
            (focused || isLifted) && !pressed && liquid -> spring(dampingRatio = maxOf(HomeMotion.pop.damping, 0.72f), stiffness = 300f)
            (focused || isLifted) && !pressed && glass -> spring(dampingRatio = maxOf(HomeMotion.pop.damping, 0.62f), stiffness = 380f)
            (focused || isLifted) && !pressed -> spring(dampingRatio = HomeMotion.pop.damping, stiffness = 650f)
            else -> spring(stiffness = 900f)
        },
        label = "TileScale"
    )
    // The light sweeping across glass, once each time the focus lands, and on liquid glass the
    // landing its focus motion plays, with which way the focus came from
    val sheen = remember { Animatable(0f) }
    val land = remember { Animatable(1f) }
    val from = remember { floatArrayOf(0f) }
    if (glass) {
        LaunchedEffect(focused) {
            if (focused) {
                sheen.snapTo(0f)
                if (motion != null) {
                    land.snapTo(0f)
                    launch { land.animateTo(1f, tween(900, easing = LinearEasing)) }
                }
                sheen.animateTo(1f, tween(900, delayMillis = 50, easing = SheenEasing))
            } else {
                sheen.snapTo(0f)
            }
        }
    }
    // Breathe: the light inside rises and falls slowly while the tile is in focus
    val breath = if (motion == GlassFocus.Breathe && focused) {
        rememberInfiniteTransition(label = "GlassBreath").animateFloat(
            1f, 0.45f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Breath"
        )
    } else null
    val tube by animateFloatAsState(if (focused || isLifted) 1f else 0f, tween(if (focused) 150 else 240), label = "TileTube")
    val faded by animateFloatAsState(if (lift == Lift.Other) 0.45f else 1f, tween(220), label = "TileFade")

    // Where the tile was laid out and last said it was, kept out of state so that moving it
    // never recomposes anything. A focused tile that moves, as its row slides in, says so again.
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val told = remember { arrayOfNulls<Rect>(1) }
    // An app the press opens grows out of the tile onto the screen, as from a flagship's icons
    val view = LocalView.current
    val press = {
        val bounds = placed[0]?.takeIf { it.isAttached }?.boundsInRoot()
        HomeApps.growFrom = bounds?.let { b -> { growOut(view, b) } }
        try {
            onClick()
        } finally {
            HomeApps.growFrom = null
        }
    }

    val holdLight = remember { Animatable(0f) }
    val holdScope = rememberCoroutineScope()
    val hold = remember { Hold() }
    val holdKeys = if (onHold == null) Modifier else Modifier.onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (native.keyCode !in SelectKeys) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (native.repeatCount == 0) {
                    hold.downAt = native.eventTime
                    hold.longPress = false
                    hold.lifted = false
                    keyHeld = true
                    hold.job?.cancel()
                    hold.job = holdScope.launch {
                        delay(HoldShowsMs)
                        // Timed by the clock rather than as an animation, so a TV with its
                        // animations sped up or off still takes the full three seconds to lift
                        val start = android.os.SystemClock.uptimeMillis()
                        while (true) {
                            val run = (android.os.SystemClock.uptimeMillis() - start).toFloat() / (HoldMs - HoldShowsMs)
                            holdLight.snapTo(run.coerceAtMost(1f))
                            if (run >= 1f) break
                            withFrameNanos { }
                        }
                        hold.lifted = true
                        keyHeld = false
                        onHold()
                        holdLight.animateTo(0f, tween(260))
                    }
                } else if (native.isLongPress) {
                    hold.longPress = true
                }
                true
            }
            KeyEventType.KeyUp -> {
                val downAt = hold.downAt
                hold.downAt = -1L
                keyHeld = false
                if (!hold.lifted) {
                    hold.job?.cancel()
                    holdScope.launch { holdLight.animateTo(0f, tween(160)) }
                }
                if (downAt >= 0L && !hold.lifted) {
                    // Judged by the keys' own times, as the remote and a test sent them
                    val heldFor = native.eventTime - downAt
                    val longPress = hold.longPress || heldFor >= ViewConfiguration.getLongPressTimeout()
                    when {
                        longPress && onLongClick != null -> onLongClick()
                        heldFor < HoldShowsMs && !longPress -> press()
                    }
                }
                true
            }
            else -> false
        }
    }
    fun tellWhere(always: Boolean) {
        val coordinates = placed[0] ?: return
        if (onFocusRect == null || !coordinates.isAttached) return
        val bounds = coordinates.boundsInRoot()
        if (always || bounds != told[0]) {
            told[0] = bounds
            onFocusRect(bounds)
        }
    }
    val captionRoom = if (glass && caption != null) CaptionRoom else 0.dp
    Box(
        modifier = modifier
            .size(width, height + captionRoom)
            .zIndex(if (focused) 1f else 0f)
            .onPlaced { placed[0] = it }
            .then(if (onFocusRect != null) Modifier.onGloballyPositioned { if (focused) tellWhere(always = false) } else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                when (motion) {
                    GlassFocus.Liquid -> squash(land.value).let { (wide, tall) ->
                        scaleX = scale * (1f + wide)
                        scaleY = scale * (1f + tall)
                    }
                    GlassFocus.Tilt -> if (focused) {
                        // Leans towards where the focus came from, then straightens
                        val lean = 1f - easeOut(land.value)
                        cameraDistance = 14f * density
                        rotationY = from[0] * 7f * lean
                        rotationX = -2f * lean
                    }
                    else -> Unit
                }
                alpha = faded
                // Glass grows round its own middle rather than that of it and its name
                if (captionRoom > 0.dp) transformOrigin = TransformOrigin(0.5f, height / (height + captionRoom) / 2f)
            }
    ) {
        if (glass) {
            // The pane lies under the Surface, which would cut off the shadow under it
            Box(
                modifier = Modifier
                    .size(width, height)
                    // Liquid glass comes forward by its size and its light alone: a shadow
                    // round clear glass reads as a dark outline
                    .then(if (liquid) Modifier else Modifier.glassShadow(shape) { tube })
                    .glassPane(
                        look.glassMode, shape, lit = { tube }, dim = look.shade == null,
                        glow = breath?.let { b -> { tube * b.value } }
                    )
                    .then(if (motion != null) Modifier.glassFocusFx(motion, shape, { land.value }, { tube }, { from[0] }) else Modifier)
                    .glassSheen(shape) { if (motion == null || motion.sweeps) sheen.value else 0f }
            )
        } else {
            behind(focused)
        }
        Surface(
            onClick = press,
            // With a hold the keys are read above, so the Surface never sees a long press
            onLongClick = if (onHold == null) onLongClick else null,
            modifier = Modifier
                .matchParentSize()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(holdKeys)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) {
                        // Which way the focus came, from where the last tile in focus sat
                        placed[0]?.takeIf { c -> c.isAttached }?.boundsInRoot()?.center?.x?.let { x ->
                            val last = GlassFocus.lastX
                            from[0] = if (last.isNaN() || last == x) 0f else if (x > last) 1f else -1f
                            GlassFocus.lastX = x
                        }
                        onFocus()
                        tellWhere(always = true)
                    }
                },
            shape = ClickableSurfaceDefaults.shape(shape = if (glass) RectangleShape else shape),
            scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = look.text,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = look.text,
                pressedContainerColor = Color.Transparent,
                pressedContentColor = look.text
            ),
            border = if (glass) ClickableSurfaceDefaults.border() else ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(0.5.dp, color.copy(alpha = 0.45f)), shape = shape),
                focusedBorder = Border(BorderStroke(1.5.dp, color), shape = shape),
                pressedBorder = Border(BorderStroke(1.5.dp, color), shape = shape)
            ),
            glow = if (glass) ClickableSurfaceDefaults.glow() else ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = color.copy(alpha = 0.6f), elevation = look.glow)
            ),
            interactionSource = interaction
        ) {
            if (glass) {
                // What is on the glass lies in the Surface, which would otherwise hide it from
                // TalkBack; the name comes first, so it reads as the tile's own
                Box {
                    if (caption != null) {
                        Box(Modifier.padding(top = height)) { TileCaption(caption, width, always = captionAlways, lit = { tube }) }
                    }
                    Box(
                        Modifier.size(width, height).graphicsLayer {
                            alpha = if (dimmed) 0.5f else 1f
                            when (motion) {
                                // The icon lifts off the glass
                                GlassFocus.Layers -> {
                                    val l = tube
                                    scaleX = 1f + 0.1f * l
                                    scaleY = 1f + 0.1f * l
                                    translationY = -4.dp.toPx() * l
                                }
                                GlassFocus.Liquid -> translationY = bob(land.value) * size.height
                                GlassFocus.Tilt -> if (focused) translationX = from[0] * 3.dp.toPx() * (1f - easeOut(land.value))
                                else -> Unit
                            }
                        }
                    ) { content(focused) }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(look.tileFill, shape)
                        .graphicsLayer { alpha = if (dimmed) 0.5f else 1f }
                ) { content(focused) }
            }
        }
        if (glass) {
            Box(Modifier.size(width, height)) {
                if (onHold != null) HoldLight(shape, Color.White, progress = { holdLight.value })
                if (isLifted) LiftArrows(Color.White)
            }
        } else {
            front(focused)
            TubeCore(shape, color, lit = { tube })
            if (onHold != null) HoldLight(shape, color, progress = { holdLight.value })
            if (isLifted) LiftArrows(color)
        }
    }
}

/** How an app opened from a tile at [bounds] (on the screen, in pixels) comes on: growing out of it. */
private fun growOut(view: View, bounds: Rect): Bundle =
    ActivityOptions.makeScaleUpAnimation(
        view,
        bounds.left.roundToInt(),
        bounds.top.roundToInt(),
        bounds.width.roundToInt().coerceAtLeast(1),
        bounds.height.roundToInt().coerceAtLeast(1)
    ).toBundle()

/** An app's name under its glass tile, faint at rest when shown all the time, rising into place as the focus lands. */
@Composable
private fun TileCaption(text: String, width: Dp, always: Boolean, lit: () -> Float) {
    val look = LocalHomeLook.current
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = look.font,
            fontWeight = look.weight,
            fontSize = 11.sp,
            color = look.text,
            textAlign = TextAlign.Center,
            shadow = Shadow(Color(0x73000000), blurRadius = 8f)
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .padding(top = 8.dp)
            .graphicsLayer {
                val l = lit()
                alpha = if (always) 0.82f + 0.18f * l else l
                if (!always) translationY = (1f - l) * -3.dp.toPx()
            }
    )
}

/** Where a hold on a tile has got to; kept out of state, as only the keys read it. */
private class Hold {
    var downAt = -1L
    var longPress = false
    var lifted = false
    var job: Job? = null
}

/**
 * The light that runs round a tile's edge while OK is held on it, white hot with a glow in the
 * tile's colour, all the way round when the tile lifts.
 */
@Composable
private fun BoxScope.HoldLight(shape: RoundedCornerShape, color: Color, progress: () -> Float) {
    Spacer(
        Modifier
            .matchParentSize()
            .drawWithCache {
                val edge = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
                val measure = PathMeasure().apply { setPath(edge, false) }
                val run = Path()
                val glow = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                val core = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                onDrawBehind {
                    val p = progress()
                    if (p <= 0f) return@onDrawBehind
                    run.reset()
                    measure.getSegment(0f, measure.length * p, run, true)
                    drawPath(run, color, alpha = 0.4f * p.coerceAtMost(0.15f) / 0.15f, style = glow)
                    drawPath(run, Color.White, style = core)
                }
            }
    )
}

/** Small arrows that breathe at the sides of a lifted tile, pointing where it can go. */
@Composable
private fun BoxScope.LiftArrows(color: Color) {
    val breath = rememberInfiniteTransition(label = "LiftArrows")
    val pulse by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "LiftPulse"
    )
    Spacer(
        Modifier
            .matchParentSize()
            .drawWithCache {
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                val glow = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                val arm = 5.dp.toPx()
                val gap = 9.dp.toPx()
                onDrawBehind {
                    val nudge = 3.dp.toPx() * pulse
                    val y = size.height / 2f
                    for (side in listOf(-1f, 1f)) {
                        val tipX = if (side < 0) -gap - nudge else size.width + gap + nudge
                        val chevron = Path().apply {
                            moveTo(tipX - side * arm, y - arm)
                            lineTo(tipX, y)
                            lineTo(tipX - side * arm, y + arm)
                        }
                        drawPath(chevron, color, alpha = 0.3f, style = glow)
                        drawPath(chevron, Color.White, alpha = 0.7f + 0.3f * pulse, style = stroke)
                    }
                }
            }
    )
}

/** The note at the foot of the screen while a tile is lifted. */
@Composable
private fun MoveHint(grid: Boolean) {
    val look = LocalHomeLook.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(look.pane)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        BasicText(
            text = if (grid) "Move it with the arrows  ·  OK puts it down" else "Move it with ◀ ▶  ·  OK puts it down",
            style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 12.sp, color = look.text)
        )
    }
}

/**
 * Slides a tile in a row to its new place when the row's order changes, instead of jumping
 * there; the grid of All apps does the same with animateItem.
 */
private fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<IntOffset?>(null) }
    var slide by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val to = target ?: return@offset IntOffset.Zero
            val anim = slide ?: Animatable(to, IntOffset.VectorConverter).also { slide = it }
            if (anim.targetValue != to) {
                scope.launch { anim.animateTo(to, spring(dampingRatio = 0.8f, stiffness = 500f)) }
            }
            anim.value - to
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

/**
 * A round button at the top: its sign, and its word beside it all the time with [labelled], or
 * else as the focus lands. On glass it is a pane of the look's glass; with the glass off it is
 * smoked in the style's colours with a rim running from one of its accents to the other. In focus
 * it turns white, or on liquid glass catches the light, and the light sweeps across it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RoundButton(
    text: String,
    labelled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    round: Boolean = false,
    face: @Composable (ink: () -> Color) -> Unit
) {
    val look = LocalHomeLook.current
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    val lit = rememberLit(focused)
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(dampingRatio = 0.62f, stiffness = 380f), label = "ButtonScale")
    val sheen = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        sheen.snapTo(0f)
        if (focused) sheen.animateTo(1f, tween(900, delayMillis = 50, easing = SheenEasing))
    }
    // Dark writing on the white button in focus; liquid glass keeps it white
    val turnsWhite = look.glassMode != HomeGlass.Liquid
    val ink = { if (turnsWhite) lerp(Color.White, GlassInk, lit.value) else Color.White }
    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 0.97f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = look.text,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = look.text,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = look.text
        ),
        // With the glass off the button keeps a little of the style's glow
        glow = if (look.glassy) ClickableSurfaceDefaults.glow() else ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = look.accent.copy(alpha = 0.45f), elevation = 12.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (look.glassy) Modifier.glassPane(look.glassMode, shape, lit = { lit.value }, white = turnsWhite)
                    else Modifier.flairPane(look.pill, look.accent, look.accent2, shape, lit = { lit.value })
                )
                .glassSheen(shape) { sheen.value }
                .height(32.dp)
                .defaultMinSize(minWidth = 32.dp)
                .padding(start = if (round) 4.dp else 10.dp, end = if (round && !labelled && !focused) 4.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            face(ink)
            AnimatedVisibility(
                visible = labelled || focused,
                enter = fadeIn(tween(200, delayMillis = 60)) + expandHorizontally(spring(dampingRatio = 0.8f, stiffness = 420f)),
                exit = fadeOut(tween(120)) + shrinkHorizontally(spring(dampingRatio = 0.9f, stiffness = 520f))
            ) {
                BasicText(
                    text = text,
                    maxLines = 1,
                    style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 11.sp),
                    color = { ink() },
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/** A button's sign, white, or dark on a white button in focus. */
@Composable
private fun ButtonGlyph(glyph: Glyph, ink: () -> Color) {
    NeonGlyph(glyph, ink, Modifier.size(13.dp), plain = true)
}

/** Home or All apps. Pointing at a tab opens it, as on other TV home screens. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TabPill(text: String, selected: Boolean, focusRequester: FocusRequester, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val look = LocalHomeLook.current
    val shape = RoundedCornerShape(50)
    if (look.glassy) {
        GlassTab(text, selected, focusRequester, modifier, onFocus, onClick)
        return
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = look.pane,
            contentColor = look.text,
            focusedContainerColor = look.pane,
            focusedContentColor = look.text
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, look.accent), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = look.accent.copy(alpha = 0.55f), elevation = look.glow * 0.85f))
    ) {
        BasicText(
            text = if (look.upperTitles) text.uppercase() else text,
            style = TextStyle(
                fontFamily = look.font,
                fontWeight = look.weight,
                fontStyle = look.fontStyle,
                fontSize = 13.sp,
                letterSpacing = look.letterSpacing,
                color = if (selected) look.text else look.dim,
                shadow = if (selected && look.glowingText) Shadow(look.accent.copy(alpha = 0.55f), blurRadius = 12f) else null
            ),
            // The neon line under the open tab is [TabGlide]'s, which glides from tab to tab
            modifier = Modifier.padding(start = TabInset, end = TabInset, top = 4.dp, bottom = 7.dp)
        )
    }
}

/** Where a tab lies in its row, in pixels. */
private data class TabSpot(val left: Float, val top: Float, val width: Float, val height: Float)

/** How far a neon tab's word sits in from the ends of its pill, and so its line too. */
private val TabInset = 11.dp

// The end of the open tab's mark that leads the way to the next tab, and the end that follows
private val TabLead = spring<Float>(dampingRatio = 0.8f, stiffness = 700f)
private val TabTrail = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)

/**
 * The mark of the open tab: on glass the pane behind its word, otherwise the neon line under it.
 * It glides from tab to tab rather than going out on one and coming on at the next, the end it
 * travels towards leading and the other following, so it stretches on the way and settles.
 */
@Composable
private fun BoxScope.TabGlide(to: TabSpot?, focused: () -> Boolean) {
    val spot = to ?: return
    val look = LocalHomeLook.current
    val inset = if (look.glassy) 0f else with(LocalDensity.current) { TabInset.toPx() }
    val start = spot.left + inset
    val end = spot.left + spot.width - inset
    // The neon line comes on the first time from the middle of its word outwards
    val left = remember { Animatable(if (look.glassy) start else (start + end) / 2f) }
    val right = remember { Animatable(if (look.glassy) end else (start + end) / 2f) }
    LaunchedEffect(start, end) {
        val opening = left.value == right.value
        val rightwards = start > left.value
        launch { left.animateTo(start, if (rightwards || opening) TabTrail else TabLead) }
        launch { right.animateTo(end, if (rightwards && !opening) TabLead else TabTrail) }
    }
    val on = focused()
    if (look.glassy) {
        // Comes forward with the tab in focus, as far and as springy as the tab itself
        val lit = rememberLit(on)
        val scale by animateFloatAsState(if (on) 1.04f else 1f, spring(dampingRatio = 0.62f, stiffness = 380f), label = "TabGlideScale")
        Spacer(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val width = (right.value - left.value).roundToInt().coerceAtLeast(0)
                    val pane = measurable.measure(Constraints.fixed(width, spot.height.roundToInt()))
                    layout(constraints.maxWidth, constraints.maxHeight) { pane.place(left.value.roundToInt(), spot.top.roundToInt()) }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .glassPane(look.glassMode, RoundedCornerShape(50), lit = { lit.value }, white = look.glassMode != HomeGlass.Liquid)
        )
    } else {
        // Grows with the pill in focus, round the pill's middle
        val scale by animateFloatAsState(if (on) 1.07f else 1f, tween(150), label = "TabGlideScale")
        Spacer(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val l = left.value
                    val r = right.value
                    if (r - l < 0.5f) return@drawBehind
                    val thick = 1.5.dp.toPx()
                    val y = spot.top + spot.height - 5.dp.toPx()
                    scale(scale, pivot = Offset(spot.left + spot.width / 2f, spot.top + spot.height / 2f)) {
                        if (look.glowingText) drawLine(look.accent.copy(alpha = 0.25f), Offset(l, y), Offset(r, y), strokeWidth = thick * 5f, cap = StrokeCap.Round)
                        drawLine(look.accent, Offset(l, y), Offset(r, y), strokeWidth = thick, cap = StrokeCap.Round)
                    }
                }
        )
    }
}

/**
 * A tab on glass: only its word while it is closed, on a pane of glass while open, white (or
 * brighter glass) in focus.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GlassTab(text: String, selected: Boolean, focusRequester: FocusRequester, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val look = LocalHomeLook.current
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    val lit = rememberLit(focused)
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(dampingRatio = 0.62f, stiffness = 380f), label = "TabScale")
    val turnsWhite = look.glassMode != HomeGlass.Liquid
    val ink = {
        when {
            turnsWhite -> lerp(if (selected) Color.White else Color.White.copy(alpha = 0.7f), GlassInk, lit.value)
            selected || focused -> Color.White
            else -> Color.White.copy(alpha = 0.7f)
        }
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 0.97f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = look.text,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = look.text,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = look.text
        )
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                fontFamily = look.font,
                fontWeight = look.weight,
                fontSize = 13.sp,
                letterSpacing = look.letterSpacing,
                shadow = if (selected || focused) null else Shadow(Color(0x59000000), blurRadius = 8f)
            ),
            color = { ink() },
            // The pane behind the open tab is [TabGlide]'s, which glides from tab to tab
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun RowTitle(text: String) {
    val look = LocalHomeLook.current
    if (look.glassy) {
        BasicText(
            text = text,
            style = TextStyle(
                fontFamily = look.font,
                fontWeight = look.weight,
                fontSize = 12.sp,
                letterSpacing = 0.2.sp,
                color = look.text.copy(alpha = 0.86f),
                shadow = Shadow(Color(0x66000000), blurRadius = 8f)
            )
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        NeonText(
            text = if (look.upperTitles) text.uppercase() else text,
            style = TextStyle(
                fontFamily = look.font,
                fontWeight = look.weight,
                fontStyle = look.fontStyle,
                fontSize = 13.sp,
                letterSpacing = look.letterSpacing,
                color = look.text,
                shadow = when {
                    look.glowingText -> Shadow(look.accent2.copy(alpha = 0.6f), blurRadius = 12f)
                    look.shade != null -> Shadow(look.shade, blurRadius = 10f)
                    else -> null
                }
            )
        )
        Spacer(Modifier.width(10.dp))
        // A thin neon rule that runs out from the title and fades away
        Spacer(
            Modifier
                .width(150.dp)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(look.accent2.copy(alpha = 0.55f), Color.Transparent)))
        )
    }
}

@Composable
private fun TileName(text: String, size: TextUnit) {
    val look = LocalHomeLook.current
    BasicText(
        text = text,
        style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = size, color = look.text),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** For an app without an icon of its own: its first letter in a neon ring. */
@Composable
internal fun Monogram(label: String, color: Color, modifier: Modifier = Modifier) {
    val look = LocalHomeLook.current
    Box(modifier, contentAlignment = Alignment.Center) {
        NeonGlyph(Glyph.Ring, color, Modifier.fillMaxSize())
        BasicText(
            text = label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
            style = TextStyle(
                fontFamily = look.font,
                fontWeight = if (look.glowingText) FontWeight.SemiBold else look.weight,
                fontStyle = look.fontStyle,
                fontSize = 12.sp,
                color = color,
                shadow = if (look.glowingText) Shadow(color.copy(alpha = 0.7f), blurRadius = 8f) else null
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
internal fun rememberFocusClock(focused: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            // On the beat the little lights keep, so the two never take turns redrawing the screen
            val now = awaitBeat()
            seconds.floatValue += (now - last) / 1_000_000_000f
            last = now
        }
    }
    return seconds
}

private enum class Scene { Video, Record, Equalizer, Film, Broadcast, Waves, Orbit, Spotlight, Ribbons, Stardust }

/**
 * A little scene behind a big tile's name about what the app is for: a video playing for
 * YouTube, a record turning for YT Music, an equalizer for Spotify, a film strip for Neon Tokyo,
 * and one each for the streaming apps people open most (TileScenes.kt). It holds still while the tile rests and plays while it is in focus. Only the drawing changes,
 * so it never recomposes or lays anything out again.
 */
@Composable
private fun BoxScope.TileScene(app: KnownApp, color: Color, focused: Boolean, shape: RoundedCornerShape) {
    val scene = when (app) {
        HomeCatalog.YouTube -> Scene.Video
        HomeCatalog.YtMusic -> Scene.Record
        HomeCatalog.Spotify -> Scene.Equalizer
        HomeCatalog.NeonTokyo, HomeCatalog.Stremio -> Scene.Film
        else -> when (app.name) {
            "TV4 Play" -> Scene.Broadcast
            "SVT Play", "HBO Max", "Apple TV" -> Scene.Waves
            "Allente", "Plex" -> Scene.Orbit
            "Viaplay", "Prime Video" -> Scene.Spotlight
            "Netflix" -> Scene.Ribbons
            "Disney+", "SkyShowtime" -> Scene.Stardust
            "SmartTube", "YouTube Kids" -> Scene.Video
            else -> return
        }
    }
    // On a pale tile the names are dark, so the film strip they run into is pale too
    val paper = LocalHomeLook.current.text.luminance() < 0.4f
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
                    Scene.Video -> videoScene(color, time, light)
                    Scene.Record -> recordScene(color, time, light)
                    Scene.Equalizer -> equalizerScene(color, time, light)
                    Scene.Film -> filmScene(color, time, light, paper)
                    Scene.Broadcast -> broadcastScene(color, time, light)
                    Scene.Waves -> wavesScene(color, time, light)
                    Scene.Orbit -> orbitScene(color, time, light)
                    Scene.Spotlight -> spotlightScene(color, time, light)
                    Scene.Ribbons -> ribbonsScene(color, time, light)
                    Scene.Stardust -> stardustScene(color, time, light)
                }
            }
    )
}

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
private fun CacheDrawScope.filmScene(color: Color, time: () -> Float, lit: () -> Float, paper: Boolean): DrawResult {
    val w = size.width
    val h = size.height
    val band = 24.dp.toPx()
    val pivot = Offset(w * 0.8f, h * 0.52f)
    val from = w * 0.44f
    val to = w * 1.25f
    val top = pivot.y - band / 2f
    val beam = Brush.radialGradient(0f to color.copy(alpha = 0.3f), 1f to Color.Transparent, center = Offset(w, 0f), radius = h * 1.1f)
    val stock = if (paper) lerp(Color.White, color, 0.1f).copy(alpha = 0.85f) else Color(0xF20E0619)
    val strip = Brush.horizontalGradient(0f to Color.Transparent, 0.22f to stock, startX = from, endX = to)
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
 * The picture behind everything, leaning gently away from the tile in focus, and for Neon the
 * sky and the light on the floor under that tile. It reads where the focus is itself, so a move
 * of the focus, or a focused tile sliding as its rows scroll, redraws this alone rather than
 * the whole home screen.
 */
@Composable
private fun FocusBackdrop(
    style: HomeStyle,
    picture: String?,
    leanX: Float,
    leanY: Float,
    screen: Size,
    wallpaper: () -> NeonWallpaper,
    spot: () -> Spot?,
    spotOn: () -> Boolean,
    ignite: () -> Float
) {
    val at = spot()
    val on = spotOn()
    // The picture lies a little further back than the tiles, and settles in the middle when the
    // focus is up top or on All apps
    val leanTo = at?.takeIf { on }?.bounds?.center?.let { c ->
        Offset(
            -(c.x / screen.width - 0.5f) * 2f * leanX,
            -(c.y / screen.height - 0.5f) * 2f * leanY
        )
    } ?: Offset.Zero
    val lean by animateOffsetAsState(leanTo, tween(1100, easing = FastOutSlowInEasing), label = "BackdropLean")
    // Only the skyline takes its colours from the tile in focus
    val skyline = picture == null && style.video == null && style.backdrop == null
    HomeBackdrop(style, picture, if (skyline) wallpaper() else NeonWallpaper.TOKYO, lean = { lean })
    if (style == HomeStyle.Neon && picture == null) {
        SkySparkles()
        FloorLight(at, on, ignite)
    }
}

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
private fun HomeClock(resumes: () -> Int, light: () -> Float, ignite: () -> Float) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Read here, so coming back to the home screen sets the clock without redrawing all of it
    LaunchedEffect(resumes()) { now = System.currentTimeMillis() }
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
    val look = LocalHomeLook.current
    Column {
        NeonText(
            text = time,
            style = TextStyle(
                fontFamily = look.font,
                fontWeight = look.weight,
                fontStyle = look.fontStyle,
                fontSize = look.clockSize,
                letterSpacing = (-0.5).sp,
                color = look.clockColor,
                shadow = if (look.clockBlur > 0f) Shadow(look.clockGlow, blurRadius = look.clockBlur) else null
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
                fontFamily = look.font,
                fontWeight = look.weight,
                fontStyle = look.fontStyle,
                fontSize = 13.sp,
                letterSpacing = 0.6.sp,
                color = if (look.glowingText) look.dim else look.text.copy(alpha = 0.82f),
                shadow = look.shade?.let { Shadow(it, blurRadius = 10f) }
            ),
            modifier = Modifier.graphicsLayer { alpha = ignite() }
        )
    }
}

/**
 * What fills the screen behind everything: the picture or film the profile sent from a phone, or
 * its style's painting or film with the little things that move in it, or for Neon the skyline.
 * Pictures and films are shown by the TV's video chip ([PlaneSurface]); what is drawn here stands
 * in until the chip shows them, and for good on a TV that can't. A new style fades in over the old.
 */
@Composable
private fun HomeBackdrop(style: HomeStyle, picture: String?, wallpaper: NeonWallpaper, lean: () -> Offset = { Offset.Zero }) {
    val context = LocalContext.current
    val plane = remember { Plane() }
    // A picture from a phone is made into a still for the video chip the first time it shows
    LaunchedEffect(picture) { if (picture != null && !isVideo(picture)) PictureStill.make(context, picture) }
    val made = PictureStill.made
    val media = remember(style, picture, made) { planeMedia(context, style, picture) }
    Box(
        Modifier
            .fillMaxSize()
            // A touch larger than the screen, so leaning never shows its edge
            .graphicsLayer {
                val l = lean()
                translationX = l.x
                translationY = l.y
                scaleX = LEAN_SCALE
                scaleY = LEAN_SCALE
            }
    ) {
        PlaneSurface(plane, media)
        Crossfade(targetState = picture to style, animationSpec = tween(650), label = "HomeBackdrop") { (shownPicture, shown) ->
            val mine = remember(shown, shownPicture, made) { planeMedia(context, shown, shownPicture) }
            StyleCover(plane, mine, shown, shownPicture, wallpaper)
        }
    }
}

private const val LEAN_SCALE = 1.035f

/**
 * How long the home screen has to itself on coming back from an app, before the slow things
 * start again: the window's own animation and the first frames then get the whole TV.
 */
internal const val RETURN_SETTLE_MS = 600L

/**
 * What is drawn for a style or a profile's own [picture] over the video chip: the stand-in until
 * the chip shows [media], then only what moves in it and the shade the tiles need.
 */
@Composable
internal fun StyleCover(plane: Plane, media: PlaneMedia?, style: HomeStyle, picture: String?, wallpaper: NeonWallpaper?) {
    val cover by rememberCover(plane, media)
    val drawn = plane.drawn(media)
    val coverAlpha = { if (drawn) 1f else cover }
    val showCover = drawn || cover > 0f
    Box(Modifier.fillMaxSize()) {
        when {
            picture != null && isVideo(picture) -> VideoShade()
            picture != null -> PictureBackdrop(picture, showCover, coverAlpha, drift = drawn)
            // A film carries its own motion, so the painting's little lights stay off
            style.video != null -> if (showCover && style.backdrop != null) PaintingBackdrop(style.backdrop, coverAlpha)
            style.backdrop == null -> if (wallpaper != null) SkylineBackdrop(wallpaper)
            // Drawn for good, the photo drifts slowly as it did before the video chip showed it
            drawn && style.drift -> DriftingBox(style.driftX, style.driftY) {
                PaintingBackdrop(style.backdrop)
                StyleAmbience(style.ambience, light = style.light)
            }
            else -> {
                if (showCover) PaintingBackdrop(style.backdrop, coverAlpha)
                StyleAmbience(style.ambience, light = style.light)
            }
        }
    }
}

/** Whether a profile's own background is a video rather than a picture, by how it was saved. */
internal fun isVideo(path: String) = path.endsWith(".mp4")

/** A photo and what moves in it, drifting slowly closer and back together so nothing slips. */
@Composable
internal fun DriftingBox(towardX: Float, towardY: Float, content: @Composable () -> Unit) {
    val drift = rememberInfiniteTransition(label = "StyleDrift")
    val zoom by drift.animateFloat(
        initialValue = 1f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(tween(48_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "StyleZoom"
    )
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom
            scaleY = zoom
            transformOrigin = TransformOrigin(towardX, towardY)
        }
    ) { content() }
}

/** A style's painting, decoded once off the main thread, at [alpha] as the video chip takes over. */
@Composable
internal fun PaintingBackdrop(drawable: Int, alpha: () -> Float = { 1f }) {
    val context = LocalContext.current
    var image by remember(drawable) { mutableStateOf(PaintingCache.cached(drawable)) }
    LaunchedEffect(drawable) {
        if (image == null) image = withContext(Dispatchers.Default) { PaintingCache.load(context, drawable) }
    }
    val shown = image ?: return
    Image(
        shown,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            this.alpha = alpha()
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
    )
}

/** The paintings last shown; full screen pictures, so only two are kept. */
private object PaintingCache {
    private val pictures = object : LinkedHashMap<Int, ImageBitmap>(3, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>?): Boolean = size > 2
    }

    @Synchronized
    fun cached(drawable: Int): ImageBitmap? = pictures[drawable]

    fun load(context: Context, drawable: Int): ImageBitmap? {
        cached(drawable)?.let { return it }
        val image = runCatching { android.graphics.BitmapFactory.decodeResource(context.resources, drawable) }
            .getOrNull()?.asImageBitmap() ?: return null
        synchronized(this) { pictures[drawable] = image }
        return image
    }
}

/**
 * A picture someone sent from their phone, darkened where the clock and the tiles sit so they stay
 * easy to read. The picture itself is drawn while [shown], at [alpha] as the video chip takes over,
 * and when it is drawn for good it drifts slowly closer and back so it never sits quite still.
 */
@Composable
internal fun PictureBackdrop(path: String, shown: Boolean, alpha: () -> Float, drift: Boolean) {
    Box(Modifier.fillMaxSize()) {
        if (shown) {
            var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(path) {
                image = withContext(Dispatchers.IO) {
                    // The phone page sends up to 4K, which must not fill the TV's memory
                    decodeSampled(java.io.File(path), 1080)
                }
            }
            val zoom = if (drift) {
                val value by rememberInfiniteTransition(label = "PictureDrift").animateFloat(
                    initialValue = 1f,
                    targetValue = 1.06f,
                    animationSpec = infiniteRepeatable(tween(40_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "PictureZoom"
                )
                value
            } else 1f
            image?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        this.alpha = alpha()
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                )
            }
        }
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)))
                drawCircle(
                    Brush.radialGradient(0f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent, center = Offset(size.width * 0.12f, size.height * 0.14f), radius = size.width * 0.32f),
                    radius = size.width * 0.32f,
                    center = Offset(size.width * 0.12f, size.height * 0.14f)
                )
            }
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
    val look = LocalHomeLook.current
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
                    .panelIn().homePanel(look, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NeonText(
                    text = title,
                    style = TextStyle(
                        fontFamily = look.font,
                        fontWeight = if (look.glowingText) FontWeight.SemiBold else look.weight,
                        fontStyle = look.fontStyle,
                        fontSize = 18.sp,
                        color = look.text,
                        shadow = if (look.glowingText) Shadow(look.accent.copy(alpha = 0.6f), blurRadius = 16f) else null
                    )
                )
                if (inputs.isEmpty()) {
                    BasicText(
                        "This TV shows no inputs to pick from.",
                        style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 12.sp, color = look.dim)
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
                        style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 10.5.sp, color = look.dim),
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
    val look = LocalHomeLook.current
    val shape = RoundedCornerShape(9.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(38.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = look.text.copy(alpha = 0.08f),
            contentColor = look.text,
            focusedContainerColor = look.accent.copy(alpha = 0.2f),
            focusedContentColor = look.text
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, look.accent), shape = shape)
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
                        .background(if (on) look.accent2 else look.dim.copy(alpha = 0.35f), CircleShape)
                )
                Spacer(Modifier.width(10.dp))
            }
            // The input's name gets all the room the names of the consoles on it leave
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = title,
                    style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 13.sp, color = look.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (detail != null) {
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = detail,
                        style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 11.sp, color = look.dim),
                        maxLines = 1
                    )
                }
            }
            if (chosen) {
                Spacer(Modifier.width(8.dp))
                BasicText("✓", style = TextStyle(fontSize = 14.sp, color = look.accent))
            }
        }
    }
}

/**
 * A menu coming on: rising out of a little depth into place as it fades in, rather than being
 * there all at once, quick enough that it is ready to use as soon as it shows.
 */
internal fun Modifier.panelIn(): Modifier = composed {
    val come = remember { Animatable(0f) }
    LaunchedEffect(Unit) { come.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 480f)) }
    graphicsLayer {
        val t = come.value
        alpha = t.coerceIn(0f, 1f)
        scaleX = 0.95f + 0.05f * t
        scaleY = 0.95f + 0.05f * t
        translationY = (1f - t) * 10.dp.toPx()
    }
}

/** A dialog's panel: the look's glass, lit neon edges for Neon, or a quiet solid card in the other styles. */
internal fun Modifier.homePanel(look: HomeLook, shape: RoundedCornerShape): Modifier = when {
    // On glass the menus are of the same glass as the tiles, darker for the writing on them
    look.glassy -> glassPane(look.glassMode, shape, lit = { 0f }, panel = true)
    look.glowingText -> neonPanel(shape)
    else -> background(lerp(look.tileBottom, look.tileTop, 0.4f).copy(alpha = 0.98f), shape).border(1.dp, look.accent.copy(alpha = 0.45f), shape)
}

/**
 * Gets the TV ready to take AirPlay from an iPhone: opens the receiver app, which shows the TV's
 * name to pick under Screen Mirroring or the AirPlay button. Without one, the Play Store page for
 * AirScreen, after which the same button opens it.
 */
private fun startAirPlay(context: Context) {
    if (!HomeApps.isInstalled(context, HomeCatalog.AirPlay)) {
        Toast.makeText(context, "Install AirScreen once, then this button turns AirPlay on", Toast.LENGTH_LONG).show()
    }
    HomeApps.open(context, HomeCatalog.AirPlay)
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
