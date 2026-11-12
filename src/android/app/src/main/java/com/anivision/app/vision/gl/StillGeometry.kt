package com.anivision.app.vision.gl

import kotlin.math.max
import kotlin.math.roundToInt

/** Size arithmetic for filtering a still image, kept free of Android types so it can be unit-tested. */
internal object StillGeometry {
    /**
     * Size of the filtered image: the source turned by [rotationDegrees] (a multiple of 90) and, if
     * its long edge exceeds [maxLongEdge], scaled down to fit. The aspect ratio is preserved.
     */
    fun outputSize(width: Int, height: Int, rotationDegrees: Int, maxLongEdge: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Empty image: ${width}x$height" }
        require(maxLongEdge > 0) { "maxLongEdge must be positive, got $maxLongEdge" }
        require(rotationDegrees % 90 == 0) { "Rotation must be a multiple of 90, got $rotationDegrees" }
        val turned = (rotationDegrees / 90) % 2 != 0
        val w = if (turned) height else width
        val h = if (turned) width else height
        val longEdge = max(w, h)
        if (longEdge <= maxLongEdge) return w to h
        val scale = maxLongEdge.toDouble() / longEdge
        return max(1, (w * scale).roundToInt()) to max(1, (h * scale).roundToInt())
    }
}
