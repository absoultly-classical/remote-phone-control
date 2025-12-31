/* 
注意：这只是核心逻辑示意代码。
在 Android 项目中，你需要依赖以下库：
- org.webrtc:google-webrtc:1.0.+
- io.socket:socket.io-client:2.0.0
*/

package com.example.remotecontrol

import android.media.projection.MediaProjection
import org.webrtc.*
import com.example.remotecontrol.service.RemoteControlAccessibilityService
import io.socket.client.Socket

class ScreenStreamer(
    private val mediaProjectionData: Intent,
    private val peerConnectionFactory: PeerConnectionFactory
) {
    private var localVideoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: VideoCapturer? = null

    fun startStreaming(pc: PeerConnection) {
        val helper = SurfaceTextureHelper.create("ScreenCaptureThread", EglBase.create().eglBaseContext)
        
        // 使用 Intent 正确初始化屏幕捕捉器
        capturer = ScreenCapturerAndroid(mediaProjectionData, object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                android.util.Log.d("RemoteControl", "MediaProjection stopped")
            }
        })
        
        videoSource = peerConnectionFactory.createVideoSource(capturer!!.isScreencast)
        capturer?.initialize(helper, null, videoSource?.capturerObserver)
        capturer?.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory.createVideoTrack("VIDEO_TRACK", videoSource)
        
        // 将轨道添加到 PeerConnection
        pc.addTrack(localVideoTrack, listOf("STREAM"))
    }

    fun stop() {
        capturer?.stopCapture()
        capturer?.dispose()
        videoSource?.dispose()
        localVideoTrack?.dispose()
    }
}

// 核心：处理控制消息并调用无障碍服务
class RemoteControlManager(val socket: io.socket.client.Socket) {
    init {
        socket.on("signal") { args: Array<Any> ->
            val data = args[0] as org.json.JSONObject
            val type = data.getString("type")
            
            if (type == "control") {
                val payload = data.getJSONObject("payload")
                handleControlMessage(payload)
            }
            // 处理 RTC 信令 (answer, candidate) ...
        }
    }

    private fun handleControlMessage(payload: org.json.JSONObject) {
        val service = RemoteControlAccessibilityService.instance
        if (service == null) {
            android.util.Log.w("RemoteControl", "Accessibility Service not running!")
            return
        }

        val action = payload.getString("action")
        when (action) {
            "click" -> {
                val x = payload.getDouble("x").toFloat()
                val y = payload.getDouble("y").toFloat()
                service.performClick(x, y)
            }
            "home" -> service.performAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            "back" -> service.performAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            "recents" -> service.performAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
        }
    }
}
