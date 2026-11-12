package com.anivision.app.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anivision.app.vision.AnimalVision
import com.anivision.app.vision.VisionSettings
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The file side of photo capture: EXIF-aware decoding and saving to the gallery. */
@RunWith(AndroidJUnit4::class)
class PhotoPipelineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()
    private val uris = mutableListOf<android.net.Uri>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
        uris.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test
    fun decodeReadsTheExifRotationAndKeepsTheStoredPixels() {
        val file = jpeg(width = 200, height = 100, exifOrientation = ExifInterface.ORIENTATION_ROTATE_90)
        val photo = PhotoDecoder().decode(file, maxLongEdge = 4096)
        assertEquals(90, photo.rotationDegrees)
        assertEquals(200 to 100, photo.bitmap.width to photo.bitmap.height)
    }

    @Test
    fun decodeReducesLargeImagesButNotBelowTheTarget() {
        val file = jpeg(width = 800, height = 400, exifOrientation = ExifInterface.ORIENTATION_NORMAL)
        val photo = PhotoDecoder().decode(file, maxLongEdge = 200)
        assertEquals(0, photo.rotationDegrees)
        assertEquals(200 to 100, photo.bitmap.width to photo.bitmap.height)
    }

    @Test
    fun saveJpegWritesTheBytesToTheGalleryAndNameIsTimestamped() {
        val bytes = ByteArray(1024) { it.toByte() }
        val uri = runBlocking { PhotoStore(context.contentResolver).saveJpeg { it.write(bytes) } }
        uris += uri

        assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH),
            null, null, null,
        )!!.use { cursor ->
            cursor.moveToFirst()
            assertEquals(true, cursor.getString(0).startsWith("AniVision_"))
            assertEquals(true, cursor.getString(1).startsWith("Pictures/AniVision"))
        }
    }

    @Test
    fun saveBitmapStoresADecodableJpegOfTheSameSize() {
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFF884422.toInt()) }
        val uri = runBlocking { PhotoStore(context.contentResolver).saveBitmap(bitmap) }
        uris += uri

        val saved = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        assertEquals(64 to 48, saved.width to saved.height)
    }

    @Test
    fun filtererRendersAGalleryStreamUprightInDogColours() {
        // 200x100 stored with a 90 degree rotation is a 100x200 portrait photo once upright.
        val file = jpeg(width = 200, height = 100, exifOrientation = ExifInterface.ORIENTATION_ROTATE_90)
        val settings = VisionSettings(right = AnimalVision.DOG)
        val result = runBlocking { PhotoFilterer().render({ file.inputStream() }, settings) }
        try {
            assertEquals(100 to 200, result.width to result.height)
            // Dog colours change the pixel from the source colour (0x336699).
            assertNotEquals(0xFF336699.toInt(), result.getPixel(50, 100))
        } finally {
            result.recycle()
        }
    }

    private fun jpeg(width: Int, height: Int, exifOrientation: Int): File {
        val file = File.createTempFile("pipeline", ".jpg", context.cacheDir).also { files += it }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFF336699.toInt()) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
            saveAttributes()
        }
        return file
    }
}
