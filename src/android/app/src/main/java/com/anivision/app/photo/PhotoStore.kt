package com.anivision.app.photo

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves JPEGs to the shared gallery (`Pictures/AniVision`) through MediaStore.
 *
 * Scoped storage (minSdk 29) means no storage permission is needed.
 */
class PhotoStore(private val resolver: ContentResolver) {
    /** Writes one JPEG with [write] and returns its MediaStore uri. Runs on the IO dispatcher. */
    suspend fun saveJpeg(now: Instant = Instant.now(), write: (OutputStream) -> Unit): Uri =
        withContext(Dispatchers.IO) {
            insert(displayName(now), write)
        }

    /** Encodes [bitmap] as a JPEG of the given [quality] (0-100) and saves it like [saveJpeg]. */
    suspend fun saveBitmap(bitmap: Bitmap, quality: Int = JPEG_QUALITY, now: Instant = Instant.now()): Uri =
        saveJpeg(now) { out -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "JPEG encoding failed" } }

    private fun insert(name: String, write: (OutputStream) -> Unit): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_JPEG)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore refused the new image")
        try {
            (resolver.openOutputStream(uri) ?: throw IOException("Cannot open $uri")).use(write)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    internal companion object {
        const val ALBUM = "AniVision"
        const val JPEG_QUALITY = 95
        private const val MIME_JPEG = "image/jpeg"
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")

        fun displayName(now: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
            "AniVision_${STAMP.format(now.atZone(zone))}.jpg"
    }
}
