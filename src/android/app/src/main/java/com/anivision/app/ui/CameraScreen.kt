package com.anivision.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.anivision.app.R
import com.anivision.app.camera.CameraPreview
import com.anivision.app.photo.PhotoCapturer
import com.anivision.app.vision.Acuity
import com.anivision.app.vision.AnimalVision
import com.anivision.app.vision.VisionSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Blur strength when the switch is on: 1.0 is physical (see Acuity); up to Acuity.MAX_EXAGGERATION. */
private const val BLUR_EXAGGERATION = Acuity.DEFAULT_EXAGGERATION

/** The camera preview with its controls. The caller must hold the CAMERA permission. */
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var dogView by rememberSaveable { mutableStateOf(true) }
    var blur by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val capturer = remember(imageCapture) { PhotoCapturer(context.applicationContext, imageCapture) }
    val settings = VisionSettings(
        profile = if (dogView) AnimalVision.DOG else null,
        blurExaggeration = if (blur) BLUR_EXAGGERATION else null,
    )

    fun takePhoto() {
        saving = true
        scope.launch {
            val message = try {
                capturer.capture(settings, view.display?.rotation ?: Surface.ROTATION_0)
                R.string.photo_saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                R.string.photo_failed
            }
            saving = false
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePhoto() else Toast.makeText(context, R.string.photo_needs_storage, Toast.LENGTH_LONG).show()
    }

    fun onShutter() {
        val needsStoragePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsStoragePermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else takePhoto()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            settings = settings,
            imageCapture = imageCapture,
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
            ShutterButton(onClick = ::onShutter, busy = saving)
        }
    }
}
