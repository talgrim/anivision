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
    fun everyVariantDeclaresTheSplitUniform() {
        for (variant in VisionShaders.Variant.entries) {
            assertTrue(variant.name, VisionShaders.fragment(variant).contains("uniform float uSplit;"))
        }
    }

    @Test
    fun theUnfilteredSideSkipsTheBlurAndTheColourMatrix() {
        for (variant in VisionShaders.Variant.entries.filter { it.blur }) {
            // Blurring the Normal side would smear the picture the user is comparing against.
            assertTrue(variant.name, VisionShaders.fragment(variant).contains("if (vUv.x < uSplit) return fetchLinear(uv);"))
        }
        for (variant in VisionShaders.Variant.entries.filter { it.colourOut }) {
            assertTrue(variant.name, VisionShaders.fragment(variant).contains("vUv.x < uSplit ? linear : uColorMatrix * shadowLift(linear, uShadowGamma)"))
        }
    }

    @Test
    fun theBlurPipelineEncodesToSrgbOnlyInTheLastPass() {
        assertTrue(VisionShaders.Variant.COLOUR.colourOut)
        assertFalse(VisionShaders.Variant.BLUR_HORIZONTAL.colourOut)
        assertTrue(VisionShaders.Variant.BLUR_VERTICAL.colourOut)
    }
}
