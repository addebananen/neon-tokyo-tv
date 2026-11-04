package com.neontokyo.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neontokyo.desktop.LocalModel
import com.neontokyo.desktop.LocalNavigator
import com.neontokyo.desktop.Screen
import com.neontokyo.desktop.theme.LocalNeon
import com.neontokyo.desktop.theme.Metrics
import com.neontokyo.desktop.theme.neonDisplay
import kotlinx.coroutines.delay
import uniffi.neon_core.CatalogDef
import uniffi.neon_core.MetaItem

/** Search every addon that can search, one row per addon and kind. */
@Composable
fun SearchScreen() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    val theme = LocalNeon.current
    var query by rememberSaveableQuery()
    var results by remember { mutableStateOf<List<Pair<CatalogDef, List<MetaItem>>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350) // wait for typing to pause
        busy = true
        results = model.search(query)
        busy = false
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 48.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Metrics.rowSpacing.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = Metrics.edge.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Search", style = neonDisplay((Metrics.titleSize * 1.4f).sp))
                OutlinedTextField(
                    query, { query = it },
                    singleLine = true,
                    placeholder = { Text("Films, series, anime…") },
                    colors = neonFieldColors(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = (Metrics.bodySize * 1.3f).sp, color = theme.text),
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().focusRequester(field),
                )
                if (busy) CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(24.dp))
                if (!busy && query.trim().length >= 2 && results.isEmpty()) Text("Nothing found.", color = theme.muted)
            }
        }
        items(results, key = { (c, _) -> c.addonBaseUrl + c.kind + c.id }) { (c, list) ->
            RowSection("${c.addonName} · ${kindName(c.kind)}") {
                items(list, key = { it.id }) { m ->
                    PosterCard(m.name, m.poster, onClick = { nav.open(Screen.Details(m)) }, progress = model.progress(m.id), watched = model.isWatched(m.id))
                }
            }
        }
    }
}

/** The query survives a trip into a film's details and back. */
private val lastQuery = mutableStateOf("")

@Composable
private fun rememberSaveableQuery(): MutableState<String> = remember { lastQuery }

fun kindName(kind: String) = when (kind) {
    "movie" -> "Movies"
    "series" -> "Series"
    "anime" -> "Anime"
    "tv", "channel" -> "Channels"
    else -> kind.replaceFirstChar { it.uppercase() }
}
