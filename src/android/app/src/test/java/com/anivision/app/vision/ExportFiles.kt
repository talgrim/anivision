package com.anivision.app.vision

import java.io.File
import org.json.JSONObject

/** Locates the prototype's exported constants relative to the module directory (`src/android/app`). */
internal object ExportFiles {
    private val dir = File("../../prototype/export")

    private fun read(name: String): JSONObject {
        val file = File(dir, name)
        check(file.isFile) { "Missing ${file.canonicalPath}; run scripts/export_constants.py in src/prototype" }
        return JSONObject(file.readText())
    }

    /** Text of `animal_vision.glsl`, with line endings normalised to `\n`. */
    val glsl: String by lazy {
        val file = File(dir, "animal_vision.glsl")
        check(file.isFile) { "Missing ${file.canonicalPath}; run scripts/export_constants.py in src/prototype" }
        file.readText().replace("\r\n", "\n")
    }

    val matrix: JSONObject by lazy { read("matrix.json") }
    val goldenVectors: JSONObject by lazy { read("golden_vectors.json") }

    private fun profile(animal: String): JSONObject = matrix.getJSONObject("profiles").getJSONObject(animal)

    /** 3x3 row-major values of an animal's variant from matrix.json (`profiles.<animal>.variants.<variant>`). */
    fun profileMatrix(animal: String, variant: String): DoubleArray {
        val rows = profile(animal).getJSONObject("variants").getJSONObject(variant).getJSONArray("matrix")
        return DoubleArray(9) { rows.getJSONArray(it / 3).getDouble(it % 3) }
    }

    /** 3x3 row-major values of a dog variant ("v2", "v0") from matrix.json. */
    fun dogMatrix(variant: String): DoubleArray = profileMatrix("dog", variant)

    /** A number from an animal's profile: `acuity_ratio`, `shadow_gamma` or `peripheral_blur`. */
    fun profileValue(animal: String, name: String): Double = profile(animal).getDouble(name)

    fun defaultVariant(animal: String): String = profile(animal).getString("default_variant")

    fun defaultDogVariant(): String =
        matrix.getJSONObject("profiles").getJSONObject("dog").getString("default_variant")
}
