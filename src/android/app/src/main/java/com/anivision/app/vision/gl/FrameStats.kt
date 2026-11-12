package com.anivision.app.vision.gl

import java.util.Locale

/**
 * Counts rendered frames and reports frames per second and draw time once per [intervalNs].
 * Not thread-safe: call [record] from the GL thread only. Pure Kotlin so it runs in JVM tests.
 *
 * The draw time is wall-clock time spent on the CPU issuing the draw and the buffer swap. It is
 * not GPU time, but it grows when the GPU backs up, so it is still a useful trend; the fps is the
 * number to trust.
 *
 * @param report receives one line per interval.
 */
class FrameStats(
    private val intervalNs: Long = ONE_SECOND_NS,
    private val report: (String) -> Unit,
) {
    private var windowStartNs = -1L
    private var frames = 0
    private var drawTotalNs = 0L
    private var drawMaxNs = 0L

    /** @param nowNs time the frame finished (any monotonic clock), @param drawNs how long drawing it took. */
    fun record(nowNs: Long, drawNs: Long) {
        if (windowStartNs < 0) {
            // The first frame only marks the start of the window, so n frames span n intervals.
            windowStartNs = nowNs
            return
        }
        frames++
        drawTotalNs += drawNs
        if (drawNs > drawMaxNs) drawMaxNs = drawNs

        val elapsedNs = nowNs - windowStartNs
        if (elapsedNs < intervalNs) return
        report(
            String.format(
                Locale.US, "%.1f fps, draw avg %.2f ms / max %.2f ms (%d frames)",
                frames * ONE_SECOND_NS.toDouble() / elapsedNs,
                drawTotalNs / frames / 1e6,
                drawMaxNs / 1e6,
                frames,
            ),
        )
        windowStartNs = nowNs
        frames = 0
        drawTotalNs = 0
        drawMaxNs = 0
    }

    private companion object {
        const val ONE_SECOND_NS = 1_000_000_000L
    }
}
