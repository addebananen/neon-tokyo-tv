package com.neontokyo.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.LocalModel
import com.neontokyo.desktop.core.AppInfo
import com.neontokyo.desktop.player.Player
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics
import com.neontokyo.desktop.theme.NeonTokens
import com.neontokyo.desktop.theme.neonDisplay
import com.neontokyo.desktop.theme.neonHeading
import kotlinx.coroutines.launch
import uniffi.neon_core.coreVersion

/** Profile, theme, addons, player and versions. */
@Composable
fun SettingsScreen() {
    val model = LocalModel.current
    val theme = LocalNeon.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Metrics.edge.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Settings", style = neonDisplay()) }

        item { Section("Profile") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                model.activeProfile?.let { p ->
                    ProfileBadge(p, Metrics.bodySize * 2.6f, showName = false)
                    Text(p.name, color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = Metrics.bodySize.sp)
                }
                PillButton("Switch profile") { model.select(null) }
            }
        }

        item { Section("Theme") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeonTokens.themes.forEach { t ->
                    FocusCard({ model.setTheme(t.id) }, shape = CircleShape) {
                        Row(
                            Modifier.background(if (t.id == model.themeId) theme.primary.copy(alpha = 0.25f) else theme.surface.copy(alpha = 0.7f))
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(t.primary))
                            Text(t.name, color = theme.text, fontSize = Metrics.bodySize.sp)
                            if (t.id == model.themeId) Text("✓", color = theme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { Section("Addons") }
        item { Text("Addons are shared by every profile.", color = theme.muted, fontSize = (Metrics.bodySize * 0.8f).sp) }
        items(model.addonUrls, key = { it }) { base ->
            val m = model.manifests.firstOrNull { it.baseUrl == base }
            Row(
                Modifier.widthIn(max = 1000.dp).fillMaxWidth().clip(RoundedCornerShape(NeonTokens.shapeCardRadius.dp))
                    .background(theme.panel.copy(alpha = 0.7f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(m?.name ?: "Addon", color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = Metrics.bodySize.sp)
                    Text(
                        m?.let { "${it.catalogs.size} catalogs · " + it.resources.joinToString(", ") } ?: base,
                        color = theme.muted, fontSize = (Metrics.bodySize * 0.75f).sp, maxLines = 1,
                    )
                }
                PillButton("Remove") { model.removeAddon(base) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    input, { input = it.trim() },
                    singleLine = true,
                    placeholder = { Text("https://…/manifest.json") },
                    colors = neonFieldColors(),
                    modifier = Modifier.width(560.dp),
                )
                PillButton(if (adding) "Adding…" else "Add addon", prominent = true) {
                    if (adding || input.isBlank()) return@PillButton
                    adding = true
                    scope.launch {
                        error = runCatching { model.addAddon(input) }.fold({ input = ""; null }, { "Could not add that addon. Check the address." })
                        adding = false
                    }
                }
            }
        }
        error?.let { item { Text(it, color = theme.error) } }

        item { Section("Player") }
        item {
            Text(
                "Films open in ${Player.playerName}. Progress is saved for Continue watching while mpv plays.",
                color = theme.muted, fontSize = (Metrics.bodySize * 0.85f).sp,
            )
        }

        item { Section("About") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Neon Tokyo ${AppInfo.version}", color = theme.text, fontSize = Metrics.bodySize.sp)
                Text("Core ${coreVersion()}", color = theme.muted, fontSize = (Metrics.bodySize * 0.85f).sp)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = neonHeading(), modifier = Modifier.padding(top = 18.dp))
}
