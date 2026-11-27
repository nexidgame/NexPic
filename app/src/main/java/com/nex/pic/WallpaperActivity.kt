package com.nex.pic

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.forEach
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nex.pic.databinding.ActivityWallpaperBinding
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/** Pilih sebagai Wallpaper: pilih ukuran, potong dengan rasio tetap (+ putar / balik), lalu terapkan. */
class WallpaperActivity : BaseActivity() {

    companion object {
        fun start(c: Context, it: MediaItem) {
            c.startActivity(Intent(c, WallpaperActivity::class.java).setData(it.uri))
        }
    }

    private lateinit var b: ActivityWallpaperBinding
    private val exec = Executors.newSingleThreadExecutor()
    private var bmp: Bitmap? = null
    private var screenW = 0
    private var screenH = 0
    private var targetW = 0
    private var cropping = false
    private val options = ArrayList<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWallpaperBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupBars(b.appBar, b.crop)

        val bounds = windowManager.maximumWindowMetrics.bounds
        screenW = min(bounds.width(), bounds.height())
        screenH = max(bounds.width(), bounds.height())

        b.toolbar.inflateMenu(R.menu.menu_wallpaper)
        b.toolbar.setNavigationOnClickListener { back() }
        b.toolbar.setOnMenuItemClickListener { onMenu(it.itemId) }
        showCrop(false)
        buildOptions()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })

        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        exec.execute {
            try {
                val bm = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { dec, info, _ ->
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val s = max(info.size.width, info.size.height)
                    if (s > 4096) dec.setTargetSampleSize(s / 4096 + 1)
                }
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    bmp = bm
                    options.forEach { it.isEnabled = true; it.alpha = 1f }
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    toast(R.string.error)
                    finish()
                }
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Dua pilihan ukuran: layar penuh, dan dua kali lebar layar (wallpaper yang bisa bergeser). */
    private fun buildOptions() {
        val h = dp(170)
        listOf(screenW, screenW * 2).forEachIndexed { i, w ->
            val box = TextView(this).apply {
                text = "${w}x$screenH"
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, 0, dp(8))
                setBackgroundResource(R.drawable.bg_wp_option)
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer))
                isEnabled = false
                alpha = 0.5f
                setOnClickListener { startCrop(w) }
            }
            options.add(box)
            b.chooser.addView(
                box,
                LinearLayout.LayoutParams((h * w.toFloat() / screenH).toInt(), h).apply { topMargin = if (i == 0) 0 else dp(28) }
            )
        }
    }

    private fun aspect() = targetW.toFloat() / screenH

    private fun startCrop(w: Int) {
        val bm = bmp ?: return
        targetW = w
        b.crop.setBitmap(bm, aspect())
        showCrop(true)
    }

    private fun showCrop(on: Boolean) {
        cropping = on
        b.crop.visibility = if (on) View.VISIBLE else View.GONE
        b.chooser.visibility = if (on) View.GONE else View.VISIBLE
        b.toolbar.menu.forEach { it.isVisible = on }
    }

    private fun back() {
        if (cropping) showCrop(false) else finish()
    }

    private fun transform(m: Matrix) {
        val s = bmp ?: return
        val n = Bitmap.createBitmap(s, 0, 0, s.width, s.height, m, true)
        bmp = n
        b.crop.setBitmap(n, aspect())
    }

    private fun onMenu(id: Int): Boolean {
        when (id) {
            R.id.rot_left -> transform(Matrix().apply { postRotate(-90f) })
            R.id.rot_right -> transform(Matrix().apply { postRotate(90f) })
            R.id.rot_180 -> transform(Matrix().apply { postRotate(180f) })
            R.id.flip_h -> transform(Matrix().apply { postScale(-1f, 1f) })
            R.id.flip_v -> transform(Matrix().apply { postScale(1f, -1f) })
            R.id.action_apply -> chooseTarget()
        }
        return true
    }

    private fun chooseTarget() {
        val out = b.crop.crop() ?: return
        val flags = intArrayOf(
            WallpaperManager.FLAG_SYSTEM,
            WallpaperManager.FLAG_LOCK,
            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.wp_set)
            .setItems(arrayOf(getString(R.string.wp_home), getString(R.string.wp_lock), getString(R.string.wp_both))) { _, i ->
                applyWallpaper(out, flags[i])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyWallpaper(src: Bitmap, flags: Int) {
        exec.execute {
            var ok = false
            try {
                val scaled = Bitmap.createScaledBitmap(src, targetW, screenH, true)
                WallpaperManager.getInstance(this).setBitmap(scaled, null, true, flags)
                ok = true
            } catch (e: Throwable) {
                ok = false
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (ok) {
                    toast(R.string.wp_done)
                    finish()
                } else toast(R.string.error)
            }
        }
    }
}
