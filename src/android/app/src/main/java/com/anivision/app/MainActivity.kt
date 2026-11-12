package com.anivision.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anivision.app.camera.CameraPermissionGate
import com.anivision.app.camera.CameraPreview
import com.anivision.app.ui.BlurSwitch
import com.anivision.app.ui.VisionModeToggle
import com.anivision.app.ui.theme.AniVisionTheme
import com.anivision.app.vision.Acuity
import com.anivision.app.vision.AnimalVision
import com.anivision.app.vision.VisionSettings

/** Blur strength when the switch is on: 1.0 is physical (see Acuity); up to Acuity.MAX_EXAGGERATION. */
private const val BLUR_EXAGGERATION = Acuity.DEFAULT_EXAGGERATION

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AniVisionTheme {
                CameraPermissionGate {
                    var dogView by rememberSaveable { mutableStateOf(true) }
                    var blur by rememberSaveable { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            settings = VisionSettings(
                                profile = if (dogView) AnimalVision.DOG else null,
                                blurExaggeration = if (blur) BLUR_EXAGGERATION else null,
                            ),
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (dogView) BlurSwitch(checked = blur, onCheckedChange = { blur = it })
                            VisionModeToggle(dogView = dogView, onDogViewChange = { dogView = it })
                        }
                    }
                }
            }
        }
    }
}
