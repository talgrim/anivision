package com.anivision.app.photo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.anivision.app.vision.VisionSettings
import com.anivision.app.vision.gl.StillImageFilter
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Takes a photo with [imageCapture] and saves it to the gallery.
 *
 * CameraX writes the plain JPEG (cropped to the preview's view port and rotated through EXIF) to a
 * cache file. In normal view that file is copied to the gallery untouched. In an animal view it is
 * decoded, run through [StillImageFilter] (the same pipeline as the live preview) and saved as a
 * new JPEG. The cache file is always removed.
 */
class PhotoCapturer(
    private val context: Context,
    private val imageCapture: ImageCapture,
    private val store: PhotoStore = PhotoStore(context.contentResolver),
    private val decoder: PhotoDecoder = PhotoDecoder(),
    private val filter: StillImageFilter = StillImageFilter(),
) {
    /**
     * Captures and saves one photo and returns its uri. Call from the main thread; the camera must
     * be bound. [displayRotation] is a `Surface.ROTATION_*` value.
     */
    suspend fun capture(settings: VisionSettings, displayRotation: Int): Uri {
        val cacheFile = withContext(Dispatchers.IO) { File.createTempFile("capture", ".jpg", context.cacheDir) }
        try {
            imageCapture.targetRotation = displayRotation
            takePicture(cacheFile)
            return if (settings.profile == null) {
                store.saveJpeg { out -> cacheFile.inputStream().use { it.copyTo(out) } }
            } else {
                saveFiltered(cacheFile, settings)
            }
        } finally {
            withContext(Dispatchers.IO) { cacheFile.delete() }
        }
    }

    private suspend fun saveFiltered(file: File, settings: VisionSettings): Uri {
        val photo = withContext(Dispatchers.IO) { decoder.decode(file, filter.maxLongEdge) }
        val filtered = try {
            filter.filter(photo.bitmap, photo.rotationDegrees, settings)
        } finally {
            photo.bitmap.recycle()
        }
        try {
            return store.saveJpeg { out ->
                check(filtered.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "JPEG encoding failed" }
            }
        } finally {
            filtered.recycle()
        }
    }

    private suspend fun takePicture(file: File) = suspendCancellableCoroutine<Unit> { continuation ->
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = continuation.resume(Unit)

                override fun onError(exception: ImageCaptureException) = continuation.resumeWithException(exception)
            },
        )
    }

    private companion object {
        const val JPEG_QUALITY = 95
    }
}
