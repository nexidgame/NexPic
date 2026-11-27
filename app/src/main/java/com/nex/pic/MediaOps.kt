package com.nex.pic

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nex.pic.databinding.DialogInputBinding
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Operasi berkas: hapus, ganti nama, putar, pindah, salin, perbaiki tanggal, peta, detail, bagikan.
 * Harus dibuat sebelum Activity STARTED (mendaftarkan launcher).
 */
class MediaOps(private val act: ComponentActivity) {

    private val cr get() = act.contentResolver
    private val bg = Executors.newSingleThreadExecutor()
    private var pending: (() -> Unit)? = null
    private var permCallback: (() -> Unit)? = null

    private val launcher = act.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val p = pending
        pending = null
        if (r.resultCode == Activity.RESULT_OK) p?.invoke()
    }

    private val permLauncher = act.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        val p = permCallback
        permCallback = null
        p?.invoke() // tetap lanjut; tanpa izin, lokasi hanya akan kosong
    }

    private fun ui(f: () -> Unit) = act.runOnUiThread { if (!act.isDestroyed) f() }
    private fun toast(res: Int) = Toast.makeText(act, res, Toast.LENGTH_SHORT).show()
    private fun result(ok: Int, fail: Int) =
        Toast.makeText(act, act.getString(R.string.op_result, ok, fail), Toast.LENGTH_LONG).show()

    // ---------- izin tulis / hapus ----------
    fun withWrite(items: List<MediaItem>, block: () -> Unit) {
        if (items.isEmpty()) return
        try {
            val pi = MediaStore.createWriteRequest(cr, items.map { it.uri })
            pending = block
            launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } catch (e: Exception) {
            toast(R.string.error)
        }
    }

    fun delete(items: List<MediaItem>, done: () -> Unit) {
        if (items.isEmpty()) return
        try {
            val pi = MediaStore.createDeleteRequest(cr, items.map { it.uri })
            pending = done
            launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } catch (e: Exception) {
            toast(R.string.error)
        }
    }

    // ---------- ganti nama ----------
    fun rename(item: MediaItem, newName: String, done: (String) -> Unit) {
        if (newName.isBlank()) return
        val ext = item.name.substringAfterLast('.', "")
        val full = if (ext.isNotEmpty() && !newName.endsWith(".$ext", ignoreCase = true)) "$newName.$ext" else newName
        withWrite(listOf(item)) {
            try {
                cr.update(item.uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, full) }, null, null)
                done(full)
            } catch (e: Exception) {
                toast(R.string.error)
            }
        }
    }

    // ---------- rotasi ----------
    fun rotate(items: List<MediaItem>, deg: Int, done: () -> Unit) {
        val imgs = items.filter { !it.isVideo && (it.mime.contains("jpeg") || it.mime.contains("png") || it.mime.contains("webp")) }
        if (imgs.isEmpty()) {
            toast(R.string.images_only)
            return
        }
        withWrite(imgs) {
            bg.execute {
                var ok = 0
                imgs.forEach { if (rotateOne(it, deg)) ok++ }
                ui {
                    result(ok, imgs.size - ok)
                    done()
                }
            }
        }
    }

    private fun rotateOne(item: MediaItem, deg: Int): Boolean {
        return try {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, item.uri)) { d, _, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
            val fmt = when {
                item.mime.contains("png") -> Bitmap.CompressFormat.PNG
                item.mime.contains("webp") -> Bitmap.CompressFormat.WEBP_LOSSLESS
                else -> Bitmap.CompressFormat.JPEG
            }
            cr.openOutputStream(item.uri, "wt")?.use { out.compress(fmt, 95, it) } ?: return false
            // jaga tanggal pengambilan asli
            cr.update(item.uri, ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTaken) }, null, null)
            true
        } catch (e: Throwable) {
            false
        }
    }

    // ---------- pindah / salin ----------
    private fun destFor(item: MediaItem, rel: String): String {
        var r = rel.trim('/') + "/"
        if (item.isVideo && r.startsWith("Pictures/")) r = "Movies/" + r.removePrefix("Pictures/")
        if (!item.isVideo && r.startsWith("Movies/")) r = "Pictures/" + r.removePrefix("Movies/")
        return r
    }

    fun move(items: List<MediaItem>, destRel: String, done: () -> Unit) {
        withWrite(items) {
            bg.execute {
                var ok = 0
                items.forEach {
                    try {
                        cr.update(it.uri, ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, destFor(it, destRel)) }, null, null)
                        ok++
                    } catch (e: Exception) {
                    }
                }
                ui {
                    result(ok, items.size - ok)
                    done()
                }
            }
        }
    }

    fun copy(items: List<MediaItem>, destRel: String, done: () -> Unit) {
        bg.execute {
            var ok = 0
            items.forEach { if (copyOne(it, destFor(it, destRel))) ok++ }
            ui {
                result(ok, items.size - ok)
                done()
            }
        }
    }

    private fun copyOne(item: MediaItem, rel: String): Boolean {
        var created: Uri? = null
        return try {
            val coll = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTaken)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val u = cr.insert(coll, cv) ?: return false
            created = u
            cr.openOutputStream(u)?.use { o -> cr.openInputStream(item.uri)?.use { i -> i.copyTo(o) } } ?: return false
            cr.update(u, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            created?.let { try { cr.delete(it, null, null) } catch (e2: Exception) { } }
            false
        }
    }

    /** Dialog pilih folder tujuan (+ "Folder baru"). */
    fun transfer(items: List<MediaItem>, isMove: Boolean, done: () -> Unit) {
        if (items.isEmpty()) return
        bg.execute {
            val folders = MediaRepo.folders(act, MediaRepo.load(act))
            ui {
                val labels = folders.map { "${it.name}\n${it.path}" } + act.getString(R.string.new_folder)
                MaterialAlertDialogBuilder(act)
                    .setTitle(if (isMove) R.string.move_to else R.string.copy_to)
                    .setItems(labels.toTypedArray()) { _, i ->
                        val go = { rel: String -> if (isMove) move(items, rel, done) else copy(items, rel, done) }
                        if (i < folders.size) go(folders[i].path)
                        else promptNewFolder { name -> go("Pictures/$name/") }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun promptNewFolder(onName: (String) -> Unit) {
        val v = DialogInputBinding.inflate(act.layoutInflater)
        v.input.hint = act.getString(R.string.folder_name)
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.new_folder)
            .setView(v.root)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = v.input.text.toString().trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                if (n.isNotEmpty()) onName(n)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Ganti nama folder = pindahkan semua berkas ke path baru. */
    fun renameFolder(items: List<MediaItem>, newName: String, done: () -> Unit) {
        val first = items.firstOrNull() ?: return
        val clean = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (clean.isEmpty()) return
        val parent = first.path.trim('/').substringBeforeLast('/', "")
        val dest = (if (parent.isEmpty()) "Pictures/" else "$parent/") + clean + "/"
        move(items, dest, done)
    }

    // ---------- perbaiki tanggal ----------
    fun fixDate(items: List<MediaItem>, done: () -> Unit) {
        withWrite(items) {
            bg.execute {
                var ok = 0
                items.forEach {
                    try {
                        val t = guessDate(it)
                        cr.update(it.uri, ContentValues().apply {
                            put(MediaStore.MediaColumns.DATE_TAKEN, t)
                            put(MediaStore.MediaColumns.DATE_MODIFIED, t / 1000)
                        }, null, null)
                        ok++
                    } catch (e: Exception) {
                    }
                }
                ui {
                    result(ok, items.size - ok)
                    done()
                }
            }
        }
    }

    private val nameDate = Regex("((?:19|20)\\d{2})[-_.]?(0[1-9]|1[0-2])[-_.]?(0[1-9]|[12]\\d|3[01])[-_. T]?([01]\\d|2[0-3])[-_.:]?([0-5]\\d)[-_.:]?([0-5]\\d)")

    /** Urutan: EXIF DateTimeOriginal, lalu tanggal pada nama berkas, lalu tanggal ubah. */
    private fun guessDate(item: MediaItem): Long {
        if (!item.isVideo) {
            try {
                cr.openInputStream(item.uri)?.use { s ->
                    val e = ExifInterface(s)
                    val d = e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(ExifInterface.TAG_DATETIME)
                    if (d != null) {
                        val t = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(d)?.time ?: 0L
                        if (t > 0) return t
                    }
                }
            } catch (e: Exception) {
            }
        }
        val m = nameDate.find(item.name)
        if (m != null) {
            val g = m.groupValues
            return Calendar.getInstance().apply {
                clear()
                set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
            }.timeInMillis
        }
        return item.dateModified
    }

    // ---------- peta ----------
    fun locate(item: MediaItem) {
        if (item.isVideo) {
            toast(R.string.no_location)
            return
        }
        val go = {
            bg.execute {
                val ll = readLatLong(item.uri)
                ui {
                    if (ll == null) toast(R.string.no_location)
                    else try {
                        act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${ll[0]},${ll[1]}?q=${ll[0]},${ll[1]}(${Uri.encode(item.name)})")))
                    } catch (e: Exception) {
                        toast(R.string.no_app)
                    }
                }
            }
        }
        if (ContextCompat.checkSelfPermission(act, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) go()
        else {
            permCallback = { go() }
            permLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    private fun readLatLong(uri: Uri): DoubleArray? {
        val original = try { MediaStore.setRequireOriginal(uri) } catch (e: Exception) { uri }
        for (u in listOf(original, uri)) {
            try {
                val r = cr.openInputStream(u)?.use { ExifInterface(it).latLong }
                if (r != null) return r
            } catch (e: Exception) {
            }
        }
        return null
    }

    // ---------- lain-lain ----------
    fun share(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val i: Intent
        if (items.size == 1) {
            i = Intent(Intent.ACTION_SEND).apply {
                type = items[0].mime.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, items[0].uri)
            }
        } else {
            i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(items.map { it.uri }))
            }
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri(null, items[0].uri).also { cd -> items.drop(1).forEach { cd.addItem(ClipData.Item(it.uri)) } }
        try {
            act.startActivity(Intent.createChooser(i, null))
        } catch (e: Exception) {
            toast(R.string.no_app)
        }
    }

    fun setAs(item: MediaItem) {
        try {
            act.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(item.uri, item.mime.ifBlank { "image/*" })
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null
                )
            )
        } catch (e: Exception) {
            toast(R.string.no_app)
        }
    }

    fun details(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val sb = StringBuilder()
        if (items.size == 1) {
            val it = items[0]
            sb.append(act.getString(R.string.name)).append(": ").append(it.name).append('\n')
            if (it.path.isNotEmpty()) sb.append(act.getString(R.string.d_path)).append(": ").append(it.path).append('\n')
            sb.append(act.getString(R.string.d_size)).append(": ").append(Formatter.formatShortFileSize(act, it.size)).append('\n')
            if (it.width > 0) sb.append(act.getString(R.string.d_res)).append(": ").append(it.width).append(" x ").append(it.height).append('\n')
            sb.append(act.getString(R.string.d_date)).append(": ")
                .append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.dateTaken))).append('\n')
            sb.append(act.getString(R.string.d_type)).append(": ").append(it.mime)
        } else {
            sb.append(act.getString(R.string.total_fmt, items.size, Formatter.formatShortFileSize(act, items.sumOf { it.size })))
        }
        MaterialAlertDialogBuilder(act).setTitle(R.string.details).setMessage(sb.toString())
            .setPositiveButton(R.string.close, null).show()
    }
}
