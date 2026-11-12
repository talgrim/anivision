package com.anivision.app.ui

import androidx.annotation.StringRes
import com.anivision.app.R
import com.anivision.app.vision.AnimalVision
import com.anivision.app.vision.VisionProfile

/**
 * Something the camera image can be shown as. [profile] is null for [HUMAN], the camera image
 * untouched. The wide angle is an artistic hint at the cat's wide field of view, not a model of it.
 */
enum class Perspective(
    @StringRes val label: Int,
    val profile: VisionProfile?,
    /** Whether showing this perspective zooms the camera out as far as it goes (the cat's wide field of view). */
    val wideAngle: Boolean = false,
) {
    HUMAN(R.string.perspective_human, null),
    DOG(R.string.perspective_dog, AnimalVision.DOG),
    CAT(R.string.perspective_cat, AnimalVision.CAT, wideAngle = true),
}
