package com.nex.pic

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.MaterialColors

/** Popup kecil (daftar baris ikon + teks) untuk konfirmasi hapus, berbagi, dan Pilih sebagai. */
object PopupPanel {

    class Row(val icon: Drawable?, val label: String, val color: Int? = null, val onClick: () -> Unit)

    fun tinted(act: Activity, res: Int, color: Int): Drawable? {
        val d = ContextCompat.getDrawable(act, res)?.mutate() ?: return null
        val w = DrawableCompat.wrap(d)
        DrawableCompat.setTint(w, color)
        return w
    }

    /**
     * @param anchor tampil tepat di bawah view ini, rata kanan
     * @param topEnd tampil di pojok kanan atas layar (dipakai bila aksi berasal dari menu titik tiga)
     */
    fun show(act: Activity, anchor: View?, rows: List<Row>, dark: Boolean = false, topEnd: Boolean = false) {
        if (rows.isEmpty()) return
        val d = act.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val bgColor = if (dark) 0xFF1F2330.toInt()
        else MaterialColors.getColor(act, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFEEF2FB.toInt())
        val textColor = if (dark) Color.WHITE
        else MaterialColors.getColor(act, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)

        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val pw = PopupWindow(box, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pw.setBackgroundDrawable(GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(16).toFloat()
        })
        pw.elevation = dp(10).toFloat()
        pw.isOutsideTouchable = true

        val tv = TypedValue()
        act.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        rows.forEach { r ->
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                minimumWidth = dp(210)
                setPadding(dp(18), 0, dp(24), 0)
                setBackgroundResource(tv.resourceId)
                setOnClickListener {
                    pw.dismiss()
                    r.onClick()
                }
            }
            if (r.icon != null) {
                row.addView(ImageView(act).apply { setImageDrawable(r.icon) }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(16) })
            }
            row.addView(TextView(act).apply {
                text = r.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(r.color ?: textColor)
            })
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        if (anchor == null || topEnd) {
            val root = (anchor ?: act.window.decorView).rootView
            val top = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
            pw.showAtLocation(root, Gravity.TOP or Gravity.END, dp(8), top + dp(8))
        } else {
            pw.showAsDropDown(anchor, 0, 0, Gravity.END)
        }
    }
}
