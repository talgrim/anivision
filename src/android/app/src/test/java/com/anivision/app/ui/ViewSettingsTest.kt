package com.anivision.app.ui

import com.anivision.app.vision.AnimalVision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewSettingsTest {
    private val humanDog = PerspectivePair(Perspective.HUMAN, Perspective.DOG)
    private val dogCat = PerspectivePair(Perspective.DOG, Perspective.CAT)
    private val catHuman = PerspectivePair(Perspective.CAT, Perspective.HUMAN)

    @Test
    fun humanIsTheUnfilteredPerspective() {
        assertNull(Perspective.HUMAN.profile)
        assertSame(AnimalVision.DOG, Perspective.DOG.profile)
        assertSame(AnimalVision.CAT, Perspective.CAT.profile)
    }

    @Test
    fun theModesPutTheDividerAtAnEdgeOrWhereTheUserDraggedIt() {
        assertEquals(1f, buildVisionSettings(ViewMode.LEFT, humanDog, 0.3f, null, null).split, 0f)
        assertEquals(0.3f, buildVisionSettings(ViewMode.SPLIT, humanDog, 0.3f, null, null).split, 0f)
        assertEquals(0f, buildVisionSettings(ViewMode.RIGHT, humanDog, 0.3f, null, null).split, 0f)
    }

    @Test
    fun theSettingsCarryTheBothSidesProfilesAndTheSharedEffects() {
        val settings = buildVisionSettings(ViewMode.SPLIT, dogCat, 0.5f, blurExaggeration = 3f, shadowGamma = 0.8f)
        assertSame(AnimalVision.DOG, settings.left)
        assertSame(AnimalVision.CAT, settings.right)
        assertEquals(3f, settings.blurExaggeration)
        assertEquals(0.8f, settings.shadowGamma)
    }

    @Test
    fun humanOnlyViewsArePassThrough() {
        assertTrue(buildVisionSettings(ViewMode.LEFT, humanDog, 0.5f, null, null).isPassThrough)
        assertFalse(buildVisionSettings(ViewMode.RIGHT, humanDog, 0.5f, null, null).isPassThrough)
        assertTrue(buildVisionSettings(ViewMode.RIGHT, catHuman, 0.5f, null, null).isPassThrough)
        assertFalse(buildVisionSettings(ViewMode.SPLIT, catHuman, 0.5f, null, null).isPassThrough)
    }

    @Test
    fun visiblePerspectivesFollowTheMode() {
        assertEquals(listOf(Perspective.HUMAN), ViewMode.LEFT.visiblePerspectives(humanDog))
        assertEquals(listOf(Perspective.HUMAN, Perspective.DOG), ViewMode.SPLIT.visiblePerspectives(humanDog))
        assertEquals(listOf(Perspective.DOG), ViewMode.RIGHT.visiblePerspectives(humanDog))
    }

    @Test
    fun theEffectControlsShowWheneverAFilteredPerspectiveIsVisible() {
        assertFalse(ViewMode.LEFT.showsFilter(humanDog))
        assertTrue(ViewMode.SPLIT.showsFilter(humanDog))
        assertTrue(ViewMode.RIGHT.showsFilter(humanDog))
        assertFalse(ViewMode.RIGHT.showsFilter(catHuman))
    }

    @Test
    fun onlyTheCatAsksForTheWideAngle() {
        assertTrue(ViewMode.RIGHT.wantsWideAngle(dogCat))
        assertTrue(ViewMode.SPLIT.wantsWideAngle(dogCat))
        assertFalse(ViewMode.LEFT.wantsWideAngle(dogCat))
        assertFalse(ViewMode.RIGHT.wantsWideAngle(humanDog))
    }
}
