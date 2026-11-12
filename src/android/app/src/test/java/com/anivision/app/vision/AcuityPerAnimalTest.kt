package com.anivision.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AcuityPerAnimalTest {
    @Test
    fun theDefaultRatioIsTheDogsExportedRatio() {
        assertEquals(ExportFiles.matrix.getJSONObject("acuity").getDouble("acuity_ratio"), Acuity.DEFAULT_ACUITY_RATIO.toDouble(), 1e-9)
        assertEquals(ExportFiles.profileValue("dog", "acuity_ratio"), Acuity.DEFAULT_ACUITY_RATIO.toDouble(), 1e-9)
    }

    @Test
    fun sigmaScalesWithTheAcuityRatio() {
        val dog = Acuity.sigmaPx(1920, 3f, AnimalVision.DOG.acuityRatio)
        val cat = Acuity.sigmaPx(1920, 3f, AnimalVision.CAT.acuityRatio)
        assertEquals(dog * AnimalVision.CAT.acuityRatio / AnimalVision.DOG.acuityRatio, cat, 1e-5f)
        assertEquals(dog, Acuity.sigmaPx(1920, 3f), 0f)
    }

    @Test
    fun rejectsNonPositiveRatios() {
        assertThrows(IllegalArgumentException::class.java) { Acuity.sigmaPx(1920, 1f, 0f) }
        assertThrows(IllegalArgumentException::class.java) { Acuity.sigmaPx(1920, 1f, -1f) }
    }
}
