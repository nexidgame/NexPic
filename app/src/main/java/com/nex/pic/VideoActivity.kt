package com.nex.pic

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

class VideoActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val vv = VideoView(this)
        root.addView(
            vv,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        )
        setContentView(root)
        val mc = MediaController(this)
        mc.setAnchorView(vv)
        vv.setMediaController(mc)
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        vv.setVideoURI(uri)
        vv.setOnPreparedListener { vv.start() }
        vv.setOnErrorListener { _, _, _ ->
            toast(R.string.error)
            true
        }
    }
}
