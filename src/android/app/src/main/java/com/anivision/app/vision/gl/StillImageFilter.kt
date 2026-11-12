package com.anivision.app.vision.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.view.Surface
import com.anivision.app.vision.VisionSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.min
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Runs a still image through the same GPU pipeline as the live preview ([VisionRenderer]) and
 * returns the result as a bitmap, so a saved photo looks like what the preview showed.
 *
 * The bitmap is fed in the way the camera is: it is drawn onto a `Surface` whose `SurfaceTexture`
 * is the renderer's external texture, and the result is rendered into an off-screen pbuffer.
 * Each call builds and tears down its own GL context on a private thread, so the filter holds no
 * state and is safe to share.
 */
class StillImageFilter(val maxLongEdge: Int = DEFAULT_MAX_LONG_EDGE) {
    /**
     * Filters [source] after turning it clockwise by [rotationDegrees] (a multiple of 90, e.g. the
     * EXIF rotation that makes the photo upright). The result is at most [maxLongEdge] pixels (and
     * the GPU's texture limit) on its long edge; larger sources are scaled down.
     *
     * Peak memory is several copies of the output (source surface, optional half-float blur
     * target, read-back buffer, result bitmap), which is why the size is capped.
     */
    suspend fun filter(source: Bitmap, rotationDegrees: Int, settings: VisionSettings): Bitmap {
        val thread = Executors.newSingleThreadExecutor { Thread(it, "AniVisionStill") }
        try {
            return withContext(thread.asCoroutineDispatcher()) { render(source, rotationDegrees, settings) }
        } finally {
            thread.shutdown()
        }
    }

    private fun render(source: Bitmap, rotationDegrees: Int, settings: VisionSettings): Bitmap {
        val egl = EglCore()
        try {
            val limit = min(maxLongEdge, maxTextureSize())
            val (width, height) = StillGeometry.outputSize(source.width, source.height, rotationDegrees, limit)
            val target = egl.createPbufferSurface(width, height)
            try {
                egl.makeCurrent(target)
                val renderer = VisionRenderer()
                try {
                    return renderThrough(renderer, source, rotationDegrees, width, height, settings)
                } finally {
                    renderer.release()
                }
            } finally {
                egl.makeDefaultCurrent()
                egl.destroySurface(target)
            }
        } finally {
            egl.release()
        }
    }

    private fun renderThrough(
        renderer: VisionRenderer,
        source: Bitmap,
        rotationDegrees: Int,
        width: Int,
        height: Int,
        settings: VisionSettings,
    ): Bitmap {
        val camera = BitmapCamera(renderer.cameraTextureId, width, height)
        try {
            camera.show(source, rotationDegrees)
            // glReadPixels returns the bottom row first; flip v so the bitmap comes out top row first.
            val texMatrix = FloatArray(16)
            android.opengl.Matrix.multiplyMM(texMatrix, 0, camera.textureMatrix(), 0, FLIP_V, 0)
            renderer.draw(texMatrix, width, height, settings)
            return readPixels(width, height)
        } finally {
            camera.release()
        }
    }

    private fun readPixels(width: Int, height: Int): Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "glReadPixels failed" }
        // The shader writes alpha 1, so premultiplied and straight RGBA are identical here.
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.copyPixelsFromBuffer(buffer) }
    }

    private fun maxTextureSize(): Int {
        val value = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, value, 0)
        return value[0].takeIf { it > 0 } ?: DEFAULT_MAX_LONG_EDGE
    }

    /** Plays the part of the camera: a SurfaceTexture bound to the renderer's external texture. GL thread only. */
    private class BitmapCamera(textureId: Int, private val width: Int, private val height: Int) {
        private val texture = SurfaceTexture(textureId).apply { setDefaultBufferSize(width, height) }
        private val surface = Surface(texture)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val matrix = FloatArray(16)

        fun show(source: Bitmap, rotationDegrees: Int) {
            val turned = (rotationDegrees / 90) % 2 != 0
            val turnedWidth = if (turned) source.height else source.width
            val turnedHeight = if (turned) source.width else source.height
            val placement = Matrix().apply {
                postTranslate(-source.width / 2f, -source.height / 2f)
                postRotate(rotationDegrees.toFloat())
                postScale(width.toFloat() / turnedWidth, height.toFloat() / turnedHeight)
                postTranslate(width / 2f, height / 2f)
            }
            val canvas: Canvas = surface.lockCanvas(null)
            try {
                canvas.drawColor(Color.BLACK)
                canvas.drawBitmap(source, placement, paint)
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
            texture.updateTexImage()
        }

        fun textureMatrix(): FloatArray = matrix.also { texture.getTransformMatrix(it) }

        fun release() {
            surface.release()
            texture.release()
        }
    }

    companion object {
        /** Default cap on the long edge of the result; keeps the GPU and bitmap buffers within a few hundred MB. */
        const val DEFAULT_MAX_LONG_EDGE = 4096

        /** v -> 1 - v, column-major. */
        private val FLIP_V = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, -1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f,
        )
    }
}
