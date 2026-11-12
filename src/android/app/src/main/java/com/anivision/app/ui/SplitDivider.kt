package com.anivision.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anivision.app.R
import kotlin.math.roundToInt

/** Keeps the divider (and its handle) clear of the screen edges so it can always be grabbed. */
const val SPLIT_MIN = 0.05f
const val SPLIT_MAX = 0.95f
const val SPLIT_DEFAULT = 0.5f

private val TOUCH_WIDTH = 48.dp
private val HANDLE_SIZE = 36.dp

/**
 * A vertical divider drawn at [fraction] of the available width, with a handle the user can drag
 * sideways. Only the strip around the line takes touches, so the camera controls stay usable.
 * [fraction] is a function so the position is read during layout only: a drag moves the divider
 * without recomposing anything.
 *
 * @param onDrag receives the drag as a change in [fraction] (positive = right); the caller applies
 * and clamps it, so quick successive drags never act on a stale position.
 * @param onDragStopped called when the finger lifts, for work that is too heavy to do on every step.
 */
@Composable
fun SplitDivider(
    fraction: () -> Float,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragStopped: () -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val touchPx = with(LocalDensity.current) { TOUCH_WIDTH.toPx() }
        val dragState = rememberDraggableState { delta -> if (widthPx > 0f) onDrag(delta / widthPx) }
        val description = stringResource(R.string.split_divider_description)

        Box(
            modifier = Modifier
                .offset { IntOffset((fraction() * widthPx - touchPx / 2f).roundToInt(), 0) }
                .width(TOUCH_WIDTH)
                .fillMaxHeight()
                .semantics { contentDescription = description }
                .draggable(state = dragState, orientation = Orientation.Horizontal, onDragStopped = { onDragStopped() }),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.9f)),
            )
            Surface(
                modifier = Modifier.size(HANDLE_SIZE),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Box(contentAlignment = Alignment.Center) { Text("◂▸") }
            }
        }
    }
}
