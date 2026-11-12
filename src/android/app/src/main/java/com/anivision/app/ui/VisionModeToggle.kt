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

/** Normal / dog switch shown over the camera preview. */
@Composable
fun VisionModeToggle(
    dogView: Boolean,
    onDogViewChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    ) {
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = !dogView,
                onClick = { onDogViewChange(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text(stringResource(R.string.mode_normal)) },
            )
            SegmentedButton(
                selected = dogView,
                onClick = { onDogViewChange(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text(stringResource(R.string.mode_dog)) },
            )
        }
    }
}
