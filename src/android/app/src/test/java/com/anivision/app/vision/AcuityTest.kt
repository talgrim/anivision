package com.anivision.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AcuityTest {
    private val acuity = ExportFiles.matrix.getJSONObject("acuity")

    @Test
    fun sigmaMatchesEveryExportedExample() {
        val examples = acuity.getJSONArray("sigma_px_examples")
        for (i in 0 until examples.length()) {
            val e = examples.getJSONObject(i)
            val longEdge = e.getInt("long_edge_px")
            val exaggeration = e.getDouble("exaggeration").toFloat()
            assertEquals(
                "long edge $longEdge x$exaggeration",
                e.getDouble("sigma_px"),
                Acuity.sigmaPx(longEdge, exaggeration).toDouble(),
                1e-4,
            )
        }
    }

    @Test
    fun exaggerationBoundsMatchTheExport() {
        assertEquals(acuity.getDouble("default_exaggeration"), Acuity.DEFAULT_EXAGGERATION.toDouble(), 0.0)
        assertEquals(acuity.getDouble("max_exaggeration"), Acuity.MAX_EXAGGERATION.toDouble(), 0.0)
    }

    @Test
    fun cutoffMatchesTheExportedRatio() {
        val expected = acuity.getDouble("human_cutoff_cpd") / acuity.getDouble("acuity_ratio")
        assertEquals(expected, Acuity.cutoffCpd, 1e-12)
    }

    @Test
    fun exaggerationIsClampedToTheSupportedRange() {
        assertEquals(Acuity.sigmaPx(1080, Acuity.MAX_EXAGGERATION), Acuity.sigmaPx(1080, 100f), 0f)
        assertEquals(0f, Acuity.sigmaPx(1080, -3f), 0f)
    }

    @Test
    fun rejectsNonPositiveFrameSize() {
        assertThrows(IllegalArgumentException::class.java) { Acuity.sigmaPx(0) }
    }
}
