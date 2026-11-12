package com.anivision.app.vision

import kotlin.math.pow
import kotlin.math.roundToInt

/** sRGB transfer function, channel values in 0..1. */
object SrgbTransfer {
    fun toLinear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    fun fromLinear(c: Double): Double =
        if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055
}

/**
 * Reference implementation of the colour step (no blur, no shadow lift): decode sRGB, multiply by
 * the matrix, clamp each channel to [0, 1], encode, round to 8 bits. The GPU shader of Phase 4 must
 * agree with this to within one level per channel.
 */
object ColorPipeline {
    fun projectSrgb8(profile: VisionProfile, r: Int, g: Int, b: Int): IntArray {
        val linear = profile.applyLinear(
            SrgbTransfer.toLinear(r / 255.0),
            SrgbTransfer.toLinear(g / 255.0),
            SrgbTransfer.toLinear(b / 255.0),
        )
        return IntArray(3) { i ->
            (SrgbTransfer.fromLinear(linear[i].coerceIn(0.0, 1.0)) * 255.0).roundToInt()
        }
    }
}
