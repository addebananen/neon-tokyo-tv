package com.lumera.app.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.lumera.app.dream.DreamVideo
import com.lumera.app.dream.NeonDreamPreviewActivity
import com.lumera.app.dream.NeonDreamService
import com.lumera.app.dream.NeonDreamStyle
import com.lumera.app.launcher.NeonHomeActivity
import com.lumera.app.projectivy.NeonWallpaperSettingsActivity

/** Personalization rows for the home screen, the screensaver and the Projectivy wallpaper. */
@Composable
fun NeonExtrasSettings(onGoBack: () -> Unit) {
    val context = LocalContext.current
    var dreamActive by remember { mutableStateOf(false) }
    var homeActive by remember { mutableStateOf(false) }
    var dreamStyle by remember { mutableStateOf(NeonDreamStyle.get(context)) }
    var choosingVideo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        dreamActive = isNeonDreamActive(context)
        homeActive = isNeonHomeActive(context)
    }

    SettingRow("Home screen") {
        Column {
            Text(
                if (homeActive) {
                    "Neon Home is your TV's home screen: your apps, your consoles on HDMI 1 to 6, lit up when they are on."
                } else {
                    "Neon Home: your apps, and your consoles on HDMI 1 to 6, lit up when they are on. The TV setup script makes it your home screen."
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White.copy(0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            VoidSegmentedControl(
                options = listOf("Open Neon Home" to 0),
                selectedOption = -1,
                onOptionSelected = {
                    context.startActivity(Intent(context, NeonHomeActivity::class.java))
                },
                onBack = onGoBack
            )
        }
    }
    Spacer(Modifier.height(15.dp))
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
    Spacer(Modifier.height(15.dp))

    SettingRow("Screensaver") {
        Column {
            val scene = when (dreamStyle) {
                NeonDreamStyle.HOME -> "Home style: the picture and motion of the style on Neon Home, under a quiet clock, or the video a home profile sent for it. Neon gets the city drive."
                NeonDreamStyle.SKYLINE -> "Skyline: the Neon Tokyo night, a neon clock and shooting stars."
                NeonDreamStyle.VIDEO -> "Your video: it plays on a loop, without sound, whenever the TV rests."
                else -> "City drive: a car cruising an endless neon Tokyo, district after district, rain and all, under a clock that says you'll be back soon."
            }
            Text(
                if (dreamActive) "$scene It is your screensaver." else "$scene The TV setup script makes it your screensaver.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White.copy(0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            VoidSegmentedControl(
                options = listOf(
                    "Home style" to NeonDreamStyle.HOME,
                    "City drive" to NeonDreamStyle.DRIVE,
                    "Skyline" to NeonDreamStyle.SKYLINE,
                    "Your video" to NeonDreamStyle.VIDEO
                ),
                selectedOption = dreamStyle,
                onOptionSelected = {
                    if (it == NeonDreamStyle.VIDEO && !DreamVideo.exists(context)) {
                        // Nothing to play yet: the dialog picks it, and the style follows once it is on
                        choosingVideo = true
                    } else {
                        dreamStyle = it
                        NeonDreamStyle.set(context, it)
                    }
                },
                onBack = onGoBack
            )
            Spacer(Modifier.height(12.dp))
            VoidSegmentedControl(
                options = if (dreamStyle == NeonDreamStyle.VIDEO) listOf("Preview" to 0, "Change video" to 1) else listOf("Preview" to 0),
                selectedOption = -1,
                onOptionSelected = {
                    if (it == 1) choosingVideo = true
                    else context.startActivity(Intent(context, NeonDreamPreviewActivity::class.java))
                },
                onBack = onGoBack
            )
        }
    }
    if (choosingVideo) {
        DreamVideoDialog(
            onDismissRequest = { choosingVideo = false },
            onVideoSet = { dreamStyle = NeonDreamStyle.VIDEO }
        )
    }
    Spacer(Modifier.height(15.dp))
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
    Spacer(Modifier.height(15.dp))

    SettingRow("Projectivy wallpaper") {
        Column {
            Text(
                "With Projectivy Launcher as the home screen, the skyline wallpaper takes the colour of the app you point at.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White.copy(0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            VoidSegmentedControl(
                options = listOf("Colours and settings" to 0),
                selectedOption = -1,
                onOptionSelected = {
                    context.startActivity(Intent(context, NeonWallpaperSettingsActivity::class.java))
                },
                onBack = onGoBack
            )
        }
    }
}

/** True when the Home button opens Neon Home, which only the TV setup script can arrange. */
private fun isNeonHomeActive(context: Context): Boolean = runCatching {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    @Suppress("DEPRECATION")
    val target = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
    target?.packageName == context.packageName
}.getOrDefault(false)

// Android 11 lets apps read this; newer versions may not, which only changes the wording above
private fun isNeonDreamActive(context: Context): Boolean = runCatching {
    Settings.Secure.getString(context.contentResolver, "screensaver_components")
        .orEmpty()
        .split(',')
        .mapNotNull { ComponentName.unflattenFromString(it.trim()) }
        .any { it.packageName == context.packageName && it.className == NeonDreamService::class.java.name }
}.getOrDefault(false)
