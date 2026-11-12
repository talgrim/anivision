package com.anivision.app.vision.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.Matrix
import com.anivision.app.vision.Acuity
import com.anivision.app.vision.VisionSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Draws one camera frame through the vision pipeline. Owns the external camera texture, the shader
 * programs and the half-float intermediate used for the two-pass blur. GL thread only; construct
 * it with the GL context current.
 */
internal class VisionRenderer {
    /** The texture a `SurfaceTexture` must be attached to. */
    val cameraTextureId: Int

    private val colour = program(VisionShaders.Variant.COLOUR)
    private val blurHorizontal = program(VisionShaders.Variant.BLUR_HORIZONTAL)
    private val blurVertical = program(VisionShaders.Variant.BLUR_VERTICAL)
    private val quad: Int

    private var blurTexture = 0
    private var blurFramebuffer = 0
    private var blurWidth = 0
    private var blurHeight = 0

    /** Set when the device cannot render to a half-float texture; the blur is then skipped. */
    var blurUnsupported = false
        private set

    init {
        val ids = IntArray(2)
        GLES20.glGenTextures(1, ids, 0)
        cameraTextureId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(vertices).also { it.position(0) }
        GLES20.glGenBuffers(1, ids, 1)
        quad = ids[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quad)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, buffer, GLES20.GL_STATIC_DRAW)
    }

    /**
     * Renders the camera texture into the current framebuffer.
     *
     * @param texMatrix maps output-space uv (origin bottom-left, upright) to camera-texture uv, as
     * produced by `SurfaceOutput.updateTransformMatrix`.
     */
    fun draw(texMatrix: FloatArray, width: Int, height: Int, settings: VisionSettings) {
        val profile = settings.profile
        val colourMatrix = profile?.linearMatrixColumnMajor() ?: IDENTITY_3
        val sigma = blurSigma(settings, width, height)
        val split = settings.split.coerceIn(0f, 1f)
        val shadowGamma = if (profile == null) 1f else (settings.shadowGamma ?: 1f).coerceIn(MIN_SHADOW_GAMMA, 1f)

        GLES20.glViewport(0, 0, width, height)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quad)

        if (sigma > MIN_SIGMA_PX && ensureBlurTarget(width, height)) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFramebuffer)
            GLES20.glViewport(0, 0, width, height)
            drawPass(blurHorizontal, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId, texMatrix, colourMatrix, 1f / width, 0f, sigma, split, shadowGamma)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            drawPass(blurVertical, GLES20.GL_TEXTURE_2D, blurTexture, IDENTITY_4, colourMatrix, 0f, 1f / height, sigma, split, shadowGamma)
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            drawPass(colour, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId, texMatrix, colourMatrix, 0f, 0f, 0f, split, shadowGamma)
        }
    }

    fun release() {
        colour.release()
        blurHorizontal.release()
        blurVertical.release()
        releaseBlurTarget()
        GLES20.glDeleteTextures(1, intArrayOf(cameraTextureId), 0)
        GLES20.glDeleteBuffers(1, intArrayOf(quad), 0)
    }

    private fun blurSigma(settings: VisionSettings, width: Int, height: Int): Float {
        val exaggeration = settings.blurExaggeration
        if (settings.profile == null || exaggeration == null || blurUnsupported) return 0f
        return Acuity.sigmaPx(max(width, height), exaggeration)
    }

    private fun drawPass(
        program: ShaderProgram,
        textureTarget: Int,
        texture: Int,
        texMatrix: FloatArray,
        colourMatrix: FloatArray,
        stepX: Float,
        stepY: Float,
        sigma: Float,
        split: Float,
        shadowGamma: Float,
    ) {
        program.use()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, texture)
        GLES20.glUniform1i(program.uniform("uTexture"), 0)
        GLES20.glUniformMatrix4fv(program.uniform("uTexMatrix"), 1, false, texMatrix, 0)
        GLES20.glUniformMatrix3fv(program.uniform("uColorMatrix"), 1, false, colourMatrix, 0)
        GLES20.glUniform2f(program.uniform("uStep"), stepX, stepY)
        GLES20.glUniform1f(program.uniform("uSigma"), sigma)
        GLES20.glUniform1f(program.uniform("uSplit"), split)
        GLES20.glUniform1f(program.uniform("uShadowGamma"), shadowGamma)

        val position = program.attribute("aPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glBindTexture(textureTarget, 0)
    }

    /** (Re)creates the half-float blur target for this size. Returns false if the device cannot render to it. */
    private fun ensureBlurTarget(width: Int, height: Int): Boolean {
        if (blurFramebuffer != 0 && blurWidth == width && blurHeight == height) return true
        releaseBlurTarget()

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        blurTexture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, width, height, 0,
            GLES20.GL_RGBA, GLES30.GL_HALF_FLOAT, null,
        )

        GLES20.glGenFramebuffers(1, ids, 0)
        blurFramebuffer = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFramebuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, blurTexture, 0,
        )
        val complete = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        if (!complete) {
            releaseBlurTarget()
            blurUnsupported = true
            return false
        }
        blurWidth = width
        blurHeight = height
        return true
    }

    private fun releaseBlurTarget() {
        if (blurFramebuffer != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(blurFramebuffer), 0)
        if (blurTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(blurTexture), 0)
        blurFramebuffer = 0
        blurTexture = 0
        blurWidth = 0
        blurHeight = 0
    }

    private companion object {
        /** Below this the blur changes nothing visible; skip the two extra passes. */
        const val MIN_SIGMA_PX = 0.2f

        /** The lift is only tuned down to this gamma (`LowLight.md`); a smaller one would look hazy. */
        const val MIN_SHADOW_GAMMA = 0.5f

        val IDENTITY_3 = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        val IDENTITY_4 = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

        fun program(variant: VisionShaders.Variant) =
            ShaderProgram(VisionShaders.VERTEX, VisionShaders.fragment(variant))
    }
}
