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
    val dreamVideo: String? = null
)

/**
 * The home profiles and which one shows, kept in the app's preferences. There is always at
 * least one: the first is Neon, as the home screen was before there were profiles.
 */
class HomeProfiles private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("neon_home_profiles", Context.MODE_PRIVATE)
    private val _profiles = MutableStateFlow(load())
    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))

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
        // Only ever files this class made
        if (file.parentFile?.name == DIR) file.delete()
    }

    private fun store(list: List<HomeProfile>) {
        _profiles.value = list
        val array = JSONArray()
        list.forEach { p ->
            array.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("style", p.style.key)
                p.photo?.let { put("photo", it) }
                p.background?.let { put("background", it) }
                p.dreamVideo?.let { put("dreamVideo", it) }
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
                    dreamVideo = o.optString("dreamVideo").ifEmpty { null }?.takeIf { File(it).exists() }
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
