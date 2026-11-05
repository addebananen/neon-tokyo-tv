package com.neontokyo.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.LocalModel
import com.neontokyo.desktop.LocalNavigator
import com.neontokyo.desktop.Screen
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics
import com.neontokyo.desktop.theme.neonDisplay

/** What the profile saved for later, newest first. */
@Composable
fun WatchlistScreen() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    val theme = LocalNeon.current
    val items = model.watchlistItems
    LazyVerticalGrid(
        GridCells.Adaptive((Metrics.posterWidth + Metrics.cardSpacing).dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Metrics.edge.dp, end = Metrics.edge.dp, top = 48.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(Metrics.cardSpacing.dp),
        verticalArrangement = Arrangement.spacedBy((Metrics.cardSpacing * 1.6f).dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Watchlist", style = neonDisplay((Metrics.titleSize * 1.4f).sp))
                if (items.isEmpty()) Text("Nothing here yet. Open a film or series and choose Watchlist.", color = theme.muted)
            }
        }
        items(items, key = { it.id }) { w ->
            PosterCard(w.title, w.poster, onClick = { nav.open(Screen.Details(w.asMeta())) }, progress = model.progress(w.id), watched = model.isWatched(w.id))
        }
    }
}
