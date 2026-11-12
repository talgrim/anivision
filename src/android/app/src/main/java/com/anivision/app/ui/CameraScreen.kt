package com.anivision.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.anivision.app.R
import com.anivision.app.camera.CameraPreview
import com.anivision.app.photo.PhotoCapturer
import com.anivision.app.photo.PhotoFilterer
import com.anivision.app.photo.PhotoStore
import com.anivision.app.photo.sharePhoto
import com.anivision.app.vision.AnimalVision
import com.anivision.app.vision.VisionSettings
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Roughly the gallery button's width, mirrored on the shutter's other side. */
private val GALLERY_BUTTON_BALANCE = 92.dp

/** The camera preview with its controls. The caller must hold the CAMERA permission. */
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var dogView by rememberSaveable { mutableStateOf(true) }
    var blur by rememberSaveable { mutableStateOf(false) }
    var blurStrength by rememberSaveable { mutableFloatStateOf(BLUR_STRENGTH_DEFAULT) }
    var saving by remember { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val capturer = remember(imageCapture) { PhotoCapturer(context.applicationContext, imageCapture) }
    val settings = VisionSettings(
        profile = if (dogView) AnimalVision.DOG else null,
        blurExaggeration = if (blur) blurStrength else null,
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

    // Gallery import: the picked image is filtered with the dog profile and shown before it is saved.
    val filterer = remember { PhotoFilterer() }
    val store = remember { PhotoStore(context.contentResolver) }
    var imported by remember { mutableStateOf<Bitmap?>(null) }
    var importedUri by remember { mutableStateOf<Uri?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    // Capture the value: onDispose must recycle the bitmap this effect was keyed on, not whatever
    // `imported` holds by then (that is the new bitmap when it replaces null).
    val shown = imported
    DisposableEffect(shown) { onDispose { shown?.recycle() } }

    fun importImage(uri: Uri) {
        importBusy = true
        scope.launch {
            try {
                val importSettings = settings.copy(profile = AnimalVision.DOG)
                val open = { context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri") }
                imported = filterer.render(open, importSettings)
                importedUri = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show()
            } finally {
                importBusy = false
            }
        }
    }

    /** Saves the shown photo once and returns its uri, or null (after a toast) if that failed. */
    suspend fun saveImported(photo: Bitmap): Uri? {
        importedUri?.let { return it }
        return try {
            store.saveBitmap(photo).also {
                importedUri = it
                Toast.makeText(context, R.string.photo_saved, Toast.LENGTH_SHORT).show()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(context, R.string.photo_failed, Toast.LENGTH_SHORT).show()
            null
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importImage(uri)
    }

    val photo = shown
    if (photo != null) {
        ImportedPhotoScreen(
            photo = photo,
            saved = importedUri != null,
            busy = importBusy,
            onClose = { imported = null },
            onSave = {
                importBusy = true
                scope.launch {
                    saveImported(photo)
                    importBusy = false
                }
            },
            onShare = {
                importBusy = true
                scope.launch {
                    saveImported(photo)?.let { sharePhoto(context, it, context.getString(R.string.share_title)) }
                    importBusy = false
                }
            },
        )
        return
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
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (dogView) {
                BlurSwitch(checked = blur, onCheckedChange = { blur = it })
                if (blur) BlurSlider(strength = blurStrength, onStrengthChange = { blurStrength = it })
            }
            VisionModeToggle(dogView = dogView, onDogViewChange = { dogView = it })
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GalleryButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !saving && !importBusy,
                )
                ShutterButton(onClick = ::takePhoto, busy = saving || importBusy)
                // Balances the gallery button so the shutter stays centred.
                Spacer(Modifier.width(GALLERY_BUTTON_BALANCE))
            }
        }
    }
}
