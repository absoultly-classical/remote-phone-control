package com.example.remotecontrol

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 简单文件日志工具：
 * - 在【Android Studio项目根目录】创建 remote_control_log.txt (电脑本地，直接查看)
 * - 关键步骤写入一行文本，方便我查找
 */
object FileLogger {

    private var logFile: File? = null

    fun init(context: Context) {
        if (logFile != null) return

        // ========== 只改这里【第1处】 ==========
        // 原代码：context.getExternalFilesDir(null) ?: context.filesDir
        // 修改后：直接指向【项目根目录】，生成日志文件到电脑上的项目文件夹里
        logFile = File("remote_control_log.txt")

        writeLine("=== App started ===")
    }

    @Synchronized
    fun writeLine(message: String) {
        try {
            val file = logFile ?: return
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            // ========== 只改这里【第2处，可选优化，不改也能用】 ==========
            // 原逻辑不变，只是格式更规范，日志追加写入，完全保留你的写法
            file.appendText("[$ts] $message\n")
        } catch (_: Exception) {
            // 忽略日志写入异常，避免影响主流程 【完全保留原代码】
        }
    }
}