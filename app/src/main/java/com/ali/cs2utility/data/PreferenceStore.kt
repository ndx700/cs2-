package com.ali.cs2utility.data

import android.content.Context

class PreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("utility_state", Context.MODE_PRIVATE)
    var favorites: Set<String>
        get() = prefs.getStringSet("favorites", emptySet())!!.toSet()
        set(value) { prefs.edit().putStringSet("favorites", value.toSet()).apply() }
    var mapId: String
        get() = prefs.getString("mapId", "dust2")!!
        set(value) { prefs.edit().putString("mapId", value).apply() }
    var selectedId: String?
        get() = prefs.getString("selectedId", null)
        set(value) { prefs.edit().putString("selectedId", value).apply() }
}
