package com.nex.pic

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore.MediaColumns
import android.provider.OpenableColumns
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val bucketId: Long,
    val bucket: String,
    val added: Long,
    val modified: Long,
    val taken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val duration: Long,
    val path: String
) {
    val dateModified: Long get() = modified * 1000L
    val dateTaken: Long get() = if (taken > 0) taken else modified * 1000L
}

/** BUCKET_ID adalah hash 32-bit bertanda: bisa NEGATIF. Jangan pakai angka negatif sebagai "tidak ada filter". */
const val NO_BUCKET = Long.MIN_VALUE

data class Album(val id: Long, val name: String, val count: Int, val cover: MediaItem, val latest: Long, val path: String)
data class Moment(val from: Long, val to: Long, val label: String, val count: Int, val cover: MediaItem)

object MediaRepo {

    private val PROJ = arrayOf(
        MediaColumns._ID, MediaColumns.DISPLAY_NAME, FileColumns.MEDIA_TYPE, MediaColumns.MIME_TYPE,
        MediaColumns.BUCKET_ID, MediaColumns.BUCKET_DISPLAY_NAME, MediaColumns.DATE_ADDED,
        MediaColumns.DATE_MODIFIED, MediaColumns.DATE_TAKEN, MediaColumns.SIZE, MediaColumns.WIDTH,
        MediaColumns.HEIGHT, MediaColumns.DURATION, MediaColumns.RELATIVE_PATH
    )

    fun load(ctx: Context, bucketId: Long? = null, from: Long = 0L, to: Long = Long.MAX_VALUE): List<MediaItem> {
        val types = if (Prefs.includeVideos)
            "${FileColumns.MEDIA_TYPE}=${FileColumns.MEDIA_TYPE_IMAGE} OR ${FileColumns.MEDIA_TYPE}=${FileColumns.MEDIA_TYPE_VIDEO}"
        else "${FileColumns.MEDIA_TYPE}=${FileColumns.MEDIA_TYPE_IMAGE}"
        var sel = "($types) AND ${MediaColumns.SIZE}>0"
        val args = ArrayList<String>()
        if (bucketId != null) {
            sel += " AND ${MediaColumns.BUCKET_ID}=?"
            args.add(bucketId.toString())
        }
        val out = ArrayList<MediaItem>()
        val excluded = Prefs.excludedIds
        try {
            ctx.contentResolver.query(
                MediaStore.Files.getContentUri("external"), PROJ, sel,
                if (args.isEmpty()) null else args.toTypedArray(), null
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaColumns.DISPLAY_NAME)
                val iType = c.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
                val iMime = c.getColumnIndexOrThrow(MediaColumns.MIME_TYPE)
                val iBid = c.getColumnIndexOrThrow(MediaColumns.BUCKET_ID)
                val iBn = c.getColumnIndexOrThrow(MediaColumns.BUCKET_DISPLAY_NAME)
                val iAdd = c.getColumnIndexOrThrow(MediaColumns.DATE_ADDED)
                val iMod = c.getColumnIndexOrThrow(MediaColumns.DATE_MODIFIED)
                val iTak = c.getColumnIndexOrThrow(MediaColumns.DATE_TAKEN)
                val iSize = c.getColumnIndexOrThrow(MediaColumns.SIZE)
                val iW = c.getColumnIndexOrThrow(MediaColumns.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaColumns.HEIGHT)
                val iDur = c.getColumnIndexOrThrow(MediaColumns.DURATION)
                val iPath = c.getColumnIndexOrThrow(MediaColumns.RELATIVE_PATH)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val video = c.getInt(iType) == FileColumns.MEDIA_TYPE_VIDEO
                    val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val item = MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        name = c.getString(iName) ?: "",
                        mime = c.getString(iMime) ?: "",
                        isVideo = video,
                        bucketId = c.getLong(iBid),
                        bucket = c.getString(iBn) ?: "",
                        added = c.getLong(iAdd),
                        modified = c.getLong(iMod),
                        taken = c.getLong(iTak),
                        size = c.getLong(iSize),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        duration = c.getLong(iDur),
                        path = c.getString(iPath) ?: ""
                    )
                    val d = item.dateTaken
                    if (item.bucketId in excluded) continue
                    if (d >= from && d < to) out.add(item)
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun fromUri(ctx: Context, uri: Uri): MediaItem {
        val mime = ctx.contentResolver.getType(uri) ?: "image/*"
        var name = uri.lastPathSegment ?: "media"
        var size = 0L
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0) ?: name
                    size = it.getLong(1)
                }
            }
        } catch (e: Exception) {
        }
        return MediaItem(-1, uri, name, mime, mime.startsWith("video"), 0, "", 0, 0, 0, size, 0, 0, 0, "")
    }

    fun natural(a: String, b: String): Int {
        if (!Prefs.numericSort) return a.compareTo(b, ignoreCase = true)
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei
                j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    fun sortMedia(l: List<MediaItem>, key: String): List<MediaItem> {
        val parts = key.split("_")
        val k = parts.getOrElse(0) { "date" }
        val desc = parts.getOrElse(1) { "desc" } == "desc"
        val cmp: Comparator<MediaItem> = when (k) {
            "name" -> Comparator { a, b -> natural(a.name, b.name) }
            "taken" -> Comparator { a, b -> a.dateTaken.compareTo(b.dateTaken) }
            "size" -> Comparator { a, b -> a.size.compareTo(b.size) }
            else -> Comparator { a, b -> a.dateModified.compareTo(b.dateModified) }
        }
        return l.sortedWith(if (desc) cmp.reversed() else cmp)
    }

    fun albumLabel(ctx: Context, m: MediaItem): String =
        m.bucket.ifBlank {
            m.path.trimEnd('/').substringAfterLast('/').ifBlank { ctx.getString(R.string.storage_root) }
        }

    fun albums(ctx: Context, items: List<MediaItem>, sortKey: String, query: String, showHidden: Boolean): List<Album> {
        val hidden = Prefs.hiddenIds
        var list = items.groupBy { it.bucketId }
            .filter { showHidden || it.key !in hidden }
            .map { (id, v) ->
                val cover = v.maxByOrNull { it.dateModified } ?: v.first()
                Album(id, albumLabel(ctx, v.first()), v.size, cover, cover.dateModified, v.first().path)
            }
        if (query.isNotBlank()) list = list.filter { it.name.contains(query, ignoreCase = true) }
        val parts = sortKey.split("_")
        val desc = parts.getOrElse(1) { "asc" } == "desc"
        val cmp: Comparator<Album> = when (parts.getOrElse(0) { "name" }) {
            "date" -> Comparator { a, b -> a.latest.compareTo(b.latest) }
            "count" -> Comparator { a, b -> a.count.compareTo(b.count) }
            else -> Comparator { a, b -> natural(a.name, b.name) }
        }
        val sorted = list.sortedWith(if (desc) cmp.reversed() else cmp)
        val pinned = Prefs.pinnedIds
        return if (pinned.isEmpty()) sorted else sorted.filter { it.id in pinned } + sorted.filter { it.id !in pinned }
    }

    /** Daftar folder tujuan untuk Salin ke / Pindahkan ke. */
    fun folders(ctx: Context, items: List<MediaItem>): List<Album> =
        albums(ctx, items, "name_asc", "", true).filter { it.path.isNotBlank() }

    fun moments(items: List<MediaItem>): List<Moment> {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val groups = LinkedHashMap<Long, MutableList<MediaItem>>()
        for (m in items) {
            cal.timeInMillis = m.dateTaken
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            groups.getOrPut(cal.timeInMillis) { ArrayList() }.add(m)
        }
        return groups.map { (start, v) ->
            cal.timeInMillis = start
            cal.add(Calendar.MONTH, 1)
            val end = cal.timeInMillis
            val label = fmt.format(Date(start)).replaceFirstChar { it.uppercase() }
            Moment(start, end, label, v.size, v.maxByOrNull { it.dateTaken } ?: v.first())
        }.sortedByDescending { it.from }
    }
}
