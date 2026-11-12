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
import com.anivision.app.vision.Acuity
import java.util.Locale

/** Lowest slider value: the physical acuity blur (see [Acuity]). */
const val BLUR_STRENGTH_MIN = Acuity.DEFAULT_EXAGGERATION

/**
 * Highest slider value. [Acuity] accepts up to [Acuity.MAX_EXAGGERATION], but the shader's blur
 * radius is capped (`MAX_RADIUS` 32), so on a full-size (4096 px) photo the blur stops growing
 * above about 3.7x and would no longer match the preview. Stay below that.
 */
const val BLUR_STRENGTH_MAX = 3.5f

/** Slider value when the blur is first switched on; 1.0 (physical) is too subtle to notice. */
const val BLUR_STRENGTH_DEFAULT = 3.0f

/** Blur strength slider, shown under the [EffectSwitch] while the blur is on. */
@Composable
fun BlurSlider(
    strength: Float,
    onStrengthChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.blur_strength_label), modifier = Modifier.padding(end = 12.dp))
            Slider(
                value = strength,
                onValueChange = onStrengthChange,
                valueRange = BLUR_STRENGTH_MIN..BLUR_STRENGTH_MAX,
                modifier = Modifier.width(180.dp),
            )
            Text(
                text = String.format(Locale.US, "%.1f×", strength),
                modifier = Modifier.padding(start = 12.dp).width(40.dp),
            )
        }
    }
}
