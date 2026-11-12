package com.anivision.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anivision.app.R
import com.anivision.app.vision.NightBoost
import kotlin.math.roundToInt

/** Night-boost strength slider, shown under the [EffectSwitch] while the boost is on. */
@Composable
fun NightBoostSlider(
    strength: Float,
    onStrengthChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.night_boost_strength_label), modifier = Modifier.padding(end = 12.dp))
            Slider(
                value = strength,
                onValueChange = onStrengthChange,
                valueRange = NightBoost.MIN_STRENGTH..NightBoost.MAX_STRENGTH,
                modifier = Modifier.width(180.dp),
            )
            Text(
                text = "${(strength * 100).roundToInt()}%",
                modifier = Modifier.padding(start = 12.dp).width(40.dp),
            )
        }
    }
}
