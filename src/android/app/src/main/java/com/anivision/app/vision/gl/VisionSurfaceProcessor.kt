package com.anivision.app.vision.gl

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import com.anivision.app.vision.VisionSettings
import java.util.concurrent.Executor

/**
 * CameraX [SurfaceProcessor] that runs every camera frame through [VisionRenderer] on its own GL
 * thread. CameraX hands it one input request (camera frames) and one output (where to draw);
 * everything happens on [executor], so no locking is needed.
 *
 * [settings] is read once per frame from the GL thread, so it may be backed by any thread-safe
 * holder; changes apply from the next frame without rebinding the camera.
 *
 * Call [release] after the use cases that feed it were unbound.
 */
class VisionSurfaceProcessor(
    private val settings: () -> VisionSettings,
    private val onError: (Throwable) -> Unit,
) : SurfaceProcessor {
    private val thread = HandlerThread("AniVisionGL").apply { start() }
    private val handler = Handler(thread.looper)

    /** Runs work on the GL thread; pass this to `CameraEffect`. */
    val executor: Executor = Executor { handler.post(it) }

    private class Output(val surfaceOutput: SurfaceOutput, val eglSurface: EGLSurface)

    private var egl: EglCore? = null
    private var renderer: VisionRenderer? = null
    private var inputTexture: SurfaceTexture? = null
    private var output: Output? = null
    private var released = false

    private val textureMatrix = FloatArray(16)
    private val outputMatrix = FloatArray(16)

    init {
        handler.post {
            guarded {
                egl = EglCore()
                renderer = VisionRenderer()
            }
        }
    }

    override fun onInputSurface(request: SurfaceRequest) {
        val renderer = renderer
        if (released || renderer == null) {
            request.willNotProvideSurface()
            return
        }
        guarded {
            val texture = SurfaceTexture(renderer.cameraTextureId)
            texture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
            texture.setOnFrameAvailableListener({ drawFrame() }, handler)
            val surface = Surface(texture)
            inputTexture = texture
            request.provideSurface(surface, executor) {
                texture.setOnFrameAvailableListener(null)
                if (inputTexture === texture) inputTexture = null
                surface.release()
                texture.release()
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        val egl = egl
        if (released || egl == null) {
            surfaceOutput.close()
            return
        }
        guarded {
            output?.let { dispose(it) }
            val surface = surfaceOutput.getSurface(executor) {
                // The only event is a request to close; CameraX needs the surface released first.
                output?.takeIf { it.surfaceOutput === surfaceOutput }?.let {
                    dispose(it)
                    output = null
                }
            }
            output = Output(surfaceOutput, egl.createWindowSurface(surface))
        }
    }

    /** Stops the GL thread and frees GL resources. Safe to call more than once. */
    fun release() {
        handler.post {
            if (released) return@post
            released = true
            guarded {
                output?.let { dispose(it) }
                output = null
                inputTexture?.setOnFrameAvailableListener(null)
                inputTexture?.release()
                inputTexture = null
                renderer?.release()
                renderer = null
                egl?.release()
                egl = null
            }
            thread.quitSafely()
        }
    }

    private fun drawFrame() {
        val texture = inputTexture ?: return
        val renderer = renderer ?: return
        val egl = egl ?: return
        guarded {
            // Always consume the frame, or the camera stalls waiting for a free buffer.
            texture.updateTexImage()
            val target = output ?: return@guarded
            texture.getTransformMatrix(textureMatrix)
            target.surfaceOutput.updateTransformMatrix(outputMatrix, textureMatrix)
            egl.makeCurrent(target.eglSurface)
            val size = target.surfaceOutput.size
            renderer.draw(outputMatrix, size.width, size.height, settings())
            egl.swap(target.eglSurface, texture.timestamp)
        }
    }

    /** Frees the EGL surface (so it is no longer current) and then closes the CameraX output. */
    private fun dispose(target: Output) {
        egl?.let {
            it.makeDefaultCurrent()
            it.destroySurface(target.eglSurface)
        }
        target.surfaceOutput.close()
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            onError(e)
        }
    }
}
