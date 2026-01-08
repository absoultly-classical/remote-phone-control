package com.example.remotecontrol

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 集中日志工具：
 * 1. 发送到服务器 (server_logs.txt)
 * 2. 写入本地文件 (remote_control_log.txt)
 */
object FileLogger {

    private var logFile: File? = null
    private var socket: io.socket.client.Socket? = null

    fun init(context: Context) {
        if (logFile != null) return
        // 尝试在外部存储或私有目录创建日志
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        logFile = File(dir, "remote_control_log.txt")
        writeLine("=== FileLogger Initialized ===")
    }

    fun setSocket(s: io.socket.client.Socket) {
        this.socket = s
        writeLine("Socket attached to Logger")
    }

    @Synchronized
    fun writeLine(message: String) {
        try {
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val formattedMsg = "[$ts] $message"

            // 1. Console Log
            android.util.Log.d("RemoteControl", formattedMsg)

            // 2. Emit to Server
            socket?.let {
                if (it.connected()) {
                    val data = org.json.JSONObject()
                    data.put("source", "ANDROID_APP")
                    data.put("message", message)
                    it.emit("client_log", data)
                }
            }

            // 3. Local File
            logFile?.appendText("$formattedMsg\n")
        } catch (_: Exception) {
        }
    }
}