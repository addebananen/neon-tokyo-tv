package com.lumera.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumera.app.overlay.ChordAction
import com.lumera.app.overlay.NeonOverlayService
import com.lumera.app.overlay.OverlayPrefs
import kotlinx.coroutines.delay

/** Picture diagnostics and the gaming HUD: switching them on, and how they behave. */
@Composable
fun OverlaySettings(onGoBack: () -> Unit) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(NeonOverlayService.running) }
    var gestures by remember { mutableStateOf(OverlayPrefs.gestures(context)) }
    var hudSeconds by remember { mutableStateOf(OverlayPrefs.hudSeconds(context)) }
    var hudOnConsole by remember { mutableStateOf(OverlayPrefs.hudOnConsole(context)) }
    // Comes back from the TV's Accessibility page switched on, or not
    LaunchedEffect(Unit) {
        while (true) {
            on = NeonOverlayService.running
            delay(1_000L)
        }
    }

    SettingRow("Picture diagnostics and gaming HUD") {
        Column {
            Text(
                if (on) {
                    "Hold Up and Down together for 2 seconds over any app, film or console to see what the TV gets and what the panel does. Hold Left and Right for the gaming HUD. The same buttons again, or Back, put them away."
                } else {
                    "Over any app, film or console: hold Up and Down for picture diagnostics, Left and Right for the gaming HUD. Turn on \"Neon Tokyo overlays\" in the TV's Accessibility settings first; it only watches the remote's buttons."
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White.copy(0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            VoidSegmentedControl(
                options = if (on) {
                    listOf("Show diagnostics" to 0, "Show gaming HUD" to 1, "Turn off" to 3)
                } else {
                    listOf("Turn on" to 2)
                },
                selectedOption = -1,
                onOptionSelected = {
                    when (it) {
                        0 -> NeonOverlayService.show(ChordAction.Diagnostics)
                        1 -> NeonOverlayService.show(ChordAction.GamingHud)
                        2 -> if (OverlayPrefs.canSwitch(context)) {
                            OverlayPrefs.switchService(context, true)
                        } else if (!OverlayPrefs.openAccessibility(context)) {
                            Toast.makeText(context, "Open the TV's Settings › Accessibility and turn on Neon Tokyo overlays", Toast.LENGTH_LONG).show()
                        }
                        3 -> if (OverlayPrefs.canSwitch(context)) {
                            OverlayPrefs.switchService(context, false)
                        } else {
                            OverlayPrefs.openAccessibility(context)
                        }
                    }
                },
                onBack = onGoBack
            )
        }
    }
    if (on) {
        Spacer(Modifier.height(15.dp))
        SettingRow("Button chords") {
            VoidSegmentedControl(
                options = listOf("On" to true, "Off" to false),
                selectedOption = gestures,
                onOptionSelected = {
                    gestures = it
                    OverlayPrefs.setGestures(context, it)
                    NeonOverlayService.reload()
                },
                onBack = onGoBack
            )
        }
        Spacer(Modifier.height(15.dp))
        SettingRow("Gaming HUD hides after") {
            VoidSegmentedControl(
                options = OverlayPrefs.HUD_CHOICES.map { (if (it == 0) "Stays" else "$it s") to it },
                selectedOption = hudSeconds,
                onOptionSelected = {
                    hudSeconds = it
                    OverlayPrefs.setHudSeconds(context, it)
                },
                onBack = onGoBack
            )
        }
        Spacer(Modifier.height(15.dp))
        SettingRow("Gaming HUD when a console starts") {
            Column {
                Text(
                    "Shows the HUD for a moment when Neon Home switches the TV to a console.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = Color.White.copy(0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                VoidSegmentedControl(
                    options = listOf("On" to true, "Off" to false),
                    selectedOption = hudOnConsole,
                    onOptionSelected = {
                        hudOnConsole = it
                        OverlayPrefs.setHudOnConsole(context, it)
                    },
                    onBack = onGoBack
                )
            }
        }
    }
}
