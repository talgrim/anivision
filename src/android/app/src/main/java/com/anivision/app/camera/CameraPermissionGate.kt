package com.anivision.app.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.anivision.app.R
import com.anivision.app.ui.MessageScreen

/**
 * Shows [content] once the camera permission is granted. Otherwise shows one of three states:
 * an explanation before the first request, a retry after a plain denial, or a pointer to the
 * app settings once Android will no longer show the dialog.
 */
@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        requested = true
    }
    // Picks up a grant made in the app settings while the app was in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = context.hasCameraPermission() }

    val request = { launcher.launch(Manifest.permission.CAMERA) }
    when {
        granted -> content()
        !requested -> MessageScreen(
            title = stringResource(R.string.camera_permission_title),
            message = stringResource(R.string.camera_permission_message),
            actionLabel = stringResource(R.string.camera_permission_allow),
            onAction = request,
        )
        context.canAskAgain() -> MessageScreen(
            title = stringResource(R.string.camera_denied_title),
            message = stringResource(R.string.camera_denied_message),
            actionLabel = stringResource(R.string.camera_denied_retry),
            onAction = request,
        )
        else -> MessageScreen(
            title = stringResource(R.string.camera_blocked_title),
            message = stringResource(R.string.camera_blocked_message),
            actionLabel = stringResource(R.string.camera_blocked_open_settings),
            onAction = { context.openAppSettings() },
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** True after a plain denial; false once the user chose "don't ask again" (or Android stopped asking). */
private fun Context.canAskAgain(): Boolean {
    val activity = findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
