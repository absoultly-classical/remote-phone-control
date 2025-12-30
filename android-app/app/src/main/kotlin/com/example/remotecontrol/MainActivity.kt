package com.example.remotecontrol

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val btn = Button(this).apply { text = "Start Remote Control" }
        setContentView(btn)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        btn.setOnClickListener {
            // 请求录屏权限
            startActivityForResult(projectionManager.createScreenCaptureIntent(), 100)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            // 在这里启动 Socket.io 连接和 WebRTC 传输逻辑
            // val mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            // ... 初始化 ScreenStreamer 并连接到信令服务器
        }
    }
}
