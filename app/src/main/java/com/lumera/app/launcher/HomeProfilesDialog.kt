package com.lumera.app.launcher

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.lumera.app.R
import com.lumera.app.ui.profiles.ProfileAssets
import com.lumera.app.ui.profiles.ProfilePhotoDialog
import com.lumera.app.ui.profiles.ProfilePhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The home profiles: OK on one switches to it, holding OK opens it to change its name, photo,
 * style and background. A style chosen there shows behind the panel at once.
 */
@Composable
fun HomeProfilesDialog(profiles: HomeProfiles, onDismiss: () -> Unit) {
    val list by profiles.profiles.collectAsState()
    val activeId by profiles.activeId.collectAsState()
    var editing by remember { mutableStateOf<String?>(null) }
    val look = LocalHomeLook.current
    Dialog(onDismissRequest = { if (editing != null) editing = null else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = editing,
                transitionSpec = { (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)).togetherWith(fadeOut(tween(140))) },
                label = "HomeProfilesStep"
            ) { id ->
                val profile = id?.let { key -> list.firstOrNull { it.id == key } }
                if (profile == null) {
                    ProfileList(
                        list = list,
                        activeId = profiles.active(list, activeId).id,
                        look = look,
                        onPick = {
                            profiles.select(it.id)
                            onDismiss()
                        },
                        onEdit = { editing = it.id },
                        onAdd = { editing = profiles.add("Profile ${list.size + 1}", HomeStyle.Neon).id }
                    )
                } else {
                    ProfileEditor(
                        profile = profile,
                        canDelete = list.size > 1,
                        profiles = profiles,
                        look = look,
                        onDone = { editing = null },
                        onDelete = {
                            profiles.remove(profile.id)
                            editing = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileList(
    list: List<HomeProfile>,
    activeId: String,
    look: HomeLook,
    onPick: (HomeProfile) -> Unit,
    onEdit: (HomeProfile) -> Unit,
    onAdd: () -> Unit
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Column(
        modifier = Modifier
            .homePanel(look, RoundedCornerShape(18.dp))
            .padding(horizontal = 30.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PanelTitle("Who's home?", look)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            list.forEach { profile ->
                ProfileCard(
                    profile = profile,
                    active = profile.id == activeId,
                    look = look,
                    onClick = { onPick(profile) },
                    onLongClick = { onEdit(profile) },
                    modifier = if (profile.id == activeId) Modifier.focusRequester(first) else Modifier
                )
            }
            if (list.size < MaxProfiles) AddCard(look, onAdd)
        }
        PanelNote("OK switches  ·  hold OK to change a profile", look)
    }
}

private const val MaxProfiles = 6

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProfileCard(
    profile: HomeProfile,
    active: Boolean,
    look: HomeLook,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(104.dp)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.size(84.dp).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(if (active) 2.dp else 1.dp, if (active) profile.style.look.accent else look.text.copy(alpha = 0.2f)), shape = CircleShape),
                focusedBorder = Border(BorderStroke(2.5.dp, look.accent), shape = CircleShape)
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = look.accent.copy(alpha = 0.5f), elevation = look.glow))
        ) {
            ProfileFace(profile, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        BasicText(
            profile.name,
            style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 13.sp, color = if (focused || active) look.text else look.dim),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            profile.style.title,
            style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 10.sp, color = profile.style.look.accent.copy(alpha = 0.9f)),
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddCard(look: HomeLook, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(104.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(84.dp),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(containerColor = look.text.copy(alpha = 0.06f), focusedContainerColor = look.accent.copy(alpha = 0.18f)),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, look.text.copy(alpha = 0.25f)), shape = CircleShape),
                focusedBorder = Border(BorderStroke(2.5.dp, look.accent), shape = CircleShape)
            )
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText("+", style = TextStyle(fontFamily = look.font, fontSize = 34.sp, color = look.text))
            }
        }
        Spacer(Modifier.height(10.dp))
        BasicText("New", style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 13.sp, color = look.dim))
    }
}

/** A profile's photo, or the first letter of its name on its style's colours. */
@Composable
fun ProfileFace(profile: HomeProfile, modifier: Modifier = Modifier, letterSize: TextUnit = 30.sp) {
    val photo = rememberPicture(profile.photo, 192)
    val look = profile.style.look
    Box(modifier.clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(look.tileTop.copy(alpha = 1f), look.accent.copy(alpha = 0.55f)))), contentAlignment = Alignment.Center) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            BasicText(
                profile.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontStyle = look.fontStyle, fontSize = letterSize, color = look.text)
            )
        }
    }
}

/** A picture file decoded small enough for [size] pixels, off the main thread. */
@Composable
private fun rememberPicture(path: String?, size: Int): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { decodeSampled(File(it), size) } }
    }
    return image
}

private fun decodeSampled(file: File, size: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

@Composable
private fun ProfileEditor(
    profile: HomeProfile,
    canDelete: Boolean,
    profiles: HomeProfiles,
    look: HomeLook,
    onDone: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var takingPhoto by remember { mutableStateOf(false) }
    var takingBackground by remember { mutableStateOf(false) }
    val styleList = remember { FocusRequester() }
    LaunchedEffect(profile.id) {
        withFrameNanos { }
        runCatching { styleList.requestFocus() }
    }
    fun saveName() {
        val clean = name.trim()
        if (clean.isNotEmpty() && clean != profile.name) profiles.update(profile.copy(name = clean))
    }
    Column(
        modifier = Modifier
            .width(820.dp)
            .homePanel(look, RoundedCornerShape(18.dp))
            .padding(horizontal = 30.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ProfileFace(profile.copy(name = name.ifBlank { profile.name }), Modifier.size(64.dp))
            Column(Modifier.weight(1f)) {
                PanelNote("NAME", look)
                NameField(name, look, onChange = { name = it.take(20) }, onDone = { saveName() })
            }
            PanelButton("Photo", look) { takingPhoto = true }
            PanelButton(if (profile.background == null) "Own background" else "New background", look) { takingBackground = true }
            if (profile.background != null) PanelButton("Use the painting", look) { profiles.update(profile.copy(background = null)) }
        }
        PanelNote("STYLE", look)
        StylePicker(profile.style, look, styleList) { style ->
            saveName()
            profiles.update(profile.copy(name = name.trim().ifEmpty { profile.name }, style = style))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelButton("Done", look, primary = true) {
                saveName()
                onDone()
            }
            if (canDelete) PanelButton("Remove profile", look, onClick = onDelete)
            Spacer(Modifier.weight(1f))
            PanelNote(profile.style.blurb, look)
        }
    }
    if (takingPhoto) {
        ProfilePhotoDialog(
            profileName = name,
            onDismissRequest = { takingPhoto = false },
            onPhotoChosen = { ref ->
                takingPhoto = false
                val saved = keepFile(context, profiles, ref)
                if (saved != null) profiles.update(profile.copy(photo = saved))
            }
        )
    }
    if (takingBackground) {
        HomeBackgroundDialog(
            profileName = name,
            profiles = profiles,
            onDismiss = { takingBackground = false },
            onChosen = { path ->
                takingBackground = false
                profiles.update(profile.copy(background = path))
            }
        )
    }
}

/** Moves a photo the phone sent into the home profiles' own folder, so the app's profiles never tidy it away. */
private fun keepFile(context: Context, profiles: HomeProfiles, avatarRef: String): String? {
    val source = ProfileAssets.customFile(avatarRef) ?: return null
    val target = profiles.newFile("photo")
    return runCatching {
        source.copyTo(target, overwrite = true)
        ProfilePhotos.delete(avatarRef)
        target.absolutePath
    }.getOrNull()
}

@Composable
private fun NameField(value: String, look: HomeLook, onChange: (String) -> Unit, onDone: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 20.sp, color = look.text),
        cursorBrush = SolidColor(look.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .onFocusChanged {
                if (focused && !it.isFocused) onDone()
                focused = it.isFocused
            }
            .border(if (focused) 1.5.dp else 1.dp, if (focused) look.accent else look.text.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/** The styles side by side, each a little picture of its painting; OK on one chooses it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StylePicker(current: HomeStyle, look: HomeLook, focusRequester: FocusRequester, onChoose: (HomeStyle) -> Unit) {
    val start = HomeStyle.entries.indexOf(current)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0))
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
    ) {
        itemsIndexed(HomeStyle.entries) { index, style ->
            val chosen = style == current
            val shape = RoundedCornerShape(10.dp)
            var focused by remember { mutableStateOf(false) }
            val lift by animateFloatAsState(if (focused) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "StyleLift")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = { onChoose(style) },
                    modifier = Modifier
                        .size(StyleThumb.first, StyleThumb.second)
                        .then(if (index == start) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged { focused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(shape = shape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black, focusedContainerColor = Color.Black),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(if (chosen) 2.dp else 0.5.dp, if (chosen) look.accent else look.text.copy(alpha = 0.2f)), shape = shape),
                        focusedBorder = Border(BorderStroke(2.5.dp, style.look.accent), shape = shape)
                    ),
                    glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = style.look.accent.copy(alpha = 0.55f), elevation = 14.dp))
                ) {
                    StyleThumbnail(style)
                }
                Spacer(Modifier.height(8.dp))
                BasicText(
                    style.title,
                    style = TextStyle(
                        fontFamily = style.look.font,
                        fontWeight = style.look.weight,
                        fontStyle = style.look.fontStyle,
                        fontSize = 12.sp,
                        color = if (focused || chosen) look.text else look.dim
                    ),
                    modifier = Modifier.graphicsLayer { translationY = lift * 4.dp.toPx() }
                )
            }
        }
    }
}

private val StyleThumb: Pair<Dp, Dp> = 144.dp to 81.dp

@Composable
private fun StyleThumbnail(style: HomeStyle) {
    val context = LocalContext.current
    val image by produceState(ThumbCache.cached(style), style) {
        if (value == null) value = withContext(Dispatchers.IO) { ThumbCache.load(context, style) }
    }
    image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
}

/** Small copies of the paintings, an eighth of their size, for the style picker. */
private object ThumbCache {
    private val thumbs = HashMap<HomeStyle, ImageBitmap>()

    @Synchronized
    fun cached(style: HomeStyle): ImageBitmap? = thumbs[style]

    fun load(context: Context, style: HomeStyle): ImageBitmap? {
        cached(style)?.let { return it }
        val drawable = style.backdrop ?: R.drawable.nt_wall_tokyo
        val image = runCatching {
            BitmapFactory.decodeResource(context.resources, drawable, BitmapFactory.Options().apply { inSampleSize = 8 })
        }.getOrNull()?.asImageBitmap() ?: return null
        synchronized(this) { thumbs[style] = image }
        return image
    }
}

@Composable
internal fun PanelTitle(text: String, look: HomeLook) {
    BasicText(
        text,
        style = TextStyle(
            fontFamily = look.font,
            fontWeight = look.weight,
            fontStyle = look.fontStyle,
            fontSize = 24.sp,
            letterSpacing = look.letterSpacing * 0.5f,
            color = look.text,
            shadow = if (look.glowingText) androidx.compose.ui.graphics.Shadow(look.accent.copy(alpha = 0.6f), blurRadius = 16f) else null
        )
    )
}

@Composable
internal fun PanelNote(text: String, look: HomeLook) {
    BasicText(
        text,
        style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 11.sp, letterSpacing = 0.8.sp, color = look.dim),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PanelButton(text: String, look: HomeLook, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) look.accent.copy(alpha = 0.22f) else look.text.copy(alpha = 0.07f),
            contentColor = look.text,
            focusedContainerColor = look.accent.copy(alpha = 0.32f),
            focusedContentColor = look.text
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.5.dp, look.text.copy(alpha = 0.2f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, look.accent), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = look.accent.copy(alpha = 0.45f), elevation = 10.dp))
    ) {
        BasicText(
            text,
            style = TextStyle(fontFamily = look.font, fontWeight = look.weight, fontSize = 12.sp, color = look.text),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
