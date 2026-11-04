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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.neontokyo.desktop.core.AppModel
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.NeonTokens
import com.neontokyo.desktop.ui.*

fun main() = application {
    val state = rememberWindowState(placement = WindowPlacement.Maximized, size = DpSize(1600.dp, 900.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "Neon Tokyo",
        state = state,
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
        NeonApp(model)
    }
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

@Composable
fun NeonApp(model: AppModel) {
    val nav = remember { Navigator() }
    val theme = model.theme
    LaunchedEffect(Unit) { model.refreshAddons() }
    MaterialTheme(colorScheme = darkColorScheme(primary = theme.primary, background = theme.background, surface = theme.panel)) {
        CompositionLocalProvider(LocalNeon provides theme, LocalNavigator provides nav, LocalModel provides model) {
            val focus = LocalFocusManager.current
            Box(
                Modifier.fillMaxSize().onKeyEvent {
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
                NeonBackground()
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
