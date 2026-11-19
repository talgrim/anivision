package com.anivision.app.vision

/**
 * What the GPU pipeline should do to the next frame.
 *
 * The frame is split at [split]: [left] is shown to the left of it and [right] to the right of it.
 * A null profile means "human": the camera image untouched.
 *
 * @param left profile shown left of the divider, or null for the unfiltered camera image.
 * @param right profile shown right of the divider, or null for the unfiltered camera image.
 * @param blurExaggeration acuity blur strength (see [Acuity]); null switches the blur off. It
 * applies to each non-null profile's side, scaled by that profile's `acuityRatio` and
 * `peripheralBlur`.
 * @param split divider position as a fraction of the output width (0..1, from the left). 0 means
 * the whole frame shows [right], 1 means the whole frame shows [left].
 * @param shadowGamma night-boost shadow lift: luminance gamma in (0, 1] (see `LowLight.md`); smaller
 * lifts more. Null, or 1, switches it off. It applies to each non-null profile's side and does not
 * stack with that profile's own `shadowGamma`: the stronger one wins ([shadowGammaFor]).
 */
data class VisionSettings(
    val left: VisionProfile? = null,
    val right: VisionProfile? = null,
    val blurExaggeration: Float? = null,
    val split: Float = 0f,
    val shadowGamma: Float? = null,
) {
    /** True when no part of the frame is shown through a profile, so the camera image can be used as is. */
    val isPassThrough: Boolean
        get() = (split >= 1f || right == null) && (split <= 0f || left == null)

    /**
     * The luminance gamma to lift [profile]'s side with: the stronger (smaller) of the user's night
     * boost and the profile's own, never below [MIN_SHADOW_GAMMA]. 1 (no lift) for the unfiltered side.
     */
    fun shadowGammaFor(profile: VisionProfile?): Float {
        if (profile == null) return 1f
        return minOf(shadowGamma ?: 1f, profile.shadowGamma).coerceIn(MIN_SHADOW_GAMMA, 1f)
    }

    companion object {
        /** The lift is only tuned down to this gamma (`LowLight.md`); a smaller one would look hazy. */
        const val MIN_SHADOW_GAMMA = 0.5f
    }
}
