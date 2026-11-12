package com.anivision.app.photo

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
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
 * On Android 10+ no permission is needed. Before that, the caller must hold
 * `WRITE_EXTERNAL_STORAGE` and the file is written to the public Pictures folder directly.
 */
class PhotoStore(private val resolver: ContentResolver) {
    /** Writes one JPEG with [write] and returns its MediaStore uri. Runs on the IO dispatcher. */
    suspend fun saveJpeg(now: Instant = Instant.now(), write: (OutputStream) -> Unit): Uri =
        withContext(Dispatchers.IO) {
            val name = displayName(now)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(name, write) else saveLegacy(name, write)
        }

    private fun saveScoped(name: String, write: (OutputStream) -> Unit): Uri {
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

    @Suppress("DEPRECATION") // getExternalStoragePublicDirectory and DATA are the only way before Android 10
    private fun saveLegacy(name: String, write: (OutputStream) -> Unit): Uri {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
        val file = File(directory, name)
        try {
            file.outputStream().use(write)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, MIME_JPEG)
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
            return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore refused the new image")
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    internal companion object {
        const val ALBUM = "AniVision"
        private const val MIME_JPEG = "image/jpeg"
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")

        fun displayName(now: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
            "AniVision_${STAMP.format(now.atZone(zone))}.jpg"
    }
}
