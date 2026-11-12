package com.anivision.app.vision.gl

import androidx.camera.core.CameraEffect
import androidx.core.util.Consumer

/** A [CameraEffect] that applies [processor] to the preview stream. */
class VisionEffect(
    processor: VisionSurfaceProcessor,
    onError: (Throwable) -> Unit,
) : CameraEffect(
    CameraEffect.PREVIEW,
    processor.executor,
    processor,
    Consumer<Throwable> { onError(it) },
)
