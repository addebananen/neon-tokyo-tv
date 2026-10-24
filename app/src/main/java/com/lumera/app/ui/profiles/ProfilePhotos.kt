package com.lumera.app.ui.profiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.min

/** Profile photos sent from a phone, kept in the app's files as square JPEGs. */
object ProfilePhotos {

    private const val DIR = "avatars"
    private const val SIZE = 512

    /**
     * Saves the middle square of the photo in [bytes] at [SIZE] pixels and returns its avatar
     * reference, or null when it isn't a photo Android can read.
     */
    fun save(context: Context, bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val photo = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square

        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "photo_${UUID.randomUUID()}.jpg")
        return try {
            FileOutputStream(file).use { photo.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            ProfileAssets.customRef(file)
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    fun delete(avatarRef: String?) {
        avatarRef?.let(ProfileAssets::customFile)?.delete()
    }

    /** Deletes photos no profile uses, such as one sent while setting up a profile that was cancelled. */
    fun pruneUnused(context: Context, inUse: Collection<String>) {
        val keep = inUse.mapNotNull { ProfileAssets.customFile(it)?.absolutePath }.toSet()
        File(context.filesDir, DIR).listFiles()?.forEach { file ->
            if (file.absolutePath !in keep) file.delete()
        }
    }
}
