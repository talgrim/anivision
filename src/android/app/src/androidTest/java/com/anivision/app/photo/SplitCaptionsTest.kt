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
        SplitCaptions().draw(bitmap, 0.5f, "Human", "Dog")

        assertTrue("left caption missing", changed(0 until WIDTH / 2, TOP_BAND) > 0)
        assertTrue("right caption missing", changed(WIDTH / 2 until WIDTH, TOP_BAND) > 0)
    }

    @Test
    fun leavesTheRestOfThePhotoAloneApartFromTheDividerLine() {
        SplitCaptions().draw(bitmap, 0.5f, "Human", "Dog")

        assertEquals(0, changed(0 until WIDTH / 2 - LINE_CLEARANCE, BOTTOM_BAND))
        assertEquals(0, changed(WIDTH / 2 + LINE_CLEARANCE until WIDTH, BOTTOM_BAND))
    }

    @Test
    fun drawsTheDividerLineTopToBottomAtTheSplit() {
        SplitCaptions().draw(bitmap, 0.3f, "Human", "Dog")

        val seam = (WIDTH * 0.3f).toInt()
        for (y in listOf(0, HEIGHT / 4, HEIGHT / 2, HEIGHT - 1)) {
            assertTrue("no divider line at row $y", bitmap.getPixel(seam, y) != BACKGROUND)
        }
        assertEquals(BACKGROUND, bitmap.getPixel(seam - LINE_CLEARANCE, HEIGHT / 2))
        assertEquals(BACKGROUND, bitmap.getPixel(seam + LINE_CLEARANCE, HEIGHT / 2))
    }

    @Test
    fun theCaptionsHugTheOuterEdgesAndNeverCrossTheDivider() {
        SplitCaptions().draw(bitmap, 0.3f, "Human", "Dog")

        val seam = (WIDTH * 0.3f).toInt()
        assertTrue("left caption missing", changed(0 until seam, TOP_BAND) > 0)
        assertTrue("right caption missing", changed(WIDTH * 8 / 10 until WIDTH, TOP_BAND) > 0)
        // The stretch from the divider to well short of the right edge holds neither caption.
        assertEquals(0, changed(seam + LINE_CLEARANCE until WIDTH * 8 / 10, TOP_BAND))
    }

    @Test
    fun aCaptionThatDoesNotFitItsHalfMovesNextToTheDividerInTheOtherHalf() {
        SplitCaptions().draw(bitmap, 0.1f, "Human", "Dog")

        // The 100 px left half is too narrow for its caption and stays clean ...
        assertEquals(
            "narrow left half should stay clean",
            0,
            changed(0 until WIDTH / 10 - LINE_CLEARANCE, TOP_BAND),
        )
        // ... which appears just right of the divider instead, and the right caption stays at the right edge.
        assertTrue("pointer caption missing", changed(WIDTH / 10 + LINE_CLEARANCE until WIDTH * 3 / 10, TOP_BAND) > 0)
        assertEquals(0, changed(WIDTH * 4 / 10 until WIDTH * 8 / 10, TOP_BAND))
        assertTrue("right caption missing", changed(WIDTH * 8 / 10 until WIDTH, TOP_BAND) > 0)

        bitmap.eraseColor(BACKGROUND)
        SplitCaptions().draw(bitmap, 0.95f, "Human", "Dog")

        assertEquals(
            "narrow right half should stay clean",
            0,
            changed(WIDTH * 95 / 100 + LINE_CLEARANCE until WIDTH, TOP_BAND),
        )
        assertTrue("pointer caption missing", changed(WIDTH * 8 / 10 until WIDTH * 95 / 100, TOP_BAND) > 0)
        assertEquals(0, changed(WIDTH * 3 / 10 until WIDTH * 8 / 10, TOP_BAND))
        assertTrue("left caption missing", changed(0 until WIDTH * 3 / 10, TOP_BAND) > 0)
    }

    @Test
    fun doesNothingWithoutADivider() {
        SplitCaptions().draw(bitmap, 0f, "Human", "Dog")
        assertEquals(0, changed(0 until WIDTH, 0 until HEIGHT))

        SplitCaptions().draw(bitmap, 1f, "Human", "Dog")
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

        /** Columns on either side of the divider that the line (about 3 px wide here) never reaches. */
        const val LINE_CLEARANCE = 5
        val TOP_BAND = 0 until 100
        val BOTTOM_BAND = 150 until 800
    }
}
