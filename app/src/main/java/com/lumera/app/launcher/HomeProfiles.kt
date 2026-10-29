package com.lumera.app.launcher

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Someone's home screen: their name, their photo, the style it wears and, if they sent them from
 * their phone, a picture of their own behind it and a video for the screensaver. These belong to the home screen alone; the
 * profiles inside Neon Tokyo are separate.
 */
data class HomeProfile(
    val id: String,
    val name: String,
    val style: HomeStyle,
    /** A photo file in the app's own storage, or null for the first letter of the name. */
    val photo: String? = null,
    /** A picture file shown instead of the style's painting, or null. */
    val background: String? = null,
    /** A video file the screensaver plays while this profile shows, or null for its style's own. */
    val dreamVideo: String? = null,
    /** How the apps on its tiles look: neon, or their own icons. */
    val icons: IconPack = IconPack.Neon,
    /** The colours those icons are shown in: their own, or a remix. */
    val tint: IconTint = IconTint.Own,
    // How its home screen is laid out, each null for what its style does
    /** The glass the tiles and menus are made of. */
    val glass: HomeGlass? = null,
    /** Watch & listen down at the foot of the screen. */
    val rowsDown: Boolean? = null,
    /** The consoles in a row on Home too. */
    val consolesOnHome: Boolean? = null,
    /** Square glass icons rather than wide tiles. */
    val squareIcons: Boolean? = null,
    /** The apps' names and the buttons' words all the time, not only in focus. */
    val names: Boolean? = null,
    /** The layout it chose for each of the other styles it has worn, back again when it wears one. */
    val looks: Map<HomeStyle, HomeLayout> = emptyMap()
) {
    /** How it lays out its current style. */
    val layout: HomeLayout get() = HomeLayout(glass, rowsDown, consolesOnHome, squareIcons, names)

    /**
     * Wearing [next]: the layout chosen for this style is kept for when it comes back, and the one
     * chosen for [next] before, if any, comes back with it.
     */
    fun wearing(next: HomeStyle): HomeProfile {
        if (next == style) return this
        val kept = if (layout.isEmpty) looks - style else looks + (style to layout)
        val back = kept[next] ?: HomeLayout()
        return copy(
            style = next, glass = back.glass, rowsDown = back.rowsDown, consolesOnHome = back.consolesOnHome,
            squareIcons = back.squareIcons, names = back.names, looks = kept - next
        )
    }

    val glassLook: HomeGlass get() = glass ?: style.glass
    val watchDown: Boolean get() = rowsDown ?: style.rowsDown
    val showConsoles: Boolean get() = consolesOnHome ?: style.consolesOnHome
    val square: Boolean get() = squareIcons ?: style.squareIcons
    val namesAlways: Boolean get() = names ?: style.namesAlways
}

/** A profile's own choices for how a style lays out its home screen, each null for what the style does. */
data class HomeLayout(
    val glass: HomeGlass? = null,
    val rowsDown: Boolean? = null,
    val consolesOnHome: Boolean? = null,
    val squareIcons: Boolean? = null,
    val names: Boolean? = null
) {
    val isEmpty: Boolean get() = this == HomeLayout()

    fun toJson(): JSONObject = JSONObject().apply {
        glass?.let { put("glass", it.key) }
        rowsDown?.let { put("rowsDown", it) }
        consolesOnHome?.let { put("consolesOnHome", it) }
        squareIcons?.let { put("squareIcons", it) }
        names?.let { put("names", it) }
    }

    companion object {
        fun of(o: JSONObject) = HomeLayout(
            glass = HomeGlass.of(o.optString("glass")),
            rowsDown = o.flag("rowsDown"),
            consolesOnHome = o.flag("consolesOnHome"),
            squareIcons = o.flag("squareIcons"),
            names = o.flag("names")
        )
    }
}

/**
 * The home profiles and which one shows, kept in the app's preferences. There is always at
 * least one: the first is Neon, as the home screen was before there were profiles.
 */
class HomeProfiles private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("neon_home_profiles", Context.MODE_PRIVATE)
    private val _profiles = MutableStateFlow(load())
    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))

    init {
        // A video that stopped arriving halfway, or a file a removed profile left: nothing points at
        // it, and a phone video can be large on a TV's small storage
        val kept = _profiles.value.flatMap { listOf(it.photo, it.background, it.background?.let { b -> PictureStill.still(b).absolutePath }, it.dreamVideo) }
            .filterNotNull().toSet()
        File(context.filesDir, DIR).listFiles()?.filter { it.absolutePath !in kept }?.forEach { it.delete() }
    }

    val profiles: StateFlow<List<HomeProfile>> = _profiles.asStateFlow()
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    fun active(list: List<HomeProfile> = _profiles.value, id: String? = _activeId.value): HomeProfile =
        list.firstOrNull { it.id == id } ?: list.first()

    fun select(id: String) {
        _activeId.value = id
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    /** Adds a profile and shows it. */
    fun add(name: String, style: HomeStyle): HomeProfile {
        val profile = HomeProfile(UUID.randomUUID().toString(), name.trim().ifEmpty { style.title }, style)
        store(_profiles.value + profile)
        select(profile.id)
        return profile
    }

    /** Changes a profile as it is now, so a screen holding an older copy can't undo a newer change. */
    fun edit(id: String, change: (HomeProfile) -> HomeProfile) {
        _profiles.value.firstOrNull { it.id == id }?.let { update(change(it)) }
    }

    fun update(profile: HomeProfile) {
        val old = _profiles.value.firstOrNull { it.id == profile.id }
        if (old != null) {
            if (old.photo != profile.photo) deleteFile(old.photo)
            if (old.background != profile.background) deleteFile(old.background)
            if (old.dreamVideo != profile.dreamVideo) deleteFile(old.dreamVideo)
        }
        store(_profiles.value.map { if (it.id == profile.id) profile else it })
    }

    /** Removes a profile, never the last one. */
    fun remove(id: String) {
        val list = _profiles.value
        if (list.size <= 1) return
        val gone = list.firstOrNull { it.id == id } ?: return
        deleteFile(gone.photo)
        deleteFile(gone.background)
        deleteFile(gone.dreamVideo)
        val rest = list - gone
        store(rest)
        if (_activeId.value == id) select(rest.first().id)
    }

    /** Where photos, pictures and videos sent from a phone are kept. */
    fun newFile(kind: String, extension: String = "jpg"): File =
        File(File(context.filesDir, DIR).apply { mkdirs() }, "${kind}_${UUID.randomUUID()}.$extension")

    private fun deleteFile(path: String?) {
        path ?: return
        val file = File(path)
        // Only ever files this class made, and the still made from a picture
        if (file.parentFile?.name == DIR) {
            file.delete()
            PictureStill.delete(path)
        }
    }

    private fun store(list: List<HomeProfile>) {
        _profiles.value = list
        val array = JSONArray()
        list.forEach { p ->
            array.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("style", p.style.key)
                put("icons", p.icons.key)
                put("tint", p.tint.key)
                p.photo?.let { put("photo", it) }
                p.background?.let { put("background", it) }
                p.dreamVideo?.let { put("dreamVideo", it) }
                p.layout.toJson().let { l -> l.keys().forEach { put(it, l.get(it)) } }
                if (p.looks.isNotEmpty()) put("looks", JSONObject().apply { p.looks.forEach { (style, look) -> put(style.key, look.toJson()) } })
            })
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun load(): List<HomeProfile> {
        val list = runCatching {
            val array = JSONArray(prefs.getString(KEY_PROFILES, null) ?: return@runCatching emptyList())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                HomeProfile(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    style = HomeStyle.of(o.optString("style")),
                    photo = o.optString("photo").ifEmpty { null }?.takeIf { File(it).exists() },
                    background = o.optString("background").ifEmpty { null }?.takeIf { File(it).exists() },
                    dreamVideo = o.optString("dreamVideo").ifEmpty { null }?.takeIf { File(it).exists() },
                    icons = IconPack.of(o.optString("icons")),
                    tint = IconTint.of(o.optString("tint")),
                    glass = HomeGlass.of(o.optString("glass")),
                    rowsDown = o.flag("rowsDown"),
                    consolesOnHome = o.flag("consolesOnHome"),
                    squareIcons = o.flag("squareIcons"),
                    names = o.flag("names"),
                    looks = o.optJSONObject("looks")?.let { looks ->
                        looks.keys().asSequence().mapNotNull { key ->
                            // A style since removed has nothing to come back to
                            HomeStyle.entries.firstOrNull { it.key == key }?.let { it to HomeLayout.of(looks.getJSONObject(key)) }
                        }.toMap()
                    } ?: emptyMap()
                )
            }
        }.getOrDefault(emptyList())
        return list.ifEmpty { listOf(HomeProfile(FIRST_ID, "Neon", HomeStyle.Neon)) }
    }

    companion object {
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE = "active"
        private const val FIRST_ID = "neon"
        private const val DIR = "home_profiles"

        @Volatile
        private var instance: HomeProfiles? = null

        fun get(context: Context): HomeProfiles =
            instance ?: synchronized(this) { instance ?: HomeProfiles(context.applicationContext).also { instance = it } }
    }
}

/** A choice kept only once it was made: null when [key] was never set. */
internal fun JSONObject.flag(key: String): Boolean? = if (has(key)) optBoolean(key) else null
