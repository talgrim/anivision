package com.anivision.app.camera

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.anivision.app.BuildConfig
import com.anivision.app.R
import com.anivision.app.ui.MessageScreen
import com.anivision.app.vision.VisionSettings
import com.anivision.app.vision.gl.FrameStats
import com.anivision.app.vision.gl.VisionEffect
import com.anivision.app.vision.gl.VisionSurfaceProcessor
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeoutOrNull

/** Logcat tag of the debug-only frame rate report: `adb logcat -s AniVisionFps`. */
private const val FPS_LOG_TAG = "AniVisionFps"

/** How long to wait for the preview to be laid out so its view port is known. */
private const val VIEW_PORT_TIMEOUT_MS = 2_000L

/**
 * Live camera preview bound to the current lifecycle. Every frame goes through the GPU pipeline
 * ([VisionSurfaceProcessor]) configured by [settings]; changing [settings] takes effect on the
 * next frame without rebinding the camera. The camera is released when the composable leaves the
 * composition; CameraX handles stop/start with the lifecycle itself.
 *
 * If [imageCapture] is given it is bound with the preview and shares its view port, so a photo
 * covers exactly the area the preview shows. It is not filtered here; see `PhotoCapturer`.
 *
 * The caller must hold the CAMERA permission (see [CameraPermissionGate]).
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    settings: VisionSettings = VisionSettings(),
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    imageCapture: ImageCapture? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val currentSettings = remember { AtomicReference(settings) }
    SideEffect { currentSettings.set(settings) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(previewView, lifecycleOwner, lensFacing) {
        failed = false
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build()
        val onError: (Throwable) -> Unit = { failed = true }
        val frameStats = if (BuildConfig.DEBUG) FrameStats(report = { Log.d(FPS_LOG_TAG, it) }) else null
        val processor = VisionSurfaceProcessor(
            settings = { currentSettings.get() },
            onError = onError,
            frameStats = frameStats,
        )
        try {
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val useCases = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addEffect(VisionEffect(processor, onError))
                .apply {
                    imageCapture?.let { addUseCase(it) }
                    previewView.awaitViewPort()?.let { setViewPort(it) }
                }
                .build()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                useCases,
            )
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        } finally {
            provider.unbind(*listOfNotNull(preview, imageCapture).toTypedArray())
            processor.release()
        }
    }

    if (failed) {
        MessageScreen(title = stringResource(R.string.app_name), message = stringResource(R.string.camera_unavailable))
    } else {
        AndroidView(factory = { previewView }, modifier = modifier)
    }
}

/** The area of the camera image this view shows, or null if it has not been laid out in time. */
private suspend fun PreviewView.awaitViewPort(): ViewPort? = withTimeoutOrNull(VIEW_PORT_TIMEOUT_MS) {
    var port = viewPort
    while (port == null) {
        withFrameNanos { }
        port = viewPort
    }
    port
}
