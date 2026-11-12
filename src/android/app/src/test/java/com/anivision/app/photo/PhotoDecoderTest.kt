package com.anivision.app.photo

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoDecoderTest {
    @Test
    fun smallImagesAreNotReduced() {
        assertEquals(1, PhotoDecoder.sampleSize(3000, 2000, 4096))
        assertEquals(1, PhotoDecoder.sampleSize(4096, 3072, 4096))
    }

    @Test
    fun reducesByPowersOfTwoWhileStayingAtOrAboveTheTarget() {
        assertEquals(1, PhotoDecoder.sampleSize(6000, 4000, 4096))
        assertEquals(2, PhotoDecoder.sampleSize(8192, 6144, 4096))
        assertEquals(2, PhotoDecoder.sampleSize(8000, 6000, 3000))
        assertEquals(4, PhotoDecoder.sampleSize(12000, 9000, 3000))
    }

    @Test
    fun usesTheLongEdgeWhateverTheOrientation() {
        assertEquals(2, PhotoDecoder.sampleSize(6144, 8192, 4096))
    }

    @Test
    fun rejectsEmptySizes() {
        assertThrows(IllegalArgumentException::class.java) { PhotoDecoder.sampleSize(0, 10, 10) }
        assertThrows(IllegalArgumentException::class.java) { PhotoDecoder.sampleSize(10, 10, 0) }
    }

    @Test
    fun exifOrientationMapsToClockwiseRotation() {
        assertEquals(0, PhotoDecoder.rotationDegrees(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(0, PhotoDecoder.rotationDegrees(ExifInterface.ORIENTATION_UNDEFINED))
        assertEquals(90, PhotoDecoder.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180, PhotoDecoder.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270, PhotoDecoder.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_270))
    }
}
