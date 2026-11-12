package com.anivision.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anivision.app.R

/** Shows a filtered gallery photo full screen with Close, Save and Share actions. */
@Composable
fun ImportedPhotoScreen(
    photo: Bitmap,
    saved: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = remember(photo) { photo.asImageBitmap() }
    Box(modifier = modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.imported_photo_description),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.close)) }
            Button(onClick = onSave, enabled = !busy && !saved) {
                Text(stringResource(if (saved) R.string.saved_label else R.string.save))
            }
            Button(onClick = onShare, enabled = !busy) { Text(stringResource(R.string.share)) }
        }
    }
}
