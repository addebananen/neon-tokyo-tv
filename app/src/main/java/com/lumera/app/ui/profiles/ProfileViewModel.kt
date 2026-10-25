package com.lumera.app.ui.profiles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.profile.ProfileConfigurationManager
import com.lumera.app.data.trakt.TraktAuthManager
import com.lumera.app.ui.theme.DefaultThemes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AddonDao,
    private val profileConfigurationManager: ProfileConfigurationManager,
    private val traktAuthManager: TraktAuthManager
) : ViewModel() {

    private val _profiles = MutableStateFlow<List<ProfileEntity>>(emptyList())
    val profiles: StateFlow<List<ProfileEntity>> = _profiles

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _wizardStep = MutableStateFlow(0)
    val wizardStep: StateFlow<Int> = _wizardStep

    /** The profile being set up after it was picked for the first time, while that runs. */
    private val _preparingProfileId = MutableStateFlow<Int?>(null)
    val preparingProfileId: StateFlow<Int?> = _preparingProfileId

    /** Each theme's accent, so every profile glows in the colour it picked. */
    val themeColors: StateFlow<Map<String, Long>> = dao.getAllThemes()
        .map { themes -> DefaultThemes.ALL.associate { it.id to it.primaryColor } + themes.associate { it.id to it.primaryColor } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DefaultThemes.ALL.associate { it.id to it.primaryColor })

    // WIZARD DATA
    var tempName = ""
    var tempAvatarRef = "avatar_1"
    var tempThemeId = "neon_tokyo"

    private var editingProfileId: Int? = null
    private var avatarChosen = false

    /** Where the avatar step starts: the profile's own picture when editing, or the one picked before going back. */
    val chosenAvatarRef: String? get() = if (editingProfileId != null || avatarChosen) tempAvatarRef else null

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            dao.getProfiles().collect { list ->
                _profiles.value = list
                _isLoading.value = false
            }
        }
    }

    // --- WIZARD ACTIONS ---

    fun startWizard() {
        editingProfileId = null
        avatarChosen = false
        tempName = ""
        tempAvatarRef = "avatar_1"
        tempThemeId = "neon_tokyo"
        _wizardStep.value = 1
    }

    fun startEditWizard(profile: ProfileEntity) {
        editingProfileId = profile.id
        avatarChosen = false
        tempName = profile.name
        tempAvatarRef = profile.avatarRef
        tempThemeId = profile.themeId
        _wizardStep.value = 1
    }

    fun cancelWizard() {
        _wizardStep.value = 0
        editingProfileId = null
        tidyPhotos()
    }

    fun setWizardName(name: String) {
        tempName = name
        _wizardStep.value = 2
    }

    fun setWizardAvatar(avatarKey: String) {
        tempAvatarRef = avatarKey
        avatarChosen = true
        _wizardStep.value = 3
    }

    fun setWizardTheme(themeId: String) {
        tempThemeId = themeId
        finishWizard()
    }

    private fun finishWizard() {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            if (editingProfileId != null) {
                val updatedProfile = _profiles.value.find { it.id == editingProfileId }?.copy(
                    name = tempName,
                    avatarRef = tempAvatarRef,
                    themeId = tempThemeId
                )
                if (updatedProfile != null) dao.updateProfile(updatedProfile)
            } else {
                val profileId = dao.insertProfile(
                    ProfileEntity(
                        name = tempName,
                        avatarRef = tempAvatarRef,
                        themeId = tempThemeId,
                        navPosition = "left",
                        homeTabLayout = "cinematic",
                        roundCorners = true,
                        // Cast, trailers and More like this from the start, when the build has a TMDB key
                        tmdbEnabled = com.lumera.app.BuildConfig.TMDB_API_KEY.isNotBlank()
                    )
                ).toInt()
                if (profileId > 0) {
                    profileConfigurationManager.markPendingSetup(profileId)
                }
            }
            _wizardStep.value = 0
            editingProfileId = null
            pruneUnusedPhotos()
        }
    }

    /** A new photo for [profile], straight from the profile screen. */
    fun setProfilePhoto(profile: ProfileEntity, avatarRef: String) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val current = dao.getProfileById(profile.id) ?: return@launch
            dao.updateProfile(current.copy(avatarRef = avatarRef))
            pruneUnusedPhotos()
        }
    }

    fun needsInitialSetup(profileId: Int): Boolean {
        return profileConfigurationManager.needsInitialSetup(profileId)
    }

    /**
     * Readies a profile picked for the first time: the first profile on the TV brings in the
     * starter catalog everyone shares, the others just start with their own history.
     */
    fun prepareProfile(profileId: Int, onReady: () -> Unit) {
        if (_preparingProfileId.value != null) return
        _preparingProfileId.value = profileId
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                profileConfigurationManager.prepareNewProfile(profileId)
                withContext(Dispatchers.Main) { onReady() }
            } finally {
                _preparingProfileId.value = null
            }
        }
    }

    fun deleteProfile(id: Int) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            dao.deleteProfile(id)
            profileConfigurationManager.deleteProfileState(id)
            traktAuthManager.clearTokensForProfile(id)
            pruneUnusedPhotos()
        }
    }

    fun goBackStep() {
        if (_wizardStep.value > 0) _wizardStep.value -= 1
    }

    private fun tidyPhotos() {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) { pruneUnusedPhotos() }
    }

    // A photo sent during a setup that was cancelled, or one that was replaced, goes
    private suspend fun pruneUnusedPhotos() {
        val inUse = dao.getProfiles().first().map { it.avatarRef }
        val inWizard = if (_wizardStep.value != 0) listOf(tempAvatarRef) else emptyList()
        ProfilePhotos.pruneUnused(context, inUse + inWizard)
    }
}
