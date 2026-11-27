package com.nex.pic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Kotak potong dengan rasio TETAP (rasio layar / layar lebar). Geser untuk memindah, tarik sudut untuk mengubah ukuran. */
class WallpaperCropView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var bmp: Bitmap? = null
    private var aspect = 0.5f // lebar / tinggi
    private val img = RectF()
    private val crop = RectF()
    private val d = resources.displayMetrics.density
    private val dim = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.5f * d }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1f * d }
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f * d; strokeCap = Paint.Cap.SQUARE
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val slop = 34 * d
    private val minW = 72 * d

    private var laidKey = ""
    private var moving = false
    private var resizing = false
    private var ax = 0f // sudut jangkar (sudut berlawanan) saat mengubah ukuran
    private var ay = 0f
    private var dirX = 1f
    private var dirY = 1f
    private var lx = 0f
    private var ly = 0f

    fun setBitmap(b: Bitmap, aspect: Float) {
        bmp = b
        this.aspect = aspect
        laidKey = ""
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        laidKey = ""
    }

    private fun layoutImage() {
        val b = bmp ?: return
        val l = paddingLeft + 16 * d
        val t = paddingTop + 16 * d
        val aw = width - paddingRight - 16 * d - l
        val ah = height - paddingBottom - 16 * d - t
        if (aw <= 0 || ah <= 0) return
        val s = min(aw / b.width, ah / b.height)
        val w = b.width * s
        val h = b.height * s
        val x = l + (aw - w) / 2
        val y = t + (ah - h) / 2
        img.set(x, y, x + w, y + h)
        // kotak terbesar dengan rasio tetap, di tengah gambar
        var cw = img.width()
        var ch = cw / aspect
        if (ch > img.height()) {
            ch = img.height()
            cw = ch * aspect
        }
        crop.set(img.centerX() - cw / 2, img.centerY() - ch / 2, img.centerX() + cw / 2, img.centerY() + ch / 2)
    }

    /** Hasil potongan dalam koordinat bitmap. */
    fun crop(): Bitmap? {
        val b = bmp ?: return null
        if (img.width() <= 0) return b
        val s = b.width / img.width()
        val x = ((crop.left - img.left) * s).toInt().coerceIn(0, b.width - 1)
        val y = ((crop.top - img.top) * s).toInt().coerceIn(0, b.height - 1)
        val w = (crop.width() * s).toInt().coerceIn(1, b.width - x)
        val h = (crop.height() * s).toInt().coerceIn(1, b.height - y)
        return Bitmap.createBitmap(b, x, y, w, h)
    }

    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        val k = "$width,$height,$paddingLeft,$paddingTop,$paddingRight,$paddingBottom"
        if (k != laidKey) {
            laidKey = k
            layoutImage()
        }
        c.drawBitmap(b, null, img, null)
        c.save()
        c.clipOutRect(crop)
        c.drawRect(img, dim)
        c.restore()
        c.drawRect(crop, line)
        val w3 = crop.width() / 3
        val h3 = crop.height() / 3
        for (i in 1..2) {
            c.drawLine(crop.left + w3 * i, crop.top, crop.left + w3 * i, crop.bottom, grid)
            c.drawLine(crop.left, crop.top + h3 * i, crop.right, crop.top + h3 * i, grid)
        }
        for (i in 1..2) for (j in 1..2) c.drawCircle(crop.left + w3 * i, crop.top + h3 * j, 4 * d, dot)
        val len = 18 * d
        val l = crop.left; val t = crop.top; val r = crop.right; val bt = crop.bottom
        c.drawLine(l, t, l + len, t, corner); c.drawLine(l, t, l, t + len, corner)
        c.drawLine(r, t, r - len, t, corner); c.drawLine(r, t, r, t + len, corner)
        c.drawLine(l, bt, l + len, bt, corner); c.drawLine(l, bt, l, bt - len, corner)
        c.drawLine(r, bt, r - len, bt, corner); c.drawLine(r, bt, r, bt - len, corner)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bmp == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = e.x
                val y = e.y
                moving = false
                resizing = false
                val nearL = abs(x - crop.left) < slop
                val nearR = abs(x - crop.right) < slop
                val nearT = abs(y - crop.top) < slop
                val nearB = abs(y - crop.bottom) < slop
                if ((nearL || nearR) && (nearT || nearB)) {
                    resizing = true
                    dirX = if (nearR) 1f else -1f
                    dirY = if (nearB) 1f else -1f
                    ax = if (nearR) crop.left else crop.right
                    ay = if (nearB) crop.top else crop.bottom
                } else if (crop.contains(x, y)) moving = true
                lx = x
                ly = y
                parent?.requestDisallowInterceptTouchEvent(moving || resizing)
                return moving || resizing
            }
            MotionEvent.ACTION_MOVE -> {
                if (moving) {
                    val dx = e.x - lx
                    val dy = e.y - ly
                    lx = e.x
                    ly = e.y
                    val mx = (crop.left + dx).coerceIn(img.left, img.right - crop.width()) - crop.left
                    val my = (crop.top + dy).coerceIn(img.top, img.bottom - crop.height()) - crop.top
                    crop.offset(mx, my)
                } else if (resizing) {
                    val wx = max(abs(e.x - ax), abs(e.y - ay) * aspect)
                    val maxW = min(
                        if (dirX > 0) img.right - ax else ax - img.left,
                        (if (dirY > 0) img.bottom - ay else ay - img.top) * aspect
                    )
                    val w = wx.coerceIn(min(minW, maxW), maxW)
                    val h = w / aspect
                    val x2 = ax + dirX * w
                    val y2 = ay + dirY * h
                    crop.set(min(ax, x2), min(ay, y2), max(ax, x2), max(ay, y2))
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                moving = false
                resizing = false
            }
        }
        return true
    }
}
