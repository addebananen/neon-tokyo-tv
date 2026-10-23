package com.lumera.app.projectivy

import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.lumera.app.R
import com.lumera.app.ui.components.NeonPalette
import com.lumera.app.ui.components.NeonType
import com.lumera.app.ui.settings.SettingRow
import com.lumera.app.ui.settings.VoidSegmentedControl
import com.lumera.app.ui.theme.DefaultThemes
import com.lumera.app.ui.theme.LumeraTheme
import kotlinx.coroutines.delay
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperProviderContract

/**
 * Settings for the Projectivy wallpaper. Projectivy opens it from its wallpaper settings; the
 * background shows the skyline being picked, and the night drive loop while that row has focus.
 */
class NeonWallpaperSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = NeonWallpaperPrefs(applicationContext)
        val projectivyInstalled = runCatching { packageManager.getApplicationInfo(PROJECTIVY, 0) }.isSuccess
        setContent {
            LumeraTheme(theme = DefaultThemes.NEON_TOKYO) {
                WallpaperSettings(
                    prefs = prefs,
                    projectivyInstalled = projectivyInstalled,
                    onChanged = ::notifyProjectivy
                )
            }
        }
    }

    // Only needed when this page was opened from somewhere other than Projectivy's own settings
    private fun notifyProjectivy() {
        sendBroadcast(Intent(WallpaperProviderContract.ACTION_WALLPAPER_PROVIDER_UPDATED).apply {
            setPackage(PROJECTIVY)
            putExtra(WallpaperProviderContract.EXTRA_PROVIDER_ID, getString(R.string.nt_wallpaper_provider_uuid))
            putExtra(WallpaperProviderContract.EXTRA_UPDATE_REASON, WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
        })
    }

    private companion object {
        const val PROJECTIVY = "com.spocky.projengmenu"
    }
}

@Composable
private fun WallpaperSettings(
    prefs: NeonWallpaperPrefs,
    projectivyInstalled: Boolean,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var followFocus by remember { mutableStateOf(prefs.followFocus) }
    var idleDrive by remember { mutableStateOf(prefs.idleDrive) }
    // The skyline in the background; null plays the night drive loop
    var preview by remember { mutableStateOf<NeonWallpaper?>(NeonWallpaper.TOKYO) }
    var lastColour by remember { mutableStateOf(NeonWallpaper.TOKYO) }
    val firstRow = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstRow.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(NeonPalette.Night)) {
        Crossfade(targetState = preview, animationSpec = tween(700), label = "wallpaper") { shown ->
            if (shown == null) {
                LoopingVideo(
                    uri = Uri.parse(NeonWallpaperProviderService.nightDrive(context).uri),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Loaded through the same android.resource address Projectivy gets
                AsyncImage(
                    model = NeonWallpaperProviderService.image(context, shown).uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Keeps the text readable over the brightest skylines
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to NeonPalette.Night.copy(alpha = 0.94f),
                    0.42f to NeonPalette.Night.copy(alpha = 0.78f),
                    0.68f to Color.Transparent
                )
            )
        )

        Column(
            Modifier
                .fillMaxHeight()
                .width(470.dp)
                .padding(start = 48.dp, top = 36.dp, bottom = 28.dp)
        ) {
            Text("PROJECTIVY WALLPAPER", style = NeonType.eyebrow(12.sp))
            Text(
                "Neon Tokyo",
                style = NeonType.display(40.sp),
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                "The skyline takes the colour of the app you point at, and starts rolling when the TV sits idle.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = NeonPalette.Lilac,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
            )

            SettingRow("Colour follows focus") {
                VoidSegmentedControl(
                    options = listOf("On" to true, "Off" to false),
                    selectedOption = followFocus,
                    onOptionSelected = {
                        followFocus = it
                        prefs.followFocus = it
                        onChanged()
                    },
                    modifier = Modifier.focusRequester(firstRow),
                    blockUp = true,
                    onFocus = { preview = lastColour }
                )
            }
            Spacer(Modifier.height(14.dp))
            SettingRow("Night drive when idle") {
                VoidSegmentedControl(
                    options = listOf("On" to true, "Off" to false),
                    selectedOption = idleDrive,
                    onOptionSelected = {
                        idleDrive = it
                        prefs.idleDrive = it
                        onChanged()
                    },
                    onFocus = { preview = null }
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("COLOURS", style = NeonType.eyebrow(11.sp, NeonPalette.Lilac))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeonWallpaper.entries.forEach { colour ->
                    Swatch(
                        colour = colour,
                        selected = preview == colour,
                        onFocus = {
                            preview = colour
                            lastColour = colour
                        }
                    )
                }
            }
            Text(
                text = preview?.let { "${it.label}: ${it.apps}" }
                    ?: "Night drive: the grid rolls towards you while Projectivy is idle",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = NeonPalette.Text.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp)
            )

            Spacer(Modifier.weight(1f))
            Text(
                if (projectivyInstalled) {
                    "In Projectivy: Settings › Appearance › Wallpaper, then pick Neon Tokyo."
                } else {
                    "Projectivy Launcher isn't installed. Get it from Google Play, then pick Neon Tokyo under Settings › Appearance › Wallpaper."
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = if (projectivyInstalled) NeonPalette.Lilac else NeonPalette.Sun
            )
        }
    }
}

@Composable
private fun Swatch(colour: NeonWallpaper, selected: Boolean, onFocus: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = Color(colour.accent)
    val scale by animateFloatAsState(if (focused) 1.18f else 1f, tween(180), label = "swatch")
    val ring by animateColorAsState(
        when {
            focused -> Color.White
            selected -> accent.copy(alpha = 0.7f)
            else -> Color.White.copy(alpha = 0.18f)
        },
        tween(180),
        label = "ring"
    )

    LaunchedEffect(focused) { if (focused) onFocus() }

    Box(
        modifier = Modifier
            .size(34.dp)
            .scale(scale)
            .shadow(if (focused) 14.dp else 0.dp, CircleShape, ambientColor = accent, spotColor = accent)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.55f), NeonPalette.Night)))
            .border(2.dp, ring, CircleShape)
            .clickable(interactionSource = interaction, indication = null) { onFocus() },
        contentAlignment = Alignment.Center
    ) {}
}

/** Plays a muted looping video behind the page, on a TextureView so Compose can fade it. */
@Composable
private fun LoopingVideo(uri: Uri, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var player: MediaPlayer? = null
                    private var surface: Surface? = null

                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        val target = Surface(texture).also { surface = it }
                        player = runCatching {
                            MediaPlayer().apply {
                                setDataSource(context, uri)
                                setSurface(target)
                                isLooping = true
                                setVolume(0f, 0f)
                                setOnPreparedListener { it.start() }
                                setOnErrorListener { _, _, _ -> true }
                                prepareAsync()
                            }
                        }.getOrNull()
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        player?.release()
                        player = null
                        surface?.release()
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        }
    )
}
