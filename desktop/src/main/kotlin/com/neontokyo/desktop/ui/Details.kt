package com.neontokyo.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.LocalModel
import com.neontokyo.desktop.LocalNavigator
import com.neontokyo.desktop.Screen
import com.neontokyo.desktop.core.Art
import com.neontokyo.desktop.core.PlayItem
import com.neontokyo.desktop.player.Player
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics
import com.neontokyo.desktop.theme.neonDisplay
import uniffi.neon_core.*

data class SourceRequest(val kind: String, val videoId: String, val play: PlayItem, val heading: String)

/** A film or series: picture, story, Play or Resume, watchlist, trailer, seasons and episodes. */
@Composable
fun DetailsScreen(item: MetaItem) {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    val theme = LocalNeon.current
    var detail by remember(item.id) { mutableStateOf<MetaDetail?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }
    var season by remember(item.id) { mutableStateOf(1u) }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(item.id, model.manifests.size) {
        if (model.manifests.isEmpty()) return@LaunchedEffect
        failed = false
        runCatching { model.meta(item.kind, item.id) }
            .onSuccess { d ->
                detail = d
                val resume = model.continueItems.firstOrNull { it.id == item.id }?.season
                season = resume ?: d.videos.firstOrNull { it.season > 0u }?.season ?: 1u
                runCatching { playFocus.requestFocus() }
            }
            .onFailure { failed = true }
    }

    val meta = detail?.item ?: item
    val isSeries = meta.kind == "series"

    fun request(v: Video): SourceRequest {
        val pid = episodePlaybackId(meta.id, v)
        return SourceRequest(
            "series", episodeStreamId(meta.id, v),
            PlayItem(pid, "series", episodeDisplayTitle(v), meta.poster, meta.id, meta.name, detail?.videos ?: emptyList()),
            "${meta.name}  ·  S${v.season}:E${v.episode}",
        )
    }

    val play: Pair<String, SourceRequest>? = if (!isSeries) {
        val r = model.resumePosition(meta.id)
        (if (r > 0uL) "Resume ${time(r)}" else if (model.resumeFractionOf(meta.id) > 0.0) "Resume" else "Play") to
            SourceRequest(meta.kind, meta.id, PlayItem(meta.id, "movie", meta.name, meta.poster), meta.name)
    } else detail?.let { d ->
        val videos = d.videos.filter { it.season > 0u }
        val c = model.continueItems.firstOrNull { it.id == meta.id }
        val v = videos.firstOrNull { it.season == c?.season && it.episode == c?.episode } ?: videos.firstOrNull()
        v?.let {
            val r = model.resumePosition(episodePlaybackId(meta.id, it))
            ((if (r > 0uL) "Resume " else "Play ") + "S${it.season}:E${it.episode}") to request(it)
        }
    }

    val drift = rememberInfiniteTransition(label = "DetailsDrift")
    val k by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Reverse), label = "Drift")
    val motion = LocalMotion.current.amount
    val logoUrl = Art.logo(meta.id, meta.logo)
    var logoOk by remember(logoUrl) { mutableStateOf(logoUrl != null) }

    Box(Modifier.fillMaxSize()) {
        // the title's picture, drifting slowly, melting into the world at the bottom
        Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(0f to Color.Black, 0.62f to Color.Black.copy(alpha = 0.75f), 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }) {
            NeonImage(
                Art.background(meta.id, meta.background) ?: meta.poster,
                Modifier.fillMaxSize().graphicsLayer {
                    val sc = 1.05f + 0.06f * k * motion
                    scaleX = sc; scaleY = sc
                    translationX = (0.5f - k) * 30f * motion
                },
                placeholder = false,
            )
            Box(Modifier.fillMaxSize().drawWithContent {
                drawRect(Brush.horizontalGradient(listOf(theme.background.copy(alpha = 0.94f), theme.background.copy(alpha = 0.55f), Color.Transparent), endX = size.width * 0.75f))
            })
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 96.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = Metrics.edge.dp).widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (logoUrl != null && logoOk) {
                        NeonImage(logoUrl, Modifier.height(140.dp).widthIn(max = 520.dp).fillMaxWidth(), ContentScale.Fit, Alignment.CenterStart,
                            placeholder = false, onFailed = { logoOk = false })
                    } else {
                        Text(meta.name, style = neonDisplay((Metrics.titleSize * 1.8f).sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    FactsLine(meta)
                    meta.description?.let {
                        Text(it, color = theme.text.copy(alpha = 0.88f), fontSize = (Metrics.bodySize * 1.04f).sp, lineHeight = (Metrics.bodySize * 1.55f).sp,
                            maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        play?.let { (label, req) ->
                            Box(Modifier.focusRequester(playFocus)) { PillButton(label, prominent = true, icon = "▶") { nav.open(Screen.Sources(req)) } }
                        }
                        PillButton(if (model.onWatchlist(meta.id)) "On watchlist" else "Watchlist", icon = if (model.onWatchlist(meta.id)) "✓" else "+") { model.toggleWatchlist(meta) }
                        detail?.trailerYoutubeIds?.firstOrNull()?.let { yt ->
                            PillButton("Trailer", icon = "◉") { Player.openWeb("https://www.youtube.com/watch?v=$yt") }
                        }
                    }
                    detail?.cast?.takeIf { it.isNotEmpty() }?.let {
                        Text("With " + it.take(5).joinToString(", "), color = theme.muted, fontSize = (Metrics.bodySize * 0.85f).sp, maxLines = 2)
                    }
                    if (failed) Text("Could not load the details. Check the connection and your addons.", color = theme.error)
                    if (detail == null && !failed) CircularProgressIndicator(color = theme.primary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
            val d = detail
            if (isSeries && d != null) {
                val seasons = d.videos.map { it.season }.filter { it > 0u }.distinct().sorted()
                if (seasons.size > 1) item("seasons") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = Metrics.edge.dp, vertical = 10.dp)) {
                        items(seasons) { s -> PillButton("Season $s", prominent = s == season) { season = s } }
                    }
                }
                item("episodes-$season") {
                    val eps = d.videos.filter { it.season == season }
                    RowSection(if (seasons.size > 1) "Episodes" else "Season $season", eps.size) {
                        items(eps, key = { it.id + ":" + it.episode }) { v -> EpisodeTile(v, meta.id) { nav.open(Screen.Sources(request(v))) } }
                    }
                }
            }
        }
    }
}

/** An episode as a wide tile: its still, its number and name, how far along. */
@Composable
private fun EpisodeTile(v: Video, seriesId: String, onClick: () -> Unit) {
    val model = LocalModel.current
    val theme = LocalNeon.current
    val pid = episodePlaybackId(seriesId, v)
    Column(Modifier.width(WideCard.WIDTH.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NeonTile(onClick, glow = rememberTint(v.thumbnail), radius = 14.dp) {
            Box(Modifier.size(WideCard.WIDTH.dp, (WideCard.WIDTH * 9 / 16).dp)) {
                NeonImage(v.thumbnail, Modifier.fillMaxSize())
                Box(Modifier.matchParentSize().drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)))
                })
                Row(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge("E${v.episode}")
                    if (model.isWatched(pid)) Badge("Watched", strong = true)
                }
                val p = model.progress(pid)
                if (p > 0.01f) ProgressLine(p, Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Text(v.title, color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = Metrics.bodySize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        v.overview?.let { Text(it, color = theme.muted, fontSize = (Metrics.bodySize * 0.8f).sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

fun time(ms: ULong): String {
    val s = (ms / 1000uL).toLong()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
