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
}