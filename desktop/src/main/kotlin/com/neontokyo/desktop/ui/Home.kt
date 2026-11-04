package com.neontokyo.desktop.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.LocalModel
import com.neontokyo.desktop.LocalNavigator
import com.neontokyo.desktop.Screen
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics
import com.neontokyo.desktop.theme.NeonTokens
import com.neontokyo.desktop.theme.neonDisplay
import uniffi.neon_core.ContinueEntry
import uniffi.neon_core.MetaItem
import uniffi.neon_core.WatchlistItem

/** The home screen: the focused title large on top, then Continue watching, the watchlist and every addon row. */
@Composable
fun HomeScreen() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    val theme = LocalNeon.current
    var hero by remember { mutableStateOf<MetaItem?>(null) }
    val first = model.rows.firstNotNullOfOrNull { model.rowItems[it.key]?.firstOrNull() }

    Box(Modifier.fillMaxSize()) {
        Hero(hero ?: first, Modifier.fillMaxWidth().height(Metrics.heroHeight.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = (Metrics.heroHeight * 0.62f).dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(Metrics.rowSpacing.dp),
        ) {
            val cw = model.continueItems
            if (cw.isNotEmpty()) item("cw") {
                RowSection("Continue watching") {
                    items(cw, key = { it.id }) { e ->
                        FocusCard({ nav.open(Screen.Details(e.asMeta())) }, onFocused = { hero = e.asMeta() }) { ContinueCard(e) }
                    }
                }
            }
            val wl = model.watchlistItems
            if (wl.isNotEmpty()) item("wl") {
                RowSection("Watchlist") {
                    items(wl, key = { it.id }) { w ->
                        FocusCard({ nav.open(Screen.Details(w.asMeta())) }, onFocused = { hero = w.asMeta() }) { PosterArt(w.title, w.poster) }
                    }
                }
            }
            items(model.rows, key = { it.key }) { row ->
                val list = model.rowItems[row.key]
                if (!list.isNullOrEmpty()) {
                    RowSection(row.title) {
                        items(list, key = { it.id }) { item ->
                            FocusCard({ nav.open(Screen.Details(item)) }, onFocused = { hero = item }) {
                                PosterArt(item.name, item.poster, model.progress(item.id), model.isWatched(item.id))
                            }
                        }
                    }
                }
            }
            if (model.rows.isEmpty()) item("empty") {
                Text(
                    if (model.loadingAddons) "Loading your addons…" else "No catalogs yet. Add an addon in Settings.",
                    color = theme.muted, modifier = Modifier.padding(Metrics.edge.dp),
                )
            }
        }
    }
}

/** The big picture: background, title or logo, facts and the start of the story. */
@Composable
fun Hero(item: MetaItem?, modifier: Modifier = Modifier) {
    val theme = LocalNeon.current
    Crossfade(item, modifier, animationSpec = tween(NeonTokens.motionPageMs.toInt())) { it ->
        Box(Modifier.fillMaxSize()) {
            NeonImage(it?.background ?: it?.poster, Modifier.fillMaxSize().drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, theme.background.copy(alpha = 0.7f), theme.background)))
                drawRect(Brush.horizontalGradient(listOf(theme.background.copy(alpha = 0.9f), Color.Transparent), endX = size.width * 0.6f))
            })
            if (it != null) {
                Column(
                    Modifier.align(Alignment.TopStart).padding(start = Metrics.edge.dp, top = 56.dp).widthIn(max = 720.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (it.logo != null) {
                        NeonImage(it.logo, Modifier.height(110.dp).widthIn(max = 420.dp).fillMaxWidth(), ContentScale.Fit)
                    } else {
                        Text(it.name, style = neonDisplay((Metrics.titleSize * 1.5f).sp), maxLines = 2)
                    }
                    FactsLine(it)
                    it.description?.let { d ->
                        Text(d, color = theme.text.copy(alpha = 0.85f), fontSize = Metrics.bodySize.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun FactsLine(item: MetaItem) {
    val theme = LocalNeon.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        item.imdbRating?.let { Badge("IMDb $it", strong = true) }
        Text(
            listOfNotNull(item.releaseInfo, item.runtime, item.genres.take(3).joinToString(", ").ifEmpty { null }).joinToString("  ·  "),
            color = theme.muted, fontSize = (Metrics.bodySize * 0.9f).sp, fontWeight = FontWeight.Medium, maxLines = 1,
        )
    }
}

@Composable
fun ContinueCard(e: ContinueEntry) {
    val w = Metrics.posterWidth * 1.6f
    Box(Modifier.size(w.dp, (w * 9 / 16).dp)) {
        NeonImage(e.background ?: e.poster, Modifier.fillMaxSize())
        Box(Modifier.matchParentSize().drawWithContent {
            drawContent(); drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
        })
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e.season != null && e.episode != null) Badge("S${e.season}:E${e.episode}")
                if (e.hasNewEpisode) Badge("NEW", strong = true)
            }
            Text(e.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = Metrics.bodySize.sp, maxLines = 1)
            if (e.progress > 0.01f) ProgressLine(e.progress)
        }
    }
}

fun ContinueEntry.asMeta() = MetaItem(id, kind, name, poster, background, logo, null, null, null, emptyList(), null)
fun WatchlistItem.asMeta() = MetaItem(id, kind, title, poster, null, null, null, null, null, emptyList(), null)
