package com.neontokyo.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.*
import com.neontokyo.desktop.theme.FocusStyle
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.neonDisplay

/**
 * The glass rail (the tower on top, the profile, the tabs with one neon mark that glides to
 * the open one, a glowing pill when an update is out) and the open screen, which slides in
 * from the side it was opened toward.
 */
@Composable
fun Shell() {
    val nav = LocalNavigator.current
    val model = LocalModel.current
    val theme = LocalNeon.current
    val glow = theme.focus == FocusStyle.Glow
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxHeight().width(104.dp)
                .background(Brush.horizontalGradient(listOf(theme.background.copy(alpha = 0.88f), theme.background.copy(alpha = 0.55f), Color.Transparent)))
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // the brand: the tower lit in neon, the name under it
            val pulse = rememberInfiniteTransition(label = "Brand")
            val lit by pulse.animateFloat(0.82f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "BrandLit")
            TowerGlyph(if (glow) theme.primary else Color.White, Modifier.size(30.dp, 44.dp), lit)
            Text("NEON", style = neonDisplay(11.sp).copy(letterSpacing = 3.sp), modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(22.dp))
            model.activeProfile?.let { p ->
                FocusCard({ model.select(null) }, shape = CircleShape) { ProfileBadge(p, 46f, showName = false) }
            }
            Spacer(Modifier.height(20.dp))
            val tabs = listOf(
                Triple(Tab.Home, Icons.Filled.Home, "Home"),
                Triple(Tab.Search, Icons.Filled.Search, "Search"),
                Triple(Tab.Watchlist, Icons.Filled.Bookmark, "Watchlist"),
                Triple(Tab.Settings, Icons.Filled.Settings, "Settings"),
            )
            val ys = remember { mutableStateMapOf<Tab, Float>() }
            val selected = nav.tab
            val markY by animateFloatAsState(ys[selected] ?: 0f, spring(dampingRatio = 0.72f, stiffness = 420f), label = "RailMark")
            val markOn by animateFloatAsState(if (ys[selected] != null) 1f else 0f, tween(300), label = "RailMarkOn")
            Box {
                // the mark: a short neon tube at the rail's edge and a soft pane behind the tab
                Box(Modifier.matchParentSize().drawBehind {
                    if (markOn <= 0f) return@drawBehind
                    val itemH = 64.dp.toPx()
                    drawRoundRect(
                        theme.primary.copy(alpha = 0.16f * markOn),
                        topLeft = Offset(10.dp.toPx(), markY),
                        size = Size(size.width - 20.dp.toPx(), itemH),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                    )
                    val x = 3.dp.toPx()
                    drawRoundRect(theme.primary.copy(alpha = 0.35f * markOn), topLeft = Offset(x - 3.dp.toPx(), markY + itemH * 0.22f),
                        size = Size(9.dp.toPx(), itemH * 0.56f), cornerRadius = CornerRadius(5.dp.toPx()))
                    drawRoundRect(theme.primary.copy(alpha = markOn), topLeft = Offset(x, markY + itemH * 0.25f),
                        size = Size(3.dp.toPx(), itemH * 0.5f), cornerRadius = CornerRadius(2.dp.toPx()))
                })
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    tabs.forEach { (tab, icon, label) ->
                        Box(Modifier.onGloballyPositioned { ys[tab] = it.positionInParent().y }) {
                            RailItem(icon, label, selected == tab && (tab != Tab.Home || nav.stack.isEmpty())) { nav.switch(tab) }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(model.updates.available != null, enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut()) {
                UpdatePill { nav.switch(Tab.Settings) }
            }
            if (model.accounts.syncing) {
                val spin = rememberInfiniteTransition(label = "Sync")
                val a by spin.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "SyncDot")
                Box(Modifier.padding(top = 12.dp).size(8.dp).clip(CircleShape).background(theme.secondary.copy(alpha = a)))
            }
        }
        Box(Modifier.fillMaxSize()) {
            val top = nav.stack.lastOrNull()
            val depth = nav.stack.size
            val lastDepth = remember { intArrayOf(0) }
            val forward = depth >= lastDepth[0]
            SideEffect { lastDepth[0] = depth }
            AnimatedContent(
                top ?: nav.tab,
                transitionSpec = {
                    (fadeIn(tween(320, delayMillis = 60)) + slideInHorizontally(tween(420, easing = NeonMotion.FastOutSlowIn)) { if (forward) it / 14 else -it / 14 })
                        .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { if (forward) -it / 30 else it / 30 })
                },
                label = "Screen",
            ) { screen ->
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
    val ink by androidx.compose.animation.animateColorAsState(if (selected) theme.text else theme.muted, tween(220), label = "RailInk")
    NeonTile(onClick, radius = 16.dp, tilt = false, pop = 1.08f) { lit ->
        Column(
            Modifier.size(76.dp, 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, label, tint = if (selected) theme.primary else if (lit) theme.text else ink, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = if (lit) theme.text else ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun UpdatePill(onClick: () -> Unit) {
    val theme = LocalNeon.current
    val pulse = rememberInfiniteTransition(label = "Update")
    val a by pulse.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "UpdateGlow")
    NeonTile(onClick, radius = 14.dp, tilt = false, glow = theme.secondary) {
        Column(
            Modifier.width(80.dp).background(Brush.verticalGradient(listOf(theme.secondary.copy(alpha = 0.28f * a), theme.primary.copy(alpha = 0.22f * a))))
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.SystemUpdateAlt, "Update", tint = theme.secondary, modifier = Modifier.size(22.dp).graphicsLayer { alpha = 0.6f + 0.4f * a })
            Text("Update", color = theme.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
