package com.nex.pic

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object Prefs {
    lateinit var sp: SharedPreferences

    fun init(c: Context) {
        sp = PreferenceManager.getDefaultSharedPreferences(c)
    }

    val themeMode: String get() = sp.getString("theme_mode", "system") ?: "system"
    val amoled: Boolean get() = sp.getBoolean("amoled", false)
    val dynamic: Boolean get() = sp.getBoolean("dynamic_color", false)
    val palette: String get() = sp.getString("palette", "blue") ?: "blue"
    val translucent: Boolean get() = sp.getBoolean("translucent", false)
    val lock: Boolean get() = sp.getBoolean("lock_app", false)
    val includeVideos: Boolean get() = sp.getBoolean("include_videos", true)
    val numericSort: Boolean get() = sp.getBoolean("numeric_sort", true)
    val squareThumbs: Boolean get() = sp.getBoolean("square_thumbs", true)
    val infoBar: Boolean get() = sp.getBoolean("info_bar", true)
    val slideInterval: Int get() = (sp.getString("slide_interval", "3") ?: "3").toIntOrNull() ?: 3
    val slideRandom: Boolean get() = sp.getBoolean("slide_random", false)
    val slideFade: Boolean get() = sp.getBoolean("slide_fade", true)
    val doubleTapZoom: Float get() = (sp.getString("double_tap_zoom", "2.5") ?: "2.5").toFloatOrNull() ?: 2.5f

    var thumbSize: String
        get() = sp.getString("thumb_size", "medium") ?: "medium"
        set(v) = sp.edit().putString("thumb_size", v).apply()

    var albumSort: String
        get() = sp.getString("album_sort", "name_asc") ?: "name_asc"
        set(v) = sp.edit().putString("album_sort", v).apply()

    var mediaSort: String
        get() = sp.getString("media_sort", "date_desc") ?: "date_desc"
        set(v) = sp.edit().putString("media_sort", v).apply()

    val showHidden: Boolean get() = sp.getBoolean("show_hidden", false)
    val lastAlbum: Boolean get() = sp.getBoolean("last_album", false)
    val tapSwap: Boolean get() = sp.getBoolean("tap_swap", false)
    val zoomButtons: Boolean get() = sp.getBoolean("zoom_buttons", true)
    val navHorizontal: Boolean get() = sp.getBoolean("nav_horizontal", true)
    val clearOnExit: Boolean get() = sp.getBoolean("clear_on_exit", false)

    /** Urutan disimpan per folder: kunci "b<bucketId>" untuk album, "m<awalBulan>" untuk momen. */
    fun sortFor(key: String): String = sp.getString("sort_$key", null) ?: "date_desc"
    fun setSortFor(key: String, v: String) = sp.edit().putString("sort_$key", v).apply()

    /** Set "id|nama" untuk album tersembunyi dan folder yang dikecualikan. */
    private fun getSet(k: String): Set<String> = HashSet(sp.getStringSet(k, emptySet()) ?: emptySet())
    private fun ids(k: String): Set<Long> = getSet(k).mapNotNull { it.substringBefore('|').toLongOrNull() }.toSet()
    val hiddenIds: Set<Long> get() = ids("hidden_buckets")
    val excludedIds: Set<Long> get() = ids("excluded_buckets")
    val excludedEntries: List<String> get() = getSet("excluded_buckets").toList().sorted()
    fun addHidden(id: Long, name: String) = sp.edit().putStringSet("hidden_buckets", getSet("hidden_buckets") + "$id|$name").apply()
    fun addExcluded(id: Long, name: String) = sp.edit().putStringSet("excluded_buckets", getSet("excluded_buckets") + "$id|$name").apply()
    fun setExcluded(entries: Set<String>) = sp.edit().putStringSet("excluded_buckets", entries).apply()

    var lastBucket: String
        get() = sp.getString("last_bucket", "") ?: ""
        set(v) = sp.edit().putString("last_bucket", v).apply()

    /** Lebar target thumbnail (dp) untuk menghitung jumlah kolom. */
    val targetDp: Int
        get() = when (thumbSize) {
            "small" -> 90
            "large" -> 170
            else -> 120
        }
}
