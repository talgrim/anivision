package com.anivision.app.photo

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplitCaptionsTest {
    private lateinit var bitmap: Bitmap

    @Before
    fun setUp() {
        bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also { it.eraseColor(BACKGROUND) }
    }

    @Test
    fun captionsTheTopOfEachHalf() {
        SplitCaptions().draw(bitmap, 0.5f, "Normal", "Dog")

        assertTrue("left caption missing", changed(0 until WIDTH / 2, TOP_BAND) > 0)
        assertTrue("right caption missing", changed(WIDTH / 2 until WIDTH, TOP_BAND) > 0)
    }

    @Test
    fun leavesTheRestOfThePhotoAlone() {
        SplitCaptions().draw(bitmap, 0.5f, "Normal", "Dog")

        assertEquals(0, changed(0 until WIDTH, BOTTOM_BAND))
    }

    @Test
    fun theCaptionsHugTheOuterEdgesAndNeverCrossTheDivider() {
        SplitCaptions().draw(bitmap, 0.3f, "Normal", "Dog")

        val seam = (WIDTH * 0.3f).toInt()
        assertTrue("left caption missing", changed(0 until seam, TOP_BAND) > 0)
        assertTrue("right caption missing", changed(WIDTH * 8 / 10 until WIDTH, TOP_BAND) > 0)
        // The stretch from the divider to well short of the right edge holds neither caption.
        assertEquals(0, changed(seam until WIDTH * 8 / 10, TOP_BAND))
    }

    @Test
    fun aCaptionThatDoesNotFitItsHalfMovesNextToTheDividerInTheOtherHalf() {
        SplitCaptions().draw(bitmap, 0.1f, "Normal", "Dog")

        // The 100 px left half is too narrow for its caption and stays clean ...
        assertEquals("narrow left half should stay clean", 0, changed(0 until WIDTH / 10, TOP_BAND))
        // ... which appears just right of the divider instead, and the right caption stays at the right edge.
        assertTrue("pointer caption missing", changed(WIDTH / 10 until WIDTH * 3 / 10, TOP_BAND) > 0)
        assertEquals(0, changed(WIDTH * 4 / 10 until WIDTH * 8 / 10, TOP_BAND))
        assertTrue("right caption missing", changed(WIDTH * 8 / 10 until WIDTH, TOP_BAND) > 0)

        bitmap.eraseColor(BACKGROUND)
        SplitCaptions().draw(bitmap, 0.95f, "Normal", "Dog")

        assertEquals("narrow right half should stay clean", 0, changed(WIDTH * 95 / 100 until WIDTH, TOP_BAND))
        assertTrue("pointer caption missing", changed(WIDTH * 8 / 10 until WIDTH * 95 / 100, TOP_BAND) > 0)
        assertEquals(0, changed(WIDTH * 3 / 10 until WIDTH * 8 / 10, TOP_BAND))
        assertTrue("left caption missing", changed(0 until WIDTH * 3 / 10, TOP_BAND) > 0)
    }

    @Test
    fun doesNothingWithoutADivider() {
        SplitCaptions().draw(bitmap, 0f, "Normal", "Dog")
        assertEquals(0, changed(0 until WIDTH, 0 until HEIGHT))

        SplitCaptions().draw(bitmap, 1f, "Normal", "Dog")
        assertEquals(0, changed(0 until WIDTH, 0 until HEIGHT))
    }

    /** Number of pixels in the given columns and rows that differ from the background. */
    private fun changed(columns: IntRange, rows: IntRange): Int {
        var count = 0
        for (y in rows) for (x in columns) {
            if (bitmap.getPixel(x, y) != BACKGROUND) count++
        }
        return count
    }

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 800
        const val BACKGROUND = 0xFF3366CC.toInt()
        val TOP_BAND = 0 until 100
        val BOTTOM_BAND = 150 until 800
    }
}
