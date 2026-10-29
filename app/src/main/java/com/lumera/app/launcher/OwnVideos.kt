package com.lumera.app.launcher

import android.content.Context
import com.lumera.app.dream.DreamVideo
import com.lumera.app.dream.NeonDreamStyle
import java.io.File

/**
 * The videos sent from a phone that the TV keeps: the screensaver's own, and each home profile's
 * background and screensaver video. A phone's film is large on a TV's small storage, so there are
 * never more than [MAX]: once a new one is on, those nothing plays any more go, and then the
 * oldest, until the new one and one other are left.
 */
object OwnVideos {
    const val MAX = 2

    private class Kept(val file: File, val inUse: Boolean, val drop: () -> Unit)

    /** Tidies up after [newest] arrived; says how many went. */
    fun tidy(context: Context, newest: File): Int {
        val profiles = HomeProfiles.get(context)
        val style = NeonDreamStyle.get(context)
        val kept = buildList {
            val screensaver = DreamVideo.file(context)
            if (screensaver.length() > 0) add(Kept(screensaver, style == NeonDreamStyle.VIDEO) {
                DreamVideo.remove(context)
                if (NeonDreamStyle.get(context) == NeonDreamStyle.VIDEO) NeonDreamStyle.set(context, NeonDreamStyle.HOME)
            })
            profiles.profiles.value.forEach { profile ->
                profile.background?.takeIf { isVideo(it) }?.let {
                    add(Kept(File(it), true) { profiles.edit(profile.id) { p -> p.copy(background = null) } })
                }
                // A profile's screensaver video only plays while the screensaver follows Neon Home
                profile.dreamVideo?.let {
                    add(Kept(File(it), style == NeonDreamStyle.HOME) { profiles.edit(profile.id) { p -> p.copy(dreamVideo = null) } })
                }
            }
        }
        val others = kept.filter { it.file.absolutePath != newest.absolutePath }
        val gone = others.filter { !it.inUse } +
            others.filter { it.inUse }.sortedByDescending { it.file.lastModified() }.drop(MAX - 1)
        gone.forEach { runCatching { it.drop() } }
        if (gone.isNotEmpty()) android.util.Log.i("OwnVideos", "removed ${gone.size} older video(s), ${kept.size - gone.size} kept")
        return gone.size
    }
}
