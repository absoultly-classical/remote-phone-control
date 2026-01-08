package com.example.remotecontrol

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

import android.media.projection.MediaProjection
import org.webrtc.*

class MainActivity : AppCompatActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var mSocket: Socket
    private lateinit var statusText: TextView
    private lateinit var codeText: TextView
    private lateinit var urlEdit: android.widget.EditText
    private lateinit var connectBtn: Button
    private lateinit var deviceIdText: TextView

    private var projectionIntent: Intent? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var screenStreamer: ScreenStreamer? = null
    private var controlManager: RemoteControlManager? = null
    private var currentCode: String? = null
    
    // 如果在收到 request_offer 时还没有录屏授权，先记下 sender，待授权后再发起握手
    private var pendingWebClientId: String? = null

    // 本地预览相关
    private var wantLocalPreview: Boolean = false
    private lateinit var previewRenderer: SurfaceViewRenderer
    private lateinit var rootEglBase: EglBase

    private val PREFS_NAME = "RemoteControlPrefs"
    private val KEY_SERVER_URL = "server_url"
    private val KEY_DEVICE_ID = "device_id"
    private val DEFAULT_URL = "http://192.168.1.100:3000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化文件日志
        FileLogger.init(this)
        FileLogger.writeLine("MainActivity onCreate")

        // 0. Initialize WebRTC
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        // 初始化本地渲染用的 EGL 上下文
        rootEglBase = EglBase.create()

        val deviceId = getOrGenerateDeviceId()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        urlEdit = android.widget.EditText(this).apply {
            hint = "Signal Server Address (URL)"
            setText(getSavedValue(KEY_SERVER_URL, DEFAULT_URL))
        }

        deviceIdText = TextView(this).apply {
            text = "Device ID: $deviceId"
            textSize = 18f
            setTextColor(android.graphics.Color.parseColor("#4caf50"))
            setPadding(0, 10, 0, 30)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        connectBtn = Button(this).apply { text = "Connect to Server" }
        
        codeText = TextView(this).apply {
            text = "Pairing Code: -"
            textSize = 24f
            setPadding(0, 40, 0, 10)
            gravity = android.view.Gravity.CENTER
        }
        
        statusText = TextView(this).apply {
            text = "Status: Disconnected"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
        }

        val startStreamBtn = Button(this).apply { text = "Start Media Projection (with Web)" }
        val localPreviewBtn = Button(this).apply { text = "Local Preview (No Server)" }

        // 创建本地预览渲染器
        previewRenderer = SurfaceViewRenderer(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        previewRenderer.init(rootEglBase.eglBaseContext, null)
        previewRenderer.setEnableHardwareScaler(true)
        previewRenderer.setMirror(false)

        layout.addView(TextView(this).apply { text = "1. Server Address:" })
        layout.addView(urlEdit)
        layout.addView(deviceIdText)
        layout.addView(connectBtn)
        layout.addView(codeText)
        layout.addView(statusText)
        // 预览区域
        layout.addView(TextView(this).apply { text = "2. Local Preview:" })
        layout.addView(previewRenderer)
        layout.addView(android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 40) })
        layout.addView(TextView(this).apply { text = "3. Controls:" })
        layout.addView(startStreamBtn)
        layout.addView(localPreviewBtn)
        setContentView(layout)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        connectBtn.setOnClickListener {
            if (this::mSocket.isInitialized && mSocket.connected()) {
                mSocket.disconnect()
            } else {
                val url = urlEdit.text.toString().trim()
                if (url.isNotEmpty()) {
                    saveValue(KEY_SERVER_URL, url)
                    initSocket(url, deviceId)
                } else {
                    android.widget.Toast.makeText(this, "Please enter Server URL", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 远程控制用的录屏授权（需要 Web 端参与）
        startStreamBtn.setOnClickListener {
            // Android 14 适配：先启动服务，确保应用处于 FGS 状态，再弹出授权框
            startMediaProjectionService()
            startActivityForResult(projectionManager.createScreenCaptureIntent(), 100)
        }

        // 仅本机预览
        localPreviewBtn.setOnClickListener {
            startMediaProjectionService()
            if (projectionIntent == null) {
                wantLocalPreview = true
                startActivityForResult(projectionManager.createScreenCaptureIntent(), 100)
            } else {
                startLocalPreview()
            }
        }
    }

    private fun getOrGenerateDeviceId(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val random = java.util.Random()
            val suffix = (1..4).map { chars[random.nextInt(chars.length)] }.joinToString("")
            id = "RC-$suffix"
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    private fun getSavedValue(key: String, default: String): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(key, default) ?: default
    }

    private fun saveValue(key: String, value: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
    }

    private fun initSocket(serverUrl: String, deviceId: String) {
        try {
            FileLogger.writeLine("initSocket called, serverUrl=$serverUrl, deviceId=$deviceId")
            var finalUrl = serverUrl
            if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                finalUrl = "http://$finalUrl"
            }

            FileLogger.writeLine("Connecting to signaling server: $finalUrl")
            statusText.text = "Status: Connecting to $finalUrl..."
            connectBtn.isEnabled = false // 防止重复点击
            
            val opts = IO.Options()
            opts.auth = mapOf("token" to deviceId) 
            mSocket = IO.socket(finalUrl, opts)

            mSocket.on(Socket.EVENT_CONNECT) {
                FileLogger.writeLine("Socket EVENT_CONNECT")
                runOnUiThread { 
                    statusText.text = "Status: Server Connected"
                    connectBtn.text = "Disconnect"
                    connectBtn.isEnabled = true
                }
                mSocket.emit("create_code", io.socket.client.Ack { args: Array<Any> ->
                    if (args.isNotEmpty()) {
                        val data = args[0] as? JSONObject
                        val code = data?.optString("code", "-") ?: "-"
                        currentCode = code
                        FileLogger.writeLine("Received pairing code from server: $code")
                        runOnUiThread { codeText.text = "Pairing Code: $code" }
                    }
                })
            }

            mSocket.on("signal") { args: Array<Any> ->
                val data = args[0] as? JSONObject ?: return@on
                val type = data.optString("type")
                val sender = data.optString("sender")
                FileLogger.writeLine("Received signal: type=$type, sender=$sender")

                when (type) {
                    "request_offer" -> {
                        FileLogger.writeLine("Handling request_offer from sender=$sender")
                        runOnUiThread { statusText.text = "Status: Pairing..." }
                        // 如果还没有录屏授权，先记录下来，等用户点“Start Media Projection”授权后自动发起握手
                        if (projectionIntent == null) {
                            pendingWebClientId = sender
                            FileLogger.writeLine("projectionIntent is null, pendingWebClientId set, wait for user to grant screen capture")
                        } else {
                            pendingWebClientId = null
                            startWebrtcHandshake(sender)
                        }
                    }
                    "answer" -> {
                        FileLogger.writeLine("Received answer from web client")
                        val payload = data.optJSONObject("payload") ?: return@on
                        val optimizedSdp = preferCodec(payload.optString("sdp"), "H264")
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, optimizedSdp)
                        peerConnection?.setRemoteDescription(object : SdpObserver {
                            override fun onCreateSuccess(desc: SessionDescription?) {}
                            override fun onSetSuccess() { android.util.Log.d("WebRTC", "Remote SDP Set Success") }
                            override fun onCreateFailure(s: String?) { android.util.Log.e("WebRTC", "Create Failure: $s") }
                            override fun onSetFailure(s: String?) { android.util.Log.e("WebRTC", "Remote SDP Set Failed: $s") }
                        }, sdp)
                    }
                    "candidate" -> {
                        FileLogger.writeLine("Received ICE candidate from web client")
                        val payload = data.optJSONObject("payload") ?: return@on
                        val candidate = IceCandidate(
                            payload.optString("sdpMid"),
                            payload.optInt("sdpMLineIndex"),
                            payload.optString("candidate")
                        )
                        if (peerConnection?.remoteDescription != null) {
                            peerConnection?.addIceCandidate(candidate)
                        } else {
                            FileLogger.writeLine("Remote description not set, buffering candidate")
                            // 这里可以增加一个简单的 List 来缓冲，但通常 Android 端作为 Offer 发起方，
                            // 收到 Candidate 时 Remote Description (Answer) 应该已经快到了。
                            // 为了稳妥，我们直接尝试添加，WebRTC 内部也有一定的容错。
                            peerConnection?.addIceCandidate(candidate)
                        }
                    }
                }
            }

            mSocket.on(Socket.EVENT_DISCONNECT) {
                FileLogger.writeLine("Socket EVENT_DISCONNECT")
                runOnUiThread { 
                    statusText.text = "Status: Disconnected"
                    connectBtn.text = "Connect to Server"
                    connectBtn.isEnabled = true
                    codeText.text = "Pairing Code: -"
                }
            }
            
            mSocket.on(Socket.EVENT_CONNECT_ERROR) { args: Array<Any> ->
                val err = if (args.isNotEmpty()) args[0].toString() else "Unknown Error"
                FileLogger.writeLine("Socket EVENT_CONNECT_ERROR: $err")
                runOnUiThread { 
                    statusText.text = "Error: $err"
                    connectBtn.text = "Retry Connect"
                    connectBtn.isEnabled = true
                }
                android.util.Log.e("RemoteControl", "Connect Error: $err")
            }
            
            mSocket.connect()
            FileLogger.writeLine("Socket connect() invoked")
            controlManager = RemoteControlManager(mSocket)

        } catch (e: Exception) {
            FileLogger.writeLine("initSocket exception: ${e.message}")
            runOnUiThread { 
                statusText.text = "Error: ${e.message}"
                connectBtn.isEnabled = true
            }
            e.printStackTrace()
        }
    }

    private fun startWebrtcHandshake(webClientId: String) {
        FileLogger.writeLine("startWebrtcHandshake called, webClientId=$webClientId")
        if (projectionIntent == null) {
            FileLogger.writeLine("startWebrtcHandshake aborted: projectionIntent is null")
            runOnUiThread { android.widget.Toast.makeText(this, "Please start Media Projection first!", android.widget.Toast.LENGTH_SHORT).show() }
            return
        }

        ensurePeerConnectionFactory()

        // Create PeerConnection with STUN + TURN servers for NAT traversal
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.anyfirewall.com:3478").createIceServer(),
            PeerConnection.IceServer.builder("turn:a.relay.metered.ca:443?transport=tcp")
                .setUsername("e8dd65c92f6067e7e3c2c6e0")
                .setPassword("uWdWNmkhvyqTmFPm")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:a.relay.metered.ca:443?transport=udp")
                .setUsername("e8dd65c92f6067e7e3c2c6e0")
                .setPassword("uWdWNmkhvyqTmFPm")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:a.relay.metered.ca:80?transport=tcp")
                .setUsername("e8dd65c92f6067e7e3c2c6e0")
                .setPassword("uWdWNmkhvyqTmFPm")
                .createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        
        peerConnection = peerConnectionFactory!!.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                val payload = JSONObject().apply {
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("candidate", candidate.sdp)
                }
                mSocket.emit("signal", JSONObject().apply {
                    put("room", currentCode)
                    put("type", "candidate")
                    put("payload", payload)
                })
            }
// ... (other methods should remain as they were, but wait, I need to match carefully)
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            override fun onTrack(transceiver: RtpTransceiver?) {}
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {}
            override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
        })

        // Add Video Track 并附加到本机预览
        FileLogger.writeLine("Creating ScreenStreamer and starting capture (with WebRTC)")
        screenStreamer = ScreenStreamer(this, projectionIntent!!, peerConnectionFactory!!, rootEglBase.eglBaseContext)
        screenStreamer!!.startStreaming(peerConnection!!)
        screenStreamer!!.attachPreview(previewRenderer)

        // Create Offer
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        
        peerConnection!!.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                FileLogger.writeLine("createOffer onCreateSuccess, sdp length=${desc.description.length}")
                
                val optimizedSdp = preferCodec(desc.description, "H264")
                val newDesc = SessionDescription(desc.type, optimizedSdp)
                
                peerConnection!!.setLocalDescription(this, newDesc)
                val payload = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", optimizedSdp)
                }
                mSocket.emit("signal", JSONObject().apply {
                    put("room", currentCode)
                    put("type", "offer")
                    put("payload", payload)
                })
                runOnUiThread { statusText.text = "Status: Streaming..." }
                FileLogger.writeLine("Offer sent to room, enter Streaming state")
            }
            override fun onCreateFailure(s: String?) {
                android.util.Log.e("WebRTC", "Create Offer Failed: $s")
                FileLogger.writeLine("createOffer onCreateFailure: $s")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(s: String?) {
                android.util.Log.e("WebRTC", "Set Local SDP Failed: $s")
                FileLogger.writeLine("setLocalDescription onSetFailure: $s")
            }
        }, mediaConstraints)
    }

    // 仅本机预览使用：不创建 PeerConnection，只采集 + 显示在本地
    private fun startLocalPreview() {
        if (projectionIntent == null) {
            FileLogger.writeLine("startLocalPreview aborted: projectionIntent is null")
            return
        }
        FileLogger.writeLine("startLocalPreview called")
        ensurePeerConnectionFactory()
        screenStreamer = ScreenStreamer(this, projectionIntent!!, peerConnectionFactory!!, rootEglBase.eglBaseContext)
        screenStreamer!!.startStreaming(null)
        screenStreamer!!.attachPreview(previewRenderer)
        runOnUiThread {
            statusText.text = "Status: Local Preview Running"
        }
    }

    private fun preferCodec(sdp: String, codec: String): String {
        val lines = sdp.split("\r\n")
        var videoMLineIndex = -1
        for (i in lines.indices) {
            if (lines[i].startsWith("m=video")) {
                videoMLineIndex = i
                break
            }
        }
        if (videoMLineIndex == -1) return sdp

        val payloadRegex = Regex("a=rtpmap:(\\d+) $codec/90000")
        var payload: String? = null
        for (i in lines.indices) {
            val match = payloadRegex.find(lines[i])
            if (match != null) {
                payload = match.groupValues[1]
                break
            }
        }
        if (payload == null) return sdp

        val elements = lines[videoMLineIndex].split(" ").toMutableList()
        val mLinePayloads = elements.subList(3, elements.size)
        val index = mLinePayloads.indexOf(payload)
        if (index != -1) {
            mLinePayloads.removeAt(index)
            mLinePayloads.add(0, payload)
        }
        val newMLine = elements.subList(0, 3).joinToString(" ") + " " + mLinePayloads.joinToString(" ")
        val newLines = lines.toMutableList()
        newLines[videoMLineIndex] = newMLine
        return newLines.joinToString("\r\n")
    }

    private fun ensurePeerConnectionFactory() {
        if (peerConnectionFactory == null) {
            FileLogger.writeLine("Creating PeerConnectionFactory with Encoder/Decoder factories")
            
            val encoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            FileLogger.writeLine("PeerConnectionFactory created successfully")
        }
    }

    private fun startMediaProjectionService() {
        try {
            val serviceIntent = Intent(this, com.example.remotecontrol.service.MediaProjectionService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            FileLogger.writeLine("startMediaProjectionService invoked successfully")
        } catch (e: Exception) {
            FileLogger.writeLine("Error starting MediaProjectionService: ${e.message}")
            android.util.Log.e("RemoteControl", "Failed to start foreground service", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        FileLogger.writeLine("onActivityResult: requestCode=$requestCode, resultCode=$resultCode, data=${data != null}")
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            projectionIntent = data
            statusText.text = "Status: Media Projection Ready"
            FileLogger.writeLine("MediaProjection permission granted, projectionIntent saved")

            if (wantLocalPreview) {
                // 优先处理本地预览
                wantLocalPreview = false
                FileLogger.writeLine("onActivityResult -> starting local preview by flag")
                startLocalPreview()
            } else {
                // 如果之前收到 request_offer 时还没授权，这里补发握手
                pendingWebClientId?.let { sender ->
                    FileLogger.writeLine("pendingWebClientId exists ($sender), start handshake now")
                    pendingWebClientId = null
                    startWebrtcHandshake(sender)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::mSocket.isInitialized) {
            mSocket.disconnect()
        }
        screenStreamer?.stop()
        peerConnection?.close()
        // Stop the foreground service
        stopService(Intent(this, com.example.remotecontrol.service.MediaProjectionService::class.java))
    }
}
