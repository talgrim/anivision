package com.anivision.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightBoostTest {
    private val shadowLift = ExportFiles.matrix.getJSONObject("shadow_lift")

    @Test
    fun constantsMatchTheExportedRecipe() {
        assertEquals(shadowLift.getDouble("off_gamma").toFloat(), NightBoost.OFF_GAMMA, 0f)
        assertEquals(shadowLift.getDouble("suggested_gamma").toFloat(), NightBoost.SUGGESTED_GAMMA, 0f)
        assertEquals(shadowLift.getDouble("strongest_gamma").toFloat(), NightBoost.STRONGEST_GAMMA, 0f)
    }

    @Test
    fun theDefaultStrengthGivesTheSuggestedGamma() {
        assertEquals(NightBoost.SUGGESTED_GAMMA, NightBoost.gammaFor(NightBoost.DEFAULT_STRENGTH), 1e-6f)
    }

    @Test
    fun zeroIsOffAndOneIsStrongest() {
        assertEquals(NightBoost.OFF_GAMMA, NightBoost.gammaFor(0f), 0f)
        assertEquals(NightBoost.STRONGEST_GAMMA, NightBoost.gammaFor(1f), 1e-6f)
    }

    @Test
    fun moreStrengthLiftsMoreAndStaysInRange() {
        var previous = NightBoost.gammaFor(NightBoost.MIN_STRENGTH)
        assertTrue(previous < NightBoost.OFF_GAMMA)
        for (i in 1..9) {
            val gamma = NightBoost.gammaFor(NightBoost.MIN_STRENGTH + i * 0.1f)
            assertTrue(gamma < previous)
            assertTrue(gamma >= NightBoost.STRONGEST_GAMMA - 1e-6f)
            previous = gamma
        }
    }

    @Test
    fun strengthsOutsideTheRangeAreClamped() {
        assertEquals(NightBoost.OFF_GAMMA, NightBoost.gammaFor(-1f), 0f)
        assertEquals(NightBoost.STRONGEST_GAMMA, NightBoost.gammaFor(5f), 1e-6f)
    }
}
