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

    val matrix: JSONObject by lazy { read("matrix.json") }
    val goldenVectors: JSONObject by lazy { read("golden_vectors.json") }

    /** 3x3 row-major values of a dog variant ("v2", "v0") from matrix.json. */
    fun dogMatrix(variant: String): DoubleArray {
        val rows = matrix.getJSONObject("profiles").getJSONObject("dog")
            .getJSONObject("variants").getJSONObject(variant).getJSONArray("matrix")
        return DoubleArray(9) { rows.getJSONArray(it / 3).getDouble(it % 3) }
    }

    fun defaultDogVariant(): String =
        matrix.getJSONObject("profiles").getJSONObject("dog").getString("default_variant")
}
