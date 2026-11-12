package com.anivision.app.vision.gl

import android.opengl.GLES20

/** A linked GL program. Must be created, used and released on the thread that owns the GL context. */
internal class ShaderProgram(vertexSource: String, fragmentSource: String) {
    val id: Int
    private val uniforms = HashMap<String, Int>()

    init {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        id = GLES20.glCreateProgram()
        check(id != 0) { "glCreateProgram failed" }
        GLES20.glAttachShader(id, vertex)
        GLES20.glAttachShader(id, fragment)
        GLES20.glLinkProgram(id)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(id)
            GLES20.glDeleteProgram(id)
            error("Shader link failed: $log")
        }
    }

    fun use() = GLES20.glUseProgram(id)

    fun attribute(name: String): Int = GLES20.glGetAttribLocation(id, name)

    fun uniform(name: String): Int = uniforms.getOrPut(name) { GLES20.glGetUniformLocation(id, name) }

    fun release() = GLES20.glDeleteProgram(id)

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "glCreateShader failed" }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Shader compile failed: $log\n--- source ---\n$source")
        }
        return shader
    }
}
