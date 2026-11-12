package com.anivision.app.ui

import android.content.Context

/** Remembers the chosen [PerspectivePair] between launches. */
class PerspectivePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The saved pair, or [PerspectivePair.DEFAULT] if there is none or it cannot be read. */
    fun load(): PerspectivePair = PerspectivePair.decode(preferences.getString(KEY, null)) ?: PerspectivePair.DEFAULT

    fun save(pair: PerspectivePair) {
        preferences.edit().putString(KEY, pair.encode()).apply()
    }

    private companion object {
        const val FILE = "anivision"
        const val KEY = "perspectives"
    }
}
