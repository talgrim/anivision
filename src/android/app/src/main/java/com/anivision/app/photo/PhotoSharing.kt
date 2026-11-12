package com.anivision.app.photo

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the system share sheet for an image already in the gallery (a MediaStore uri). */
fun sharePhoto(context: Context, uri: Uri, chooserTitle: CharSequence) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
