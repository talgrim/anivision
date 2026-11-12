package com.anivision.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionSettingsTest {
    private val dog = AnimalVision.DOG
    private val cat = AnimalVision.CAT

    @Test
    fun withoutProfilesNothingIsFiltered() {
        assertTrue(VisionSettings().isPassThrough)
        assertTrue(VisionSettings(split = 0.5f).isPassThrough)
    }

    @Test
    fun aFilteredSideThatIsShownMakesTheFrameFiltered() {
        assertFalse(VisionSettings(right = dog, split = 0f).isPassThrough)
        assertFalse(VisionSettings(left = dog, split = 1f).isPassThrough)
        assertFalse(VisionSettings(right = dog, split = 0.5f).isPassThrough)
        assertFalse(VisionSettings(left = cat, right = dog, split = 0.5f).isPassThrough)
    }

    @Test
    fun aFilteredSideThatIsHiddenDoesNotCount() {
        // split = 1 shows only the left side, so a profile on the right is not visible.
        assertTrue(VisionSettings(right = dog, split = 1f).isPassThrough)
        // split = 0 shows only the right side.
        assertTrue(VisionSettings(left = dog, split = 0f).isPassThrough)
    }

    @Test
    fun theUnfilteredSideHasNoLift() {
        assertEquals(1f, VisionSettings(shadowGamma = 0.7f).shadowGammaFor(null), 0f)
    }

    @Test
    fun withoutNightBoostAProfileUsesItsOwnLift() {
        val settings = VisionSettings()
        assertEquals(1f, settings.shadowGammaFor(dog), 0f)
        assertEquals(cat.shadowGamma, settings.shadowGammaFor(cat), 0f)
    }

    @Test
    fun theStrongerLiftWinsAndNeverStacks() {
        // A stronger night boost beats the cat's own lift, a weaker one does not weaken it.
        assertEquals(0.7f, VisionSettings(shadowGamma = 0.7f).shadowGammaFor(cat), 0f)
        assertEquals(cat.shadowGamma, VisionSettings(shadowGamma = 0.95f).shadowGammaFor(cat), 0f)
        // The dog has no lift of its own, so the night boost applies as set.
        assertEquals(0.85f, VisionSettings(shadowGamma = 0.85f).shadowGammaFor(dog), 0f)
    }

    @Test
    fun theLiftIsNeverStrongerThanTheTunedLimit() {
        assertEquals(VisionSettings.MIN_SHADOW_GAMMA, VisionSettings(shadowGamma = 0.1f).shadowGammaFor(dog), 0f)
    }
}
