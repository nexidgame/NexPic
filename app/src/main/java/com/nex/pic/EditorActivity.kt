package com.nex.pic

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nex.pic.databinding.ActivityEditorBinding
import java.util.concurrent.Executors
import kotlin.math.max

/** Sunting: potong, putar, balik, menyusut. Hasil disimpan sebagai salinan "_edit" di folder yang sama. */
class EditorActivity : BaseActivity() {

    companion object {
        fun start(c: Context, it: MediaItem) {
            c.startActivity(
                Intent(c, EditorActivity::class.java).setData(it.uri)
                    .putExtra("name", it.name).putExtra("mime", it.mime).putExtra("path", it.path)
            )
        }
    }

    private lateinit var b: ActivityEditorBinding
    private val exec = Executors.newSingleThreadExecutor()
    private var bmp: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupBars(b.appBar, b.crop)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.inflateMenu(R.menu.menu_editor)
        b.toolbar.setOnMenuItemClickListener { onMenu(it.itemId) }

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
                    b.crop.setBitmap(bm)
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    toast(R.string.error)
                    finish()
                }
            }
        }
    }

    private fun transform(m: Matrix) {
        val s = bmp ?: return
        val n = Bitmap.createBitmap(s, 0, 0, s.width, s.height, m, true)
        bmp = n
        b.crop.setBitmap(n)
    }

    private fun onMenu(id: Int): Boolean {
        when (id) {
            R.id.rot_left -> transform(Matrix().apply { postRotate(-90f) })
            R.id.rot_right -> transform(Matrix().apply { postRotate(90f) })
            R.id.rot_180 -> transform(Matrix().apply { postRotate(180f) })
            R.id.flip_h -> transform(Matrix().apply { postScale(-1f, 1f) })
            R.id.flip_v -> transform(Matrix().apply { postScale(1f, -1f) })
            R.id.action_crop -> b.crop.crop()?.let {
                bmp = it
                b.crop.setBitmap(it)
            }
            R.id.action_resize -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.resize)
                .setItems(arrayOf(getString(R.string.resize_75), getString(R.string.resize_50), getString(R.string.resize_25))) { _, i ->
                    val s = bmp ?: return@setItems
                    val f = floatArrayOf(0.75f, 0.5f, 0.25f)[i]
                    val n = Bitmap.createScaledBitmap(s, max(1, (s.width * f).toInt()), max(1, (s.height * f).toInt()), true)
                    bmp = n
                    b.crop.setBitmap(n)
                }.show()
            R.id.action_save -> save()
        }
        return true
    }

    private fun save() {
        val out = b.crop.crop() ?: return
        val mime = intent.getStringExtra("mime") ?: "image/jpeg"
        val fmt: Bitmap.CompressFormat
        val ext: String
        val outMime: String
        when {
            mime.contains("png") -> { fmt = Bitmap.CompressFormat.PNG; ext = "png"; outMime = "image/png" }
            mime.contains("webp") -> { fmt = Bitmap.CompressFormat.WEBP_LOSSLESS; ext = "webp"; outMime = "image/webp" }
            else -> { fmt = Bitmap.CompressFormat.JPEG; ext = "jpg"; outMime = "image/jpeg" }
        }
        val base = (intent.getStringExtra("name") ?: "image").substringBeforeLast('.') + "_edit"
        val rel0 = intent.getStringExtra("path") ?: ""
        val rel = if (rel0.startsWith("DCIM/") || rel0.startsWith("Pictures/")) rel0 else "Pictures/NexPic/"
        exec.execute {
            var ok = false
            try {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$base.$ext")
                    put(MediaStore.MediaColumns.MIME_TYPE, outMime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                    put(MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis())
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val u = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                if (u != null) {
                    contentResolver.openOutputStream(u)?.use { out.compress(fmt, 95, it) }
                    contentResolver.update(u, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    ok = true
                }
            } catch (e: Exception) {
                ok = false
            }
            runOnUiThread {
                if (ok) {
                    Toast.makeText(this, getString(R.string.saved_fmt, rel), Toast.LENGTH_LONG).show()
                    finish()
                } else toast(R.string.error)
            }
        }
    }
}
