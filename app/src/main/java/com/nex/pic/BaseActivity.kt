package com.nex.pic

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.graphics.ColorUtils
import androidx.core.view.MenuItemCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.forEach
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.color.DynamicColors
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nex.pic.databinding.DialogInputBinding

object ThemeState { var version = 0 }
object Session { var unlockedAt = -1L }

abstract class BaseActivity : AppCompatActivity() {

    private var themeVer = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        themeVer = ThemeState.version
        applyNexTheme()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    override fun onResume() {
        super.onResume()
        if (themeVer != ThemeState.version) recreate()
    }

    private fun applyNexTheme() {
        if (Prefs.themeMode == "blue") {
            theme.applyStyle(R.style.ThemeOverlay_NexPic_Blue, true)
            theme.applyStyle(R.style.ThemeOverlay_NexPic_BlueMode, true)
            return
        }
        if (Prefs.dynamic && DynamicColors.isDynamicColorAvailable()) {
            DynamicColors.applyToActivityIfAvailable(this)
        } else {
            theme.applyStyle(
                when (Prefs.palette) {
                    "teal" -> R.style.ThemeOverlay_NexPic_Teal
                    "purple" -> R.style.ThemeOverlay_NexPic_Purple
                    "rose" -> R.style.ThemeOverlay_NexPic_Rose
                    "orange" -> R.style.ThemeOverlay_NexPic_Orange
                    else -> R.style.ThemeOverlay_NexPic_Blue
                }, true
            )
        }
        val night = when (Prefs.themeMode) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        if (night && Prefs.amoled) theme.applyStyle(R.style.ThemeOverlay_NexPic_Amoled, true)
    }

    /** Bilah atas melayang di atas daftar; daftar diberi padding setinggi bilah + inset sistem/cutout. */
    protected fun setupBars(appBar: View, list: View) {
        val blue = Prefs.themeMode == "blue"
        val surface = MaterialColors.getColor(
            appBar,
            if (blue) com.google.android.material.R.attr.colorPrimary else com.google.android.material.R.attr.colorSurface
        )
        appBar.setBackgroundColor(if (Prefs.translucent) ColorUtils.setAlphaComponent(surface, 0xCC) else surface)
        if (blue) (appBar as? ViewGroup)?.let { g ->
            val tb = (0 until g.childCount).map { g.getChildAt(it) }.filterIsInstance<MaterialToolbar>().firstOrNull()
            if (tb != null) g.post { tintBar(tb) }
        }
        (list as? ViewGroup)?.clipToPadding = false
        var inset = Insets.NONE
        val apply = {
            list.updatePadding(left = inset.left, top = appBar.height, right = inset.right, bottom = inset.bottom)
        }
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { v, w ->
            inset = w.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = inset.left, top = inset.top, right = inset.right)
            apply()
            w
        }
        appBar.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob -> if (b - t != ob - ot) apply() }
    }

    /** Hanya tema Biru: judul & ikon bilah atas putih di atas latar biru. */
    protected fun tintBar(t: MaterialToolbar) {
        if (Prefs.themeMode != "blue") return
        val white = ColorStateList.valueOf(Color.WHITE)
        t.setTitleTextColor(Color.WHITE)
        t.setNavigationIconTint(Color.WHITE)
        t.menu.forEach { MenuItemCompat.setIconTintList(it, white) }
        t.overflowIcon?.mutate()?.setTint(Color.WHITE)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
    }

    protected fun toast(@StringRes r: Int) = Toast.makeText(this, r, Toast.LENGTH_SHORT).show()

    protected fun inputDialog(
        @StringRes title: Int,
        initial: String,
        @StringRes neutral: Int? = null,
        onNeutral: (() -> Unit)? = null,
        onOk: (String) -> Unit
    ) {
        val v = DialogInputBinding.inflate(layoutInflater)
        v.input.setText(initial)
        v.input.setSelection(initial.length)
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(v.root)
            .setPositiveButton(R.string.ok) { _, _ -> onOk(v.input.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
        if (neutral != null) builder.setNeutralButton(neutral) { _, _ -> onNeutral?.invoke() }
        val dlg = builder.create()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
        v.input.requestFocus()
    }

    /** Menu urutan: pilih item yang sama untuk membalik arah (↑ / ↓). */
    protected fun sortMenu(anchor: View, opts: List<Pair<String, Int>>, cur: String, pick: (String) -> Unit) {
        val parts = cur.split("_")
        val ck = parts.getOrElse(0) { "name" }
        val cd = parts.getOrElse(1) { "asc" }
        val pm = PopupMenu(this, anchor)
        opts.forEachIndexed { i, (k, res) ->
            val arrow = if (k == ck) (if (cd == "asc") "  ↑" else "  ↓") else ""
            pm.menu.add(0, i, i, getString(res) + arrow)
        }
        pm.setOnMenuItemClickListener {
            val k = opts[it.itemId].first
            pick(if (k == ck) k + "_" + (if (cd == "asc") "desc" else "asc") else k + "_" + (if (k == "name") "asc" else "desc"))
            true
        }
        pm.show()
    }

    protected fun thumbDialog(onChanged: () -> Unit) {
        val values = resources.getStringArray(R.array.thumb_values)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.view_mode)
            .setSingleChoiceItems(R.array.thumb_entries, values.indexOf(Prefs.thumbSize)) { d, i ->
                Prefs.thumbSize = values[i]
                d.dismiss()
                onChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
