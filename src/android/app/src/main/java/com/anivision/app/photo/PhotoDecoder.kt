package com.anivision.app.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.max

/** A decoded photo and the clockwise rotation that makes it upright (the bitmap itself is not rotated). */
class DecodedPhoto(val bitmap: Bitmap, val rotationDegrees: Int)

/**
 * Decodes a JPEG-style image into a bitmap no larger than needed, and reads its EXIF rotation.
 * Mirrored EXIF orientations are treated as their plain rotation; cameras do not produce them.
 */
class PhotoDecoder {
    fun decode(file: File, maxLongEdge: Int): DecodedPhoto = decode({ file.inputStream() }, maxLongEdge)

    /**
     * [open] is called once per pass (size, pixels, orientation), so it must return a fresh stream
     * each time, e.g. `{ resolver.openInputStream(uri)!! }`. The bitmap is decoded at the largest
     * power-of-two reduction that keeps its long edge at or above [maxLongEdge].
     */
    fun decode(open: () -> InputStream, maxLongEdge: Int): DecodedPhoto {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a readable image")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxLongEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = open().use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("The image could not be decoded")

        val orientation = open().use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        return DecodedPhoto(bitmap, rotationDegrees(orientation))
    }

    internal companion object {
        /** Largest power of two that keeps `longEdge / sampleSize >= maxLongEdge` (1 if already small enough). */
        fun sampleSize(width: Int, height: Int, maxLongEdge: Int): Int {
            require(width > 0 && height > 0 && maxLongEdge > 0) { "Sizes must be positive" }
            val longEdge = max(width, height)
            var sample = 1
            while (longEdge / (sample * 2) >= maxLongEdge) sample *= 2
            return sample
        }

        /** Clockwise degrees that turn an image with this EXIF orientation upright. */
        fun rotationDegrees(exifOrientation: Int): Int = when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSVERSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSPOSE -> 270
            else -> 0
        }
    }
}
