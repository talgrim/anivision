package com.anivision.app.vision.gl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameStatsTest {
    private val ms = 1_000_000L
    private val lines = mutableListOf<String>()

    @Test
    fun reportsNothingBeforeTheIntervalElapses() {
        val stats = FrameStats(report = lines::add)
        for (i in 1..20) stats.record(nowNs = i * 33 * ms, drawNs = 2 * ms)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun reportsFpsAndDrawTimeOncePerInterval() {
        val stats = FrameStats(report = lines::add)
        // 31 frames at 1/30 s spacing (30 intervals), each drawn in 4 ms except one that takes 10 ms.
        for (i in 1..31) stats.record(nowNs = i * 1_000 * ms / 30, drawNs = if (i == 7) 10 * ms else 4 * ms)
        assertEquals(1, lines.size)
        assertTrue(lines[0], lines[0].startsWith("30.0 fps"))
        assertTrue(lines[0], "max 10.00 ms" in lines[0])
        assertTrue(lines[0], "(30 frames)" in lines[0])
    }

    @Test
    fun startsANewWindowAfterEachReport() {
        val stats = FrameStats(report = lines::add)
        for (i in 1..61) stats.record(nowNs = i * 1_000 * ms / 30, drawNs = 2 * ms)
        assertEquals(2, lines.size)
        assertTrue(lines[1], lines[1].startsWith("30.0 fps"))
    }

    @Test
    fun aSlowStretchShowsAsLowerFps() {
        val stats = FrameStats(report = lines::add)
        for (i in 1..16) stats.record(nowNs = i * 1_000 * ms / 15, drawNs = ms)
        assertEquals(1, lines.size)
        assertTrue(lines[0], lines[0].startsWith("15.0 fps"))
    }
}
