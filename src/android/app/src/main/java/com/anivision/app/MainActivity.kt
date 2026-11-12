package com.anivision.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anivision.app.camera.CameraPermissionGate
import com.anivision.app.ui.CameraScreen
import com.anivision.app.ui.theme.AniVisionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AniVisionTheme {
                CameraPermissionGate {
                    CameraScreen()
                }
            }
        }
    }
}
