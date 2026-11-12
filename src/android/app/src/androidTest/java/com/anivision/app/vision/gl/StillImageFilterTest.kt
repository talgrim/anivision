package com.anivision.app.vision.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anivision.app.vision.Acuity
import com.anivision.app.vision.AnimalVision
import com.anivision.app.vision.ColorPipeline
import com.anivision.app.vision.VisionSettings
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs [StillImageFilter] on the device's GPU with a four-quadrant test card, which makes
 * orientation mistakes (flips, wrong rotation direction) visible, unlike a flat colour.
 */
@RunWith(AndroidJUnit4::class)
class StillImageFilterTest {
    private val filter = StillImageFilter()

    @Test
    fun passThroughKeepsPixelsAndOrientation() {
        val result = run(card(), rotation = 0, VisionSettings())
        assertEquals(CARD_W to CARD_H, result.width to result.height)
        for (quadrant in Quadrant.entries) assertNear(quadrant.name, quadrant.rgb, result.centreOf(quadrant), 2)
    }

    @Test
    fun clockwiseRotationMovesTheQuadrantsClockwise() {
        val result = run(card(), rotation = 90, VisionSettings())
        assertEquals(CARD_H to CARD_W, result.width to result.height)
        // Turning clockwise: top-left -> top-right -> bottom-right -> bottom-left -> top-left.
        assertNear("TL", Quadrant.BOTTOM_LEFT.rgb, result.centreOf(Quadrant.TOP_LEFT), 2)
        assertNear("TR", Quadrant.TOP_LEFT.rgb, result.centreOf(Quadrant.TOP_RIGHT), 2)
        assertNear("BR", Quadrant.TOP_RIGHT.rgb, result.centreOf(Quadrant.BOTTOM_RIGHT), 2)
        assertNear("BL", Quadrant.BOTTOM_RIGHT.rgb, result.centreOf(Quadrant.BOTTOM_LEFT), 2)
    }

    @Test
    fun halfTurnSwapsOppositeCorners() {
        val result = run(card(), rotation = 180, VisionSettings())
        assertNear("TL", Quadrant.BOTTOM_RIGHT.rgb, result.centreOf(Quadrant.TOP_LEFT), 2)
        assertNear("BR", Quadrant.TOP_LEFT.rgb, result.centreOf(Quadrant.BOTTOM_RIGHT), 2)
    }

    @Test
    fun dogViewMatchesTheCpuReferencePipeline() {
        val result = run(card(), rotation = 0, VisionSettings(profile = AnimalVision.DOG))
        for (quadrant in Quadrant.entries) {
            val (r, g, b) = quadrant.rgb
            assertNear(quadrant.name, ColorPipeline.projectSrgb8(AnimalVision.DOG, r, g, b).toList(), result.centreOf(quadrant), 2)
        }
    }

    @Test
    fun largeSourcesAreScaledDownKeepingTheAspectRatio() {
        val result = runBlocking { StillImageFilter(maxLongEdge = 40).filter(card(), 0, VisionSettings()) }
        assertEquals(40 to 30, result.width to result.height)
        for (quadrant in Quadrant.entries) assertNear(quadrant.name, quadrant.rgb, result.centreOf(quadrant), 2)
    }

    @Test
    fun blurSoftensTheSeamButNotTheFlatAreas() {
        val size = 512
        val source = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(source).apply {
            drawColor(Color.BLACK)
            drawRect(size / 2f, 0f, size.toFloat(), size.toFloat(), Paint().apply { color = Color.WHITE })
        }
        val result = run(source, rotation = 0, VisionSettings(profile = AnimalVision.DOG, blurExaggeration = Acuity.MAX_EXAGGERATION))
        val seam = Color.green(result.getPixel(size / 2 - 1, size / 2))
        assertTrue("seam pixel should be a grey mix, was $seam", seam in 20..235)
        assertEquals(0, Color.green(result.getPixel(20, size / 2)))
        assertTrue(Color.green(result.getPixel(size - 20, size / 2)) >= 245)
    }

    private fun run(source: Bitmap, rotation: Int, settings: VisionSettings): Bitmap =
        runBlocking { filter.filter(source, rotation, settings) }

    private fun Bitmap.centreOf(quadrant: Quadrant): List<Int> {
        val x = if (quadrant.right) width * 3 / 4 else width / 4
        val y = if (quadrant.bottom) height * 3 / 4 else height / 4
        return getPixel(x, y).let { listOf(Color.red(it), Color.green(it), Color.blue(it)) }
    }

    private fun assertNear(label: String, expected: List<Int>, actual: List<Int>, tolerance: Int) {
        val diff = expected.indices.maxOf { abs(expected[it] - actual[it]) }
        assertTrue("$label: expected $expected, got $actual", diff <= tolerance)
    }

    private fun card(): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        for (quadrant in Quadrant.entries) {
            val (r, g, b) = quadrant.rgb
            paint.color = Color.rgb(r, g, b)
            val left = if (quadrant.right) CARD_W / 2f else 0f
            val top = if (quadrant.bottom) CARD_H / 2f else 0f
            canvas.drawRect(left, top, left + CARD_W / 2f, top + CARD_H / 2f, paint)
        }
        return bitmap
    }

    private enum class Quadrant(val right: Boolean, val bottom: Boolean, val rgb: List<Int>) {
        TOP_LEFT(false, false, listOf(220, 30, 30)),
        TOP_RIGHT(true, false, listOf(40, 200, 60)),
        BOTTOM_LEFT(false, true, listOf(30, 60, 220)),
        BOTTOM_RIGHT(true, true, listOf(230, 210, 40)),
    }

    private companion object {
        const val CARD_W = 80
        const val CARD_H = 60
    }
}
