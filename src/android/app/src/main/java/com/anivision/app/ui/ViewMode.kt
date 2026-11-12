package com.anivision.app.ui

/** What the camera preview shows, relative to the chosen [PerspectivePair]. */
enum class ViewMode {
    /** The pair's left perspective everywhere. */
    LEFT,

    /** The left perspective on the left of a draggable divider, the right one on its right. */
    SPLIT,

    /** The pair's right perspective everywhere. */
    RIGHT,
}
