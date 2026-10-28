package com.lumera.app.livetv

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.remote_input.ServerInfo
import com.lumera.app.ui.components.NeonPalette
import com.lumera.app.ui.components.NeonText
import com.lumera.app.ui.components.NeonType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FAVOURITES = "\u0000fav"
private const val ALL = "\u0000all"
private const val SEARCH = "\u0000search"
private const val POPULAR = "\u0000popular"
private const val LIST = "\u0000list:"

/**
 * Live TV: the household's IPTV channels in their groups, with each profile's favourites first.
 * The groups are on the left and the selection follows the focus, so moving down the groups
 * shows each one's channels at once; holding OK on a channel makes it a favourite, and holding
 * it on a group hides that group for this profile.
 */
@Composable
fun LiveTvScreen(
    currentProfile: ProfileEntity?,
    entryRequester: FocusRequester,
    drawerRequester: FocusRequester
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileId = currentProfile?.id ?: 0
    val state by LiveTv.state.collectAsState()
    val guide by LiveTv.guide.collectAsState()

    var favourites by remember(profileId) { mutableStateOf(LiveTv.favourites(context, profileId)) }
    var hidden by remember(profileId) { mutableStateOf(LiveTv.hiddenGroups(context, profileId)) }
    var lists by remember(profileId) { mutableStateOf<Map<String, List<String>>>(LiveTv.lists(context, profileId)) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var channelMenu by remember { mutableStateOf<IptvChannel?>(null) }
    var listMenu by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<IptvChannel?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var group by remember(profileId) { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<Pair<List<IptvChannel>, Int>?>(null) }
    var setup by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var lastPlayed by remember(profileId) { mutableStateOf(LiveTv.lastChannel(context, profileId)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // After the phone adds a service the button that had the focus is gone; the list takes it
    var focusList by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { LiveTv.load(context) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    BackHandler { drawerRequester.requestFocus() }

    val isTopNav = currentProfile?.navPosition == "top"
    val startPadding = if (isTopNav) 50.dp else 120.dp
    val topPadding = if (isTopNav) 96.dp else 36.dp

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            LiveTv.State.Empty -> Invite(
                eyebrow = "LIVE TV",
                headline = "Add your IPTV service",
                body = "Scan a code with your phone and type your Xtream login or playlist link there. Your favourite channels then wait for you here, in their own groups.",
                action = "Add with my phone",
                entryRequester = entryRequester,
                onAction = { setup = true }
            )
            LiveTv.State.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NeonPalette.Magenta, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(18.dp))
                    NeonText("Tuning in the channels", style = NeonType.headline(24.sp))
                }
                // Somewhere for the focus to rest while the list loads
                Box(Modifier.size(1.dp).focusRequester(entryRequester).focusable())
            }
            is LiveTv.State.Failed -> if (s.channels.isEmpty()) {
                Invite(
                    eyebrow = "LIVE TV",
                    headline = "The channels did not load",
                    body = s.message + ". Try again, or add the service anew if its login has changed.",
                    action = "Try again",
                    secondary = "Change service",
                    entryRequester = entryRequester,
                    onAction = { scope.launch { LiveTv.load(context, force = true) } },
                    onSecondary = { setup = true }
                )
            } else Unit
            is LiveTv.State.Ready -> Unit
        }

        val channels = when (val s = state) {
            is LiveTv.State.Ready -> s.channels
            is LiveTv.State.Failed -> s.channels
            else -> emptyList()
        }
        if (channels.isNotEmpty()) {
            val byId = remember(channels) { channels.associateBy { it.id } }
            val groups = remember(channels) { channels.map { it.group }.distinct() }
            val favouriteChannels = remember(favourites, byId) { favourites.mapNotNull { byId[it] } }
            val shownGroups = groups.filter { it !in hidden }
            val hiddenGroups = groups.filter { it in hidden }
            val popular = remember(channels) { PopularChannels.sweden(channels) }
            val searchable = remember(channels) { channels.map { PopularChannels.normalize(it.name) + " " + it.name.lowercase() } }
            val selected = group ?: if (favouriteChannels.isNotEmpty()) FAVOURITES else ALL
            val list = remember(selected, channels, favouriteChannels, lists, query) {
                when {
                    selected == FAVOURITES -> favouriteChannels
                    selected == ALL -> channels.filter { it.group !in hidden }
                    selected == POPULAR -> popular
                    selected == SEARCH -> {
                        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
                        if (words.isEmpty()) emptyList()
                        else channels.filterIndexed { i, _ -> words.all { it in searchable[i] } }.take(400)
                    }
                    selected.startsWith(LIST) -> lists[selected.removePrefix(LIST)].orEmpty().mapNotNull { byId[it] }
                    else -> channels.filter { it.group == selected }
                }
            }
            val counts = remember(channels) { channels.groupingBy { it.group }.eachCount() }
            // A channel picked on Neon Home: played at once, among the favourites when it is one
            val wanted by LiveTv.wanted.collectAsState()
            LaunchedEffect(wanted, byId) {
                val channel = wanted?.let { byId[it] } ?: return@LaunchedEffect
                val among = favouriteChannels.takeIf { channel in it } ?: listOf(channel)
                playing = among to among.indexOf(channel)
                LiveTv.want(null)
            }
            LaunchedEffect(focusList) {
                if (!focusList) return@LaunchedEffect
                delay(150)
                runCatching { entryRequester.requestFocus() }
                focusList = false
            }

            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = startPadding, top = topPadding, end = 48.dp)
            ) {
                // Groups
                Column(Modifier.width(270.dp).fillMaxHeight()) {
                    NeonText("LIVE TV", style = NeonType.eyebrow(13.sp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if ((state as? LiveTv.State.Ready)?.refreshing == true) "Updating the channels…" else "${channels.size} channels",
                        color = NeonPalette.Lilac,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 40.dp, end = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        item(key = SEARCH) {
                            GroupItem(
                                if (query.isBlank()) "⌕  Search channels" else "⌕  “$query”",
                                null, selected == SEARCH,
                                onFocus = { group = SEARCH },
                                onClick = { searching = true }
                            )
                        }
                        item(key = FAVOURITES) {
                            GroupItem(
                                "★  Favourites", favouriteChannels.size, selected == FAVOURITES,
                                modifier = Modifier.focusRequester(entryRequester),
                                onFocus = { group = FAVOURITES }
                            )
                        }
                        items(lists.keys.toList(), key = { "l:$it" }) { name ->
                            GroupItem(
                                "✦  $name", lists[name]?.count { it in byId } ?: 0, selected == LIST + name,
                                onFocus = { group = LIST + name },
                                onHold = { listMenu = name }
                            )
                        }
                        if (popular.size >= 2) item(key = POPULAR) {
                            GroupItem("Popular in Sweden", popular.size, selected == POPULAR, onFocus = { group = POPULAR })
                        }
                        item(key = ALL) {
                            GroupItem("All channels", channels.count { it.group !in hidden }, selected == ALL, onFocus = { group = ALL })
                        }
                        items(shownGroups, key = { "g:$it" }) { name ->
                            GroupItem(
                                name, counts[name] ?: 0, selected == name,
                                onFocus = { group = name },
                                onHold = {
                                    LiveTv.setGroupHidden(context, profileId, name, true)
                                    hidden = hidden + name
                                    group = ALL
                                }
                            )
                        }
                        if (hiddenGroups.isNotEmpty()) {
                            item(key = "hidden-toggle") {
                                GroupItem(
                                    if (showHidden) "Hide hidden groups" else "Hidden groups",
                                    hiddenGroups.size, false, dim = true,
                                    onClick = { showHidden = !showHidden }
                                )
                            }
                            if (showHidden) items(hiddenGroups, key = { "h:$it" }) { name ->
                                GroupItem(
                                    name, counts[name] ?: 0, selected == name, dim = true,
                                    onFocus = { group = name },
                                    onHold = {
                                        LiveTv.setGroupHidden(context, profileId, name, false)
                                        hidden = hidden - name
                                    }
                                )
                            }
                        }
                        item(key = "service") {
                            GroupItem(
                                "Service settings", null, false, dim = true,
                                modifier = Modifier.padding(top = 10.dp),
                                onClick = { menu = true }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(18.dp))

                // Channels
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    NeonText(
                        when {
                            selected == FAVOURITES -> "Favourites"
                            selected == ALL -> "All channels"
                            selected == POPULAR -> "Popular in Sweden"
                            selected == SEARCH -> if (query.isBlank()) "Search" else "“$query”"
                            selected.startsWith(LIST) -> selected.removePrefix(LIST)
                            else -> selected
                        },
                        style = NeonType.headline(28.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            selected in groups -> "Hold OK on a channel to add it to a list · hold OK on a group to hide it"
                            selected.startsWith(LIST) -> "Hold OK on a channel to add or remove it · hold OK on the list to delete it"
                            selected == SEARCH -> "Press OK on Search to type, by name, in any language the provider uses"
                            else -> "Hold OK on a channel to add it to Favourites or a list of your own"
                        },
                        color = NeonPalette.Lilac.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )
                    if (list.isEmpty()) {
                        Text(
                            when {
                                selected == FAVOURITES -> "No favourites yet. Hold OK on a channel to add it here."
                                selected == SEARCH && query.isBlank() -> "Press OK on Search channels and type a name."
                                selected == SEARCH -> "No channel is called anything like that."
                                else -> "No channels here."
                            },
                            color = NeonPalette.Lilac,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
                    val listState = rememberLazyListState()
                    LaunchedEffect(selected) { if (list.isNotEmpty()) listState.scrollToItem(0) }
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp, start = 4.dp, end = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(list, key = { it.id }) { channel ->
                            ChannelRow(
                                channel = channel,
                                programmes = guide[channel.epgId],
                                now = now,
                                favourite = channel.id in favourites,
                                last = channel.id == lastPlayed,
                                onClick = { playing = list to list.indexOf(channel) },
                                onHold = { channelMenu = channel }
                            )
                        }
                    }
                }
            }
        }
    }

    playing?.let { (list, index) ->
        LiveTvPlayer(
            channels = list,
            startIndex = index,
            guide = guide,
            isFavourite = { it.id in favourites },
            onToggleFavourite = { channel ->
                val on = channel.id !in favourites
                LiveTv.setFavourite(context, profileId, channel.id, on)
                favourites = LiveTv.favourites(context, profileId)
                on
            },
            onChannel = { channel ->
                LiveTv.setLastChannel(context, profileId, channel.id)
                lastPlayed = channel.id
            },
            onClose = { playing = null }
        )
    }

    if (searching) {
        TextDialog(
            title = "Search channels",
            initial = query,
            onChange = { query = it; group = SEARCH },
            onDone = { searching = false },
            action = "Show"
        )
    }

    channelMenu?.let { channel ->
        fun toggleIn(name: String) {
            val now = lists[name].orEmpty()
            val next = lists.toMutableMap().apply { put(name, if (channel.id in now) now - channel.id else now + channel.id) }
            LiveTv.setLists(context, profileId, next)
            lists = LiveTv.lists(context, profileId)
        }
        ChoiceDialog(
            title = channel.name,
            choices = buildList<Pair<String, () -> Unit>> {
                add((if (channel.id in favourites) "Remove from Favourites" else "Add to Favourites") to {
                    LiveTv.setFavourite(context, profileId, channel.id, channel.id !in favourites)
                    favourites = LiveTv.favourites(context, profileId)
                })
                lists.forEach { (name, ids) ->
                    add((if (channel.id in ids) "Remove from $name" else "Add to $name") to { toggleIn(name) })
                }
                add("New list with this channel" to { naming = channel })
            },
            onDismiss = { channelMenu = null }
        )
    }

    naming?.let { channel ->
        TextDialog(
            title = "Name the new list",
            initial = "",
            onChange = {},
            onDone = { name ->
                val clean = name.trim().take(30)
                if (clean.isNotEmpty()) {
                    val next = lists.toMutableMap().apply { put(clean, (get(clean).orEmpty() - channel.id) + channel.id) }
                    LiveTv.setLists(context, profileId, next)
                    lists = LiveTv.lists(context, profileId)
                }
                naming = null
            },
            action = "Make list"
        )
    }

    listMenu?.let { name ->
        ChoiceDialog(
            title = name,
            choices = listOf<Pair<String, () -> Unit>>("Delete this list" to {
                LiveTv.setLists(context, profileId, lists - name)
                lists = LiveTv.lists(context, profileId)
                if (group == LIST + name) group = FAVOURITES
            }),
            onDismiss = { listMenu = null }
        )
    }

    if (setup) {
        LiveTvSetupDialog(
            onDismiss = { setup = false },
            onConnected = {
                setup = false
                group = null
                focusList = true
                scope.launch { LiveTv.load(context, force = true) }
            }
        )
    }

    if (menu) {
        ServiceMenu(
            onRefresh = { menu = false; scope.launch { LiveTv.load(context, force = true) } },
            onChange = { menu = false; setup = true },
            onRemove = { menu = false; LiveTv.remove(context) },
            onDismiss = { menu = false }
        )
    }
}

/**
 * OK pressed and let go is a click; OK held until the remote reports a long press is a hold,
 * and its release then does nothing more. A release whose press this did not see (the press
 * that opened the screen) is ignored.
 */
internal fun Modifier.okPress(onClick: () -> Unit, onHold: (() -> Unit)?): Modifier = composed {
    var down by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    onPreviewKeyEvent { e ->
        if (e.key != Key.DirectionCenter && e.key != Key.Enter && e.key != Key.NumPadEnter) return@onPreviewKeyEvent false
        when (e.type) {
            KeyEventType.KeyDown -> {
                val native = e.nativeKeyEvent
                if (native.repeatCount == 0) {
                    down = true
                    held = false
                } else if (down && !held && onHold != null && (native.isLongPress || native.repeatCount >= 8)) {
                    held = true
                    onHold()
                }
                true
            }
            KeyEventType.KeyUp -> {
                if (down && !held) onClick()
                down = false
                held = false
                true
            }
            else -> false
        }
    }
}

@Composable
private fun GroupItem(
    name: String,
    count: Int?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    onFocus: () -> Unit = {},
    onClick: () -> Unit = {},
    onHold: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        when {
            focused -> NeonPalette.Magenta.copy(alpha = 0.22f)
            selected -> NeonPalette.Violet.copy(alpha = 0.55f)
            else -> Color.Transparent
        },
        tween(160), label = "group"
    )
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.5.dp, if (focused) NeonPalette.Magenta else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .okPress(onClick, onHold)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            color = when {
                focused || selected -> NeonPalette.Text
                dim -> NeonPalette.Lilac.copy(alpha = 0.65f)
                else -> NeonPalette.Lilac
            },
            fontSize = 16.sp,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Text("$count", color = if (focused) NeonPalette.Cyan else NeonPalette.Lilac.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
internal fun hhmm(time: Long): String = clock.format(Date(time))

@Composable
private fun ChannelRow(
    channel: IptvChannel,
    programmes: List<Programme>?,
    now: Long,
    favourite: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onHold: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "lift")
    val shape = RoundedCornerShape(14.dp)
    val nowNext = remember(programmes, now) { programmes?.nowNext(now) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(78.dp)
            .graphicsLayer {
                val scale = 1f + 0.015f * lift
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                if (focused) Brush.horizontalGradient(listOf(NeonPalette.Magenta.copy(alpha = 0.30f), NeonPalette.Violet.copy(alpha = 0.55f)))
                else Brush.horizontalGradient(listOf(NeonPalette.Panel.copy(alpha = 0.72f), NeonPalette.Panel.copy(alpha = 0.45f)))
            )
            .border(if (focused) 2.dp else 1.dp, if (focused) NeonPalette.Magenta else Color.White.copy(alpha = 0.06f), shape)
            .onFocusChanged { focused = it.isFocused }
            .okPress(onClick, onHold)
            .focusable()
            .semantics { contentDescription = channel.name }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${channel.number}",
            color = if (focused) NeonPalette.Cyan else NeonPalette.Lilac.copy(alpha = 0.7f),
            fontSize = 15.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp)
        )
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .size(width = 96.dp, height = 54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = if (focused) 0.12f else 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logo.isNotBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp)
                )
            } else {
                Text(channel.name.take(3).uppercase(), color = NeonPalette.Lilac, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.name,
                    color = NeonPalette.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (favourite) Text("  ★", color = NeonPalette.Sun, fontSize = 15.sp)
                if (last) Text("  · last watched", color = NeonPalette.Cyan.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            val on = nowNext?.now
            if (on != null) {
                Text(
                    "${hhmm(on.start)}  ${on.title}",
                    color = NeonPalette.Lilac,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Progress(on.progress(now), Modifier.widthIn(max = 360.dp).fillMaxWidth())
            }
        }
        val next = nowNext?.next
        if (next != null) {
            Spacer(Modifier.width(16.dp))
            Column(Modifier.width(240.dp)) {
                Text("NEXT ${hhmm(next.start)}", color = NeonPalette.Cyan.copy(alpha = 0.75f), fontSize = 11.sp, letterSpacing = 2.sp)
                Text(next.title, color = NeonPalette.Lilac, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun Progress(value: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.12f))) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .background(Brush.horizontalGradient(listOf(NeonPalette.Magenta, NeonPalette.Cyan)))
        )
    }
}

@Composable
private fun Invite(
    eyebrow: String,
    headline: String,
    body: String,
    action: String,
    entryRequester: FocusRequester,
    onAction: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {}
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 560.dp)) {
            NeonText(eyebrow, style = NeonType.eyebrow(13.sp))
            Spacer(Modifier.height(8.dp))
            NeonText(headline, style = NeonType.headline(32.sp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(body, color = NeonPalette.Lilac, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeonButton(action, Modifier.focusRequester(entryRequester), onAction)
                if (secondary != null) NeonButton(secondary, onClick = onSecondary, quiet = true)
            }
        }
    }
}

@Composable
internal fun NeonButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit, quiet: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                when {
                    focused -> Brush.horizontalGradient(listOf(NeonPalette.Magenta, Color(0xFFFF7AC0)))
                    quiet -> Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f)))
                    else -> Brush.horizontalGradient(listOf(NeonPalette.Magenta.copy(alpha = 0.35f), NeonPalette.Violet))
                }
            )
            .border(1.5.dp, if (focused) Color.White.copy(alpha = 0.7f) else NeonPalette.Magenta.copy(alpha = 0.5f), shape)
            .onFocusChanged { focused = it.isFocused }
            .okPress(onClick, null)
            .focusable()
            .padding(horizontal = 26.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (focused) Color(0xFF1A0420) else NeonPalette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ServiceMenu(onRefresh: () -> Unit, onChange: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    var confirmRemove by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(NeonPalette.Night)
                .border(1.dp, NeonPalette.Magenta.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NeonText("IPTV SERVICE", style = NeonType.eyebrow(13.sp))
            if (!confirmRemove) {
                NeonButton("Refresh channels and guide", Modifier.fillMaxWidth().focusRequester(first), onRefresh)
                NeonButton("Change service", Modifier.fillMaxWidth(), onChange, quiet = true)
                NeonButton("Remove service", Modifier.fillMaxWidth(), { confirmRemove = true }, quiet = true)
            } else {
                Text("Remove the service from this TV? Favourites are kept.", color = NeonPalette.Lilac, fontSize = 15.sp, textAlign = TextAlign.Center)
                NeonButton("Remove", Modifier.fillMaxWidth().focusRequester(first), onRemove)
                NeonButton("Keep it", Modifier.fillMaxWidth(), { confirmRemove = false }, quiet = true)
            }
        }
    }
    LaunchedEffect(confirmRemove) {
        delay(80)
        runCatching { first.requestFocus() }
    }
}

@Composable
fun LiveTvSetupDialog(onDismiss: () -> Unit, onConnected: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { IptvSetupServer.Manager() }
    var info by remember { mutableStateOf<ServerInfo?>(null) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        val started = manager.start(context) { onConnected() }
        if (started == null) failed = true else {
            info = started
            qr = qrCode(started.url)
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { manager.stop() } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(NeonPalette.Violet, NeonPalette.Night)))
                .border(1.dp, NeonPalette.Magenta.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .focusRequester(focus)
                .focusable()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NeonText("LIVE TV", style = NeonType.eyebrow(13.sp))
            Spacer(Modifier.height(6.dp))
            NeonText("Scan with your phone", style = NeonType.headline(26.sp))
            Spacer(Modifier.height(8.dp))
            Text(
                "Type your Xtream login or playlist link on the phone. The TV checks it with your provider and keeps it encrypted.",
                color = NeonPalette.Lilac, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            val code = qr
            when {
                failed -> Text("The TV is not on a network the phone can reach.", color = Color(0xFFFF8FB8), textAlign = TextAlign.Center)
                code != null -> {
                    Box(Modifier.size(210.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                        Image(code.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(info?.url.orEmpty(), color = NeonPalette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                else -> CircularProgressIndicator(color = NeonPalette.Magenta, modifier = Modifier.size(40.dp))
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { focus.requestFocus() }
    }
}

private fun qrCode(url: String, size: Int = 512): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix[i % matrix.width, i / matrix.width]) 0xFF0D0721.toInt() else 0xFFFFFFFF.toInt()
    }
    Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
}.getOrNull()

/** A short menu of choices, each closing it; Back or Cancel closes it with nothing done. */
@Composable
private fun ChoiceDialog(title: String, choices: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(NeonPalette.Night)
                .border(1.dp, NeonPalette.Magenta.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NeonText(title, style = NeonType.headline(22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            choices.forEachIndexed { index, (label, action) ->
                NeonButton(
                    label,
                    Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                    { onDismiss(); action() },
                    quiet = index > 0
                )
            }
            NeonButton("Cancel", Modifier.fillMaxWidth(), onDismiss, quiet = true)
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { first.requestFocus() }
    }
}

/** A line of text typed with the TV's keyboard (or a phone's Google TV remote). */
@Composable
private fun TextDialog(title: String, initial: String, onChange: (String) -> Unit, onDone: (String) -> Unit, action: String) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Dialog(onDismissRequest = { onDone(text) }) {
        Column(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(NeonPalette.Night)
                .border(1.dp, NeonPalette.Magenta.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NeonText(title, style = NeonType.headline(22.sp))
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { text = it; onChange(it) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = NeonPalette.Text, fontSize = 20.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(NeonPalette.Cyan),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { keyboard?.hide(); onDone(text) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.5.dp, NeonPalette.Cyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .focusRequester(field)
                    .semantics { contentDescription = title }
            )
            NeonButton(action, Modifier.fillMaxWidth(), { keyboard?.hide(); onDone(text) })
        }
    }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { field.requestFocus() }
        keyboard?.show()
    }
}
