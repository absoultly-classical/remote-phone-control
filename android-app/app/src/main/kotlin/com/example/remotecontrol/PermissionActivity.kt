package com.example.remotecontrol

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.remotecontrol.service.RemoteControlAccessibilityService

/**
 * 首启权限引导页：
 * - 检测无障碍服务是否已开启
 * - 引导用户跳转到系统无障碍设置
 * - 开启后自动进入 MainActivity
 */
class PermissionActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var openAccBtn: Button
    private lateinit var continueBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 简单的纵向布局，避免额外 XML
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 80, 60, 60)
        }

        val title = TextView(this).apply {
            text = "RemoteControl · 权限引导"
            textSize = 22f
            setPadding(0, 0, 0, 30)
        }

        val desc = TextView(this).apply {
            text = "为了让电脑可以远程操作这台手机，需要开启无障碍服务，并在后续授权屏幕录制。" +
                    "应用不会在未授权的情况下录屏或执行操作。"
            textSize = 14f
        }

        statusText = TextView(this).apply {
            textSize = 16f
            setPadding(0, 30, 0, 20)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        openAccBtn = Button(this).apply {
            text = "去开启无障碍服务"
            setOnClickListener {
                // 跳转到系统无障碍设置页（所有 ROM 通用）
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }

        continueBtn = Button(this).apply {
            text = "我已经开启好了"
            setOnClickListener {
                if (isAccessibilityServiceEnabled(
                        this@PermissionActivity,
                        RemoteControlAccessibilityService::class.java
                    )
                ) {
                    goToMain()
                } else {
                    statusText.text =
                        "检测到无障碍服务仍未开启。\n请在系统设置中找到 “RemoteControl” 并打开开关。"
                }
            }
        }

        val tip = TextView(this).apply {
            text = "提示：不同品牌手机路径略有差异，一般为：设置 → 辅助功能 / 无障碍 → 已安装服务 → RemoteControl。"
            textSize = 12f
            setPadding(0, 20, 0, 0)
        }

        layout.addView(title)
        layout.addView(desc)
        layout.addView(statusText)
        layout.addView(openAccBtn)
        layout.addView(continueBtn)
        layout.addView(tip)

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        // 每次回到该界面时自动检测一次
        if (isAccessibilityServiceEnabled(this, RemoteControlAccessibilityService::class.java)) {
            statusText.text = "无障碍服务状态：已开启 ✅"
            // 无障碍已开，直接进入主页面
            goToMain()
        } else {
            statusText.text =
                "无障碍服务状态：未开启 ❌\n请点击下方按钮前往系统设置开启。"
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * 检测指定无障碍服务是否已启用。
     * 通过读取 Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES（只读）实现。
     */
    private fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<*>
    ): Boolean {
        val expectedComponentName = "${context.packageName}/${serviceClass.name}"

        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            enabledServices
                .split(':')
                .any { it.equals(expectedComponentName, ignoreCase = true) }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

