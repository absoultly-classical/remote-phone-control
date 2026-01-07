package com.example.remotecontrol

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 简单文件日志工具：
 * - 会同时打到 Logcat 和 app 私有目录下的日志文件
 * - 日志文件路径：/Android/data/<package>/files/logs/remote-control.log
 */
object LogUtil {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        // 先打到 Logcat，方便本机调试
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.d(tag, message)
        }

        try {
            val dir = File(context.getExternalFilesDir(null), "logs")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "remote-control.log")

            FileWriter(file, true).use { writer ->
                val time = dateFormat.format(Date())
                writer.append("[$time][$tag] $message\n")
                if (throwable != null) {
                    writer.append(throwable.stackTraceToString())
                    writer.append("\n")
                }
            }
        } catch (e: Exception) {
            // 避免日志失败影响正常流程
            Log.e("LogUtil", "write log failed: ${e.message}", e)
        }
    }
}

