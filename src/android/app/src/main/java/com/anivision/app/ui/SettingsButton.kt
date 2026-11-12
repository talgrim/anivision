package com.anivision.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anivision.app.R

/** Round cog button that opens the dialog for choosing the left and right perspectives. */
@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.settings),
            modifier = Modifier.padding(12.dp).size(24.dp),
        )
    }
}
