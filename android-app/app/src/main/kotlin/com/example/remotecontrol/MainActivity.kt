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

    private val PREFS_NAME = "RemoteControlPrefs"
    private val KEY_SERVER_URL = "server_url"
    private val KEY_DEVICE_ID = "device_id"
    private val DEFAULT_URL = "http://192.168.1.100:3000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 0. Initialize WebRTC
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

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

        val startStreamBtn = Button(this).apply { text = "Start Media Projection" }

        layout.addView(TextView(this).apply { text = "1. Server Address:" })
        layout.addView(urlEdit)
        layout.addView(deviceIdText)
        layout.addView(connectBtn)
        layout.addView(codeText)
        layout.addView(statusText)
        layout.addView(android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 80) })
        layout.addView(TextView(this).apply { text = "2. Controls:" })
        layout.addView(startStreamBtn)
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

        startStreamBtn.setOnClickListener {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), 100)
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
            var finalUrl = serverUrl
            if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                finalUrl = "http://$finalUrl"
            }

            statusText.text = "Status: Connecting to $finalUrl..."
            connectBtn.isEnabled = false // 防止重复点击
            
            val opts = IO.Options()
            opts.auth = mapOf("token" to deviceId) 
            mSocket = IO.socket(finalUrl, opts)

            mSocket.on(Socket.EVENT_CONNECT) {
                runOnUiThread { 
                    statusText.text = "Status: Server Connected"
                    connectBtn.text = "Disconnect"
                    connectBtn.isEnabled = true
                }
                mSocket.emit("create_code", io.socket.client.Ack { args: Array<Any> ->
                    if (args.isNotEmpty()) {
                        val data = args[0] as? JSONObject
                        val code = data?.optString("code", "-") ?: "-"
                        runOnUiThread { codeText.text = "Pairing Code: $code" }
                    }
                })
            }

            mSocket.on("signal") { args: Array<Any> ->
                val data = args[0] as? JSONObject ?: return@on
                val type = data.optString("type")
                val sender = data.optString("sender")

                when (type) {
                    "request_offer" -> {
                        runOnUiThread { statusText.text = "Status: Pairing..." }
                        startWebrtcHandshake(sender)
                    }
                    "answer" -> {
                        val payload = data.optJSONObject("payload") ?: return@on
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, payload.optString("sdp"))
                        peerConnection?.setRemoteDescription(object : SdpObserver {
                            override fun onCreateSuccess(desc: SessionDescription?) {}
                            override fun onSetSuccess() { android.util.Log.d("WebRTC", "Remote SDP Set Success") }
                            override fun onCreateFailure(s: String?) { android.util.Log.e("WebRTC", "Create Failure: $s") }
                            override fun onSetFailure(s: String?) { android.util.Log.e("WebRTC", "Remote SDP Set Failed: $s") }
                        }, sdp)
                    }
                    "candidate" -> {
                        val payload = data.optJSONObject("payload") ?: return@on
                        val candidate = IceCandidate(
                            payload.optString("sdpMid"),
                            payload.optInt("sdpMLineIndex"),
                            payload.optString("candidate")
                        )
                        peerConnection?.addIceCandidate(candidate)
                    }
                }
            }

            mSocket.on(Socket.EVENT_DISCONNECT) {
                runOnUiThread { 
                    statusText.text = "Status: Disconnected"
                    connectBtn.text = "Connect to Server"
                    connectBtn.isEnabled = true
                    codeText.text = "Pairing Code: -"
                }
            }
            
            mSocket.on(Socket.EVENT_CONNECT_ERROR) { args: Array<Any> ->
                val err = if (args.isNotEmpty()) args[0].toString() else "Unknown Error"
                runOnUiThread { 
                    statusText.text = "Error: $err"
                    connectBtn.text = "Retry Connect"
                    connectBtn.isEnabled = true
                }
                android.util.Log.e("RemoteControl", "Connect Error: $err")
            }
            
            mSocket.connect()
            controlManager = RemoteControlManager(mSocket)

        } catch (e: Exception) {
            runOnUiThread { 
                statusText.text = "Error: ${e.message}"
                connectBtn.isEnabled = true
            }
            e.printStackTrace()
        }
    }

    private fun startWebrtcHandshake(webClientId: String) {
        if (projectionIntent == null) {
            runOnUiThread { android.widget.Toast.makeText(this, "Please start Media Projection first!", android.widget.Toast.LENGTH_SHORT).show() }
            return
        }

        // Start Foreground Service for MediaProjection (Required for Android 10+)
        val serviceIntent = Intent(this, com.example.remotecontrol.service.MediaProjectionService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Initialize Factory if needed
        if (peerConnectionFactory == null) {
            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()
        }

        // Create PeerConnection
        val iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory!!.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                val payload = JSONObject().apply {
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("candidate", candidate.sdp)
                }
                mSocket.emit("signal", JSONObject().apply {
                    put("room", codeText.text.split(": ")[1])
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

        // Add Video Track
        screenStreamer = ScreenStreamer(projectionIntent!!, peerConnectionFactory!!)
        screenStreamer!!.startStreaming(peerConnection!!)

        // Create Offer
        peerConnection!!.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peerConnection!!.setLocalDescription(this, desc)
                val payload = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", desc.description)
                }
                mSocket.emit("signal", JSONObject().apply {
                    put("room", codeText.text.split(": ")[1])
                    put("type", "offer")
                    put("payload", payload)
                })
                runOnUiThread { statusText.text = "Status: Streaming..." }
            }
            override fun onCreateFailure(s: String?) { android.util.Log.e("WebRTC", "Create Offer Failed: $s") }
            override fun onSetSuccess() {}
            override fun onSetFailure(s: String?) { android.util.Log.e("WebRTC", "Set Local SDP Failed: $s") }
        }, MediaConstraints())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            projectionIntent = data
            statusText.text = "Status: Media Projection Ready"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mSocket.disconnect()
        screenStreamer?.stop()
        peerConnection?.close()
        // Stop the foreground service
        stopService(Intent(this, com.example.remotecontrol.service.MediaProjectionService::class.java))
    }
}
