package com.anivision.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.anivision.app.camera.CameraPermissionGate
import com.anivision.app.camera.CameraPreview
import com.anivision.app.ui.theme.AniVisionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AniVisionTheme {
                CameraPermissionGate {
                    CameraPreview(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
