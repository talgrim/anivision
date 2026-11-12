package com.anivision.app.vision

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    fun columnMajorMatrixIsTheTransposeOfTheExportedRows() {
        val rowMajor = ExportFiles.dogMatrix("v2")
        val columnMajor = AnimalVision.DOG_V2.linearMatrixColumnMajor()
        assertEquals(9, columnMajor.size)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                assertEquals("row $row col $col", rowMajor[row * 3 + col], columnMajor[col * 3 + row].toDouble(), 1e-6)
            }
        }
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
    fun catV2MatchesMatrixJson() {
        val expected = ExportFiles.profileMatrix("cat", "cat")
        val actual = AnimalVision.CAT_V2.linearMatrix().map { it.toDouble() }.toDoubleArray()
        assertArrayEquals("cat matrix", expected, actual, 1e-6)
    }

    @Test
    fun catProfileIsTheExportedDefaultVariant() {
        assertEquals(ExportFiles.defaultVariant("cat"), AnimalVision.CAT.id)
        assertSame(AnimalVision.CAT_V2, AnimalVision.CAT)
    }

    @Test
    fun profileTraitsMatchMatrixJson() {
        for ((animal, profile) in mapOf("dog" to AnimalVision.DOG, "cat" to AnimalVision.CAT)) {
            assertEquals("$animal acuity_ratio", ExportFiles.profileValue(animal, "acuity_ratio"), profile.acuityRatio.toDouble(), 1e-6)
            assertEquals("$animal shadow_gamma", ExportFiles.profileValue(animal, "shadow_gamma"), profile.shadowGamma.toDouble(), 1e-6)
            assertEquals("$animal peripheral_blur", ExportFiles.profileValue(animal, "peripheral_blur"), profile.peripheralBlur.toDouble(), 1e-6)
        }
    }

    @Test
    fun theDogHasNoBuiltInTraitsAndTheCatHas() {
        assertEquals(1f, AnimalVision.DOG.shadowGamma, 0f)
        assertEquals(0f, AnimalVision.DOG.peripheralBlur, 0f)
        assertTrue(AnimalVision.CAT.shadowGamma < 1f)
        assertTrue(AnimalVision.CAT.peripheralBlur > 0f)
    }

    @Test
    fun rejectsInvalidTraits() {
        val m = FloatArray(9)
        assertThrows(IllegalArgumentException::class.java) { VisionProfile("bad", m, acuityRatio = 0f) }
        assertThrows(IllegalArgumentException::class.java) { VisionProfile("bad", m, shadowGamma = 0f) }
        assertThrows(IllegalArgumentException::class.java) { VisionProfile("bad", m, shadowGamma = 1.1f) }
        assertThrows(IllegalArgumentException::class.java) { VisionProfile("bad", m, peripheralBlur = -0.1f) }
    }

    @Test
    fun rejectsWrongSizedMatrix() {
        assertThrows(IllegalArgumentException::class.java) { VisionProfile("bad", FloatArray(8)) }
    }
}
