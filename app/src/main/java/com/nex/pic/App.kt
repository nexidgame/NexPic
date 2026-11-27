package com.nex.pic

import android.app.Application
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {

    private val themeKeys = setOf("theme_mode", "amoled", "dynamic_color", "palette", "translucent", "include_videos", "numeric_sort", "square_thumbs")

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in themeKeys) {
            ThemeState.version++
            applyNight()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        applyNight()
        Prefs.sp.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun applyNight() {
        AppCompatDelegate.setDefaultNightMode(
            when (Prefs.themeMode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
