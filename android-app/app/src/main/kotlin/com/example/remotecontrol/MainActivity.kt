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

class MainActivity : AppCompatActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var mSocket: Socket
    private lateinit var statusText: TextView
    private lateinit var codeText: TextView

    // Replace with your actual server URL
    private val SERVER_URL = "http://411501e9.r12.cpolar.top" 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        codeText = TextView(this).apply {
            text = "Code: Loading..."
            textSize = 30f
            setPadding(0, 0, 0, 50)
        }
        
        statusText = TextView(this).apply {
            text = "Status: Disconnected"
            textSize = 18f
        }

        val btn = Button(this).apply { text = "Start Remote Control" }

        layout.addView(codeText)
        layout.addView(statusText)
        layout.addView(btn)
        setContentView(layout)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        btn.setOnClickListener {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), 100)
        }

        initSocket()
    }

    private fun initSocket() {
        try {
            val opts = IO.Options()
            opts.auth = mapOf("token" to "your_secret_password") // Match server AUTH_TOKEN
            mSocket = IO.socket(SERVER_URL, opts)

            mSocket.on(Socket.EVENT_CONNECT) {
                runOnUiThread { statusText.text = "Status: Connected" }
                // Request a code
                mSocket.emit("create_code", io.socket.client.Ack { args ->
                    if (args.isNotEmpty()) {
                        val data = args[0] as JSONObject
                        val code = data.getString("code")
                        runOnUiThread { codeText.text = "Code: $code" }
                    }
                })
            }

            mSocket.on(Socket.EVENT_DISCONNECT) {
                runOnUiThread { statusText.text = "Status: Disconnected" }
            }
            
            mSocket.connect()

        } catch (e: Exception) {
            statusText.text = "Error: ${e.message}"
            e.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            // In a real app, you would start a Foreground Service here to keep the connection alive
            // and pass 'mSocket' to the service or manager.
            // For this demo, we assume the socket is ready and just printing a log.
            // val mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            // ScreenStreamer(mediaProjection, ...).startStreaming(pc)
            statusText.text = "Status: Streaming Started (Check Log)"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::mSocket.isInitialized) {
            mSocket.disconnect()
            mSocket.off()
        }
    }
}
