package com.nex.pic

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.min

/**
 * - Ketuk dua kali: zoom ke titik ketukan, ketuk dua kali lagi: kembali 100% (muat layar).
 * - Cubit dua jari: zoom in / zoom out.
 * - Geser saat ter-zoom; di tepi gambar, geseran diteruskan ke ViewPager (ganti gambar).
 */
class ZoomImageView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(ctx, attrs) {

    var onSingleTap: (() -> Unit)? = null

    private val mx = Matrix()
    private val vals = FloatArray(9)
    private val rect = RectF()
    private var minScale = 1f
    private val maxScale get() = minScale * 8f
    private var anim: ValueAnimator? = null

    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            anim?.cancel()
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onScale(d: ScaleGestureDetector): Boolean {
            val cur = curScale()
            val target = (cur * d.scaleFactor).coerceIn(minScale, maxScale)
            val f = target / cur
            mx.postScale(f, f, d.focusX, d.focusY)
            fixTrans()
            imageMatrix = mx
            return true
        }
    })

    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (Prefs.tapSwap) toggleZoom(e.x, e.y) else onSingleTap?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (Prefs.tapSwap) onSingleTap?.invoke() else toggleZoom(e.x, e.y)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scaleDetector.isInProgress || !isZoomed()) return false
            val before = transX()
            mx.postTranslate(-dx, -dy)
            fixTrans()
            imageMatrix = mx
            val atEdge = abs(dx) > abs(dy) && abs(transX() - before) < 0.5f
            parent?.requestDisallowInterceptTouchEvent(!atEdge)
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        fit()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        fit()
    }

    private fun toggleZoom(x: Float, y: Float) {
        val target = if (isZoomed()) minScale else (minScale * Prefs.doubleTapZoom).coerceAtMost(maxScale)
        animateTo(target, x, y)
    }

    /** Dipakai tombol perbesar / perkecil. */
    fun zoomBy(factor: Float) {
        if (drawable == null) return
        animateTo((curScale() * factor).coerceIn(minScale, maxScale), width / 2f, height / 2f)
    }

    fun reset() {
        anim?.cancel()
        fit()
    }

    private fun fit() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0) return
        val s = min(width / dw, height / dh)
        minScale = s
        mx.reset()
        mx.postScale(s, s)
        mx.postTranslate((width - dw * s) / 2f, (height - dh * s) / 2f)
        imageMatrix = mx
    }

    private fun curScale(): Float {
        mx.getValues(vals)
        return vals[Matrix.MSCALE_X]
    }

    private fun transX(): Float {
        mx.getValues(vals)
        return vals[Matrix.MTRANS_X]
    }

    private fun isZoomed() = curScale() > minScale * 1.02f

    private fun animateTo(target: Float, fx: Float, fy: Float) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(curScale(), target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = (it.animatedValue as Float) / curScale()
                mx.postScale(f, f, fx, fy)
                fixTrans()
                imageMatrix = mx
            }
            start()
        }
    }

    private fun fixTrans() {
        val d = drawable ?: return
        rect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        mx.mapRect(rect)
        var dx = 0f
        var dy = 0f
        if (rect.width() <= width) dx = (width - rect.width()) / 2f - rect.left
        else if (rect.left > 0) dx = -rect.left
        else if (rect.right < width) dx = width - rect.right
        if (rect.height() <= height) dy = (height - rect.height()) / 2f - rect.top
        else if (rect.top > 0) dy = -rect.top
        else if (rect.bottom < height) dy = height - rect.bottom
        mx.postTranslate(dx, dy)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (drawable == null) return false
        if (e.actionMasked == MotionEvent.ACTION_DOWN && isZoomed()) parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(e)
        gd.onTouchEvent(e)
        return true
    }
}
