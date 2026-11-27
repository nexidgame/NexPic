package com.nex.pic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.recyclerview.widget.GridLayoutManager
import com.nex.pic.databinding.ActivityListBinding
import java.util.concurrent.Executors

/** Layar pilih folder tujuan ("Pindahkan ke:" / "Salin ke:") berupa grid folder, seperti QuickPic. */
class FolderPickerActivity : BaseActivity() {

    companion object {
        const val EXTRA_PATH = "path"

        fun intent(c: Context, @StringRes title: Int): Intent =
            Intent(c, FolderPickerActivity::class.java).putExtra("title", title)
    }

    private lateinit var b: ActivityListBinding
    private lateinit var adapter: TileAdapter
    private val exec = Executors.newSingleThreadExecutor()
    private var folders: List<Album> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        setupBars(b.appBar, b.list)

        b.toolbar.setTitle(intent.getIntExtra("title", R.string.move_to_colon))
        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.inflateMenu(R.menu.menu_folder_picker)
        b.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_new_folder) {
                inputDialog(R.string.new_folder, "") { n ->
                    val clean = n.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    if (clean.isNotEmpty()) pick("Pictures/$clean/")
                }
            }
            true
        }

        adapter = TileAdapter(onClick = { pos, _ -> folders.getOrNull(pos)?.let { pick(it.path) } })
        b.list.layoutManager = GridLayoutManager(this, 2)
        b.list.adapter = adapter

        exec.execute {
            val l = MediaRepo.folders(this, MediaRepo.load(this))
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                folders = l
                adapter.tiles = l.mapIndexed { i, a -> Tile(i.toLong(), a.name, a.count.toString(), a.cover) }
            }
        }
    }

    private fun pick(path: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, path))
        finish()
    }
}
