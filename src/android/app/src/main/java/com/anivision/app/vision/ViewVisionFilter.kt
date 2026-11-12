package com.anivision.app.vision

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View

/**
 * MVP filter: renders [this] through a hardware layer with a [ColorMatrixColorFilter]. Android's
 * ColorMatrix works on gamma-encoded values, so this only approximates the linear-light pipeline
 * (the GPU shader of Phase 4 replaces it). Pass null to show the view unfiltered.
 */
fun View.applyVisionProfile(profile: VisionProfile?) {
    if (profile == null) {
        setLayerType(View.LAYER_TYPE_NONE, null)
    } else {
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(profile.toColorMatrixValues()))
        }
        setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }
}
