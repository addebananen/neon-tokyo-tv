package com.lumera.app.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import com.lumera.app.dream.NeonDreamPreviewActivity
import com.lumera.app.dream.NeonDreamService
import com.lumera.app.projectivy.NeonWallpaperSettingsActivity

/** Personalization rows for the screensaver and the Projectivy wallpaper. */
@Composable
fun NeonExtrasSettings(onGoBack: () -> Unit) {
    val context = LocalContext.current
    var dreamActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { dreamActive = isNeonDreamActive(context) }

    SettingRow("Screensaver") {
        Column {
            Text(
                if (dreamActive) {
                    "Neon Tokyo night is your screensaver: the skyline, a neon clock and shooting stars."
                } else {
                    "Neon Tokyo night: the skyline, a neon clock and shooting stars. The TV setup script makes it your screensaver."
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White.copy(0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            VoidSegmentedControl(
                options = listOf("Preview" to 0),
                selectedOption = -1,
                onOptionSelected = {
                    context.startActivity(Intent(context, NeonDreamPreviewActivity::class.java))
                },
                onBack = onGoBack
            )
        }
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

// Android 11 lets apps read this; newer versions may not, which only changes the wording above
private fun isNeonDreamActive(context: Context): Boolean = runCatching {
    Settings.Secure.getString(context.contentResolver, "screensaver_components")
        .orEmpty()
        .split(',')
        .mapNotNull { ComponentName.unflattenFromString(it.trim()) }
        .any { it.packageName == context.packageName && it.className == NeonDreamService::class.java.name }
}.getOrDefault(false)
