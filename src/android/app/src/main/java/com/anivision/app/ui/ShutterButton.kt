package com.anivision.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.anivision.app.R

/** Round camera shutter; shows a spinner and ignores taps while a photo is being [busy] saving. */
@Composable
fun ShutterButton(
    onClick: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.take_photo)
    Box(
        modifier = modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (busy) Color.White.copy(alpha = 0.45f) else Color.White)
            .clickable(enabled = !busy, onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
    }
}
