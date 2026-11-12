package com.anivision.app.ui

import androidx.annotation.StringRes
import com.anivision.app.R

/** What the camera preview shows. */
enum class ViewMode(@StringRes val label: Int) {
    /** The camera image untouched. */
    NORMAL(R.string.mode_normal),

    /** Normal on the left of a draggable divider, dog vision on the right. */
    SPLIT(R.string.mode_split),

    /** Dog vision everywhere. */
    DOG(R.string.mode_dog),
}
