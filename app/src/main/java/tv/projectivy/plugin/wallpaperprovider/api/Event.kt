package tv.projectivy.plugin.wallpaperprovider.api

import android.os.Parcel
import android.os.Parcelable

/*
 * Projectivy Launcher's wallpaper provider plugin API, from
 * https://github.com/spocky/projectivy-plugin-wallpaper-provider (Apache License 2.0).
 * Written out by hand in the field order @Parcelize uses in the original (see Wallpaper.kt).
 * Every event starts with its type; a @Parcelize object writes the int 1, which is TIME_ELAPSED.
 */

object WallpaperUpdateEventType {
    const val TIME_ELAPSED = 1
    const val NOW_PLAYING_CHANGED = 2
    const val CARD_FOCUSED = 4
    const val PROGRAM_CARD_FOCUSED = 8
    const val LAUNCHER_IDLE_MODE_CHANGED = 16
}

sealed class Event(open val eventType: Int) : Parcelable {

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Event> {
        override fun createFromParcel(parcel: Parcel): Event? {
            val start = parcel.dataPosition()
            return when (val type = parcel.readInt()) {
                WallpaperUpdateEventType.TIME_ELAPSED -> TimeElapsed
                WallpaperUpdateEventType.NOW_PLAYING_CHANGED -> NowPlayingChanged(
                    eventType = type,
                    isPlaying = parcel.readInt() != 0,
                    title = parcel.readString(),
                    artist = parcel.readString(),
                    album = parcel.readString(),
                    iconUri = parcel.readString()
                )
                WallpaperUpdateEventType.CARD_FOCUSED -> CardFocused(
                    eventType = type,
                    lightColor = parcel.readInt(),
                    darkColor = parcel.readInt(),
                    title = parcel.readString(),
                    packageName = parcel.readString()
                )
                WallpaperUpdateEventType.PROGRAM_CARD_FOCUSED -> ProgramCardFocused(
                    eventType = type,
                    title = parcel.readString(),
                    type = if (parcel.readInt() != 0) parcel.readInt() else null,
                    iconUri = parcel.readString(),
                    iconAspectRatio = parcel.readInt()
                )
                WallpaperUpdateEventType.LAUNCHER_IDLE_MODE_CHANGED -> LauncherIdleModeChanged(
                    eventType = type,
                    isIdle = parcel.readInt() != 0
                )
                else -> {
                    parcel.setDataPosition(start)
                    null
                }
            }
        }

        override fun newArray(size: Int): Array<Event?> = arrayOfNulls(size)
    }

    object TimeElapsed : Event(WallpaperUpdateEventType.TIME_ELAPSED) {
        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(1)
        }
    }

    data class NowPlayingChanged(
        override val eventType: Int = WallpaperUpdateEventType.NOW_PLAYING_CHANGED,
        val isPlaying: Boolean = true,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val iconUri: String? = null
    ) : Event(WallpaperUpdateEventType.NOW_PLAYING_CHANGED) {
        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(eventType)
            parcel.writeInt(if (isPlaying) 1 else 0)
            parcel.writeString(title)
            parcel.writeString(artist)
            parcel.writeString(album)
            parcel.writeString(iconUri)
        }
    }

    data class CardFocused(
        override val eventType: Int = WallpaperUpdateEventType.CARD_FOCUSED,
        val lightColor: Int = 0,
        val darkColor: Int = 0,
        val title: String? = null,
        val packageName: String? = null
    ) : Event(WallpaperUpdateEventType.CARD_FOCUSED) {
        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(eventType)
            parcel.writeInt(lightColor)
            parcel.writeInt(darkColor)
            parcel.writeString(title)
            parcel.writeString(packageName)
        }
    }

    // type refers to TYPE_* in TvContractCompat.PreviewProgramColumns if < 100,
    // or androidx.media3.common.@MediaType if >= 100 (a Now Playing card)
    data class ProgramCardFocused(
        override val eventType: Int = WallpaperUpdateEventType.PROGRAM_CARD_FOCUSED,
        val title: String? = null,
        val type: Int? = null,
        val iconUri: String? = null,
        val iconAspectRatio: Int = 0
    ) : Event(WallpaperUpdateEventType.PROGRAM_CARD_FOCUSED) {
        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(eventType)
            parcel.writeString(title)
            if (type == null) {
                parcel.writeInt(0)
            } else {
                parcel.writeInt(1)
                parcel.writeInt(type)
            }
            parcel.writeString(iconUri)
            parcel.writeInt(iconAspectRatio)
        }
    }

    data class LauncherIdleModeChanged(
        override val eventType: Int = WallpaperUpdateEventType.LAUNCHER_IDLE_MODE_CHANGED,
        val isIdle: Boolean = true
    ) : Event(WallpaperUpdateEventType.LAUNCHER_IDLE_MODE_CHANGED) {
        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(eventType)
            parcel.writeInt(if (isIdle) 1 else 0)
        }
    }
}
