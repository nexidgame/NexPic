package com.nex.pic

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nex.pic.databinding.ActivityMainBinding
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : BaseActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: TileAdapter
    private val exec = Executors.newSingleThreadExecutor()
    private var all: List<MediaItem> = emptyList()
    private var momentMap: Map<Long, Moment> = emptyMap()
    private var mode = 0 // 0 = album, 1 = momen
    private var query = ""
    private var asked = false
    private val ops = MediaOps(this)
    private var selected: Set<Long> = emptySet()
    private var albumList: List<Album> = emptyList()

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) {
            Session.unlockedAt = SystemClock.elapsedRealtime()
            b.drawer.visibility = View.VISIBLE
            refresh()
        } else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupBars(b.appBar, b.list)

        adapter = TileAdapter({ _, t -> if (mode == 0 && selected.isNotEmpty()) toggle(t.key) else open(t) }, { _, t -> if (mode == 0) toggle(t.key) })
        b.list.adapter = adapter
        b.list.layoutManager = GridLayoutManager(this, 2)
        setupGrid()

        b.toolbar.setNavigationOnClickListener { if (selected.isNotEmpty()) clearSel() else b.drawer.open() }
        b.toolbar.setOnMenuItemClickListener { onMenu(it.itemId) }
        renderToolbar()
        b.nav.setCheckedItem(R.id.nav_albums)
        b.nav.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_albums -> switchMode(0)
                R.id.nav_moments -> switchMode(1)
                R.id.nav_camera -> try {
                    startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                } catch (e: Exception) {
                    toast(R.string.no_app)
                }
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            b.drawer.closeDrawers()
            item.itemId == R.id.nav_albums || item.itemId == R.id.nav_moments
        }
        b.btnGrant.setOnClickListener { permLauncher.launch(perms()) }

        if (savedInstanceState == null && Prefs.lastAlbum && !Prefs.lock) {
            val last = Prefs.lastBucket
            val id = last.substringBefore('|').toLongOrNull()
            if (id != null) MediaListActivity.start(this, bucket = id, title = last.substringAfter('|'))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    selected.isNotEmpty() -> clearSel()
                    b.drawer.isDrawerOpen(GravityCompat.START) -> b.drawer.closeDrawers()
                    mode == 1 -> {
                        b.nav.setCheckedItem(R.id.nav_albums)
                        switchMode(0)
                    }
                    else -> finish()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        val km = getSystemService(KeyguardManager::class.java)
        val need = Prefs.lock && km.isDeviceSecure &&
            (Session.unlockedAt < 0 || SystemClock.elapsedRealtime() - Session.unlockedAt > 60_000)
        if (need) {
            b.drawer.visibility = View.INVISIBLE
            val i = km.createConfirmDeviceCredentialIntent(getString(R.string.app_name), getString(R.string.unlock))
            if (i != null) authLauncher.launch(i) else b.drawer.visibility = View.VISIBLE
        } else {
            b.drawer.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasPerm() && !asked) {
            asked = true
            permLauncher.launch(perms())
        }
        refresh()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) Session.unlockedAt = SystemClock.elapsedRealtime()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && Prefs.clearOnExit) {
            val app = applicationContext
            com.bumptech.glide.Glide.get(app).clearMemory()
            Thread { com.bumptech.glide.Glide.get(app).clearDiskCache() }.start()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGrid()
    }

    private fun perms(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun hasPerm() = perms().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun setupGrid() {
        val factor = if (mode == 0) 1.5f else 1.3f
        val spans = max(2, (resources.configuration.screenWidthDp / (Prefs.targetDp * factor)).toInt())
        (b.list.layoutManager as GridLayoutManager).spanCount = spans
    }

    private fun refresh() {
        if (!hasPerm()) {
            showEmpty(R.string.need_perm, true)
            return
        }
        exec.execute {
            val l = MediaRepo.load(this)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                all = l
                render()
            }
        }
    }

    private fun showEmpty(text: Int, grant: Boolean) {
        b.empty.visibility = View.VISIBLE
        b.emptyText.setText(text)
        b.btnGrant.visibility = if (grant) View.VISIBLE else View.GONE
        adapter.tiles = emptyList()
    }

    private fun render() {
        val tiles: List<Tile>
        if (mode == 0) {
            albumList = MediaRepo.albums(this, all, Prefs.albumSort, query, Prefs.showHidden)
            tiles = albumList.map { Tile(it.id, it.name, it.count.toString(), it.cover) }
            selected = selected.filter { id -> albumList.any { it.id == id } }.toSet()
        } else {
            val hidden = Prefs.hiddenIds
            val ms = MediaRepo.moments(if (Prefs.showHidden) all else all.filter { it.bucketId !in hidden })
            momentMap = ms.associateBy { it.from }
            tiles = ms.map { Tile(it.from, it.label, it.count.toString(), it.cover) }
        }
        if (tiles.isEmpty()) showEmpty(R.string.empty, false)
        else {
            b.empty.visibility = View.GONE
            adapter.tiles = tiles
        }
        adapter.selected = if (mode == 0) selected else emptySet()
        renderToolbar()
    }

    private fun renderToolbar() {
        b.toolbar.menu.clear()
        if (mode == 0 && selected.isNotEmpty()) {
            b.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            b.toolbar.title = getString(R.string.sel_fmt, selected.size, albumList.size)
            b.toolbar.inflateMenu(R.menu.menu_album_select)
            b.toolbar.menu.findItem(R.id.action_rename_folder)?.isVisible = selected.size == 1
            val allPinned = selected.all { it in Prefs.pinnedIds }
            b.toolbar.menu.findItem(R.id.action_pin)?.setTitle(if (allPinned) R.string.unpin_top else R.string.pin_top)
        } else {
            b.toolbar.setNavigationIcon(R.drawable.ic_menu)
            b.toolbar.setTitle(if (mode == 0) R.string.albums else R.string.moments)
            b.toolbar.inflateMenu(R.menu.menu_main)
            b.toolbar.menu.findItem(R.id.action_show_hidden)?.isChecked = Prefs.showHidden
            b.toolbar.menu.findItem(R.id.action_sort)?.isVisible = mode == 0
        }
        tintBar(b.toolbar)
    }

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

    private fun selectedAlbums() = albumList.filter { it.id in selected }

    private fun switchMode(m: Int) {
        mode = m
        selected = emptySet()
        setupGrid()
        render()
    }

    private fun open(t: Tile) {
        if (mode == 0) {
            Prefs.lastBucket = "${t.key}|${t.title ?: ""}"
            MediaListActivity.start(this, bucket = t.key, title = t.title ?: "")
        }
        else {
            val m = momentMap[t.key] ?: return
            MediaListActivity.start(this, from = m.from, to = m.to, title = m.label)
        }
    }

    private fun onMenu(id: Int): Boolean {
        when (id) {
            R.id.action_search -> inputDialog(
                R.string.search, query,
                neutral = if (query.isNotEmpty()) R.string.clear_filter else null,
                onNeutral = {
                    query = ""
                    render()
                }
            ) {
                query = it
                render()
            }
            R.id.action_sort -> sortMenu(
                b.toolbar.findViewById<View>(R.id.action_sort) ?: b.toolbar,
                listOf("name" to R.string.sort_name, "date" to R.string.sort_date, "count" to R.string.sort_count),
                Prefs.albumSort
            ) {
                Prefs.albumSort = it
                render()
            }
            R.id.action_view_mode -> thumbDialog {
                setupGrid()
                b.list.adapter?.notifyDataSetChanged()
            }
            R.id.action_show_hidden -> {
                Prefs.sp.edit().putBoolean("show_hidden", !Prefs.showHidden).apply()
                b.toolbar.menu.findItem(R.id.action_show_hidden)?.isChecked = Prefs.showHidden
                render()
            }
            R.id.action_refresh -> refresh()
            // ---- aksi untuk folder yang ditandai ----
            R.id.action_delete -> ops.confirmDelete(
                b.toolbar.findViewById<View>(R.id.action_delete) ?: b.toolbar,
                all.filter { it.bucketId in selected }
            ) {
                clearSel()
                refresh()
            }
            R.id.action_select_all -> {
                selected = albumList.map { it.id }.toSet()
                adapter.selected = selected
                renderToolbar()
            }
            R.id.action_hide -> {
                selectedAlbums().forEach { Prefs.addHidden(it.id, it.name) }
                toast(R.string.hidden_done)
                clearSel()
                render()
            }
            R.id.action_pin -> {
                Prefs.setPinned(selected, !selected.all { it in Prefs.pinnedIds })
                clearSel()
                render()
            }
            R.id.action_exclude -> {
                val sel = selectedAlbums()
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.exclude)
                    .setMessage(sel.joinToString(", ") { it.name })
                    .setPositiveButton(R.string.ok) { _, _ ->
                        sel.forEach { Prefs.addExcluded(it.id, it.name) }
                        toast(R.string.excluded_done)
                        clearSel()
                        refresh()
                    }
                    .setNegativeButton(R.string.cancel, null).show()
            }
            R.id.action_rename_folder -> selectedAlbums().singleOrNull()?.let { a ->
                inputDialog(R.string.rename, a.name) { n ->
                    ops.renameFolder(all.filter { it.bucketId == a.id }, n) {
                        clearSel()
                        refresh()
                    }
                }
            }
            R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
        }
        return true
    }
}
