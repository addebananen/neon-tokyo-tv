package com.lumera.app.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.lumera.app.ui.settings.ThemeEditorScreen
import com.lumera.app.R
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.model.ThemeEntity
import com.lumera.app.ui.addons.VoidButton
import com.lumera.app.ui.addons.VoidInput
import com.lumera.app.ui.components.CenterCarouselRow
import com.lumera.app.ui.components.NeonPalette
import com.lumera.app.ui.components.NeonSkylineBackdrop
import com.lumera.app.ui.components.NeonText
import com.lumera.app.ui.components.NeonType
import com.lumera.app.ui.components.neonPanel
import com.lumera.app.ui.home.DpadRepeatGate
import com.lumera.app.ui.theme.DefaultThemes
import com.lumera.app.ui.theme.LumeraTheme
import com.lumera.app.ui.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

private const val PROFILE_HORIZONTAL_REPEAT_INTERVAL_MS = 150L

@Composable
fun ProfileScreen(
    profiles: List<ProfileEntity>,
    onProfileSelected: (ProfileEntity) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val wizardStep by viewModel.wizardStep.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val isWelcome = wizardStep == 0 && profiles.isEmpty()
    Box(modifier = Modifier.fillMaxSize()) {
        // The skyline stays put while the steps fade over it; it dims on screens with more to read
        NeonSkylineBackdrop(
            dim = when {
                isLoading -> 0.75f
                isWelcome -> 0.05f
                wizardStep == 0 -> 0.45f
                else -> 0.62f
            },
            leftScrim = !isLoading && isWelcome
        )

        // While loading only the backdrop shows, so the WelcomeView never flashes
        if (!isLoading) {
            AnimatedContent(
                targetState = wizardStep,
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
                label = "ProfileFlow"
            ) { step ->
                when (step) {
                    0 -> {
                        // ZERO STATE or SELECTOR
                        if (profiles.isEmpty()) {
                            WelcomeView(onStart = { viewModel.startWizard() })
                        } else {
                            ProfileSelectorView(
                                profiles = profiles,
                                onSelect = onProfileSelected,
                                onAdd = { viewModel.startWizard() },
                                onEdit = { viewModel.startEditWizard(it) },
                                onDelete = { viewModel.deleteProfile(it.id) },
                                viewModel = viewModel
                            )
                        }
                    }
                    1 -> WizardNameStep(
                        initialName = viewModel.tempName,
                        onNext = { viewModel.setWizardName(it) },
                        onCancel = { viewModel.cancelWizard() }
                    )
                    2 -> WizardAvatarStep(
                        profileName = viewModel.tempName,
                        selectedAvatarRef = viewModel.chosenAvatarRef,
                        onNext = { viewModel.setWizardAvatar(it) },
                        onBack = { viewModel.goBackStep() }
                    )
                    3 -> WizardThemeStep(
                        onFinish = { viewModel.setWizardTheme(it) },
                        onBack = { viewModel.goBackStep() }
                    )
                }
            }
        }

    }
}


// --- 1. WELCOME VIEW ---
@Composable
fun WelcomeView(onStart: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); requester.requestFocus() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 96.dp)
                .widthIn(max = 520.dp)
        ) {
            NeonText("WELCOME TO", style = NeonType.eyebrow(15.sp))
            Spacer(Modifier.height(6.dp))
            NeonText("NEON TOKYO", style = NeonType.display(64.sp))
            Spacer(Modifier.height(14.dp))
            Text(
                "Films, series and anime, after dark.",
                style = MaterialTheme.typography.titleMedium,
                color = NeonPalette.Lilac
            )
            Spacer(Modifier.height(40.dp))

            VoidButton(
                text = "Create First Profile",
                onClick = onStart,
                isPrimary = true,
                modifier = Modifier.width(260.dp),
                focusRequester = requester
            )
        }
    }
}

// --- 2. SELECTOR VIEW ---
@Composable
fun ProfileSelectorView(
    profiles: List<ProfileEntity>,
    onSelect: (ProfileEntity) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ProfileEntity) -> Unit,
    onDelete: (ProfileEntity) -> Unit,
    viewModel: ProfileViewModel
) {
    var activeProfileForOptions by remember { mutableStateOf<ProfileEntity?>(null) }
    var photoTargetProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    val preparingProfileId by viewModel.preparingProfileId.collectAsState()
    val themeColors by viewModel.themeColors.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeonText("NEON TOKYO", style = NeonType.eyebrow())
        Spacer(Modifier.height(8.dp))
        NeonText("Who's watching?", style = NeonType.headline(36.sp))

        Spacer(Modifier.height(50.dp))

        // Static centered row (no scrolling)
        val focusRequesters = remember(profiles.size) {
            List(profiles.size) { FocusRequester() }
        }

        // Request focus on first profile
        LaunchedEffect(profiles.size, activeProfileForOptions, photoTargetProfile) {
            if (profiles.isNotEmpty() && activeProfileForOptions == null && photoTargetProfile == null) {
                delay(100)
                focusRequesters[0].requestFocus()
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            profiles.forEachIndexed { index, profile ->
                ProfileCard(
                    profile = profile,
                    ringColor = themeColors[profile.themeId]?.let { Color(it.toInt()) } ?: NeonPalette.Magenta,
                    isPreparing = preparingProfileId == profile.id,
                    onClick = {
                        if (preparingProfileId != null) return@ProfileCard
                        if (viewModel.needsInitialSetup(profile.id)) {
                            // Addons are shared, so a new profile only needs its own empty history
                            viewModel.prepareProfile(profile.id) { onSelect(profile) }
                        } else {
                            onSelect(profile)
                        }
                    },
                    onEdit = { activeProfileForOptions = profile },
                    focusRequester = focusRequesters[index]
                )
            }

            // Only show Add button if under 6 profiles
            if (profiles.size < 6) {
                AddProfileCard(onClick = onAdd)
            }
        }

        // The first profile on a new TV brings in the starter catalog, which takes a moment
        Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.BottomCenter) {
            androidx.compose.animation.AnimatedVisibility(
                visible = preparingProfileId != null,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300))
            ) {
                Text(
                    "Tuning in the city lights…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonPalette.Lilac
                )
            }
        }
    }

    if (activeProfileForOptions != null) {
        ProfileOptionsDialog(
            profile = activeProfileForOptions!!,
            onDismiss = { activeProfileForOptions = null },
            onEdit = {
                onEdit(activeProfileForOptions!!)
                activeProfileForOptions = null
            },
            onChangePhoto = {
                photoTargetProfile = activeProfileForOptions
                activeProfileForOptions = null
            },
            onDelete = {
                onDelete(activeProfileForOptions!!)
                activeProfileForOptions = null
            }
        )
    }

    photoTargetProfile?.let { target ->
        ProfilePhotoDialog(
            profileName = target.name,
            onDismissRequest = { photoTargetProfile = null },
            onPhotoChosen = { avatarRef ->
                viewModel.setProfilePhoto(target, avatarRef)
                photoTargetProfile = null
            }
        )
    }
}

@Composable
fun ProfileOptionsDialog(
    profile: ProfileEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onChangePhoto: () -> Unit,
    onDelete: () -> Unit
) {
    val editRequester = remember { FocusRequester() }

    // SAFETY LOCK: Delay input to prevent accidental clicks
    var areButtonsReady by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        editRequester.requestFocus()
        delay(400)
        areButtonsReady = true
    }

    if (!showDeleteConfirmation) {
        Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .width(400.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .neonPanel()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 1. RESOLVE AVATAR STRING TO INT ID
                    val avatarSource = ProfileAssets.getAvatarSource(profile.avatarRef)

                    // 2. DISPLAY IMAGE
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarSource)
                            .size(300, 300)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )

                    Spacer(Modifier.height(16.dp))

                    NeonText(
                        text = profile.name.uppercase(),
                        style = NeonType.headline(24.sp)
                    )
                    Spacer(Modifier.height(32.dp))

                    // Action Buttons
                    VoidButton(
                        text = "Edit Profile",
                        onClick = {
                            if (areButtonsReady) onEdit()
                        },
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = editRequester
                    )
                    Spacer(Modifier.height(16.dp))
                    VoidButton(
                        text = if (ProfileAssets.isCustomAvatar(profile.avatarRef)) "Change Photo" else "Add Your Photo",
                        onClick = {
                            if (areButtonsReady) onChangePhoto()
                        },
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    VoidButton(
                        text = "Delete Profile",
                        onClick = {
                            if (areButtonsReady) showDeleteConfirmation = true
                        },
                        isPrimary = false,
                        isDestructive = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        DeleteConfirmationDialog(
            profileName = profile.name,
            onConfirm = {
                showDeleteConfirmation = false
                onDelete()
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val noRequester = remember { FocusRequester() }
    var isReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50)
        noRequester.requestFocus()
        isReady = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(24.dp))
                .neonPanel()
                .padding(32.dp)
                .alpha(if (isReady) 1f else 0f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NeonText(
                    text = "Delete Profile?",
                    style = NeonType.headline(24.sp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Are you sure you want to delete \"${profileName}\"?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonPalette.Lilac
                )
                Spacer(Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoidButton(
                        text = "Yes",
                        onClick = onConfirm,
                        isPrimary = false,
                        isDestructive = true,
                        modifier = Modifier.weight(1f)
                    )
                    VoidButton(
                        text = "No",
                        onClick = onDismiss,
                        isPrimary = false,
                        modifier = Modifier.weight(1f),
                        focusRequester = noRequester
                    )
                }
            }
        }
    }
}

// --- 3. WIZARD STEPS ---

@Composable
fun WizardNameStep(initialName: String, onNext: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { delay(100); focusRequester.requestFocus() }
    BackHandler { onCancel() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = (-80).dp), // Move up to avoid virtual keyboard cropping
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeonText(
            if(initialName.isEmpty()) "What should we call you?" else "Update your name",
            style = NeonType.headline(32.sp)
        )
        Spacer(Modifier.height(32.dp))

        Box(modifier = Modifier.width(400.dp)) {
            VoidInput(
                value = name,
                onValueChange = { name = it },
                placeholder = "Enter Name",
                modifier = Modifier.focusRequester(focusRequester),
                onDone = { if(name.isNotEmpty()) onNext(name) }
            )
        }
    }
}

@Composable
fun WizardAvatarStep(
    profileName: String,
    selectedAvatarRef: String?,
    onNext: (String) -> Unit,
    onBack: () -> Unit
) {
    // A photo already chosen comes first, so OK keeps it
    val avatars = remember(selectedAvatarRef) {
        val photo = selectedAvatarRef?.takeIf { ProfileAssets.isCustomAvatar(it) }
        listOfNotNull(photo) + ProfileAssets.AVATAR_MAP.keys
    }
    val initialIndex = avatars.indexOf(selectedAvatarRef).takeIf { it >= 0 } ?: (avatars.size / 2)
    val listState = rememberLazyListState()
    val horizontalRepeatGate = remember {
        DpadRepeatGate(horizontalRepeatIntervalMs = PROFILE_HORIZONTAL_REPEAT_INTERVAL_MS)
    }
    val focusRequesters = remember(avatars.size) { List(avatars.size) { FocusRequester() } }
    val uploadButtonRequester = remember { FocusRequester() }

    var showPhotoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        listState.scrollToItem(initialIndex)
        delay(100)
        focusRequesters[initialIndex].requestFocus()
    }
    BackHandler { onBack() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeonText("Choose your picture", style = NeonType.headline(32.sp))
        Spacer(Modifier.height(16.dp))
        Text("A photo of you makes it feel like yours. Or pick an avatar.", color = NeonPalette.Lilac)

        Spacer(Modifier.height(40.dp))

        CenterCarouselRow(
            itemWidth = 120.dp,
            itemSpacing = 24.dp,
            state = listState,
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
                ) {
                    horizontalRepeatGate.shouldConsume(event)
                } else {
                    false
                }
            }
        ) {
            items(avatars.size) { index ->
                val key = avatars[index]
                AvatarGridItem(
                    source = ProfileAssets.getAvatarSource(key),
                    onClick = { onNext(key) },
                    focusRequester = focusRequesters[index],
                    modifier = Modifier.size(120.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "Or",
            style = MaterialTheme.typography.bodyLarge,
            color = NeonPalette.Lilac
        )

        Spacer(Modifier.height(16.dp))

        UploadAvatarButton(
            onClick = { showPhotoDialog = true },
            focusRequester = uploadButtonRequester
        )
    }

    if (showPhotoDialog) {
        ProfilePhotoDialog(
            profileName = profileName,
            onDismissRequest = { showPhotoDialog = false },
            onPhotoChosen = { avatarRef ->
                showPhotoDialog = false
                onNext(avatarRef)
            }
        )
    }
}

@Composable
private fun UploadAvatarButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (isFocused) Brush.horizontalGradient(listOf(NeonPalette.Magenta, NeonPalette.Sun))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))
            )
            .border(
                width = 1.5.dp,
                color = if (isFocused) Color.Transparent else NeonPalette.Cyan.copy(alpha = 0.55f),
                shape = RoundedCornerShape(28.dp)
            )
            .focusRequester(focusRequester)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (isFocused) NeonPalette.Night else NeonPalette.Text
        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            "Use a Photo From Your Phone",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = tint
        )
    }
}

@Composable
fun WizardThemeStep(onFinish: (String) -> Unit, onBack: () -> Unit) {
    val themeManager: ThemeManager = hiltViewModel()
    val themes = DefaultThemes.ALL
    // Start on Neon Tokyo so a single press of OK picks the house theme
    val initialIndex = themes.indexOfFirst { it.id == DefaultThemes.NEON_TOKYO.id }.coerceAtLeast(0)
    var previewTheme by remember { mutableStateOf(themes[initialIndex]) }
    val listState = rememberLazyListState()
    val horizontalRepeatGate = remember {
        DpadRepeatGate(horizontalRepeatIntervalMs = PROFILE_HORIZONTAL_REPEAT_INTERVAL_MS)
    }
    val focusRequesters = remember(themes.size) { List(themes.size) { FocusRequester() } }
    val createButtonRequester = remember { FocusRequester() }
    
    var showThemeEditor by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        listState.scrollToItem(initialIndex)
        delay(100)
        focusRequesters[initialIndex].requestFocus()
    }
    BackHandler { onBack() }

    LumeraTheme(theme = previewTheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Offset for theme name labels below circles (not present in avatar selector)
            Spacer(Modifier.height(32.dp))
            
            NeonText("Choose a theme", style = NeonType.headline(32.sp))
            Spacer(Modifier.height(16.dp))
            Text("Select a color scheme for your experience.", color = NeonPalette.Lilac)

            Spacer(Modifier.height(40.dp))

            CenterCarouselRow(
                itemWidth = 120.dp,
                itemSpacing = 24.dp,
                state = listState,
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
                    ) {
                        horizontalRepeatGate.shouldConsume(event)
                    } else {
                        false
                    }
                }
            ) {
                items(themes.size) { index ->
                    val theme = themes[index]
                    ThemePickItem(
                        theme = theme,
                        onClick = { onFinish(theme.id) },
                        focusRequester = focusRequesters[index],
                        onFocused = { previewTheme = theme }
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                "Or",
                style = MaterialTheme.typography.bodyLarge,
                color = NeonPalette.Lilac
            )
            
            Spacer(Modifier.height(16.dp))
            
            CreateThemeButton(
                onClick = { showThemeEditor = true },
                focusRequester = createButtonRequester
            )
        }
    }
    
    // Theme Editor Dialog
    if (showThemeEditor) {
        Dialog(
            onDismissRequest = { showThemeEditor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NeonPalette.Night)
                    .padding(32.dp)
            ) {
                ThemeEditorScreen(
                    editingTheme = null,
                    onSave = { name, primary, background ->
                        val newThemeId = themeManager.createCustomTheme(
                            name = name,
                            primaryColor = primary,
                            backgroundColor = background
                        )
                        showThemeEditor = false
                        onFinish(newThemeId)
                    },
                    onCancel = { showThemeEditor = false }
                )
            }
        }
    }
}

@Composable
private fun CreateThemeButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)
    
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.1f)
            )
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusRequester(focusRequester)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Create Your Own",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun ThemePickItem(
    theme: ThemeEntity,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    onFocused: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f)

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused?.invoke()
    }

    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .then(focusModifier)
                .clickable(interactionSource = interactionSource, indication = null) { onClick() }
                .focusable(interactionSource = interactionSource),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(Color(theme.backgroundColor.toInt()))
                    .border(
                        3.dp,
                        if (isFocused) Color.White else Color.Transparent,
                        CircleShape
                    )
                    .padding(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(theme.primaryColor.toInt()))
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = theme.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isFocused) Color.White else NeonPalette.Lilac
        )
    }
}

@Composable
fun ProfileCard(
    profile: ProfileEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    focusRequester: FocusRequester? = null,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    isPreparing: Boolean = false
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardFocused by cardInteractionSource.collectIsFocusedAsState()
    
    val editInteractionSource = remember { MutableInteractionSource() }
    val isEditFocused by editInteractionSource.collectIsFocusedAsState()
    
    // Pencil is visible when either card or pencil is focused
    val isPencilVisible = isCardFocused || isEditFocused
    
    val context = LocalContext.current

    val scale by animateFloatAsState(if (isCardFocused) 1.1f else 1f)
    val borderAlpha by animateFloatAsState(if (isCardFocused || isEditFocused) 1f else 0f)

    // CONVERT STRING ("avatar_5") -> Source (Int or File)
    val avatarSource = ProfileAssets.getAvatarSource(profile.avatarRef)

    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        val bloomColor = ringColor
        val preparingPulse = if (isPreparing) {
            val transition = rememberInfiniteTransition(label = "profile_preparing")
            transition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "profile_preparing_pulse"
            )
        } else {
            null
        }
        Box(
            modifier = Modifier
                .size(120.dp)
                // Neon halo that fades in with the focus ring (drawn before the clip so it can spill out)
                .drawBehind {
                    val lit = preparingPulse?.value ?: borderAlpha
                    if (lit > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.55f to bloomColor.copy(alpha = 0.5f * lit),
                                1f to Color.Transparent,
                                radius = size.minDimension * 0.85f
                            ),
                            radius = size.minDimension * 0.85f
                        )
                    }
                }
                .clip(CircleShape)
                .background(NeonPalette.Violet)
                .border(3.dp, ringColor.copy(alpha = borderAlpha), CircleShape)
                .then(focusModifier)
                .onPreviewKeyEvent { event ->
                    val isConfirm = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (isConfirm && event.type == KeyEventType.KeyUp) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }
                .focusable(interactionSource = cardInteractionSource)
                .clickable(
                    interactionSource = cardInteractionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarSource)
                    .size(300, 300)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = profile.name.uppercase(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = if(isCardFocused || isEditFocused) Color.White else NeonPalette.Lilac
        )
        
        Spacer(Modifier.height(8.dp))
        
        // Pencil Edit Icon - always in composition but visibility controlled by alpha
        val editScale by animateFloatAsState(if (isEditFocused) 1.2f else 1f)
        val pencilAlpha by animateFloatAsState(if (isPencilVisible) 1f else 0f)
        
        Box(
            modifier = Modifier
                .size(32.dp)
                .scale(editScale)
                .alpha(pencilAlpha)
                .clip(CircleShape)
                .background(if (isEditFocused) Color.White else Color.Transparent)
                .clickable(
                    interactionSource = editInteractionSource,
                    indication = null,
                    onClick = onEdit
                )
                .focusable(interactionSource = editInteractionSource),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Profile",
                tint = if (isEditFocused) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AddProfileCard(onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f)

    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(NeonPalette.Violet.copy(alpha = 0.6f))
                .border(2.dp, if (isFocused) MaterialTheme.colorScheme.primary else NeonPalette.Lilac.copy(alpha = 0.35f), CircleShape)
                .then(focusModifier)
                .clickable(interactionSource = interactionSource, indication = null) { onClick() }
                .focusable(interactionSource = interactionSource),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "ADD PROFILE",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = if(isFocused) Color.White else NeonPalette.Lilac
        )
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun AvatarGridItem(
    source: Any,
    onClick: () -> Unit, 
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.15f else 1f)

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused?.invoke()
    }

    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    val context = LocalContext.current

    Box(
        modifier = modifier
            .then(focusModifier)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .border(3.dp, if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
        ) {
            AsyncImage(
            model = ImageRequest.Builder(context)
                .data(source)
                .size(300, 300)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
}
