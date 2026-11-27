package com.nex.pic

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class SquareImageView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(ctx, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
