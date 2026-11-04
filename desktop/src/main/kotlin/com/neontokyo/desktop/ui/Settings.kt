package com.neontokyo.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import uniffi.neon_core.CoreException
import uniffi.neon_core.coreVersion

/** Profile, accounts (Stremio, Trakt), look and motion, addons, player, updates. */
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
        contentPadding = PaddingValues(start = Metrics.edge.dp, end = Metrics.edge.dp, top = 48.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Text("Settings", style = neonDisplay((Metrics.titleSize * 1.4f).sp)) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.widthIn(max = 1180.dp)) {
                GlassPanel(Modifier.weight(1f)) {
                    Section("Profile")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        model.activeProfile?.let { p ->
                            ProfileBadge(p, 56f, showName = false)
                            Text(p.name, color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = (Metrics.bodySize * 1.1f).sp)
                        }
                        Spacer(Modifier.weight(1f))
                        PillButton("Switch profile") { model.select(null) }
                    }
                }
                UpdatesPanel(Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.widthIn(max = 1180.dp)) {
                StremioPanel(Modifier.weight(1f))
                TraktPanel(Modifier.weight(1f))
            }
        }

        item {
            GlassPanel(Modifier.widthIn(max = 1180.dp).fillMaxWidth()) {
                Section("Look and feel")
                Choice("Theme", NeonTokens.themes.map { it.name to (it.id == model.themeId) }) { i -> model.setTheme(NeonTokens.themes[i].id) }
                Choice("Motion", MotionLevel.values().map { it.title to (it == model.motion) }) { i -> model.setMotion(MotionLevel.values()[i]) }
                Choice("Sounds", listOf("On" to model.sounds, "Off" to !model.sounds)) { i -> model.setSounds(i == 0) }
            }
        }

        item {
            GlassPanel(Modifier.widthIn(max = 1180.dp).fillMaxWidth()) {
                Section("Addons")
                Note("Shared by every profile. A Stremio sign-in brings the account's addons in too.")
            }
        }
        items(model.addonUrls, key = { it }) { base ->
            val m = model.manifests.firstOrNull { it.baseUrl == base }
            Row(
                Modifier.widthIn(max = 1180.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(theme.panel.copy(alpha = 0.7f)).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(m?.name ?: "Addon", color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = Metrics.bodySize.sp)
                    Text(m?.let { "${it.catalogs.size} catalogs · " + it.resources.joinToString(", ") } ?: base,
                        color = theme.muted, fontSize = (Metrics.bodySize * 0.75f).sp, maxLines = 1)
                }
                PillButton("Remove") { model.removeAddon(base) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    input, { input = it.trim() }, singleLine = true,
                    placeholder = { Text("https://…/manifest.json") },
                    colors = neonFieldColors(), shape = RoundedCornerShape(14.dp),
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

        item {
            GlassPanel(Modifier.widthIn(max = 1180.dp).fillMaxWidth()) {
                Section("Player")
                Note("Films open in ${Player.playerName}, full screen. Where you stop is saved for Continue watching, Stremio and Trakt.")
            }
        }
        item {
            Text("Neon Tokyo ${AppInfo.version}  ·  core ${coreVersion()}", color = theme.muted, fontSize = (Metrics.bodySize * 0.8f).sp)
        }
    }
}

@Composable
private fun UpdatesPanel(modifier: Modifier) {
    val model = LocalModel.current
    val theme = LocalNeon.current
    val scope = rememberCoroutineScope()
    val u = model.updates
    GlassPanel(modifier) {
        Section("Updates")
        val next = u.available
        if (next == null) {
            Note(u.status.ifEmpty { "Neon Tokyo ${AppInfo.version}" })
            PillButton(if (u.checking) "Checking…" else "Check for updates") { scope.launch { u.check() } }
        } else {
            Text("Version ${next.version} is out", color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = (Metrics.bodySize * 1.1f).sp)
            next.notes.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(3).forEach { Note(it.removePrefix("- ")) }
            val p = u.progress
            if (p != null) {
                ProgressLine(p, Modifier.padding(vertical = 6.dp))
                Note(if (p >= 1f) "Installing, Neon Tokyo opens again by itself" else "Downloading ${(p * 100).toInt()} %")
            } else {
                PillButton("Download and install", prominent = true) { u.install() }
            }
            if (u.status.isNotEmpty()) Note(u.status)
        }
    }
}

@Composable
private fun StremioPanel(modifier: Modifier) {
    val model = LocalModel.current
    val theme = LocalNeon.current
    val acc = model.accounts
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    GlassPanel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("Stremio")
            if (acc.stremio != null) LinkedDot()
        }
        val link = acc.stremio
        if (link == null) {
            Note("Sign in to bring in this account's addons and keep Continue watching and the library the same as in Stremio.")
            OutlinedTextField(email, { email = it.trim() }, singleLine = true, label = { Text("Email") }, colors = neonFieldColors(),
                shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, singleLine = true, label = { Text("Password") }, colors = neonFieldColors(),
                shape = RoundedCornerShape(14.dp), visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { }), modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(if (busy) "Signing in…" else "Sign in", prominent = true) {
                    if (busy || email.isBlank() || password.isEmpty()) return@PillButton
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            acc.stremioSignIn(email, password)
                            password = ""
                        } catch (e: CoreException.BadResponse) {
                            error = e.reason
                        } catch (e: Exception) {
                            error = "Stremio could not be reached"
                        }
                        busy = false
                    }
                }
                Note("The password goes only to Stremio and is not kept.")
            }
            error?.let { Text(it, color = theme.error, fontSize = (Metrics.bodySize * 0.85f).sp) }
        } else {
            Text(link.email, color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = Metrics.bodySize.sp)
            Note(acc.stremioStatus)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(if (acc.syncing) "Syncing…" else "Sync now", prominent = true) { scope.launch { acc.syncAll(full = true) } }
                PillButton("Sign out") { acc.stremioSignOut() }
            }
        }
    }
}

@Composable
private fun TraktPanel(modifier: Modifier) {
    val model = LocalModel.current
    val theme = LocalNeon.current
    val acc = model.accounts
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    GlassPanel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("Trakt")
            if (acc.trakt != null) LinkedDot()
        }
        val code = acc.traktCode
        when {
            acc.trakt != null -> {
                Note(acc.traktStatus)
                Note("What you watch here is scrobbled to Trakt, and what you watch in other apps (Omni, the TV) shows up here.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(if (acc.syncing) "Syncing…" else "Sync now", prominent = true) { scope.launch { acc.syncAll() } }
                    PillButton("Sign out") { acc.traktSignOut() }
                }
            }
            code != null -> {
                Note("Open ${code.verificationUrl} and type this code:")
                val pulse = rememberInfiniteTransition(label = "Code")
                val a by pulse.animateFloat(0.75f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "CodeGlow")
                Text(
                    code.userCode.chunked(4).joinToString(" "),
                    style = neonDisplay((Metrics.titleSize * 1.5f).sp).copy(fontFamily = FontFamily.Monospace, letterSpacing = 6.sp),
                    modifier = Modifier.alpha(a),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Open trakt.tv", prominent = true) {
                        clipboard.setText(AnnotatedString(code.userCode))
                        Player.openWeb(code.verificationUrl)
                    }
                    PillButton("Cancel") { acc.traktCancel() }
                }
                Note("The code is copied, so you can paste it. This page signs in by itself once it is entered.")
            }
            else -> {
                Note("Connect Trakt to keep watched episodes and progress the same everywhere, including apps like Omni.")
                PillButton("Connect Trakt", prominent = true) { acc.traktSignIn() }
                if (acc.traktStatus.isNotEmpty()) Text(acc.traktStatus, color = theme.error, fontSize = (Metrics.bodySize * 0.85f).sp)
            }
        }
    }
}

@Composable
private fun LinkedDot() {
    val theme = LocalNeon.current
    Box(Modifier.size(9.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color.White, theme.secondary))))
}

@Composable
private fun Choice(title: String, options: List<Pair<String, Boolean>>, pick: (Int) -> Unit) {
    val theme = LocalNeon.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = theme.muted, fontSize = Metrics.bodySize.sp, modifier = Modifier.width(110.dp))
        options.forEachIndexed { i, (label, on) -> PillButton(label, prominent = on) { pick(i) } }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = LocalNeon.current.muted, fontSize = (Metrics.bodySize * 0.85f).sp, lineHeight = (Metrics.bodySize * 1.25f).sp)
}

@Composable
private fun Section(title: String) {
    Text(title, style = neonHeading())
}
