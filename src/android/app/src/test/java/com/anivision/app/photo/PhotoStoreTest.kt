package com.anivision.app.photo

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoStoreTest {
    @Test
    fun displayNameIsATimestampedJpeg() {
        val instant = Instant.parse("2026-11-12T08:05:09.042Z")
        assertEquals("AniVision_20261112_080509_042.jpg", PhotoStore.displayName(instant, ZoneOffset.UTC))
    }

    @Test
    fun displayNameUsesTheGivenTimeZone() {
        val instant = Instant.parse("2026-11-12T23:30:00Z")
        assertEquals("AniVision_20261113_003000_000.jpg", PhotoStore.displayName(instant, ZoneOffset.ofHours(1)))
    }
}
