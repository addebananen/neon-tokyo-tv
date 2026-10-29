package com.lumera.app.control

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumera.app.BuildConfig
import com.lumera.app.R
import com.lumera.app.launcher.GlassInk
import com.lumera.app.launcher.Glyph
import com.lumera.app.launcher.NeonGlyph
import com.lumera.app.overlay.NeonOverlayService
import com.lumera.app.overlay.OverlayPrefs
import com.lumera.app.ui.components.NeonPalette

/**
 * Setting the Control Center up on the TV itself, with the remote, no computer: the overlay
 * service is switched on under the TV's Accessibility settings, and Android asks once whether
 * Neon Tokyo may see Wi-Fi names. Neon Home shows it once when something is missing.
 */
class ControlSetupActivity : ComponentActivity() {
    private var serviceOn by mutableStateOf(false)
    private var namesOn by mutableStateOf(false)

    private val ask = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        markSeen(this)
        refresh()
        if (intent.getBooleanExtra(EXTRA_NAMES, false) && !namesOn) askNames()
        setContent { Sheet() }
    }

    override fun onResume() {
        super.onResume()
        // Back from the TV's Accessibility page, switched on or not
        refresh()
    }

    private fun refresh() {
        serviceOn = OverlayPrefs.serviceOn(this) || NeonOverlayService.running
        namesOn = names(this)
    }

    private fun turnOn() {
        if (OverlayPrefs.canSwitch(this) && OverlayPrefs.switchService(this, true)) {
            refresh()
            return
        }
        Toast.makeText(this, "Pick \"Neon Tokyo overlays\" and switch it on, then press Back", Toast.LENGTH_LONG).show()
        if (!OverlayPrefs.openAccessibility(this)) {
            Toast.makeText(this, "Open the TV's Settings › Accessibility and switch on Neon Tokyo overlays", Toast.LENGTH_LONG).show()
        }
    }

    private fun askNames() = ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

    @Composable
    private fun Sheet() {
        // The focus starts on the first thing left to do
        val first = remember { FocusRequester() }
        val second = remember { FocusRequester() }
        val done = remember { FocusRequester() }
        LaunchedEffect(serviceOn, namesOn) {
            runCatching { (if (!serviceOn) first else if (!namesOn) second else done).requestFocus() }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(30.dp)
            Column(
                Modifier
                    .width(540.dp)
                    .background(Color(0xF2100E18), shape)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)), shape)
                    .padding(horizontal = 30.dp, vertical = 26.dp)
            ) {
                BasicText("Control Center", style = TextStyle(fontFamily = Body, fontSize = 28.sp, color = Color.White))
                Spacer(Modifier.height(6.dp))
                BasicText(
                    "Hold Home on any remote, over any app: the sound, Wi-Fi, who's watching, Do Not Disturb, a sleep timer and Power Off.",
                    style = TextStyle(fontFamily = Body, fontSize = 15.sp, color = Color.White.copy(alpha = 0.65f), lineHeight = 21.sp)
                )
                Spacer(Modifier.height(22.dp))
                Step(
                    Glyph.Power, "Hold Home",
                    if (serviceOn) "On" else "Switch on \"Neon Tokyo overlays\" on the TV's Accessibility page",
                    done = serviceOn, action = "Turn on", onClick = ::turnOn, modifier = Modifier.focusRequester(first)
                )
                Spacer(Modifier.height(12.dp))
                Step(
                    Glyph.Wifi, "Wi-Fi names",
                    if (namesOn) "On" else "Android only names networks to apps it lets see the location. Nothing is located.",
                    done = namesOn, action = "Allow", onClick = ::askNames, modifier = Modifier.focusRequester(second)
                )
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Pill(if (serviceOn && namesOn) "Done" else "Not now", onClick = ::finish, modifier = Modifier.focusRequester(done))
                }
            }
        }
    }

    @Composable
    private fun Step(glyph: Glyph, title: String, detail: String, done: Boolean, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(if (done) NeonPalette.Cyan else Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                NeonGlyph(if (done) Glyph.Check else glyph, if (done) GlassInk else Color.White, Modifier.size(24.dp), plain = true)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText(title, style = TextStyle(fontFamily = Body, fontSize = 17.sp, color = Color.White))
                BasicText(detail, style = TextStyle(fontFamily = Body, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), lineHeight = 18.sp))
            }
            Spacer(Modifier.width(14.dp))
            if (!done) Pill(action, onClick = onClick, modifier = modifier)
        }
    }

    @Composable
    private fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
        var focused by remember { mutableStateOf(false) }
        val lit by animateFloatAsState(if (focused) 1f else 0f, tween(150), label = "SetupLit")
        val shape = RoundedCornerShape(50)
        Box(
            modifier
                .onFocusChanged { focused = it.isFocused }
                .graphicsLayer { val s = 1f + 0.06f * lit; scaleX = s; scaleY = s }
                .background(lerp(Color.White.copy(alpha = 0.12f), Color.White, lit), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(text, style = TextStyle(fontFamily = Body, fontSize = 15.sp, color = lerp(Color.White, GlassInk, lit)))
        }
    }

    companion object {
        private val Body = FontFamily(Font(R.font.outfit_medium, FontWeight.Medium))
        private const val PREFS = "neon_control"
        private const val SEEN = "setup_seen_version"
        /** Opened from the Wi-Fi list: ask for the names at once. */
        const val EXTRA_NAMES = "names"

        fun names(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        private fun markSeen(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(SEEN, BuildConfig.VERSION_CODE).apply()

        /**
         * Neon Home shows the setup once per installed version, after each update, while the
         * Control Center can't open yet; once all is on it stays away. Never on the
         * emulator, where the TV test drives the home screen with the remote and a card would
         * stand in its way; the test opens it by name instead.
         */
        fun dueOnHome(context: Context): Boolean {
            if (Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") return false
            if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(SEEN, 0) == BuildConfig.VERSION_CODE) return false
            return !(OverlayPrefs.serviceOn(context) && names(context))
        }

        fun open(context: Context, names: Boolean = false) = runCatching {
            context.startActivity(
                Intent(context, ControlSetupActivity::class.java).putExtra(EXTRA_NAMES, names).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }
}
