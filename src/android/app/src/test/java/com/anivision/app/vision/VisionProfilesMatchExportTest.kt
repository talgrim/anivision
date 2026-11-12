package com.anivision.app.vision

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class VisionProfilesMatchExportTest {
    private fun assertMatchesExport(profile: VisionProfile, variant: String) {
        val expected = ExportFiles.dogMatrix(variant)
        val actual = profile.linearMatrix().map { it.toDouble() }.toDoubleArray()
        assertArrayEquals("matrix $variant", expected, actual, 1e-6)
    }

    @Test
    fun dogV2MatchesMatrixJson() = assertMatchesExport(AnimalVision.DOG_V2, "v2")

    @Test
    fun dogV0MatchesMatrixJson() = assertMatchesExport(AnimalVision.DOG_V0, "v0")

    @Test
    fun profileIdsMatchVariantNames() {
        assertEquals("v2", AnimalVision.DOG_V2.id)
        assertEquals("v0", AnimalVision.DOG_V0.id)
    }

    @Test
    fun dogProfileIsTheExportedDefaultVariant() {
        assertEquals(ExportFiles.defaultDogVariant(), AnimalVision.DOG.id)
        assertSame(AnimalVision.DOG_V2, AnimalVision.DOG)
    }

    @Test
    fun colorMatrixIsFourByFiveWithIdentityAlphaAndNoOffsets() {
        val m = AnimalVision.DOG_V2.toColorMatrixValues()
        val rowMajor = AnimalVision.DOG_V2.linearMatrix()
        assertEquals(20, m.size)
        for (row in 0 until 3) {
            for (col in 0 until 3) assertEquals(rowMajor[row * 3 + col], m[row * 5 + col], 0f)
            assertEquals(0f, m[row * 5 + 3], 0f)
            assertEquals(0f, m[row * 5 + 4], 0f)
        }
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f), m.copyOfRange(15, 20), 0f)
    }

    @Test
    fun linearMatrixIsADefensiveCopy() {
        val profile = AnimalVision.DOG_V2
        val first = profile.linearMatrix()
        first[0] = 123f
        assertNotSame(first, profile.linearMatrix())
        assertEquals(AnimalVision.DOG_V2.linearMatrix()[0], profile.linearMatrix()[0], 0f)
        assertEquals(0.325424108f, profile.linearMatrix()[0], 0f)
    }

    @Test
    fun rejectsWrongSizedMatrix() {
        assertThrows(IllegalArgumentException::class.java) { VisionProfile("bad", FloatArray(8)) }
    }
}
