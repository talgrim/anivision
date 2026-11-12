package com.anivision.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Round icon toggle that shows or hides an optional effect (blur, night boost), drawn over the camera preview.
 * [contentDescription] is what screen readers announce for the icon.
 */
@Composable
fun EffectToggle(
    @DrawableRes icon: Int,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        shape = CircleShape,
        color = if (checked) colors.primary else colors.surface.copy(alpha = 0.85f),
        contentColor = if (checked) colors.onPrimary else colors.onSurface,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.padding(12.dp).size(24.dp),
        )
    }
}
