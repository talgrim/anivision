package com.anivision.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.anivision.app.R

/**
 * Lets the user choose what the left and right buttons of the view toggle show. A side cannot pick
 * the perspective the other side already shows, so that choice is greyed out.
 */
@Composable
fun PerspectiveSettingsDialog(
    pair: PerspectivePair,
    onPairChange: (PerspectivePair) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.views_title)) },
        text = {
            Column {
                PerspectiveChoice(
                    title = stringResource(R.string.views_left),
                    selected = pair.left,
                    enabled = pair::canSetLeft,
                    onSelect = { onPairChange(pair.withLeft(it)) },
                )
                PerspectiveChoice(
                    title = stringResource(R.string.views_right),
                    selected = pair.right,
                    enabled = pair::canSetRight,
                    onSelect = { onPairChange(pair.withRight(it)) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun PerspectiveChoice(
    title: String,
    selected: Perspective,
    enabled: (Perspective) -> Boolean,
    onSelect: (Perspective) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Perspective.entries.forEach { perspective ->
            val isEnabled = enabled(perspective)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = perspective == selected,
                        enabled = isEnabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(perspective) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = perspective == selected, onClick = null, enabled = isEnabled)
                Text(
                    text = stringResource(perspective.label),
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isEnabled) 1f else 0.38f),
                )
            }
        }
    }
}
