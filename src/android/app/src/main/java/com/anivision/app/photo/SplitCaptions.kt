package com.anivision.app.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * Marks a split-view photo: a thin line along the divider, as in the live view, and a small caption
 * in each half, so the saved picture shows where the views meet and says which side is which. The
 * captions sit at the top, the first at the left edge of the left half and the second
 * at the right edge of the right half. A caption that does not fit in its half (the divider is
 * close to an edge) moves to the other half, next to the divider, with an arrow pointing at the
 * side it names; it never straddles the divider.
 */
class SplitCaptions {
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(PILL_ALPHA, 0, 0, 0) }
    private val line = Paint().apply { color = Color.argb(LINE_ALPHA, 255, 255, 255) }

    /**
     * Draws the divider line at [split] and [leftLabel] on the part of [bitmap] left of it and
     * [rightLabel] on the rest.
     * [split] is the divider position as a fraction of the width; at 0 or below, or 1 or above,
     * there is nothing to caption and the bitmap is not touched. [bitmap] must be mutable.
     */
    fun draw(bitmap: Bitmap, split: Float, leftLabel: String, rightLabel: String) {
        if (split <= 0f || split >= 1f) return
        val width = bitmap.width.toFloat()
        val unit = min(bitmap.width, bitmap.height).toFloat()
        text.textSize = unit * TEXT_SIZE
        val seam = width * split

        val canvas = Canvas(bitmap)
        line.strokeWidth = max(unit * LINE_WIDTH, MIN_LINE_WIDTH_PX)
        canvas.drawLine(seam, 0f, seam, bitmap.height.toFloat(), line)
        // A half too narrow for its caption (divider near an edge) is labelled from the other half
        // instead: the caption hugs the divider and its arrow points at the side it names.
        if (!drawCaption(canvas, leftLabel, 0f, seam, alignRight = false, unit = unit)) {
            drawCaption(canvas, "$LEFT_ARROW $leftLabel", seam, width, alignRight = false, unit = unit)
        }
        if (!drawCaption(canvas, rightLabel, seam, width, alignRight = true, unit = unit)) {
            drawCaption(canvas, "$rightLabel $RIGHT_ARROW", 0f, seam, alignRight = true, unit = unit)
        }
    }

    /** Draws [label] inside the region if it fits and returns whether it did. */
    private fun drawCaption(
        canvas: Canvas,
        label: String,
        regionStart: Float,
        regionEnd: Float,
        alignRight: Boolean,
        unit: Float,
    ): Boolean {
        val margin = unit * MARGIN
        val padding = text.textSize * PADDING
        val pillWidth = text.measureText(label) + 2 * padding
        if (pillWidth + 2 * margin > regionEnd - regionStart) return false

        val left = if (alignRight) regionEnd - margin - pillWidth else regionStart + margin
        val metrics = text.fontMetrics
        val pillHeight = metrics.descent - metrics.ascent + padding
        val rect = RectF(left, margin, left + pillWidth, margin + pillHeight)
        canvas.drawRoundRect(rect, pillHeight / 2, pillHeight / 2, pill)
        canvas.drawText(label, left + padding, margin + padding / 2 - metrics.ascent, text)
        return true
    }

    private companion object {
        const val LEFT_ARROW = "◂"
        const val RIGHT_ARROW = "▸"

        /** Caption text height as a fraction of the photo's shorter side. */
        const val TEXT_SIZE = 0.025f
        const val MARGIN = 0.02f
        const val PADDING = 0.5f
        const val PILL_ALPHA = 140

        /** Divider line width as a fraction of the photo's shorter side, and its floor in pixels. */
        const val LINE_WIDTH = 0.004f
        const val MIN_LINE_WIDTH_PX = 2f
        const val LINE_ALPHA = 230
    }
}
