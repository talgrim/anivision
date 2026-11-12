package com.anivision.app.vision

/**
 * What the GPU pipeline should do to the next frame.
 *
 * @param profile colour projection, or null to pass the camera image through untouched.
 * @param blurExaggeration acuity blur strength (see [Acuity]); null switches the blur off. Ignored
 * when [profile] is null.
 */
data class VisionSettings(
    val profile: VisionProfile? = null,
    val blurExaggeration: Float? = null,
)
