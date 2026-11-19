package com.anivision.app.ui

import com.anivision.app.vision.VisionSettings

/** The perspectives [mode] shows for [pair]. */
fun ViewMode.visiblePerspectives(pair: PerspectivePair): List<Perspective> = when (this) {
    ViewMode.LEFT -> listOf(pair.left)
    ViewMode.SPLIT -> listOf(pair.left, pair.right)
    ViewMode.RIGHT -> listOf(pair.right)
}

/** True when part of the view is filtered, so the blur and night-boost controls have something to act on. */
fun ViewMode.showsFilter(pair: PerspectivePair): Boolean =
    visiblePerspectives(pair).any { it != Perspective.HUMAN }

/** True when a visible perspective wants the camera zoomed out as far as it goes. */
fun ViewMode.wantsWideAngle(pair: PerspectivePair): Boolean =
    visiblePerspectives(pair).any { it.wideAngle }

/**
 * The pipeline settings for [mode] showing [pair]. [splitFraction] only matters in [ViewMode.SPLIT];
 * the other modes put the divider at an edge so one side fills the frame.
 */
fun buildVisionSettings(
    mode: ViewMode,
    pair: PerspectivePair,
    splitFraction: Float,
    blurExaggeration: Float?,
    shadowGamma: Float?,
): VisionSettings = VisionSettings(
    left = pair.left.profile,
    right = pair.right.profile,
    blurExaggeration = blurExaggeration,
    split = when (mode) {
        ViewMode.LEFT -> 1f
        ViewMode.SPLIT -> splitFraction
        ViewMode.RIGHT -> 0f
    },
    shadowGamma = shadowGamma,
)
