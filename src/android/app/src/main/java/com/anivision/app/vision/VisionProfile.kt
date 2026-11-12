package com.anivision.app.vision

/**
 * A colour-vision projection: a 3x3 matrix acting on LINEAR sRGB column vectors, `out = M * rgb`.
 *
 * The output approximates what the animal's cones capture, never what the animal experiences.
 * The matrix is kept as data so profiles can be added or swapped without touching the pipeline.
 * Pure Kotlin (no Android types) so it runs in plain JVM unit tests.
 *
 * @param rowMajor nine values, `matrix[row][col]` flattened row by row.
 */
class VisionProfile(val id: String, rowMajor: FloatArray) {
    private val matrix: FloatArray

    init {
        require(rowMajor.size == 9) { "A 3x3 matrix needs 9 values, got ${rowMajor.size}" }
        matrix = rowMajor.copyOf()
    }

    /** Defensive copy, 3x3 row-major. */
    fun linearMatrix(): FloatArray = matrix.copyOf()

    /** Column-major order, as `glUniformMatrix3fv(..., transpose = false, ...)` and GLSL `mat3(...)` expect. */
    fun linearMatrixColumnMajor(): FloatArray = floatArrayOf(
        matrix[0], matrix[3], matrix[6],
        matrix[1], matrix[4], matrix[7],
        matrix[2], matrix[5], matrix[8],
    )

    /** `android.graphics.ColorMatrix` layout: 4x5 row-major, alpha row identity, zero offsets. */
    fun toColorMatrixValues(): FloatArray = floatArrayOf(
        matrix[0], matrix[1], matrix[2], 0f, 0f,
        matrix[3], matrix[4], matrix[5], 0f, 0f,
        matrix[6], matrix[7], matrix[8], 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Multiplies a linear-light RGB triple by the matrix. No clamping. */
    fun applyLinear(r: Double, g: Double, b: Double): DoubleArray = doubleArrayOf(
        matrix[0] * r + matrix[1] * g + matrix[2] * b,
        matrix[3] * r + matrix[4] * g + matrix[5] * b,
        matrix[6] * r + matrix[7] * g + matrix[8] * b,
    )
}
