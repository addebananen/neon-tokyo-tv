package com.lumera.app.launcher

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.lumera.app.BuildConfig
import com.lumera.app.data.update.AppUpdateManager
import com.lumera.app.data.update.UpdateState
import com.lumera.app.ui.components.NeonText
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

/** The app's updater, for the home screen, which Hilt does not build. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HomeUpdatesEntryPoint {
    fun updates(): AppUpdateManager
}

internal fun homeUpdates(context: Context): AppUpdateManager =
    EntryPointAccessors.fromApplication(context.applicationContext, HomeUpdatesEntryPoint::class.java).updates()

/** The version in the pill at the top: "nt.77" from "0.2.0-nt.77". The app and the home screen are one. */
internal fun shortVersion(name: String = BuildConfig.VERSION_NAME): String = name.substringAfterLast('-')

/**
 * Which version this is and whether a newer one is out, checked as it opens. A newer one is put
 * on with one press; Android asks once per TV to let Neon Tokyo install it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun HomeUpdatesDialog(updates: AppUpdateManager, onDismiss: () -> Unit) {
    val look = LocalHomeLook.current
    val state by updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (state !is UpdateState.Downloading && state !is UpdateState.ReadyToInstall) updates.checkForUpdate()
    }
    // The focus goes to the button that fits what the check found, as the buttons change
    LaunchedEffect(state::class) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .panelIn().homePanel(look, RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeonText(
                    text = "Updates",
                    style = TextStyle(
                        fontFamily = look.font,
                        fontWeight = if (look.glowingText) FontWeight.SemiBold else look.weight,
                        fontStyle = look.fontStyle,
                        fontSize = 18.sp,
                        color = look.text,
                        shadow = if (look.glowingText) Shadow(look.accent.copy(alpha = 0.6f), blurRadius = 16f) else null
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        "This version",
                        style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 12.sp, color = look.dim),
                        modifier = Modifier.weight(1f)
                    )
                    BasicText(
                        BuildConfig.VERSION_NAME,
                        style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = look.accent)
                    )
                }
                val status = when (val s = state) {
                    UpdateState.Idle, UpdateState.Checking -> "Looking for a newer version…"
                    UpdateState.UpToDate -> "You're on the newest version."
                    is UpdateState.UpdateAvailable -> "Version ${s.info.versionName} is out."
                    is UpdateState.Downloading -> if (s.totalMb > 0f) "Downloading… %.0f of %.0f MB".format(s.downloadedMb, s.totalMb) else "Downloading…"
                    is UpdateState.ReadyToInstall -> "Downloaded. Android asks you to confirm the install."
                    is UpdateState.Error -> s.message
                }
                BasicText(status, style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 13.sp, color = look.text))
                (state as? UpdateState.Downloading)?.let { d ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(look.text.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = d.progress.coerceIn(0f, 1f)
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                }
                                .background(look.accent, RoundedCornerShape(2.dp))
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val s = state) {
                        is UpdateState.UpdateAvailable -> UpdateButton("Update now", Modifier.focusRequester(first)) {
                            scope.launch { updates.downloadAndInstall(s.info.apkUrl) }
                        }
                        UpdateState.Checking, is UpdateState.Downloading -> Unit
                        else -> UpdateButton("Check again", Modifier.focusRequester(first)) {
                            scope.launch { updates.checkForUpdate() }
                        }
                    }
                    UpdateButton("Close", if (state is UpdateState.Checking || state is UpdateState.Downloading) Modifier.focusRequester(first) else Modifier, onClick = onDismiss)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun UpdateButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = LocalHomeLook.current
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = look.pill,
            contentColor = look.text,
            focusedContainerColor = look.pillFocused,
            focusedContentColor = look.text
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.5.dp, look.text.copy(alpha = 0.18f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, look.accent), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = look.accent.copy(alpha = 0.55f), elevation = look.glow * 0.85f))
    ) {
        BasicText(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = look.text)
        )
    }
}
