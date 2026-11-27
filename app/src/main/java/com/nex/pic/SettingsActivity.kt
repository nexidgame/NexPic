package com.nex.pic

import android.app.KeyguardManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.bumptech.glide.Glide
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nex.pic.databinding.ActivitySettingsBinding

class SettingsActivity : BaseActivity() {

    private val themeKeys = setOf("theme_mode", "amoled", "dynamic_color", "palette", "translucent")
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in themeKeys) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupBars(b.appBar, b.container)
        val page = intent.getStringExtra("page") ?: "root"
        b.toolbar.setTitle(
            when (page) {
                "ui" -> R.string.set_ui
                "album" -> R.string.set_album
                "view" -> R.string.set_view
                "security" -> R.string.set_security
                "slideshow" -> R.string.set_slideshow
                "cache" -> R.string.set_cache
                "about" -> R.string.set_about
                else -> R.string.settings
            }
        )
        b.toolbar.setNavigationOnClickListener { finish() }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, PrefFragment().apply { arguments = bundleOf("page" to page) })
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        Prefs.sp.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onStop() {
        super.onStop()
        Prefs.sp.unregisterOnSharedPreferenceChangeListener(listener)
    }

    class PrefFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val page = requireArguments().getString("page") ?: "root"
            setPreferencesFromResource(
                when (page) {
                    "ui" -> R.xml.pref_ui
                    "album" -> R.xml.pref_album
                    "view" -> R.xml.pref_view
                    "security" -> R.xml.pref_security
                    "slideshow" -> R.xml.pref_slideshow
                    "cache" -> R.xml.pref_cache
                    "about" -> R.xml.pref_about
                    else -> R.xml.pref_root
                }, rootKey
            )
            when (page) {
                "root" -> listOf("ui", "album", "view", "security", "slideshow", "cache", "about").forEach { p ->
                    findPreference<Preference>("go_$p")?.setOnPreferenceClickListener {
                        startActivity(Intent(requireContext(), SettingsActivity::class.java).putExtra("page", p))
                        true
                    }
                }
                "album" -> findPreference<Preference>("excluded")?.setOnPreferenceClickListener {
                    val entries = Prefs.excludedEntries
                    if (entries.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.no_excluded, Toast.LENGTH_SHORT).show()
                    } else {
                        val keep = BooleanArray(entries.size) { true }
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.excluded_title)
                            .setMultiChoiceItems(entries.map { e -> e.substringAfter('|') }.toTypedArray(), keep) { _, i, c -> keep[i] = c }
                            .setPositiveButton(R.string.ok) { _, _ ->
                                Prefs.setExcluded(entries.filterIndexed { i, _ -> keep[i] }.toSet())
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                    true
                }
                "ui" -> findPreference<SwitchPreferenceCompat>("dynamic_color")?.apply {
                    if (!DynamicColors.isDynamicColorAvailable()) {
                        isEnabled = false
                        summary = getString(R.string.dynamic_unavailable)
                    }
                }
                "security" -> findPreference<SwitchPreferenceCompat>("lock_app")?.setOnPreferenceChangeListener { _, v ->
                    val km = requireContext().getSystemService(KeyguardManager::class.java)
                    if (v == true && !km.isDeviceSecure) {
                        Toast.makeText(requireContext(), R.string.lock_no_secure, Toast.LENGTH_SHORT).show()
                        false
                    } else true
                }
                "cache" -> findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
                    val ctx = requireContext().applicationContext
                    Glide.get(ctx).clearMemory()
                    Thread { Glide.get(ctx).clearDiskCache() }.start()
                    Toast.makeText(ctx, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                    true
                }
                "about" -> {
                    val v = try {
                        requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    findPreference<Preference>("about_version")?.summary = getString(R.string.version_fmt, v)
                }
            }
        }
    }
}
