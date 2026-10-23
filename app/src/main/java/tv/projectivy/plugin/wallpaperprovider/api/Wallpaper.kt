package tv.projectivy.plugin.wallpaperprovider.api

import android.os.Parcel
import android.os.Parcelable

/*
 * Projectivy Launcher's wallpaper provider plugin API, from
 * https://github.com/spocky/projectivy-plugin-wallpaper-provider (Apache License 2.0).
 * The package and class names must stay as they are: Projectivy talks to the plugin over AIDL.
 * The original uses @Parcelize; the Parcelable code here is written out by hand in the same
 * field order, so the bytes on the wire are identical.
 */

object WallpaperType {
    const val IMAGE = 0
    const val DRAWABLE = 1
    const val ANIMATED_DRAWABLE = 2
    const val LOTTIE = 3
    const val VIDEO = 4
    const val COLOR = 5
}

object WallpaperDisplayMode {
    const val DEFAULT = 0
    const val CROP = 1
    const val STRETCH = 2
    const val BLUR = 3
    const val FIT_CENTER = 4
    const val FIT_CENTER_BLURRED_BG = 5
}

data class Wallpaper(
    val uri: String,
    val type: Int = WallpaperType.IMAGE,
    val displayMode: Int = WallpaperDisplayMode.CROP,
    val title: String? = null,
    val source: String? = null,
    val author: String? = null,
    val actionUri: String? = null
) : Parcelable {

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(uri)
        parcel.writeInt(type)
        parcel.writeInt(displayMode)
        parcel.writeString(title)
        parcel.writeString(source)
        parcel.writeString(author)
        parcel.writeString(actionUri)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Wallpaper> = object : Parcelable.Creator<Wallpaper> {
            override fun createFromParcel(parcel: Parcel): Wallpaper = Wallpaper(
                uri = parcel.readString().orEmpty(),
                type = parcel.readInt(),
                displayMode = parcel.readInt(),
                title = parcel.readString(),
                source = parcel.readString(),
                author = parcel.readString(),
                actionUri = parcel.readString()
            )

            override fun newArray(size: Int): Array<Wallpaper?> = arrayOfNulls(size)
        }
    }
}
