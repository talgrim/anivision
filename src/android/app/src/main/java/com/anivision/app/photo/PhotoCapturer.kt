package com.anivision.app.photo

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.anivision.app.vision.VisionSettings
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
 * cache file. If nothing in the view is filtered ([VisionSettings.isPassThrough]) that file is
 * copied to the gallery untouched. Otherwise it is run through [PhotoFilterer] (the same pipeline
 * as the live preview) and saved as a new JPEG. A split-view photo keeps the divider, with each
 * half captioned by [SplitCaptions]. The cache file is always removed.
 */
class PhotoCapturer(
    private val context: Context,
    private val imageCapture: ImageCapture,
    private val store: PhotoStore = PhotoStore(context.contentResolver),
    private val filterer: PhotoFilterer = PhotoFilterer(),
    private val captions: SplitCaptions = SplitCaptions(),
) {
    /**
     * Captures and saves one photo and returns its uri. Call from the main thread; the camera must
     * be bound. [displayRotation] is a `Surface.ROTATION_*` value. [leftLabel] and [rightLabel]
     * caption the two halves of a split photo.
     */
    suspend fun capture(
        settings: VisionSettings,
        displayRotation: Int,
        leftLabel: String,
        rightLabel: String,
    ): Uri {
        val cacheFile = withContext(Dispatchers.IO) { File.createTempFile("capture", ".jpg", context.cacheDir) }
        try {
            imageCapture.targetRotation = displayRotation
            takePicture(cacheFile)
            return if (settings.isPassThrough) {
                store.saveJpeg { out -> cacheFile.inputStream().use { it.copyTo(out) } }
            } else {
                saveFiltered(cacheFile, settings, leftLabel, rightLabel)
            }
        } finally {
            withContext(Dispatchers.IO) { cacheFile.delete() }
        }
    }

    private suspend fun saveFiltered(file: File, settings: VisionSettings, leftLabel: String, rightLabel: String): Uri {
        val filtered = filterer.render(file, settings)
        try {
            // A split photo keeps the live view's divider; label what each side is.
            captions.draw(filtered, settings.split, leftLabel, rightLabel)
            return store.saveBitmap(filtered)
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
}
