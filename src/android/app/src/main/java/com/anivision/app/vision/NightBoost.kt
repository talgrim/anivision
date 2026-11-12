package com.anivision.app.vision

/**
 * Night boost: the optional, artistic shadow lift (see `LowLight.md`). The shader takes a luminance
 * gamma; the app exposes a strength so that "more" moves the slider right.
 */
object NightBoost {
    /** No lift. */
    const val OFF_GAMMA = 1.0f

    /** The prototype's suggested lift (`export/matrix.json`, `shadow_lift.suggested_gamma`). */
    const val SUGGESTED_GAMMA = 0.85f

    /** The strongest lift before blacks look veiled (`shadow_lift.strongest_gamma`). */
    const val STRONGEST_GAMMA = 0.7f

    /** Slider range: 0 is no lift, 1 is [STRONGEST_GAMMA]. The lowest slider value stays visible. */
    const val MIN_STRENGTH = 0.1f
    const val MAX_STRENGTH = 1.0f

    /** Strength that gives [SUGGESTED_GAMMA]. */
    const val DEFAULT_STRENGTH = (OFF_GAMMA - SUGGESTED_GAMMA) / (OFF_GAMMA - STRONGEST_GAMMA)

    /** Luminance gamma for a strength in 0..1 (linear between [OFF_GAMMA] and [STRONGEST_GAMMA]). */
    fun gammaFor(strength: Float): Float =
        OFF_GAMMA - strength.coerceIn(0f, 1f) * (OFF_GAMMA - STRONGEST_GAMMA)
}
