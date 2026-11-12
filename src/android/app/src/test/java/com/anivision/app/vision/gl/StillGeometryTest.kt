package com.anivision.app.vision.gl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StillGeometryTest {
    @Test
    fun smallImagesKeepTheirSize() {
        assertEquals(400 to 300, StillGeometry.outputSize(400, 300, 0, 4096))
    }

    @Test
    fun quarterTurnsSwapWidthAndHeight() {
        assertEquals(300 to 400, StillGeometry.outputSize(400, 300, 90, 4096))
        assertEquals(300 to 400, StillGeometry.outputSize(400, 300, 270, 4096))
        assertEquals(400 to 300, StillGeometry.outputSize(400, 300, 180, 4096))
        assertEquals(300 to 400, StillGeometry.outputSize(400, 300, -90, 4096))
    }

    @Test
    fun largeImagesAreScaledToTheLongEdgeKeepingTheAspectRatio() {
        assertEquals(4096 to 3072, StillGeometry.outputSize(8000, 6000, 0, 4096))
        assertEquals(1000 to 750, StillGeometry.outputSize(4000, 3000, 0, 1000))
    }

    @Test
    fun scalingAppliesAfterTheTurn() {
        assertEquals(750 to 1000, StillGeometry.outputSize(4000, 3000, 90, 1000))
    }

    @Test
    fun neverReturnsAnEmptyEdge() {
        assertEquals(1000 to 1, StillGeometry.outputSize(100_000, 10, 0, 1000))
    }

    @Test
    fun rejectsBadInput() {
        assertThrows(IllegalArgumentException::class.java) { StillGeometry.outputSize(0, 10, 0, 100) }
        assertThrows(IllegalArgumentException::class.java) { StillGeometry.outputSize(10, 10, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { StillGeometry.outputSize(10, 10, 45, 100) }
    }
}
