# Android 自动化调试脚本 (Deploy & Log)
# 只要手机开启了 USB 调试并连接电脑，运行此脚本即可一键完成：编译 -> 安装 -> 运行 -> 看日志

$APK_PATH = "app/build/outputs/apk/debug/app-debug.apk"

Write-Host "--- 1. 正在清理并编译 APK (assembleDebug) ---" -ForegroundColor Cyan
.\gradlew assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "编译失败，请检查代码错误。" -ForegroundColor Red
    exit
}

Write-Host "--- 2. 正在通过 ADB 安装到手机 ---" -ForegroundColor Cyan
adb install -r $APK_PATH

if ($LASTEXITCODE -ne 0) {
    Write-Host "安装失败，请确保手机已连接并开启 USB 调试。" -ForegroundColor Red
    exit
}

Write-Host "--- 3. 正在启动 App ---" -ForegroundColor Cyan
adb shell am start -n com.example.remotecontrol/.MainActivity

Write-Host "--- 4. 正在追踪日志 (按 Ctrl+C 退出) ---" -ForegroundColor Cyan
Write-Host "过滤关键字: RemoteControl, WebRTC, AndroidRuntime" -ForegroundColor Yellow
adb logcat -v time | Select-String "RemoteControl|WebRTC|AndroidRuntime|Exception"
