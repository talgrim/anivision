package com.anivision.app.vision

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPipelineGoldenTest {
    private val profiles = mapOf("v2" to AnimalVision.DOG_V2, "v0" to AnimalVision.DOG_V0, "cat" to AnimalVision.CAT_V2)

    @Test
    fun srgbTransferRoundTrips() {
        for (i in 0..255) {
            val c = i / 255.0
            assertEquals(c, SrgbTransfer.fromLinear(SrgbTransfer.toLinear(c)), 1e-9)
        }
    }

    @Test
    fun srgbTransferEndpointsAndMidGrey() {
        assertEquals(0.0, SrgbTransfer.toLinear(0.0), 0.0)
        assertEquals(1.0, SrgbTransfer.toLinear(1.0), 1e-12)
        // sRGB 188/255 is the 50/50 black-white checker mean noted in matrix.json
        assertEquals(0.5, SrgbTransfer.toLinear(188 / 255.0), 0.005)
    }

    @Test
    fun goldenVectorsAreLoaded() {
        val vectors = ExportFiles.goldenVectors.getJSONArray("vectors")
        assertTrue("expected the full golden set, got ${vectors.length()}", vectors.length() >= 70)
    }

    @Test
    fun colourStepMatchesGoldenVectorsWithinTolerance() {
        val golden = ExportFiles.goldenVectors
        val tolerance = golden.getJSONObject("tolerance_8bit").getInt("recommended")
        val vectors = golden.getJSONArray("vectors")
        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val label = vector.getString("label")
            val src = vector.getJSONArray("srgb")
            for ((variant, profile) in profiles) {
                val expected = vector.getJSONObject(variant).getJSONArray("srgb")
                val actual = ColorPipeline.projectSrgb8(profile, src.getInt(0), src.getInt(1), src.getInt(2))
                for (c in 0 until 3) {
                    val diff = kotlin.math.abs(actual[c] - expected.getInt(c))
                    assertTrue(
                        "$label $variant channel $c: expected ${expected.getInt(c)}, got ${actual[c]}",
                        diff <= tolerance,
                    )
                }
            }
        }
    }

    @Test
    fun unclippedLinearOutputMatchesGoldenVectors() {
        val vectors = ExportFiles.goldenVectors.getJSONArray("vectors")
        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val src = vector.getJSONArray("srgb")
            val lin = DoubleArray(3) { SrgbTransfer.toLinear(src.getInt(it) / 255.0) }
            for ((variant, profile) in profiles) {
                val expected = vector.getJSONObject(variant).getJSONArray("linear_unclipped")
                val actual = profile.applyLinear(lin[0], lin[1], lin[2])
                for (c in 0 until 3) {
                    assertEquals("${vector.getString("label")} $variant channel $c", expected.getDouble(c), actual[c], 1e-6)
                }
            }
        }
    }

    @Test
    fun blackAndWhiteArePreserved() {
        for (profile in profiles.values) {
            assertArrayEquals(intArrayOf(0, 0, 0), ColorPipeline.projectSrgb8(profile, 0, 0, 0))
            assertArrayEquals(intArrayOf(255, 255, 255), ColorPipeline.projectSrgb8(profile, 255, 255, 255))
        }
    }

    @Test
    fun outOfGamutChannelsAreClippedNotWrapped() {
        // Pure blue under v2 has a negative red channel (-0.233) that must clip to 0.
        val out = ColorPipeline.projectSrgb8(AnimalVision.DOG_V2, 0, 0, 255)
        assertEquals(0, out[0])
    }
}
