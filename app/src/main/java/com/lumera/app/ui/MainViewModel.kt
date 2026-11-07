package com.lumera.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.profile.ProfileConfigurationManager
import com.lumera.app.data.trakt.TraktAuthManager
import com.lumera.app.data.trakt.TraktSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dao: AddonDao,
    private val profileConfigurationManager: ProfileConfigurationManager,
    private val traktAuthManager: TraktAuthManager,
    private val traktSyncManager: TraktSyncManager,
    private val stremioWatchSync: com.lumera.app.handoff.StremioWatchSync
) : ViewModel() {

    private val _activeProfile = MutableStateFlow<ProfileEntity?>(null)
    val activeProfile: StateFlow<ProfileEntity?> = _activeProfile

    private var profileJob: Job? = null
    private var traktSyncJob: Job? = null
    private var traktConnectionJob: Job? = null
    private var loginJob: Job? = null
    private var stremioSyncJob: Job? = null

    companion object {
        private const val TRAKT_POLL_INTERVAL_MS = 30_000L // 30 seconds
        private const val STREMIO_POLL_INTERVAL_MS = 120_000L
        private const val STREMIO_PUSH_DELAY_MS = 5_000L
    }

    // Call this when user clicks a profile
    fun login(id: Int) {
        val previousLogin = loginJob
        loginJob = viewModelScope.launch {
            previousLogin?.cancelAndJoin()
            // The last profile's Trakt sync must be done before anything is saved as the new one's
            stopTraktSync()

            profileConfigurationManager.activateProfile(id)

            profileJob?.cancel()
            profileJob = viewModelScope.launch {
                dao.getProfileFlow(id).collect { profile ->
                    _activeProfile.value = profile
                }
            }

            startStremioWatchSync()

            // Refresh Trakt connection for this profile and start periodic sync
            traktAuthManager.refreshConnectionState()
            traktSyncManager.resetActivityState()
            startTraktPeriodicSync()

            // Watch for Trakt connection changes (e.g. user connects after login)
            traktConnectionJob = viewModelScope.launch {
                traktAuthManager.isConnected.collect { connected ->
                    if (connected && traktSyncJob?.isActive != true) {
                        startTraktPeriodicSync()
                    } else if (!connected) {
                        traktSyncJob?.cancel()
                    }
                }
            }
        }
    }

    /**
     * Continue watching kept the same as the Stremio account's (for the profile it belongs to):
     * now, a few seconds after each change here, and every two minutes for changes elsewhere.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startStremioWatchSync() {
        stremioSyncJob?.cancel()
        stremioSyncJob = viewModelScope.launch(Dispatchers.IO) {
            if (!stremioWatchSync.appliesNow()) return@launch
            stremioWatchSync.sync()
            launch {
                dao.getWatchHistory()
                    .drop(1)
                    .debounce(STREMIO_PUSH_DELAY_MS)
                    .collect { stremioWatchSync.sync() }
            }
            while (isActive) {
                delay(STREMIO_POLL_INTERVAL_MS)
                stremioWatchSync.sync()
            }
        }
    }

    /** The app came back to the front: what was watched on the phone meanwhile comes in now. */
    fun onResumed() {
        viewModelScope.launch(Dispatchers.IO) {
            if (stremioWatchSync.appliesNow()) stremioWatchSync.sync()
        }
    }

    private suspend fun stopTraktSync() {
        stremioSyncJob?.cancelAndJoin()
        traktConnectionJob?.cancelAndJoin()
        traktSyncJob?.cancelAndJoin()
    }

    private fun startTraktPeriodicSync() {
        traktSyncJob?.cancel()
        if (!traktAuthManager.isConnected.value) return

        traktSyncJob = viewModelScope.launch(Dispatchers.IO) {
            // Immediate full sync on login; what was watched here before Trakt was linked goes up first
            traktSyncManager.pushLocalHistory()
            traktSyncManager.syncWatchlist()
            traktSyncManager.syncPlaybackProgress()
            traktSyncManager.syncSeriesNextUp()

            // Then lightweight activity check every 30 seconds —
            // only triggers a full sync when Trakt detects changes
            while (isActive) {
                delay(TRAKT_POLL_INTERVAL_MS)
                traktSyncManager.checkAndSync()
            }
        }
    }

    fun logout() {
        val previousLogin = loginJob
        loginJob = viewModelScope.launch {
            previousLogin?.cancelAndJoin()
            stopTraktSync()
            profileJob?.cancel()
            profileConfigurationManager.deactivateProfile()
            _activeProfile.value = null
        }
    }
}
