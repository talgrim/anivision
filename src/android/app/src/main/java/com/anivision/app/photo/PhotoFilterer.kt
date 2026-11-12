package com.anivision.app.photo

import android.graphics.Bitmap
import com.anivision.app.vision.VisionSettings
import com.anivision.app.vision.gl.StillImageFilter
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a plain photo (a camera capture or an image picked from the gallery) into its animal-vision
 * version: decode (EXIF-aware, no larger than the filter needs), then [StillImageFilter], the same
 * pipeline as the live preview.
 */
class PhotoFilterer(
    private val decoder: PhotoDecoder = PhotoDecoder(),
    private val stillFilter: StillImageFilter = StillImageFilter(),
) {
    suspend fun render(file: File, settings: VisionSettings): Bitmap = render({ file.inputStream() }, settings)

    /**
     * [open] must return a fresh stream on every call (see [PhotoDecoder.decode]). The caller owns
     * the returned bitmap and should recycle it when done.
     */
    suspend fun render(open: () -> InputStream, settings: VisionSettings): Bitmap {
        val photo = withContext(Dispatchers.IO) { decoder.decode(open, stillFilter.maxLongEdge) }
        try {
            return stillFilter.filter(photo.bitmap, photo.rotationDegrees, settings)
        } finally {
            photo.bitmap.recycle()
        }
    }
}
