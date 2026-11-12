package com.anivision.app.vision.gl

import android.graphics.Color
import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anivision.app.vision.Acuity
import com.anivision.app.vision.AnimalVision
import com.anivision.app.vision.VisionProfile
import com.anivision.app.vision.VisionSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real shaders on the device's GPU for every colour in `golden_vectors.json` and compares
 * the pixels with the prototype's expected 8-bit output. Each colour is fed to [VisionRenderer]
 * as a flat camera frame (through the same external-texture path the camera uses) and rendered
 * into an off-screen surface.
 *
 * The vectors are the prototype's exported file, packaged as an androidTest asset (see
 * `app/build.gradle.kts`). All GL work runs on one dedicated thread, like in the app.
 */
@RunWith(AndroidJUnit4::class)
class GpuGoldenVectorTest {
    private val gl = Executors.newSingleThreadExecutor { Thread(it, "GpuGoldenVectorTest-GL") }

    private lateinit var egl: EglCore
    private lateinit var renderer: VisionRenderer
    private lateinit var camera: FakeCamera
    private lateinit var target: EGLSurface

    @Before
    fun setUp() = onGl {
        egl = EglCore()
        target = egl.createPbufferSurface(SIZE, SIZE)
        egl.makeCurrent(target)
        renderer = VisionRenderer()
        camera = FakeCamera(renderer.cameraTextureId)
    }

    @After
    fun tearDown() {
        onGl {
            camera.release()
            renderer.release()
            egl.makeDefaultCurrent()
            egl.destroySurface(target)
            egl.release()
        }
        gl.shutdown()
    }

    @Test
    fun colourStepMatchesGoldenVectors() {
        for (variant in VARIANTS) {
            checkAllVectors(variant, blur = null)
        }
    }

    @Test
    fun passThroughKeepsTheCameraColour() {
        val failures = mutableListOf<String>()
        for (vector in vectors()) {
            val src = vector.rgb("srgb")
            val actual = render(src, VisionSettings())
            if (maxDiff(actual, src) > 1) failures += "${vector.label}: expected ${src.toList()}, got ${actual.toList()}"
        }
        assertTrue("pass-through differs by more than 1:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** A flat image has nothing to blur, so the blur path must give the same pixels as no blur. */
    @Test
    fun blurLeavesFlatColoursUnchanged() {
        for (variant in VARIANTS) {
            checkAllVectors(variant, blur = Acuity.MAX_EXAGGERATION)
        }
    }

    private fun checkAllVectors(variant: Variant, blur: Float?) {
        val tolerance = tolerance()
        val settings = VisionSettings(variant.profile, blur)
        val failures = mutableListOf<String>()
        for (vector in vectors()) {
            val expected = vector.rgb(variant.id)
            val actual = render(vector.rgb("srgb"), settings)
            val diff = maxDiff(actual, expected)
            if (diff > tolerance) {
                failures += "${vector.label} ${variant.id}: expected ${expected.toList()}, got ${actual.toList()} (off by $diff)"
            }
        }
        assertTrue(
            "${variant.id} blur=$blur: ${failures.size} of ${vectors().size} vectors off by more than $tolerance:\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /** Renders one flat colour and returns the 8-bit RGB of the centre pixel, after checking a corner agrees. */
    private fun render(srgb: IntArray, settings: VisionSettings): IntArray = onGl {
        camera.show(srgb)
        renderer.draw(camera.textureMatrix(), SIZE, SIZE, settings)
        val centre = readPixel(SIZE / 2, SIZE / 2)
        val corner = readPixel(0, 0)
        assertEquals("corner differs from centre for ${srgb.toList()}", centre.toList(), corner.toList())
        centre
    }

    private fun readPixel(x: Int, y: Int): IntArray {
        val buffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(x, y, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "glReadPixels failed" }
        return IntArray(3) { buffer.get(it).toInt() and 0xFF }
    }

    private fun maxDiff(a: IntArray, b: IntArray) = (0 until 3).maxOf { abs(a[it] - b[it]) }

    private fun <T> onGl(block: () -> T): T = gl.submit(Callable(block)).get()

    private class Variant(val id: String, val profile: VisionProfile)

    private class Vector(val label: String, private val json: JSONObject) {
        fun rgb(key: String): IntArray {
            val array = if (key == "srgb") json.getJSONArray("srgb") else json.getJSONObject(key).getJSONArray("srgb")
            return IntArray(3) { array.getInt(it) }
        }
    }

    private val golden: JSONObject by lazy {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        JSONObject(assets.open("golden_vectors.json").bufferedReader().use { it.readText() })
    }

    private fun tolerance() = golden.getJSONObject("tolerance_8bit").getInt("recommended")

    private fun vectors(): List<Vector> {
        val array = golden.getJSONArray("vectors")
        return List(array.length()) { Vector(array.getJSONObject(it).getString("label"), array.getJSONObject(it)) }
    }

    /**
     * Stands in for the camera: a SurfaceTexture bound to the renderer's external texture, filled
     * with one flat opaque colour at a time. GL thread only.
     */
    private class FakeCamera(textureId: Int) {
        private val texture = SurfaceTexture(textureId).apply { setDefaultBufferSize(SIZE, SIZE) }
        private val surface = Surface(texture)
        private val matrix = FloatArray(16)

        fun show(srgb: IntArray) {
            val canvas = surface.lockCanvas(null)
            canvas.drawColor(Color.rgb(srgb[0], srgb[1], srgb[2]))
            surface.unlockCanvasAndPost(canvas)
            texture.updateTexImage()
        }

        fun textureMatrix(): FloatArray = matrix.also { texture.getTransformMatrix(it) }

        fun release() {
            surface.release()
            texture.release()
        }
    }

    private companion object {
        /** Large enough that the strongest blur (sigma ~1.2 px) is above the renderer's skip threshold. */
        const val SIZE = 256

        val VARIANTS = listOf(Variant("v2", AnimalVision.DOG_V2), Variant("v0", AnimalVision.DOG_V0))
    }
}
