package com.anivision.app.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.anivision.app.R
import com.anivision.app.ui.MessageScreen
import com.anivision.app.vision.VisionProfile
import com.anivision.app.vision.applyVisionProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

/**
 * Live camera preview bound to the current lifecycle, optionally seen through [profile] (null
 * shows the unfiltered image). The camera is released when the composable leaves the composition;
 * CameraX handles stop/start with the lifecycle itself.
 *
 * The preview uses [PreviewView.ImplementationMode.COMPATIBLE] (a TextureView) because a colour
 * filter can only be applied to a hardware layer, not to the default SurfaceView.
 * The caller must hold the CAMERA permission (see [CameraPermissionGate]).
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    profile: VisionProfile? = null,
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(previewView, lifecycleOwner, lensFacing) {
        failed = false
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build()
        try {
            preview.setSurfaceProvider(previewView.surfaceProvider)
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                preview,
            )
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        } finally {
            provider.unbind(preview)
        }
    }

    if (failed) {
        MessageScreen(title = stringResource(R.string.app_name), message = stringResource(R.string.camera_unavailable))
    } else {
        AndroidView(
            factory = { previewView },
            modifier = modifier,
            update = { it.applyVisionProfile(profile) },
        )
    }
}
