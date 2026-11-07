package com.lumera.app.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity

@Immutable
@Entity(tableName = "watch_history", primaryKeys = ["profileId", "id"])
data class WatchHistoryEntity(
    val id: String,
    val title: String,
    val poster: String?,
    val background: String? = null,
    val logo: String? = null,
    val position: Long,
    val duration: Long,
    val lastWatched: Long,
    val type: String,
    val watched: Boolean = false,   // true = fully watched (past threshold), false = in progress
    val scrobbled: Boolean = false, // true = Trakt knows about this item (scrobble was sent successfully)
    val profileId: Int = 0          // whose history this is; AddonDao fills in the watching profile
) {
    fun progress(): Float {
        if (duration == 0L) return 0f
        return position.toFloat() / duration.toFloat()
    }

    /** Only how far in is known (Trakt, another device), not the length: see [PERCENT_DURATION_MS]. */
    val isShare: Boolean get() = duration == PERCENT_DURATION_MS

    companion object {
        /**
         * The length saved for a title when only its share watched is known: position is then
         * that share of this number, so [progress] still reads right and the player goes to the
         * same share of the real length. The same marker as the web app's and the shared core's.
         */
        const val PERCENT_DURATION_MS = 1_000_000_000L
    }
}