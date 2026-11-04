package com.neontokyo.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.*
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.NeonTokens

/** The side rail (profile badge on top, as on Android) and the open screen. */
@Composable
fun Shell() {
    val nav = LocalNavigator.current
    val model = LocalModel.current
    val theme = LocalNeon.current
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxHeight().width(96.dp).background(theme.panel.copy(alpha = 0.6f)).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            model.activeProfile?.let { p ->
                FocusCard({ model.select(null) }, shape = CircleShape) { ProfileBadge(p, 52f, showName = false) }
            }
            Spacer(Modifier.height(18.dp))
            RailItem(Icons.Filled.Home, "Home", nav.tab == Tab.Home && nav.stack.isEmpty()) { nav.switch(Tab.Home) }
            RailItem(Icons.Filled.Search, "Search", nav.tab == Tab.Search) { nav.switch(Tab.Search) }
            RailItem(Icons.Filled.Bookmark, "Watchlist", nav.tab == Tab.Watchlist) { nav.switch(Tab.Watchlist) }
            RailItem(Icons.Filled.Settings, "Settings", nav.tab == Tab.Settings) { nav.switch(Tab.Settings) }
        }
        Box(Modifier.fillMaxSize()) {
            val top = nav.stack.lastOrNull()
            AnimatedContent(top ?: nav.tab, transitionSpec = {
                fadeIn(tween(NeonTokens.motionPageMs.toInt())) togetherWith fadeOut(tween(NeonTokens.motionPageMs.toInt() / 2))
            }) { screen ->
                when (screen) {
                    is Screen.Details -> DetailsScreen(screen.item)
                    is Screen.Sources -> SourcesScreen(screen.request)
                    Tab.Home -> HomeScreen()
                    Tab.Search -> SearchScreen()
                    Tab.Watchlist -> WatchlistScreen()
                    Tab.Settings -> SettingsScreen()
                    else -> HomeScreen()
                }
            }
        }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalNeon.current
    FocusCard(onClick, shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.width(76.dp).background(if (selected) theme.primary.copy(alpha = 0.22f) else theme.panel.copy(alpha = 0f)).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, label, tint = if (selected) theme.primary else theme.muted, modifier = Modifier.size(28.dp))
            Text(label, color = if (selected) theme.text else theme.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
