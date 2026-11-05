package com.neontokyo.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.LocalModel
import com.neontokyo.desktop.LocalNavigator
import com.neontokyo.desktop.Screen
import com.neontokyo.desktop.core.Art
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics
import com.neontokyo.desktop.theme.neonDisplay
import kotlinx.coroutines.delay
import uniffi.neon_core.ContinueEntry
import uniffi.neon_core.MetaItem
import uniffi.neon_core.WatchlistItem

/**
 * The home screen: the focused title large on top (its picture slowly drifting), then
 * Continue watching and the first catalog as wide tiles, the watchlist and every other
 * catalog as posters. The world shows through below the picture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    val theme = LocalNeon.current
    var focusedItem by remember { mutableStateOf<MetaItem?>(null) }
    var hero by remember { mutableStateOf<MetaItem?>(null) }
    val first = model.rows.firstNotNullOfOrNull { model.rowItems[it.key]?.firstOrNull() }
    // The picture follows the focus a beat later, so racing along a row does not flicker it
    LaunchedEffect(focusedItem) {
        delay(140)
        hero = focusedItem
    }
    // The focused row's cards stay where the first row's cards start (under the story, like
    // Neon Home's film rows): rows above slide up and fade out under the story, never over it
    val density = LocalDensity.current
    val pivot = with(density) { (Metrics.heroHeight * 0.80f + 59f).dp.toPx() }
    val fadeFrom = with(density) { 150.dp.toPx() }
    val fadeTo = with(density) { 64.dp.toPx() }
    val vertical = remember(pivot) { PivotSpec(pivot) }

    Box(Modifier.fillMaxSize()) {
        Hero(hero ?: first, Modifier.fillMaxWidth().height((Metrics.heroHeight + 120).dp))
        CompositionLocalProvider(LocalBringIntoViewSpec provides vertical) {
            LazyColumn(
                Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val h = size.height.coerceAtLeast(1f)
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                ((pivot - fadeFrom) / h).coerceIn(0f, 1f) to Color.Transparent,
                                ((pivot - fadeTo) / h).coerceIn(0f, 1f) to Color.Black,
                                1f to Color.Black,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                contentPadding = PaddingValues(top = (Metrics.heroHeight * 0.80f).dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy((Metrics.rowSpacing * 0.4f).dp),
            ) {
                val cw = model.continueItems
                if (cw.isNotEmpty()) item("cw") {
                    RowSection("Continue watching", cw.size) {
                        items(cw, key = { "cw" + it.id }) { e ->
                            val meta = e.asMeta()
                            WideTile(
                                e.id, e.name, e.background, e.logo, e.poster,
                                onClick = { nav.open(Screen.Details(meta)) },
                                onFocused = { focusedItem = meta },
                                progress = e.progress,
                                badge = if (e.season != null && e.episode != null) "S${e.season} · E${e.episode}" else null,
                                fresh = e.hasNewEpisode,
                            )
                        }
                    }
                }
                val wl = model.watchlistItems
                if (wl.isNotEmpty()) item("wl") {
                    RowSection("Watchlist", wl.size) {
                        items(wl, key = { "wl" + it.id }) { w ->
                            val meta = w.asMeta()
                            PosterCard(w.title, w.poster, onClick = { nav.open(Screen.Details(meta)) }, onFocused = { focusedItem = meta })
                        }
                    }
                }
                model.rows.forEachIndexed { index, row ->
                    val list = model.rowItems[row.key]
                    if (!list.isNullOrEmpty()) item(row.key) {
                        RowSection(row.title) {
                            items(list, key = { row.key + it.id }) { item ->
                                if (index == 0) {
                                    WideTile(
                                        item.id, item.name, item.background, item.logo, item.poster,
                                        onClick = { nav.open(Screen.Details(item)) },
                                        onFocused = { focusedItem = item },
                                        progress = model.progress(item.id),
                                        caption = listOfNotNull(item.releaseInfo, item.genres.firstOrNull()).joinToString("  ·  ").ifEmpty { null },
                                    )
                                } else {
                                    PosterCard(
                                        item.name, item.poster,
                                        onClick = { nav.open(Screen.Details(item)) },
                                        onFocused = { focusedItem = item },
                                        progress = model.progress(item.id),
                                        watched = model.isWatched(item.id),
                                    )
                                }
                            }
                        }
                    }
                }
                if (model.rows.isEmpty()) item("empty") {
                    Row(Modifier.padding(Metrics.edge.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (model.loadingAddons) CircularProgressIndicator(color = theme.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        Text(
                            if (model.loadingAddons) "Tuning in your addons…" else "No catalogs yet. Add an addon in Settings, or sign in with Stremio.",
                            color = theme.muted, fontSize = Metrics.bodySize.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The big picture: the title's wide picture drifting slowly (Ken Burns), its logo or name
 * lit in neon, facts and the start of the story, all sliding in when the title changes.
 */
@Composable
fun Hero(item: MetaItem?, modifier: Modifier = Modifier) {
    val theme = LocalNeon.current
    val motion = LocalMotion.current.amount
    val drift = rememberInfiniteTransition(label = "HeroDrift")
    val k by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "Drift")
    AnimatedContent(
        item, modifier,
        transitionSpec = {
            fadeIn(tween(520, easing = NeonMotion.FastOutSlowIn)) togetherWith fadeOut(tween(380))
        },
        contentKey = { it?.id },
        label = "Hero",
    ) { it ->
        Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(0f to Color.Black, 0.58f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }) {
            if (it != null) {
                NeonImage(
                    Art.background(it.id, it.background) ?: it.poster,
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            val s = 1.04f + 0.06f * k * motion
                            scaleX = s; scaleY = s
                            translationX = (k - 0.5f) * 36f * motion
                        },
                    placeholder = false,
                )
                // Shade the left for the words, and let the picture melt into the world below
                Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
                    drawRect(Brush.horizontalGradient(listOf(theme.background.copy(alpha = 0.9f), theme.background.copy(alpha = 0.3f), Color.Transparent), endX = size.width * 0.72f))
                    drawRect(Brush.verticalGradient(0f to theme.background.copy(alpha = 0.25f), 0.25f to Color.Transparent))
                })
                HeroText(it)
            }
        }
    }
}

@Composable
private fun HeroText(it: MetaItem) {
    val theme = LocalNeon.current
    var shown by remember(it.id) { mutableStateOf(false) }
    LaunchedEffect(it.id) { shown = true }
    val logoUrl = Art.logo(it.id, it.logo)
    var logoOk by remember(logoUrl) { mutableStateOf(logoUrl != null) }
    Column(
        Modifier.padding(start = Metrics.edge.dp, top = 64.dp).widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Staggered(shown, 0) {
            if (logoUrl != null && logoOk) {
                NeonLogo(logoUrl, Modifier.height(120.dp).widthIn(max = 460.dp).fillMaxWidth(), Alignment.CenterStart,
                    onFailed = { logoOk = false })
            } else {
                Text(it.name, style = neonDisplay((Metrics.titleSize * 1.7f).sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Staggered(shown, 1) { FactsLine(it) }
        it.description?.let { d ->
            Staggered(shown, 2) {
                Text(d, color = theme.text.copy(alpha = 0.86f), fontSize = (Metrics.bodySize * 1.02f).sp, lineHeight = (Metrics.bodySize * 1.5f).sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Slides a line of the hero in from the left, each a little after the one above. */
@Composable
private fun Staggered(shown: Boolean, index: Int, content: @Composable () -> Unit) {
    val motion = LocalMotion.current.amount
    val p by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(520, delayMillis = 60 + index * 70, easing = NeonMotion.FastOutSlowIn), label = "HeroLine",
    )
    Box(Modifier.graphicsLayer { alpha = p; translationX = (1f - p) * -28f * motion }) { content() }
}

@Composable
fun FactsLine(item: MetaItem) {
    val theme = LocalNeon.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        item.imdbRating?.let { Badge("IMDb $it", strong = true) }
        Text(
            listOfNotNull(item.releaseInfo, item.runtime, item.genres.take(3).joinToString(", ").ifEmpty { null }).joinToString("   ·   "),
            color = theme.text.copy(alpha = 0.78f), fontSize = (Metrics.bodySize * 0.92f).sp, fontWeight = FontWeight.Medium, maxLines = 1,
        )
    }
}

fun ContinueEntry.asMeta() = MetaItem(id, kind, name, poster, background, logo, null, null, null, emptyList(), null)
fun WatchlistItem.asMeta() = MetaItem(id, kind, title, poster, null, null, null, null, null, emptyList(), null)
