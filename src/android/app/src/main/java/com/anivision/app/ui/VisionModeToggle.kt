package com.anivision.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.anivision.app.R

/** Left perspective / split / right perspective switch shown over the camera preview. */
@Composable
fun VisionModeToggle(
    pair: PerspectivePair,
    mode: ViewMode,
    onModeChange: (ViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        SingleChoiceSegmentedButtonRow {
            ViewMode.entries.forEachIndexed { index, entry ->
                val label = when (entry) {
                    ViewMode.LEFT -> pair.left.label
                    ViewMode.SPLIT -> R.string.mode_split
                    ViewMode.RIGHT -> pair.right.label
                }
                SegmentedButton(
                    selected = mode == entry,
                    onClick = { onModeChange(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ViewMode.entries.size),
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}
