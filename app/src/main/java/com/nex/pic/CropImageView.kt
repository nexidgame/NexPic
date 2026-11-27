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

/** Menampilkan bitmap dan kotak potong yang bisa digeser / ditarik dari tepi dan sudut. */
class CropImageView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var bmp: Bitmap? = null
    private val img = RectF()
    private val crop = RectF()
    private val d = resources.displayMetrics.density
    private val dim = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.5f * d }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1f * d }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val slop = 28 * d
    private val minSize = 48 * d

    private val L = 1
    private val T = 2
    private val R = 4
    private val B = 8
    private val MOVE = 16
    private var mode = 0
    private var laidKey = ""
    private var lx = 0f
    private var ly = 0f

    fun setBitmap(b: Bitmap) {
        bmp = b
        laidKey = ""
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        layoutImage()
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
        crop.set(img)
    }

    /** Hasil potongan dalam koordinat bitmap asli. */
    fun crop(): Bitmap? {
        val b = bmp ?: return null
        if (img.width() <= 0) return b
        val sx = b.width / img.width()
        val x = ((crop.left - img.left) * sx).toInt().coerceIn(0, b.width - 1)
        val y = ((crop.top - img.top) * sx).toInt().coerceIn(0, b.height - 1)
        val w = (crop.width() * sx).toInt().coerceIn(1, b.width - x)
        val h = (crop.height() * sx).toInt().coerceIn(1, b.height - y)
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
        val r = 5 * d
        val xs = floatArrayOf(crop.left, crop.centerX(), crop.right)
        val ys = floatArrayOf(crop.top, crop.centerY(), crop.bottom)
        for (xi in xs.indices) for (yi in ys.indices) if (!(xi == 1 && yi == 1)) c.drawCircle(xs[xi], ys[yi], r, dot)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bmp == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = e.x
                val y = e.y
                val inX = x > crop.left - slop && x < crop.right + slop
                val inY = y > crop.top - slop && y < crop.bottom + slop
                var f = 0
                if (inY && abs(x - crop.left) < slop) f = f or L
                if (inY && abs(x - crop.right) < slop) f = f or R
                if (inX && abs(y - crop.top) < slop) f = f or T
                if (inX && abs(y - crop.bottom) < slop) f = f or B
                if (f == 0 && crop.contains(x, y)) f = MOVE
                mode = f
                lx = x
                ly = y
                parent?.requestDisallowInterceptTouchEvent(f != 0)
                return f != 0
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lx
                val dy = e.y - ly
                lx = e.x
                ly = e.y
                if (mode == MOVE) {
                    val mx = (crop.left + dx).coerceIn(img.left, img.right - crop.width()) - crop.left
                    val my = (crop.top + dy).coerceIn(img.top, img.bottom - crop.height()) - crop.top
                    crop.offset(mx, my)
                } else {
                    if (mode and L != 0) crop.left = e.x.coerceIn(img.left, crop.right - minSize)
                    if (mode and R != 0) crop.right = e.x.coerceIn(crop.left + minSize, img.right)
                    if (mode and T != 0) crop.top = e.y.coerceIn(img.top, crop.bottom - minSize)
                    if (mode and B != 0) crop.bottom = e.y.coerceIn(crop.top + minSize, img.bottom)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = 0
        }
        return true
    }
}
