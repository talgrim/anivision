package com.anivision.app.vision.gl

import com.anivision.app.vision.ExportFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionShadersTest {
    @Test
    fun colourFunctionsAreVerbatimFromTheExportedGlsl() {
        assertTrue(
            "VisionShaders.COLOUR_FUNCTIONS drifted from export/animal_vision.glsl",
            ExportFiles.glsl.contains(VisionShaders.COLOUR_FUNCTIONS),
        )
    }

    @Test
    fun shadowLiftIsVerbatimFromTheExportedGlsl() {
        assertTrue(
            "VisionShaders.SHADOW_LIFT_FUNCTIONS drifted from export/animal_vision.glsl",
            ExportFiles.glsl.contains(VisionShaders.SHADOW_LIFT_FUNCTIONS),
        )
    }

    @Test
    fun everyVariantEmbedsTheShadowLiftAndDeclaresItsUniform() {
        for (variant in VisionShaders.Variant.entries) {
            val source = VisionShaders.fragment(variant)
            assertTrue(variant.name, source.contains(VisionShaders.SHADOW_LIFT_FUNCTIONS))
            assertTrue(variant.name, source.contains("uniform float uShadowGamma;"))
        }
    }

    @Test
    fun theLiftRunsOnlyInTheFinalPassAfterTheBlurAndBeforeTheMatrix() {
        val call = "shadowLift(linear, uShadowGamma)"
        for (variant in VisionShaders.Variant.entries) {
            // The `#ifdef` branches are all present in the source text; the preprocessor picks one.
            val source = VisionShaders.fragment(variant)
            val main = source.substring(source.indexOf("void main()"))
            val colourOut = main.substring(main.indexOf("#ifdef COLOUR_OUT"), main.indexOf("#else", main.indexOf("#ifdef COLOUR_OUT")))
            assertTrue(variant.name, colourOut.contains(call))
            assertTrue(variant.name, main.indexOf(call) == main.lastIndexOf(call))
            // The blur comes first, then the lift, and the matrix takes the lifted colour.
            assertTrue(variant.name, main.indexOf("blurred(vUv)") < main.indexOf(call))
            assertTrue(variant.name, colourOut.contains("uColorMatrix * $call"))
        }
    }

    @Test
    fun everyVariantEmbedsTheColourFunctions() {
        for (variant in VisionShaders.Variant.entries) {
            assertTrue(variant.name, VisionShaders.fragment(variant).contains(VisionShaders.COLOUR_FUNCTIONS))
        }
    }

    @Test
    fun externalTextureExtensionIsDeclaredFirstAndOnlyForCameraSources() {
        for (variant in VisionShaders.Variant.entries) {
            val source = VisionShaders.fragment(variant)
            if (variant.oesSource) {
                assertTrue(variant.name, source.startsWith("#extension GL_OES_EGL_image_external : require\n"))
            } else {
                assertFalse(variant.name, source.contains("GL_OES_EGL_image_external"))
            }
        }
    }

    @Test
    fun definesFollowTheVariantFlags() {
        for (variant in VisionShaders.Variant.entries) {
            val source = VisionShaders.fragment(variant)
            assertTrue(variant.name, source.contains("#define SOURCE_OES") == variant.oesSource)
            assertTrue(variant.name, source.contains("#define BLUR\n") == variant.blur)
            assertTrue(variant.name, source.contains("#define COLOUR_OUT") == variant.colourOut)
            assertTrue(variant.name, source.contains("#define MAX_RADIUS ${VisionShaders.MAX_RADIUS}"))
        }
    }

    @Test
    fun cameraSamplesAreClampedBeforeDecodingToAvoidNaN() {
        val source = VisionShaders.fragment(VisionShaders.Variant.BLUR_HORIZONTAL)
        assertTrue(source.contains("srgbToLinear(clamp(camera, 0.0, 1.0))"))
    }

    @Test
    fun everyVariantDeclaresThePeripheryAndSizeUniforms() {
        for (variant in VisionShaders.Variant.entries) {
            val source = VisionShaders.fragment(variant)
            assertTrue(variant.name, source.contains("uniform float uPeriphery;"))
            assertTrue(variant.name, source.contains("uniform vec2 uSize;"))
        }
    }

    @Test
    fun theShaderHasNoSplitLogicBecauseTheRendererDrawsEachSideSeparately() {
        for (variant in VisionShaders.Variant.entries) {
            assertFalse(variant.name, VisionShaders.fragment(variant).contains("uSplit"))
        }
    }

    @Test
    fun theBlurSigmaGrowsWithTheSquaredRadiusFromTheFrameCentre() {
        for (variant in VisionShaders.Variant.entries.filter { it.blur }) {
            val source = VisionShaders.fragment(variant)
            // matrix.json acuity.peripheral_blur: sigma(r) = sigma * (1 + periphery * r^2), r^2 = 1 in the corners.
            assertTrue(variant.name, source.contains("float r2 = dot(fromCentre, fromCentre) / (0.25 * dot(uSize, uSize));"))
            assertTrue(variant.name, source.contains("float sigma = uSigma * (1.0 + uPeriphery * r2);"))
            assertTrue(variant.name, source.contains("float radius = min(ceil(3.0 * sigma), float(MAX_RADIUS));"))
        }
    }

    @Test
    fun everySideGoesThroughTheSameMatrixAndLift() {
        for (variant in VisionShaders.Variant.entries.filter { it.colourOut }) {
            assertTrue(
                variant.name,
                VisionShaders.fragment(variant).contains("vec3 shown = uColorMatrix * shadowLift(linear, uShadowGamma);"),
            )
        }
    }

    @Test
    fun theBlurPipelineEncodesToSrgbOnlyInTheLastPass() {
        assertTrue(VisionShaders.Variant.COLOUR.colourOut)
        assertFalse(VisionShaders.Variant.BLUR_HORIZONTAL.colourOut)
        assertTrue(VisionShaders.Variant.BLUR_VERTICAL.colourOut)
    }
}
