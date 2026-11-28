package com.nex.pic

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.Insets
import androidx.core.view.MenuItemCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.forEach
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nex.pic.databinding.ActivityViewerBinding
import com.nex.pic.databinding.ItemPagerBinding
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.random.Random

class ViewerActivity : BaseActivity() {

    companion object {
        fun start(c: Context, bucket: Long, from: Long, to: Long, pos: Int, autoplay: Boolean, skey: String) {
            c.startActivity(
                Intent(c, ViewerActivity::class.java)
                    .putExtra("bucket", bucket).putExtra("from", from).putExtra("to", to)
                    .putExtra("pos", pos).putExtra("autoplay", autoplay).putExtra("skey", skey)
            )
        }
    }

    private lateinit var b: ActivityViewerBinding
    private val ops = MediaOps(this)
    private val exec = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val items = ArrayList<MediaItem>()
    private lateinit var adapter: PagerAdapter
    private var playing = false
    private var barsVisible = true
    private var inset = Insets.NONE

    private val tick = object : Runnable {
        override fun run() {
            if (!playing || items.isEmpty()) return
            val n = if (Prefs.slideRandom) Random.nextInt(items.size) else (b.pager.currentItem + 1) % items.size
            b.pager.setCurrentItem(n, true)
            handler.postDelayed(this, Prefs.slideInterval * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        b = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, w ->
            inset = w.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            b.appBar.updatePadding(left = inset.left, top = inset.top, right = inset.right)
            b.infoBar.updatePadding(left = inset.left + dp(20), right = inset.right + dp(20), bottom = inset.bottom + dp(14))
            w
        }

        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.inflateMenu(R.menu.menu_viewer)
        val white = ColorStateList.valueOf(Color.WHITE)
        b.toolbar.menu.forEach { MenuItemCompat.setIconTintList(it, white) }
        b.toolbar.overflowIcon?.mutate()?.setTint(Color.WHITE)
        b.toolbar.setOnMenuItemClickListener { onMenu(it.itemId) }
        b.infoBtn.setOnClickListener { cur()?.let { ops.details(listOf(it)) } }
        b.pager.orientation = if (Prefs.navHorizontal) ViewPager2.ORIENTATION_HORIZONTAL else ViewPager2.ORIENTATION_VERTICAL
        b.zoomIn.setOnClickListener { currentZoom()?.zoomBy(1.6f) }
        b.zoomOut.setOnClickListener { currentZoom()?.zoomBy(1f / 1.6f) }
        val zb = if (Prefs.zoomButtons) View.VISIBLE else View.GONE
        b.zoomIn.visibility = zb
        b.zoomOut.visibility = zb

        adapter = PagerAdapter(items, { onTap() }, { playVideo(it) })
        b.pager.adapter = adapter

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (playing) stopSlideshow() else finish()
            }
        })

        load()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private var registered = false

    private fun load(keepPos: Boolean = false) {
        val ext = if (intent.action == Intent.ACTION_VIEW) intent.data else null
        val bucket = intent.getLongExtra("bucket", NO_BUCKET)
        val from = intent.getLongExtra("from", 0L)
        val to = intent.getLongExtra("to", Long.MAX_VALUE)
        val skey = intent.getStringExtra("skey") ?: ""
        exec.execute {
            val l = if (ext != null) listOf(MediaRepo.fromUri(this, ext))
            else MediaRepo.sortMedia(
                MediaRepo.load(this, if (bucket != NO_BUCKET) bucket else null, from, to), Prefs.sortFor(skey)
            )
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (l.isEmpty()) {
                    finish()
                    return@runOnUiThread
                }
                val keep = if (keepPos) b.pager.currentItem else intent.getIntExtra("pos", 0)
                items.clear()
                items.addAll(l)
                adapter.notifyDataSetChanged()
                val start = keep.coerceIn(0, items.size - 1)
                b.pager.setCurrentItem(start, false)
                if (!registered) {
                    registered = true
                    b.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) = updateInfo(position)
                    })
                    if (intent.getBooleanExtra("autoplay", false)) startSlideshow()
                }
                updateInfo(start)
            }
        }
    }

    /** MediaStore memperbarui metadata dengan sedikit jeda setelah berkas diubah. */
    private fun reloadSoon() = handler.postDelayed({ if (!isDestroyed) load(true) }, 700)

    private fun currentZoom(): ZoomImageView? {
        val rv = b.pager.getChildAt(0) as? RecyclerView ?: return null
        val vh = rv.findViewHolderForAdapterPosition(b.pager.currentItem) as? PagerAdapter.VH ?: return null
        return vh.b.zoom
    }

    private fun cur(): MediaItem? = items.getOrNull(b.pager.currentItem)

    /** Video: tanpa pensil, Slideshow, Rotasi, Pilih sebagai, Perbaiki tanggal, dan Tampilkan di peta. */
    private fun updateMenu(video: Boolean) {
        listOf(
            R.id.action_edit, R.id.action_slideshow, R.id.action_rotate,
            R.id.action_set_as, R.id.action_fix_date, R.id.action_show_map
        ).forEach { id -> b.toolbar.menu.findItem(id)?.isVisible = !video }
        // ikon putar layar (landscape <-> potret) hanya untuk video, di posisi ikon pensil
        b.toolbar.menu.findItem(R.id.action_orient)?.isVisible = video
        if (!video && requestedOrientation != android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun anchor(id: Int): View = b.toolbar.findViewById<View>(id) ?: b.toolbar

    private fun updateInfo(pos: Int) {
        val it = items.getOrNull(pos) ?: return
        updateMenu(it.isVideo)
        b.toolbar.title = it.name
        val res = if (it.width > 0) "${it.width}x${it.height}" else ""
        val date = if (it.dateTaken > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.dateTaken)) else ""
        b.infoText.text = listOf(res, date).filter { s -> s.isNotEmpty() }.joinToString("\n")
        b.infoBar.visibility = if (Prefs.infoBar && barsVisible) View.VISIBLE else View.GONE
    }

    private fun onTap() {
        if (playing) stopSlideshow() else setBars(!barsVisible)
    }

    private fun setBars(show: Boolean) {
        barsVisible = show
        val c = WindowCompat.getInsetsController(window, b.root)
        if (show) c.show(WindowInsetsCompat.Type.systemBars()) else {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        }
        fade(b.appBar, show)
        fade(b.infoBar, show && Prefs.infoBar)
    }

    private fun fade(v: View, show: Boolean) {
        if (show) {
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(150).start()
        } else {
            v.animate().alpha(0f).setDuration(150).withEndAction { v.visibility = View.INVISIBLE }.start()
        }
    }

    private fun startSlideshow() {
        playing = true
        setBars(false)
        if (Prefs.slideFade) b.pager.setPageTransformer { page, pos -> page.alpha = 1f - abs(pos) }
        handler.postDelayed(tick, Prefs.slideInterval * 1000L)
    }

    private fun stopSlideshow() {
        playing = false
        handler.removeCallbacks(tick)
        b.pager.setPageTransformer(null)
        setBars(true)
    }

    override fun onStop() {
        super.onStop()
        if (playing) stopSlideshow()
    }

    private fun playVideo(it: MediaItem) {
        startActivity(Intent(this, VideoActivity::class.java).setData(it.uri).putExtra("name", it.name))
    }

    private fun onMenu(id: Int): Boolean {
        val it = cur() ?: return true
        when (id) {
            R.id.action_edit -> if (it.isVideo) toast(R.string.images_only) else EditorActivity.start(this, it)
            R.id.action_delete -> ops.confirmDelete(anchor(R.id.action_delete), listOf(it)) {
                val p = b.pager.currentItem
                items.removeAt(p)
                if (items.isEmpty()) finish() else {
                    adapter.notifyItemRemoved(p)
                    updateInfo(b.pager.currentItem)
                }
            }
            R.id.action_share -> ops.share(listOf(it), anchor(R.id.action_share))
            R.id.action_orient -> {
                val land = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                requestedOrientation = if (land) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            R.id.action_slideshow -> startSlideshow()
            R.id.rot_left -> ops.rotate(listOf(it), 270) { reloadSoon() }
            R.id.rot_right -> ops.rotate(listOf(it), 90) { reloadSoon() }
            R.id.rot_180 -> ops.rotate(listOf(it), 180) { reloadSoon() }
            R.id.action_set_as -> ops.setAs(it, b.toolbar)
            R.id.action_move -> ops.transfer(listOf(it), true) { reloadSoon() }
            R.id.action_copy -> ops.transfer(listOf(it), false) { }
            R.id.action_fix_date -> ops.fixDate(listOf(it)) { reloadSoon() }
            R.id.action_show_map -> ops.locate(it)
            R.id.action_rename -> inputDialog(R.string.rename, it.name.substringBeforeLast('.')) { n ->
                ops.rename(it, n) { full ->
                    val p = items.indexOfFirst { m -> m.uri == it.uri }
                    if (p >= 0) {
                        items[p] = items[p].copy(name = full)
                        updateInfo(b.pager.currentItem)
                    }
                }
            }
            R.id.action_details -> ops.details(listOf(it))
        }
        return true
    }

    private class PagerAdapter(
        private val items: List<MediaItem>,
        private val onTap: () -> Unit,
        private val onPlay: (MediaItem) -> Unit
    ) : RecyclerView.Adapter<PagerAdapter.VH>() {

        class VH(val b: ItemPagerBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemPagerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val it = items[position]
            val dm = h.b.root.resources.displayMetrics
            val max = minOf(maxOf(dm.widthPixels, dm.heightPixels) * 2, 4096)
            h.b.zoom.onSingleTap = onTap
            Glide.with(h.b.zoom).load(it.uri).signature(ObjectKey("${it.modified}_${it.size}")).override(max)
                .downsample(DownsampleStrategy.AT_MOST).fitCenter().dontAnimate().into(h.b.zoom)
            h.b.play.visibility = if (it.isVideo) View.VISIBLE else View.GONE
            h.b.play.setOnClickListener { _ -> onPlay(it) }
        }

        override fun onViewRecycled(h: VH) {
            h.b.zoom.reset()
            Glide.with(h.b.zoom).clear(h.b.zoom)
        }
    }
}
