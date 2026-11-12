package com.anivision.app.vision

/**
 * What the GPU pipeline should do to the next frame.
 *
 * @param profile colour projection, or null to pass the camera image through untouched.
 * @param blurExaggeration acuity blur strength (see [Acuity]); null switches the blur off. Ignored
 * when [profile] is null.
 * @param split divider position as a fraction of the output width (0..1, from the left). Everything
 * left of it is shown unfiltered (no colour change, no blur), everything right of it gets the
 * profile. 0, the default, means no Normal region: the whole image is filtered. Ignored when
 * [profile] is null.
 * @param shadowGamma night-boost shadow lift: luminance gamma in (0, 1] (see `LowLight.md`); smaller
 * lifts more. Null, or 1, switches it off. Applies only to the filtered side. Ignored when
 * [profile] is null.
 */
data class VisionSettings(
    val profile: VisionProfile? = null,
    val blurExaggeration: Float? = null,
    val split: Float = 0f,
    val shadowGamma: Float? = null,
)
