package com.anivision.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anivision.app.R

/**
 * Shows a filtered gallery photo full screen with the same view toggle as the camera, plus Close,
 * Save and Share actions. Choosing another view re-renders the photo (the caller's job).
 *
 * [effectControls] (blur and night boost) show while any visible side is filtered, and the cog
 * button opens the dialog that chooses the left and right views ([onOpenSettings]).
 *
 * In split view a divider sits over the photo, inside the photo's own bounds. It follows the finger
 * while dragged and calls [onSplitChange] when the finger lifts, because the photo is rendered
 * again for the new position (the old divider line stays in the picture until then).
 */
@Composable
fun ImportedPhotoScreen(
    photo: Bitmap,
    pair: PerspectivePair,
    mode: ViewMode,
    onModeChange: (ViewMode) -> Unit,
    splitFraction: Float,
    onSplitChange: (Float) -> Unit,
    effectControls: @Composable () -> Unit,
    onOpenSettings: () -> Unit,
    saved: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = remember(photo) { photo.asImageBitmap() }
    // Where the divider is while it is being dragged; it starts again from the rendered position.
    var dragFraction by remember(splitFraction) { mutableFloatStateOf(splitFraction) }
    Box(modifier = modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        // Sized to the photo itself, so the divider's 0..1 range is the photo's width, not the screen's.
        Box(
            modifier = Modifier.align(Alignment.Center).aspectRatio(photo.width.toFloat() / photo.height),
        ) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.imported_photo_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (mode == ViewMode.SPLIT) {
                SplitDivider(
                    fraction = { dragFraction },
                    onDrag = { dragFraction = (dragFraction + it).coerceIn(SPLIT_MIN, SPLIT_MAX) },
                    modifier = Modifier.fillMaxSize(),
                    onDragStopped = { if (dragFraction != splitFraction) onSplitChange(dragFraction) },
                )
            }
        }
        SettingsButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 16.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (mode.showsFilter(pair)) effectControls()
            VisionModeToggle(pair = pair, mode = mode, onModeChange = onModeChange)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.close)) }
                Button(onClick = onSave, enabled = !busy && !saved) {
                    Text(stringResource(if (saved) R.string.saved_label else R.string.save))
                }
                Button(onClick = onShare, enabled = !busy) { Text(stringResource(R.string.share)) }
            }
        }
    }
}
