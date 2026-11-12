package com.anivision.app.vision

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Acuity blur recipe from `src/prototype/export/matrix.json` (`acuity`): how many pixels of Gaussian
 * sigma a frame needs so that its finest detail matches what a dog resolves. `AcuityTest` fails
 * if the constants drift from the export.
 *
 * `sigma_px = k * (acuity_ratio / 60 / 2) * (long_edge_px / hfov_deg) * exaggeration`
 */
object Acuity {
    const val DEFAULT_EXAGGERATION = 1.0f
    const val MAX_EXAGGERATION = 8.0f

    private const val HUMAN_CUTOFF_CPD = 30.0
    private const val MTF_AT_CUTOFF = 0.1
    private const val ACUITY_RATIO = 3.75
    private const val HFOV_DEG = 70.0

    private val k = 2.0 * sqrt(2.0 * ln(1.0 / MTF_AT_CUTOFF)) / Math.PI

    /**
     * Gaussian sigma in pixels for a frame whose long edge is [longEdgePx] pixels. [exaggeration]
     * is clamped to `0..MAX_EXAGGERATION`; 1.0 is the physical value.
     */
    fun sigmaPx(longEdgePx: Int, exaggeration: Float = DEFAULT_EXAGGERATION): Float {
        require(longEdgePx > 0) { "longEdgePx must be positive, got $longEdgePx" }
        val e = exaggeration.coerceIn(0f, MAX_EXAGGERATION)
        return (k * (ACUITY_RATIO / 60.0 / 2.0) * (longEdgePx / HFOV_DEG) * e).toFloat()
    }

    /** Cutoff of the modelled animal in cycles per degree. */
    val cutoffCpd: Double get() = HUMAN_CUTOFF_CPD / ACUITY_RATIO
}
