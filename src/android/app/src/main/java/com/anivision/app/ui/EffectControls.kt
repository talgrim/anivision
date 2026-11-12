package com.anivision.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anivision.app.R

/**
 * The blur and night-boost icon toggles, each followed by its strength slider while it is on. Shared
 * by the camera and the gallery photo so both offer the same controls.
 */
@Composable
fun EffectControls(
    blur: Boolean,
    onBlurChange: (Boolean) -> Unit,
    blurStrength: Float,
    onBlurStrengthChange: (Float) -> Unit,
    nightBoost: Boolean,
    onNightBoostChange: (Boolean) -> Unit,
    nightStrength: Float,
    onNightStrengthChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EffectToggle(
                icon = R.drawable.ic_blur,
                contentDescription = stringResource(R.string.blur_label),
                checked = blur,
                onCheckedChange = onBlurChange,
            )
            EffectToggle(
                icon = R.drawable.ic_night,
                contentDescription = stringResource(R.string.night_boost_label),
                checked = nightBoost,
                onCheckedChange = onNightBoostChange,
            )
        }
        if (blur) BlurSlider(strength = blurStrength, onStrengthChange = onBlurStrengthChange)
        if (nightBoost) NightBoostSlider(strength = nightStrength, onStrengthChange = onNightStrengthChange)
    }
}
