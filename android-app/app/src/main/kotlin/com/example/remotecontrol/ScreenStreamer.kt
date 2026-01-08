/* 
注意：这只是核心逻辑示意代码。
在 Android 项目中，你需要依赖以下库：
- org.webrtc:google-webrtc:1.0.+
- io.socket:socket.io-client:2.0.0
*/

package com.example.remotecontrol

import android.content.Context
import android.media.projection.MediaProjection
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import org.webrtc.*
import com.example.remotecontrol.service.RemoteControlAccessibilityService
import io.socket.client.Socket
import java.io.ByteArrayOutputStream

class ScreenStreamer(
    private val context: Context,
    private val mediaProjectionData: Intent,
    private val peerConnectionFactory: PeerConnectionFactory,
    private val eglContext: EglBase.Context
) {
    private var localVideoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    
    // WebSocket Streaming
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    fun startStreaming(pc: PeerConnection?) {
        FileLogger.writeLine("ScreenStreamer.startStreaming called, withPeerConnection=${pc != null}")
        
        surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglContext)
        
        // 使用 Intent 正确初始化屏幕捕捉器
        capturer = ScreenCapturerAndroid(mediaProjectionData, object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                android.util.Log.d("RemoteControl", "MediaProjection stopped")
                FileLogger.writeLine("MediaProjection stopped by system")
            }
        })
        
        videoSource = peerConnectionFactory.createVideoSource(capturer!!.isScreencast)
        capturer?.initialize(surfaceTextureHelper, context.applicationContext, videoSource?.capturerObserver)
        try {
            // 设置一个更通用的分辨率，或者尝试根据屏幕动态获取（这里暂设为 720p 兼容性更好）
            capturer?.startCapture(1280, 720, 30)
            FileLogger.writeLine("Screen capture started at 1280x720@30fps")
        } catch (e: Exception) {
            FileLogger.writeLine("startCapture exception: ${e.message}")
        }

        localVideoTrack = peerConnectionFactory.createVideoTrack("VIDEO_TRACK", videoSource)
        
        // 将轨道添加到 PeerConnection
        pc?.addTrack(localVideoTrack, listOf("STREAM"))
    }

    // 给本机预览使用
    fun attachPreview(renderer: VideoSink) {
        localVideoTrack?.addSink(renderer)
    }

    fun startWebSocketStreaming(socket: Socket, roomId: String) {
        FileLogger.writeLine("startWebSocketStreaming called")
        val metrics = context.resources.displayMetrics
        val width = 480 // 降低分辨率以保证流畅度
        val height = (width * (metrics.heightPixels.toFloat() / metrics.widthPixels)).toInt()
        
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        backgroundThread = HandlerThread("ImageReaderThread")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread!!.looper)

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        // 注意：这里需要从 MainActivity 传入已经授权的 MediaProjection 实例，或者重新获取
        // 为了简化，我们假设 MainActivity 会调用这个方法并传入必要的对象
    }

    fun stop() {
        try {
            capturer?.stopCapture()
            FileLogger.writeLine("Screen capture stopped")
        } catch (_: Exception) {
        }
        capturer?.dispose()
        videoSource?.dispose()
        localVideoTrack?.dispose()
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        
        virtualDisplay?.release()
        imageReader?.close()
        backgroundThread?.quitSafely()
    }
}

// 核心：处理控制消息并调用无障碍服务
class RemoteControlManager(val socket: io.socket.client.Socket) {
    init {
        socket.on("signal") { args: Array<Any> ->
            val data = args[0] as? org.json.JSONObject ?: return@on
            val type = data.optString("type")
            
            if (type == "control") {
                val payload = data.optJSONObject("payload") ?: return@on
                handleControlMessage(payload)
            }
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
            "swipe" -> {
                val x1 = payload.getDouble("x1").toFloat()
                val y1 = payload.getDouble("y1").toFloat()
                val x2 = payload.getDouble("x2").toFloat()
                val y2 = payload.getDouble("y2").toFloat()
                val duration = payload.optLong("duration", 300L)
                service.performSwipe(x1, y1, x2, y2, duration)
            }
        }
    }
}
