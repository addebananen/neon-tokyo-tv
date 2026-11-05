package com.neontokyo.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.LocalModel
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics
import com.neontokyo.desktop.theme.hexColor
import com.neontokyo.desktop.theme.neonDisplay
import uniffi.neon_core.Profile

/**
 * "Who's watching?" Each profile has its own history, watchlist and next episodes; addons are
 * shared, as in the Android app. Right-click (or the Delete key) on a profile removes it.
 */
@Composable
fun ProfilePicker() {
    val model = LocalModel.current
    val theme = LocalNeon.current
    var adding by remember { mutableStateOf(model.liveProfiles.isEmpty()) }
    var name by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<Profile?>(null) }
    val first = remember { FocusRequester() }

    Column(
        Modifier.fillMaxSize().padding(Metrics.edge.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.rowSpacing.dp, Alignment.CenterVertically),
    ) {
        Text(if (model.liveProfiles.isEmpty()) "Welcome to Neon Tokyo" else "Who's watching?", style = neonDisplay((Metrics.titleSize * 1.4f).sp))
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.cardSpacing.dp)) {
            model.liveProfiles.forEachIndexed { i, p ->
                Box(if (i == 0) Modifier.focusRequester(first) else Modifier) {
                    FocusCard({ model.select(p) }, shape = RoundedCornerShape(24.dp)) {
                        ProfileBadge(p, Metrics.posterWidth * 0.8f)
                    }
                }
            }
            FocusCard({ name = ""; adding = true }, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size((Metrics.posterWidth * 0.8f).dp).clip(CircleShape).border(2.dp, theme.muted, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("+", color = theme.muted, fontSize = (Metrics.titleSize * 1.4f).sp) }
                    Text("Add profile", color = theme.muted, fontSize = (Metrics.bodySize * 0.85f).sp)
                }
            }
        }
        if (adding) {
            val field = remember { FocusRequester() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    name, { name = it.take(24) },
                    singleLine = true,
                    label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create(name) { model.createProfile(it) } }),
                    colors = neonFieldColors(),
                    modifier = Modifier.width(320.dp).focusRequester(field),
                )
                PillButton("Create", prominent = true) { create(name) { model.createProfile(it) } }
                if (model.liveProfiles.isNotEmpty()) PillButton("Cancel") { adding = false }
            }
            LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
        } else if (model.liveProfiles.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                model.liveProfiles.forEach { p -> PillButton("Remove ${p.name}") { confirmRemove = p } }
            }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        }
        confirmRemove?.let { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Remove ${p.name} and their history?", color = theme.text)
                PillButton("Remove", prominent = true) { model.remove(p); confirmRemove = null }
                PillButton("Keep") { confirmRemove = null }
            }
        }
    }
}

private fun create(name: String, make: (String) -> Unit) {
    val n = name.trim()
    if (n.isNotEmpty()) make(n)
}

@Composable
fun neonFieldColors() = LocalNeon.current.let { t ->
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = t.text, unfocusedTextColor = t.text,
        focusedBorderColor = t.primary, unfocusedBorderColor = t.muted,
        focusedLabelColor = t.primary, unfocusedLabelColor = t.muted, cursorColor = t.primary,
        focusedContainerColor = t.panel.copy(alpha = 0.6f), unfocusedContainerColor = t.panel.copy(alpha = 0.4f),
    )
}

/** A round badge with the profile's photo, or its first letter in its colour. */
@Composable
fun ProfileBadge(profile: Profile, size: Float, showName: Boolean = true) {
    val theme = LocalNeon.current
    val c = hexColor(profile.color)
    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(c, c.copy(alpha = 0.7f).compositeOver(Color.Black)))),
            contentAlignment = Alignment.Center,
        ) {
            if (profile.photo != null) NeonImage(profile.photo, Modifier.fillMaxSize())
            else Text(profile.name.take(1).uppercase(), style = neonDisplay((size * 0.45f).sp).copy(color = Color.White, shadow = null))
        }
        if (showName) Text(profile.name, color = theme.text, fontWeight = FontWeight.Medium, fontSize = (Metrics.bodySize * 0.85f).sp)
    }
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}
