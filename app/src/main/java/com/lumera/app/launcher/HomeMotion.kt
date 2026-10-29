package com.lumera.app.launcher

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * How much Neon Home moves, as the Motion panel sets it for the whole TV: how far the picture
 * behind the tiles leans as the focus moves, how a tile pops when the focus lands on it, how
 * lively the apps' icons are, and whether the intro plays when the TV is switched on. Read as
 * state, so a change shows at once behind the panel.
 */
internal object HomeMotion {
    private const val PREFS = "neon_home"

    /** How far the picture leans, in dp across and down. */
    enum class Lean(val key: String, val title: String, val across: Float, val down: Float) {
        Off("off", "Off", 0f, 0f),
        Subtle("subtle", "Subtle", 4f, 1.5f),
        Normal("normal", "Normal", 8f, 3f),
        Strong("strong", "Strong", 14f, 5f)
    }

    /** How a tile grows when the focus lands on it, and whether it overshoots a little. */
    enum class Pop(val key: String, val title: String, val scale: Float, val damping: Float) {
        Still("still", "Still", 1.03f, 1f),
        Soft("soft", "Soft", 1.06f, 0.85f),
        Bouncy("bouncy", "Bouncy", 1.07f, 0.55f)
    }

    /** How much the apps' own icons do in focus: from nothing to all of it. */
    enum class Icons(val key: String, val title: String, val amount: Float) {
        Off("off", "Off", 0f),
        Calm("calm", "Calm", 0.5f),
        Full("full", "Full", 1f)
    }

    var lean by mutableStateOf(Lean.Subtle)
        private set
    var pop by mutableStateOf(Pop.Bouncy)
        private set
    var icons by mutableStateOf(Icons.Full)
        private set
    var wakeIntro by mutableStateOf(true)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lean = Lean.values().firstOrNull { it.key == prefs.getString("motion.lean", null) } ?: Lean.Subtle
        pop = Pop.values().firstOrNull { it.key == prefs.getString("motion.pop", null) } ?: Pop.Bouncy
        icons = Icons.values().firstOrNull { it.key == prefs.getString("motion.icons", null) } ?: Icons.Full
        wakeIntro = prefs.getBoolean("motion.wakeIntro", true)
    }

    fun set(context: Context, lean: Lean = this.lean, pop: Pop = this.pop, icons: Icons = this.icons, wakeIntro: Boolean = this.wakeIntro) {
        this.lean = lean
        this.pop = pop
        this.icons = icons
        this.wakeIntro = wakeIntro
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("motion.lean", lean.key)
            .putString("motion.pop", pop.key)
            .putString("motion.icons", icons.key)
            .putBoolean("motion.wakeIntro", wakeIntro)
            .apply()
    }
}

/** The Motion panel: one row of choices for each of [HomeMotion]'s settings. */
@Composable
internal fun HomeMotionDialog(context: Context, onDismiss: () -> Unit) {
    val look = LocalHomeLook.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(640.dp)
                    .panelIn().homePanel(look, RoundedCornerShape(16.dp))
                    .padding(horizontal = 26.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PanelTitle("Motion", look)
                MotionRow("BACKGROUND", "How far the picture moves as you go from app to app") {
                    HomeMotion.Lean.values().forEachIndexed { i, lean ->
                        PanelButton(
                            lean.title,
                            look,
                            primary = lean == HomeMotion.lean,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier
                        ) { HomeMotion.set(context, lean = lean) }
                    }
                }
                MotionRow("TILES", "How a tile grows when you land on it") {
                    HomeMotion.Pop.values().forEach { pop ->
                        PanelButton(pop.title, look, primary = pop == HomeMotion.pop) { HomeMotion.set(context, pop = pop) }
                    }
                }
                MotionRow("APP ICONS", "The little animation each app's own icon does") {
                    HomeMotion.Icons.values().forEach { icons ->
                        PanelButton(icons.title, look, primary = icons == HomeMotion.icons) { HomeMotion.set(context, icons = icons) }
                    }
                }
                MotionRow("TV SWITCHED ON", "The neon intro as the TV comes on") {
                    PanelButton("On", look, primary = HomeMotion.wakeIntro) { HomeMotion.set(context, wakeIntro = true) }
                    PanelButton("Off", look, primary = !HomeMotion.wakeIntro) { HomeMotion.set(context, wakeIntro = false) }
                }
                Row {
                    PanelButton("Done", look, primary = true, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun MotionRow(title: String, note: String, buttons: @Composable () -> Unit) {
    val look = LocalHomeLook.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelNote(title, look)
            BasicText(
                "   $note",
                style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 11.sp, color = look.dim)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { buttons() }
    }
}
