package com.example.remotecontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class RemoteControlAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // 核心：执行点击操作
    fun performClick(xPercent: Float, yPercent: Float) {
        val displayMetrics = resources.displayMetrics
        val x = xPercent * displayMetrics.widthPixels
        val y = yPercent * displayMetrics.heightPixels

        Log.d("RemoteControl", "Clicking at $x, $y")

        val path = Path()
        path.moveTo(x, y)
        
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        
        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d("RemoteControl", "Gesture completed")
            }
        }, null)
    }

    // 执行全局按键（Home, Back等）
    fun performGlobalAction(actionId: Int) {
        performGlobalAction(actionId)
    }
}
