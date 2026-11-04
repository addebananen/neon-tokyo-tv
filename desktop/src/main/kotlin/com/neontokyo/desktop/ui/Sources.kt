package com.neontokyo.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.LocalModel
import com.neontokyo.desktop.core.AppModel
import com.neontokyo.desktop.player.Player
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics
import com.neontokyo.desktop.theme.neonDisplay
import uniffi.neon_core.StreamSource

/** The sources the addons offer, best first, opened in mpv (or VLC). */
@Composable
fun SourcesScreen(request: SourceRequest) {
    val model = LocalModel.current
    val theme = LocalNeon.current
    var streams by remember(request) { mutableStateOf<List<StreamSource>?>(null) }
    LaunchedEffect(request) { streams = model.streams(request.kind, request.videoId) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Metrics.edge.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(request.heading, style = neonDisplay())
                Text("Plays in ${Player.playerName}", color = theme.muted, fontSize = (Metrics.bodySize * 0.8f).sp)
            }
        }
        val list = streams
        when {
            list == null -> item { CircularProgressIndicator(color = theme.primary) }
            list.isEmpty() -> item {
                Text(
                    if (model.hasStreamAddon) "No sources found for this one."
                    else "Your addons only list titles. Add an addon that provides streams in Settings › Addons.",
                    color = theme.muted,
                )
            }
            else -> itemsIndexed(list) { _, s ->
                FocusCard({ start(s, request, model) }) { SourceRow(s) }
            }
        }
    }
}

private fun start(s: StreamSource, request: SourceRequest, model: AppModel) {
    val url = s.url ?: s.externalUrl ?: return
    val headers = s.requestHeaders.associate { it.name to it.value }
    Player.play(url, request.play.title, model.resumePosition(request.play.playbackId), headers) { pos, dur ->
        model.playerProgress(request.play, pos, dur)
    }
}

@Composable
private fun SourceRow(s: StreamSource) {
    val theme = LocalNeon.current
    Row(
        Modifier.widthIn(max = 1100.dp).fillMaxWidth().background(theme.panel.copy(alpha = 0.8f)).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.qualityLabel.isNotEmpty()) Badge(s.qualityLabel, strong = true)
                s.tags.forEach { Badge(it) }
                s.sizeLabel?.let { Badge(it) }
            }
            Text(s.name.replace("\n", " "), color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = Metrics.bodySize.sp, maxLines = 1)
            if (s.title.isNotEmpty()) Text(s.title, color = theme.muted, fontSize = (Metrics.bodySize * 0.8f).sp, maxLines = 3)
        }
        Text(s.addonName, color = theme.muted, fontSize = (Metrics.bodySize * 0.75f).sp)
    }
}
