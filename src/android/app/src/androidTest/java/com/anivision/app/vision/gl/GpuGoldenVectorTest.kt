package com.anivision.app.vision.gl

import android.graphics.Color
import android.graphics.Paint
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

    /** Left of the divider is the untouched camera colour, right of it the dog colour. */
    @Test
    fun splitShowsNormalOnTheLeftAndDogOnTheRight() {
        val tolerance = tolerance()
        val settings = VisionSettings(right = AnimalVision.DOG_V2, split = 0.5f)
        val failures = mutableListOf<String>()
        for (vector in vectors()) {
            val src = vector.rgb("srgb")
            val dog = vector.rgb("v2")
            val (left, right) = renderRow(settings, listOf(SIZE / 4, SIZE * 3 / 4)) { camera.show(src) }
            if (maxDiff(left, src) > 1) failures += "${vector.label} left: expected ${src.toList()}, got ${left.toList()}"
            if (maxDiff(right, dog) > tolerance) failures += "${vector.label} right: expected ${dog.toList()}, got ${right.toList()}"
        }
        assertTrue("split sides wrong:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun splitAtTheEdgesIsAllDogOrAllNormal() {
        val tolerance = tolerance()
        val xs = listOf(2, SIZE - 3)
        val failures = mutableListOf<String>()
        for (vector in vectors()) {
            val src = vector.rgb("srgb")
            val dog = vector.rgb("v2")
            renderRow(VisionSettings(right = AnimalVision.DOG_V2, split = 0f), xs) { camera.show(src) }.forEach {
                if (maxDiff(it, dog) > tolerance) failures += "${vector.label} split 0: expected ${dog.toList()}, got ${it.toList()}"
            }
            renderRow(VisionSettings(right = AnimalVision.DOG_V2, split = 1f), xs) { camera.show(src) }.forEach {
                if (maxDiff(it, src) > 1) failures += "${vector.label} split 1: expected ${src.toList()}, got ${it.toList()}"
            }
        }
        assertTrue("split extremes wrong:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * The picture has a hard white/black edge in the middle and the divider sits to the right of it,
     * so the edge is on the Normal side: the blur must leave it sharp there (and not on the Dog side).
     */
    @Test
    fun theNormalSideStaysSharpWhenTheBlurIsOn() {
        val white = intArrayOf(255, 255, 255)
        val black = intArrayOf(0, 0, 0)
        val beforeEdge = SIZE / 2 - 1
        val show = { camera.showHalves(white, black) }

        val blurredEverywhere = VisionSettings(right = AnimalVision.DOG_V2, blurExaggeration = Acuity.MAX_EXAGGERATION, split = 0f)
        val unblurred = VisionSettings(right = AnimalVision.DOG_V2, split = 0f)
        val dogBlurred = renderRow(blurredEverywhere, listOf(beforeEdge), show).single()
        val dogSharp = renderRow(unblurred, listOf(beforeEdge), show).single()
        assertTrue("blur should be visible next to the edge, got ${dogBlurred.toList()} vs ${dogSharp.toList()}", maxDiff(dogBlurred, dogSharp) > 8)

        val split = VisionSettings(right = AnimalVision.DOG_V2, blurExaggeration = Acuity.MAX_EXAGGERATION, split = 0.75f)
        val normalSide = renderRow(split, listOf(beforeEdge, SIZE / 2), show)
        assertTrue("white next to the edge was blurred: ${normalSide[0].toList()}", maxDiff(normalSide[0], white) <= 1)
        assertTrue("black next to the edge was blurred: ${normalSide[1].toList()}", maxDiff(normalSide[1], black) <= 1)
    }

    /** Each side of the divider goes through its own profile: dog on the left, cat on the right. */
    @Test
    fun eachSideUsesItsOwnProfile() {
        val tolerance = tolerance()
        val settings = VisionSettings(left = AnimalVision.DOG_V2, right = CAT_WITHOUT_LIFT, split = 0.5f)
        val failures = mutableListOf<String>()
        for (vector in vectors()) {
            val (left, right) = renderRow(settings, listOf(SIZE / 4, SIZE * 3 / 4)) { camera.show(vector.rgb("srgb")) }
            val dog = vector.rgb("v2")
            val cat = vector.rgb("cat")
            if (maxDiff(left, dog) > tolerance) failures += "${vector.label} left: expected ${dog.toList()}, got ${left.toList()}"
            if (maxDiff(right, cat) > tolerance) failures += "${vector.label} right: expected ${cat.toList()}, got ${right.toList()}"
        }
        assertTrue("per-side profiles wrong:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** The cat's own lift is on without any night boost, and is the same lift the night boost would apply. */
    @Test
    fun theCatCarriesItsOwnShadowLift() {
        val dark = intArrayOf(40, 40, 40)
        val withBuiltIn = render(dark, VisionSettings(right = AnimalVision.CAT_V2))
        val plain = render(dark, VisionSettings(right = CAT_WITHOUT_LIFT))
        val boosted = render(dark, VisionSettings(right = CAT_WITHOUT_LIFT, shadowGamma = AnimalVision.CAT_V2.shadowGamma))
        assertTrue("the cat should be brighter than without its lift: ${withBuiltIn.toList()} vs ${plain.toList()}", withBuiltIn[1] > plain[1] + 4)
        assertTrue("built-in lift should equal an explicit night boost: ${withBuiltIn.toList()} vs ${boosted.toList()}", maxDiff(withBuiltIn, boosted) <= 1)
    }

    /** A night boost weaker than the cat's own lift changes nothing: the two do not stack. */
    @Test
    fun aWeakerNightBoostDoesNotStackOnTheCatsLift() {
        val dark = intArrayOf(40, 40, 40)
        val catAlone = render(dark, VisionSettings(right = AnimalVision.CAT_V2))
        val withWeakBoost = render(dark, VisionSettings(right = AnimalVision.CAT_V2, shadowGamma = 0.95f))
        assertTrue("${catAlone.toList()} vs ${withWeakBoost.toList()}", maxDiff(catAlone, withWeakBoost) <= 1)
    }

    /**
     * A hard vertical edge in the middle of the frame. Next to it at the centre the blur has the
     * profile's sigma; next to it near the top it is wider, so more black leaks into the white.
     */
    @Test
    fun peripheralBlurSoftensTheEdgeOfTheFrameMoreThanTheCentre() {
        val white = intArrayOf(255, 255, 255)
        val black = intArrayOf(0, 0, 0)
        val x = SIZE / 2 - 2
        fun probe(profile: VisionProfile): Pair<Int, Int> = onGl {
            camera.showHalves(white, black)
            renderer.draw(
                camera.textureMatrix(), SIZE, SIZE,
                VisionSettings(right = profile, blurExaggeration = Acuity.MAX_EXAGGERATION),
            )
            readPixel(x, SIZE / 2)[1] to readPixel(x, 3)[1]
        }

        val (uniformCentre, uniformTop) = probe(PLAIN_DOG)
        assertTrue("without a periphery the blur is the same everywhere: $uniformCentre vs $uniformTop", abs(uniformCentre - uniformTop) <= 2)

        val (centre, top) = probe(PERIPHERAL_DOG)
        assertTrue("the edge pixel at the centre should barely change, was $centre", centre >= uniformCentre - 2)
        assertTrue("the periphery should leak more black near the top: centre $centre, top $top", top < centre - 8)
    }

    /** The night-boost shadow lift, in front of the matrix, against the prototype's lifted vectors. */
    @Test
    fun shadowLiftMatchesGoldenVectors() {
        checkShadowLift(blur = null)
    }

    /** Flat colours are unchanged by the blur, so the lift must also be right in the two-pass path. */
    @Test
    fun shadowLiftIsAppliedAfterTheBlur() {
        checkShadowLift(blur = 3.0f)
    }

    /** Left of the divider stays the camera colour even with the lift on; right of it is lifted. */
    @Test
    fun shadowLiftOnlyTouchesTheFilteredSide() {
        val tolerance = tolerance()
        val lift = shadowLiftVectors().last()
        val vectors = vectors()
        val failures = mutableListOf<String>()
        vectors.forEachIndexed { i, vector ->
            val src = vector.rgb("srgb")
            val settings = VisionSettings(right = AnimalVision.DOG_V2, split = 0.5f, shadowGamma = lift.gamma)
            val (left, right) = renderRow(settings, listOf(SIZE / 4, SIZE * 3 / 4)) { camera.show(src) }
            if (maxDiff(left, src) > 1) failures += "${vector.label} left: expected ${src.toList()}, got ${left.toList()}"
            if (maxDiff(right, lift.srgb[i]) > tolerance) {
                failures += "${vector.label} right: expected ${lift.srgb[i].toList()}, got ${right.toList()}"
            }
        }
        assertTrue("lift split wrong:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun shadowLiftIsIgnoredWithoutAProfile() {
        val failures = mutableListOf<String>()
        for (vector in vectors()) {
            val src = vector.rgb("srgb")
            val actual = render(src, VisionSettings(shadowGamma = 0.7f))
            if (maxDiff(actual, src) > 1) failures += "${vector.label}: expected ${src.toList()}, got ${actual.toList()}"
        }
        assertTrue("normal view was lifted:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun checkShadowLift(blur: Float?) {
        val tolerance = tolerance()
        val vectors = vectors()
        val failures = mutableListOf<String>()
        for (lift in shadowLiftVectors()) {
            val settings = VisionSettings(right = AnimalVision.DOG_V2, blurExaggeration = blur, shadowGamma = lift.gamma)
            vectors.forEachIndexed { i, vector ->
                val actual = render(vector.rgb("srgb"), settings)
                val diff = maxDiff(actual, lift.srgb[i])
                if (diff > tolerance) {
                    failures += "${vector.label} gamma ${lift.gamma}: expected ${lift.srgb[i].toList()}, got ${actual.toList()} (off by $diff)"
                }
            }
        }
        assertTrue("shadow lift blur=$blur:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun checkAllVectors(variant: Variant, blur: Float?) {
        val tolerance = tolerance()
        val settings = VisionSettings(right = variant.profile, blurExaggeration = blur)
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

    /** Runs [show] to set up the camera frame, draws it, and reads the pixels at [xs] on the middle row. */
    private fun renderRow(settings: VisionSettings, xs: List<Int>, show: () -> Unit): List<IntArray> = onGl {
        show()
        renderer.draw(camera.textureMatrix(), SIZE, SIZE, settings)
        xs.map { readPixel(it, SIZE / 2) }
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

    private class Lift(val gamma: Float, val srgb: List<IntArray>)

    /** Lifted expectations (variant v2, see `shadow_lift_vectors`), index-aligned with [vectors]. */
    private fun shadowLiftVectors(): List<Lift> {
        val section = golden.getJSONObject("shadow_lift_vectors")
        assertEquals("v2", section.getString("variant"))
        val gammas = section.getJSONArray("gammas")
        return List(gammas.length()) { g ->
            val entry = gammas.getJSONObject(g)
            val rows = entry.getJSONArray("srgb")
            Lift(entry.getDouble("gamma").toFloat(), List(rows.length()) { r -> IntArray(3) { rows.getJSONArray(r).getInt(it) } })
        }
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

        /** Left half [left], right half [right]. */
        fun showHalves(left: IntArray, right: IntArray) {
            val canvas = surface.lockCanvas(null)
            val paint = Paint()
            paint.color = Color.rgb(left[0], left[1], left[2])
            canvas.drawRect(0f, 0f, SIZE / 2f, SIZE.toFloat(), paint)
            paint.color = Color.rgb(right[0], right[1], right[2])
            canvas.drawRect(SIZE / 2f, 0f, SIZE.toFloat(), SIZE.toFloat(), paint)
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

        /** The cat's matrix and acuity without its built-in lift, which is what the golden vectors hold. */
        val CAT_WITHOUT_LIFT = AnimalVision.CAT_V2.let { VisionProfile(it.id, it.linearMatrix(), it.acuityRatio) }

        val PLAIN_DOG = VisionProfile("plain-dog", AnimalVision.DOG_V2.linearMatrix())
        val PERIPHERAL_DOG = VisionProfile("peripheral-dog", AnimalVision.DOG_V2.linearMatrix(), peripheralBlur = 3f)

        val VARIANTS = listOf(
            Variant("v2", AnimalVision.DOG_V2),
            Variant("v0", AnimalVision.DOG_V0),
            Variant("cat", CAT_WITHOUT_LIFT),
        )
    }
}
