package com.nex.pic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.nex.pic.databinding.ItemTileBinding

data class Tile(val key: Long, val title: String?, val sub: String?, val item: MediaItem)

class TileAdapter(
    private val onClick: (Int, Tile) -> Unit,
    private val onLong: ((Int, Tile) -> Unit)? = null
) : RecyclerView.Adapter<TileAdapter.VH>() {

    class VH(val b: ItemTileBinding) : RecyclerView.ViewHolder(b.root)

    var tiles: List<Tile> = emptyList()
        set(v) {
            field = v
            notifyDataSetChanged()
        }

    var selected: Set<Long> = emptySet()
        set(v) {
            field = v
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = tiles.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val t = tiles[position]
        val b = h.b
        val req = Glide.with(b.img).load(t.item.uri).signature(ObjectKey("${t.item.modified}_${t.item.size}")).override(320)
        if (Prefs.squareThumbs) req.centerCrop().into(b.img) else req.fitCenter().into(b.img)
        b.labels.visibility = if (t.title == null) View.GONE else View.VISIBLE
        b.title.text = t.title
        b.sub.text = t.sub
        b.sub.visibility = if (t.sub == null) View.GONE else View.VISIBLE
        b.play.visibility = if (t.item.isVideo) View.VISIBLE else View.GONE
        val sel = t.key in selected
        b.selOverlay.visibility = if (sel) View.VISIBLE else View.GONE
        b.check.visibility = if (sel) View.VISIBLE else View.GONE
        b.root.setOnClickListener { onClick(h.bindingAdapterPosition, t) }
        b.root.setOnLongClickListener {
            onLong?.invoke(h.bindingAdapterPosition, t)
            onLong != null
        }
    }

    override fun onViewRecycled(h: VH) {
        Glide.with(h.b.img).clear(h.b.img)
    }
}
