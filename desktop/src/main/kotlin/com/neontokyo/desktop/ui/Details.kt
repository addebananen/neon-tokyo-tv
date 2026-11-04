package com.neontokyo.desktop.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.LocalModel
import com.neontokyo.desktop.LocalNavigator
import com.neontokyo.desktop.Screen
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
        (if (r > 0uL) "Resume ${time(r)}" else "Play") to
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

    Box(Modifier.fillMaxSize()) {
        NeonImage(meta.background ?: meta.poster, Modifier.fillMaxSize().drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(listOf(theme.background.copy(alpha = 0.35f), theme.background.copy(alpha = 0.92f), theme.background)))
            drawRect(Brush.horizontalGradient(listOf(theme.background.copy(alpha = 0.9f), Color.Transparent)))
        })
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Metrics.edge.dp, end = Metrics.edge.dp, top = 120.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.widthIn(max = 900.dp)) {
                    if (meta.logo != null) {
                        NeonImage(meta.logo, Modifier.height(130.dp).widthIn(max = 480.dp).fillMaxWidth(), ContentScale.Fit)
                    } else {
                        Text(meta.name, style = neonDisplay((Metrics.titleSize * 1.6f).sp))
                    }
                    FactsLine(meta)
                    meta.description?.let { Text(it, color = theme.text.copy(alpha = 0.88f), fontSize = Metrics.bodySize.sp, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        play?.let { (label, req) ->
                            Box(Modifier.focusRequester(playFocus)) { PillButton("▶  $label", prominent = true) { nav.open(Screen.Sources(req)) } }
                        }
                        PillButton(if (model.onWatchlist(meta.id)) "✓  On watchlist" else "+  Watchlist") { model.toggleWatchlist(meta) }
                        detail?.trailerYoutubeIds?.firstOrNull()?.let { yt ->
                            PillButton("Trailer") { Player.openWeb("https://www.youtube.com/watch?v=$yt") }
                        }
                    }
                    detail?.cast?.takeIf { it.isNotEmpty() }?.let {
                        Text("Cast: " + it.take(6).joinToString(", "), color = theme.muted, fontSize = (Metrics.bodySize * 0.85f).sp, maxLines = 2)
                    }
                    if (failed) Text("Could not load the details. Check the connection and your addons.", color = theme.error)
                    if (detail == null && !failed) CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(28.dp))
                }
            }
            val d = detail
            if (isSeries && d != null) {
                val seasons = d.videos.map { it.season }.filter { it > 0u }.distinct().sorted()
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(seasons) { s -> PillButton("Season $s", prominent = s == season) { season = s } }
                    }
                }
                items(d.videos.filter { it.season == season }, key = { it.id + it.episode }) { v ->
                    FocusCard({ nav.open(Screen.Sources(request(v))) }) { EpisodeRow(v, meta.id) }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(v: Video, seriesId: String) {
    val model = LocalModel.current
    val theme = LocalNeon.current
    val pid = episodePlaybackId(seriesId, v)
    Row(
        Modifier.widthIn(max = 1000.dp).fillMaxWidth().background(theme.panel.copy(alpha = 0.7f)).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(230.dp, 130.dp).clip(RoundedCornerShape(8.dp))) {
            NeonImage(v.thumbnail, Modifier.fillMaxSize())
            val p = model.progress(pid)
            if (p > 0.01f) ProgressLine(p, Modifier.align(Alignment.BottomCenter).padding(6.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${v.episode}. ${v.title}", color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = Metrics.bodySize.sp, maxLines = 1)
                if (model.isWatched(pid)) Badge("Watched", strong = true)
            }
            v.overview?.let { Text(it, color = theme.muted, fontSize = (Metrics.bodySize * 0.85f).sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            v.released?.let { Text(it.take(10), color = theme.muted.copy(alpha = 0.8f), fontSize = (Metrics.bodySize * 0.75f).sp) }
        }
    }
}

fun time(ms: ULong): String {
    val s = (ms / 1000uL).toLong()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
