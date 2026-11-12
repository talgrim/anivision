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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anivision.app.BuildConfig
import com.anivision.app.R
import com.anivision.app.camera.CameraPreview
import com.anivision.app.photo.PhotoCapturer
import com.anivision.app.photo.PhotoFilterer
import com.anivision.app.photo.PhotoStore
import com.anivision.app.photo.SplitCaptions
import com.anivision.app.photo.sharePhoto
import com.anivision.app.vision.NightBoost
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the gallery photo waits after a setting changes before it is rendered again. */
private const val IMPORT_RENDER_DEBOUNCE_MS = 250L

/** Roughly the gallery button's width, mirrored on the shutter's other side. */
private val GALLERY_BUTTON_BALANCE = 48.dp

/** The camera preview with its controls. The caller must hold the CAMERA permission. */
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // What the left and right buttons show. Kept between launches; the view mode is not.
    val perspectivePreferences = remember { PerspectivePreferences(context.applicationContext) }
    var pair by rememberSaveable(stateSaver = PerspectivePair.PairSaver) { mutableStateOf(perspectivePreferences.load()) }
    LaunchedEffect(pair) { perspectivePreferences.save(pair) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ViewMode.RIGHT) }
    var splitFraction by rememberSaveable { mutableFloatStateOf(SPLIT_DEFAULT) }
    var blur by rememberSaveable { mutableStateOf(false) }
    var blurStrength by rememberSaveable { mutableFloatStateOf(BLUR_STRENGTH_DEFAULT) }
    var nightBoost by rememberSaveable { mutableStateOf(false) }
    var nightStrength by rememberSaveable { mutableFloatStateOf(NightBoost.DEFAULT_STRENGTH) }
    var saving by remember { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val capturer = remember(imageCapture) { PhotoCapturer(context.applicationContext, imageCapture) }
    // A function rather than a value: the preview calls it for every frame, so dragging the divider
    // changes what is drawn without recomposing this screen.
    fun currentSettings() = buildVisionSettings(
        mode = mode,
        pair = pair,
        splitFraction = splitFraction,
        blurExaggeration = if (blur) blurStrength else null,
        shadowGamma = if (nightBoost) NightBoost.gammaFor(nightStrength) else null,
    )

    // The blur and night-boost controls, on the camera and on the gallery photo alike.
    val effectControls: @Composable () -> Unit = {
        EffectControls(
            blur = blur,
            onBlurChange = { blur = it },
            blurStrength = blurStrength,
            onBlurStrengthChange = { blurStrength = it },
            nightBoost = nightBoost,
            onNightBoostChange = { nightBoost = it },
            nightStrength = nightStrength,
            onNightStrengthChange = { nightStrength = it },
        )
    }

    fun takePhoto() {
        saving = true
        scope.launch {
            val message = try {
                capturer.capture(
                    currentSettings(),
                    view.display?.rotation ?: Surface.ROTATION_0,
                    leftLabel = context.getString(pair.left.label),
                    rightLabel = context.getString(pair.right.label),
                )
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

    // Gallery import: the picked image is filtered like the live view (same pair, mode, blur and
    // night boost) and shown before it is saved. It is rendered again when any of them changes.
    val filterer = remember { PhotoFilterer() }
    val importCaptions = remember { SplitCaptions() }
    val store = remember { PhotoStore(context.contentResolver) }
    var importSource by remember { mutableStateOf<Uri?>(null) }
    var imported by remember { mutableStateOf<Bitmap?>(null) }
    var importedUri by remember { mutableStateOf<Uri?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    // Capture the value: onDispose must recycle the bitmap this effect was keyed on, not whatever
    // `imported` holds by then (that is the new bitmap when it replaces null).
    val shown = imported
    DisposableEffect(shown) { onDispose { shown?.recycle() } }

    val importSettings = currentSettings()
    LaunchedEffect(importSource, importSettings) {
        val uri = importSource ?: return@LaunchedEffect
        importBusy = true
        try {
            // A slider drag changes the settings many times a second; wait for the changes to stop
            // before rendering (the first render after picking a photo goes straight away).
            if (imported != null) delay(IMPORT_RENDER_DEBOUNCE_MS)
            val open = { context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri") }
            val rendered = filterer.render(open, importSettings)
            importCaptions.draw(rendered, importSettings.split, context.getString(pair.left.label), context.getString(pair.right.label))
            imported = rendered
            importedUri = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            importSource = null
            imported = null
            Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show()
        } finally {
            importBusy = false
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
        if (uri != null) importSource = uri
    }

    // Shown over whichever screen is open: the camera, or the gallery photo.
    if (showSettings) {
        PerspectiveSettingsDialog(pair = pair, onPairChange = { pair = it }, onDismiss = { showSettings = false })
    }

    val photo = shown
    if (photo != null) {
        ImportedPhotoScreen(
            photo = photo,
            pair = pair,
            mode = mode,
            onModeChange = { mode = it },
            splitFraction = splitFraction,
            onSplitChange = { splitFraction = it },
            effectControls = effectControls,
            onOpenSettings = { showSettings = true },
            saved = importedUri != null,
            busy = importBusy,
            onClose = {
                importSource = null
                imported = null
            },
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

    if (showAbout) {
        AboutScreen(versionName = BuildConfig.VERSION_NAME, onClose = { showAbout = false })
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            settings = { currentSettings() },
            imageCapture = imageCapture,
            wideAngle = mode.wantsWideAngle(pair),
        )
        // Before the controls in the Box, so the controls stay on top of the divider's touch strip.
        if (mode == ViewMode.SPLIT) {
            SplitDivider(
                fraction = { splitFraction },
                onDrag = { splitFraction = (splitFraction + it).coerceIn(SPLIT_MIN, SPLIT_MAX) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AboutButton(onClick = { showAbout = true })
            SettingsButton(onClick = { showSettings = true })
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (mode.showsFilter(pair)) effectControls()
            VisionModeToggle(pair = pair, mode = mode, onModeChange = { mode = it })
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
