package com.anivision.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PerspectivePairTest {
    @Test
    fun theDefaultIsHumanAndDog() {
        assertEquals(PerspectivePair(Perspective.HUMAN, Perspective.DOG), PerspectivePair.DEFAULT)
    }

    @Test
    fun bothSidesCannotShowTheSamePerspective() {
        assertThrows(IllegalArgumentException::class.java) { PerspectivePair(Perspective.CAT, Perspective.CAT) }
    }

    @Test
    fun aSideCannotPickWhatTheOtherSideShows() {
        val pair = PerspectivePair(Perspective.HUMAN, Perspective.DOG)
        assertFalse(pair.canSetLeft(Perspective.DOG))
        assertTrue(pair.canSetLeft(Perspective.CAT))
        assertFalse(pair.canSetRight(Perspective.HUMAN))
        assertTrue(pair.canSetRight(Perspective.CAT))
        assertEquals(pair, pair.withLeft(Perspective.DOG))
        assertEquals(pair, pair.withRight(Perspective.HUMAN))
    }

    @Test
    fun everyPairOfTheThreeIsReachableByChoosingOneSideAtATime() {
        // Dog | Cat from Human | Dog: the right side first, then the left.
        val toDogCat = PerspectivePair.DEFAULT.withRight(Perspective.CAT).withLeft(Perspective.DOG)
        assertEquals(PerspectivePair(Perspective.DOG, Perspective.CAT), toDogCat)
        // Cat | Human from Human | Dog: the left side first, then the right.
        val toCatHuman = PerspectivePair.DEFAULT.withLeft(Perspective.CAT).withRight(Perspective.HUMAN)
        assertEquals(PerspectivePair(Perspective.CAT, Perspective.HUMAN), toCatHuman)
    }

    @Test
    fun encodingRoundTrips() {
        for (left in Perspective.entries) {
            for (right in Perspective.entries.filter { it != left }) {
                val pair = PerspectivePair(left, right)
                assertEquals(pair, PerspectivePair.decode(pair.encode()))
            }
        }
    }

    @Test
    fun decodingRejectsAnythingElse() {
        assertNull(PerspectivePair.decode(null))
        assertNull(PerspectivePair.decode(""))
        assertNull(PerspectivePair.decode("HUMAN"))
        assertNull(PerspectivePair.decode("HUMAN,DOG,CAT"))
        assertNull(PerspectivePair.decode("HUMAN,BIRD"))
        assertNull(PerspectivePair.decode("CAT,CAT"))
    }
}
