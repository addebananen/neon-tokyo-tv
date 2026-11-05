package com.neontokyo.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.neontokyo.desktop.core.AppModel
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.NeonTokens
import com.neontokyo.desktop.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

fun main() = application {
    // CI screenshots (NEON_SHOT=file.png): a 1920 x 1080 window at the screen's corner
    val shot = System.getenv("NEON_SHOT")?.ifBlank { null }
    val state = if (shot != null) {
        rememberWindowState(placement = WindowPlacement.Floating, position = WindowPosition(0.dp, 0.dp), size = DpSize(1920.dp, 1080.dp))
    } else {
        rememberWindowState(placement = WindowPlacement.Maximized, size = DpSize(1600.dp, 900.dp))
    }
    var closing by remember { mutableStateOf(false) }
    Window(
        // switching off like an old TV, then gone
        onCloseRequest = { if (closing) exitApplication() else closing = true },
        title = "Neon Tokyo",
        state = state,
        undecorated = shot != null,
        icon = painterResource("icon.png"),
        onPreviewKeyEvent = {
            // F11 toggles full screen, like a browser
            if (it.type == KeyEventType.KeyDown && it.key == Key.F11) {
                state.placement = if (state.placement == WindowPlacement.Fullscreen) WindowPlacement.Maximized else WindowPlacement.Fullscreen
                true
            } else false
        },
    ) {
        val scope = rememberCoroutineScope()
        val model = remember { AppModel(scope) }
        Box(Modifier.fillMaxSize()) {
            NeonApp(model)
            if (closing) PowerOff { exitApplication() }
        }
        if (shot != null) LaunchedEffect(Unit) { takeScreenshot(window, shot); exitApplication() }
    }
}

/** For CI: waits NEON_SHOT_AFTER ms (pictures load, NEON_KEYS are pressed), then saves what the window shows. */
private suspend fun takeScreenshot(window: java.awt.Window, path: String) {
    val robot = java.awt.Robot()
    // the pointer off the cards, so only the keys light one
    robot.mouseMove(window.x + window.width - 2, window.y + 2)
    delay(System.getenv("NEON_SHOT_AFTER")?.toLongOrNull() ?: 20_000L)
    val image = robot.createScreenCapture(window.bounds)
    withContext(Dispatchers.IO) { javax.imageio.ImageIO.write(image, "png", java.io.File(path)) }
}

/** Where the app is: a tab, and the screens opened on top of it. */
sealed interface Screen {
    data class Details(val item: uniffi.neon_core.MetaItem) : Screen
    data class Sources(val request: SourceRequest) : Screen
}

enum class Tab { Home, Search, Watchlist, Settings }

class Navigator {
    var tab by mutableStateOf(Tab.Home)
    var stack by mutableStateOf(listOf<Screen>())
    fun open(s: Screen) { stack = stack + s }
    fun back(): Boolean = if (stack.isEmpty()) false else { stack = stack.dropLast(1); true }
    fun switch(t: Tab) { tab = t; stack = emptyList() }
}

val LocalNavigator = staticCompositionLocalOf { Navigator() }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NeonApp(model: AppModel) {
    val nav = remember { Navigator() }
    val theme = model.theme
    val spot = remember { Spot() }
    LaunchedEffect(Unit) {
        model.refreshAddons()
        // CI screenshots open a screen directly
        val parts = model.launchScreen?.split(":", limit = 3).orEmpty()
        when {
            parts.firstOrNull() == "details" && parts.size == 3 ->
                nav.open(Screen.Details(uniffi.neon_core.MetaItem(parts[2], parts[1], "", null, null, null, null, null, null, emptyList(), null)))
            parts.firstOrNull() == "settings" -> nav.switch(Tab.Settings)
            parts.firstOrNull() == "search" -> nav.switch(Tab.Search)
            parts.firstOrNull() == "profiles" -> model.select(null)
        }
    }
    // CI screenshots: NEON_KEYS (`Down,Down,Right`) move the focus like the arrow keys, 3 s before the shot
    val keys = remember { System.getenv("NEON_KEYS").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() } }
    if (keys.isNotEmpty()) {
        val focus = LocalFocusManager.current
        LaunchedEffect(Unit) {
            delay(((System.getenv("NEON_SHOT_AFTER")?.toLongOrNull() ?: 20_000L) - 3_000L - keys.size * 450L).coerceAtLeast(1_000L))
            keys.forEach { k ->
                when (k) {
                    "Down" -> focus.moveFocus(FocusDirection.Down)
                    "Up" -> focus.moveFocus(FocusDirection.Up)
                    "Left" -> focus.moveFocus(FocusDirection.Left)
                    "Right" -> focus.moveFocus(FocusDirection.Right)
                }
                delay(450)
            }
        }
    }
    // a new screen: the light on the floor waits for the focus to land again
    LaunchedEffect(nav.tab, nav.stack.size) { spot.on = false }
    MaterialTheme(colorScheme = darkColorScheme(primary = theme.primary, background = theme.background, surface = theme.panel)) {
        CompositionLocalProvider(
            LocalNeon provides theme, LocalNavigator provides nav, LocalModel provides model,
            LocalSpot provides spot, LocalMotion provides model.motion,
        ) {
            val focus = LocalFocusManager.current
            Box(
                Modifier.fillMaxSize()
                    .onPointerEvent(PointerEventType.Move) { e ->
                        val p = e.changes.firstOrNull()?.position ?: return@onPointerEvent
                        spot.pointer = Offset(p.x / size.width.coerceAtLeast(1), p.y / size.height.coerceAtLeast(1))
                    }
                    .onKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (it.key) {
                        // arrow keys move between cards, as the remote does on the TV (text fields use them first)
                        Key.DirectionLeft -> focus.moveFocus(FocusDirection.Left)
                        Key.DirectionRight -> focus.moveFocus(FocusDirection.Right)
                        Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
                        Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
                        Key.Escape, Key.Backspace -> nav.back()
                        else -> false
                    }
                }
            ) {
                NeonWorld(dim = if (model.activeProfile == null) 0f else 0.18f)
                FloorLight()
                AnimatedContent(model.activeProfile == null, transitionSpec = {
                    fadeIn(tween(NeonTokens.motionPageMs.toInt())) togetherWith fadeOut(tween(NeonTokens.motionPageMs.toInt()))
                }) { picking ->
                    if (picking) ProfilePicker() else Shell()
                }
                AnimatedVisibility(model.showIntro, exit = fadeOut(tween(400))) {
                    Intro { model.showIntro = false }
                }
            }
        }
    }
}

val LocalModel = staticCompositionLocalOf<AppModel> { error("no model") }
