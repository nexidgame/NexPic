package com.nex.pic

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nex.pic.databinding.ActivityListBinding
import java.util.concurrent.Executors
import kotlin.math.max

class MediaListActivity : BaseActivity() {

    companion object {
        fun start(c: Context, bucket: Long = NO_BUCKET, from: Long = 0L, to: Long = Long.MAX_VALUE, title: String = "") {
            c.startActivity(
                Intent(c, MediaListActivity::class.java)
                    .putExtra("bucket", bucket).putExtra("from", from).putExtra("to", to).putExtra("title", title)
            )
        }
    }

    private lateinit var b: ActivityListBinding
    private lateinit var adapter: TileAdapter
    private val ops = MediaOps(this)
    private val exec = Executors.newSingleThreadExecutor()
    private var items: List<MediaItem> = emptyList()
    private var selected: Set<Long> = emptySet()
    private var bucket = NO_BUCKET
    private var from = 0L
    private var to = Long.MAX_VALUE
    private var title = ""

    private val hasBucket get() = bucket != NO_BUCKET

    /** Album: urutan disimpan per folder. Momen: satu urutan bersama untuk semua bulan. */
    private val sortKey get() = if (hasBucket) "b$bucket" else "moments"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupBars(b.appBar, b.list)
        bucket = intent.getLongExtra("bucket", NO_BUCKET)
        from = intent.getLongExtra("from", 0L)
        to = intent.getLongExtra("to", Long.MAX_VALUE)
        title = intent.getStringExtra("title") ?: ""

        adapter = TileAdapter(
            onClick = { pos, t -> if (selected.isNotEmpty()) toggle(t.key) else openViewer(pos, false) },
            onLong = { _, t -> toggle(t.key) }
        )
        b.list.layoutManager = GridLayoutManager(this, 3)
        b.list.adapter = adapter
        setupGrid()

        b.toolbar.setNavigationOnClickListener { if (selected.isNotEmpty()) clearSel() else finish() }
        b.toolbar.setOnMenuItemClickListener { onMenu(it.itemId) }
        renderToolbar()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selected.isNotEmpty()) clearSel() else finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGrid()
    }

    private fun setupGrid() {
        val spans = max(2, resources.configuration.screenWidthDp / Prefs.targetDp)
        (b.list.layoutManager as GridLayoutManager).spanCount = spans
    }

    private fun load() {
        exec.execute {
            val l = MediaRepo.sortMedia(
                MediaRepo.load(this, if (hasBucket) bucket else null, from, to), Prefs.sortFor(sortKey)
            )
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (l.isEmpty()) {
                    finish()
                    return@runOnUiThread
                }
                items = l
                selected = selected.filter { id -> l.any { it.id == id } }.toSet()
                adapter.tiles = l.map { Tile(it.id, null, null, it) }
                adapter.selected = selected
                renderToolbar()
            }
        }
    }

    private fun renderToolbar() {
        b.toolbar.menu.clear()
        if (selected.isNotEmpty()) {
            b.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            b.toolbar.title = getString(R.string.sel_fmt, selected.size, items.size)
            b.toolbar.inflateMenu(R.menu.menu_select)
        } else {
            b.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            b.toolbar.title = "$title (${items.size})"
            b.toolbar.inflateMenu(R.menu.menu_list)
            // sembunyikan / kecualikan / ganti nama hanya untuk album (bukan momen)
            listOf(R.id.action_hide, R.id.action_exclude, R.id.action_rename_folder).forEach {
                b.toolbar.menu.findItem(it)?.isVisible = hasBucket
            }
        }
        tintBar(b.toolbar)
    }

    private fun anchor(id: Int): View = b.toolbar.findViewById<View>(id) ?: b.toolbar

    private fun toggle(key: Long) {
        selected = if (key in selected) selected - key else selected + key
        adapter.selected = selected
        renderToolbar()
    }

    private fun clearSel() {
        selected = emptySet()
        adapter.selected = selected
        renderToolbar()
    }

    private fun openViewer(pos: Int, autoplay: Boolean) {
        ViewerActivity.start(this, bucket, from, to, pos, autoplay, sortKey)
    }

    private fun selectedItems() = items.filter { it.id in selected }

    /** Item yang dipakai aksi: yang dipilih, atau seluruh isi folder bila tidak ada pilihan. */
    private fun targets() = if (selected.isEmpty()) items else selectedItems()

    private fun afterOp() {
        selected = emptySet()
        load()
    }

    private fun onMenu(id: Int): Boolean {
        when (id) {
            R.id.action_sort -> sortMenu(
                b.toolbar.findViewById<View>(R.id.action_sort) ?: b.toolbar,
                listOf("name" to R.string.sort_name, "date" to R.string.sort_date, "taken" to R.string.sort_taken, "size" to R.string.sort_size),
                Prefs.sortFor(sortKey)
            ) {
                Prefs.setSortFor(sortKey, it)
                load()
            }
            R.id.action_slideshow -> openViewer(0, true)
            R.id.action_view_mode -> thumbDialog {
                setupGrid()
                adapter.notifyDataSetChanged()
            }
            R.id.action_select_all -> {
                selected = items.map { it.id }.toSet()
                adapter.selected = selected
                renderToolbar()
            }
            R.id.action_hide -> {
                Prefs.addHidden(bucket, title)
                toast(R.string.hidden_done)
                finish()
            }
            R.id.action_exclude -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.exclude)
                .setMessage(title)
                .setPositiveButton(R.string.ok) { _, _ ->
                    Prefs.addExcluded(bucket, title)
                    toast(R.string.excluded_done)
                    finish()
                }
                .setNegativeButton(R.string.cancel, null).show()
            R.id.action_rename_folder -> inputDialog(R.string.rename, title) { n ->
                ops.renameFolder(items, n) { finish() }
            }
            R.id.action_fix_date -> ops.fixDate(targets()) { afterOp() }
            // ---- aksi untuk item terpilih ----
            R.id.action_share -> ops.share(selectedItems(), anchor(R.id.action_share))
            R.id.action_delete -> ops.confirmDelete(anchor(R.id.action_delete), selectedItems()) { afterOp() }
            R.id.rot_left -> ops.rotate(selectedItems(), 270) { afterOp() }
            R.id.rot_right -> ops.rotate(selectedItems(), 90) { afterOp() }
            R.id.rot_180 -> ops.rotate(selectedItems(), 180) { afterOp() }
            R.id.action_move -> ops.transfer(selectedItems(), true) { afterOp() }
            R.id.action_copy -> ops.transfer(selectedItems(), false) { afterOp() }
            R.id.action_details -> ops.details(selectedItems())
            R.id.action_edit -> single()?.let { EditorActivity.start(this, it) }
            R.id.action_set_as -> single()?.let { ops.setAs(it, b.toolbar) }
            R.id.action_show_map -> single()?.let { ops.locate(it) }
            R.id.action_rename -> {
                val sel = selectedItems()
                if (sel.size == 1) {
                    val m = sel[0]
                    inputDialog(R.string.rename, m.name.substringBeforeLast('.')) { n -> ops.rename(m, n) { afterOp() } }
                } else if (sel.size > 1) {
                    patternDialog(sel[0].name.substringBeforeLast('.')) { p -> ops.renameMany(sel, p) { afterOp() } }
                }
            }
        }
        return true
    }

    private fun single(): MediaItem? {
        val s = selectedItems()
        if (s.size != 1) {
            toast(R.string.pick_one)
            return null
        }
        return s[0]
    }
}
