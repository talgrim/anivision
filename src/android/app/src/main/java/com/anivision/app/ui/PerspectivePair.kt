package com.anivision.app.ui

import androidx.compose.runtime.saveable.Saver

/**
 * The two perspectives the view toggle offers: [left] on the left of the divider, [right] on the
 * right. They always differ, because showing one perspective twice would make the split pointless.
 */
data class PerspectivePair(val left: Perspective, val right: Perspective) {
    init {
        require(left != right) { "Both sides show $left" }
    }

    /** False for the perspective the other side already shows. */
    fun canSetLeft(perspective: Perspective): Boolean = perspective != right

    fun canSetRight(perspective: Perspective): Boolean = perspective != left

    /** This pair with [perspective] on the left, or unchanged if the right side already shows it. */
    fun withLeft(perspective: Perspective): PerspectivePair =
        if (canSetLeft(perspective)) copy(left = perspective) else this

    /** This pair with [perspective] on the right, or unchanged if the left side already shows it. */
    fun withRight(perspective: Perspective): PerspectivePair =
        if (canSetRight(perspective)) copy(right = perspective) else this

    /** Text form for preferences and saved state, e.g. `HUMAN,DOG`. */
    fun encode(): String = "${left.name},${right.name}"

    companion object {
        /** What the app starts with: the camera image on the left, the dog on the right. */
        val DEFAULT = PerspectivePair(Perspective.HUMAN, Perspective.DOG)

        /** Parses [encode]'s output; null for anything else, including two equal sides. */
        fun decode(text: String?): PerspectivePair? {
            val parts = text?.split(',') ?: return null
            if (parts.size != 2) return null
            val left = Perspective.entries.firstOrNull { it.name == parts[0] } ?: return null
            val right = Perspective.entries.firstOrNull { it.name == parts[1] } ?: return null
            return if (left == right) null else PerspectivePair(left, right)
        }

        val PairSaver: Saver<PerspectivePair, String> = Saver(
            save = { it.encode() },
            restore = { decode(it) },
        )
    }
}
