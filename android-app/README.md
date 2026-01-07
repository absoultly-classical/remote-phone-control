# Android App 编译指南 🛠️

本文档介绍如何在本地环境手动编译并生成远程控制 App 的 APK 文件。

## 📋 环境要求
- **JDK 17**: 必须安装 JDK 17 或更高版本。
- **Android SDK**: 确保已配置 `ANDROID_HOME` 环境变量。
- **内存**: 建议至少 4GB 可用内存。

## 🚀 编译步骤

### 1. 进入目录
在终端中进入项目下的 `android-app` 文件夹：
```powershell
cd android-app
```

### 2. 执行编译
使用 Gradle Wrapper 进行编译。
- **生成 Debug 版 APK (推荐测试使用)**:
  ```powershell
  .\gradlew.bat assembleDebug
  ```
- **清理并编译 (如果遇到奇怪的报错)**:
  ```powershell
  .\gradlew.bat clean assembleDebug
  ```

### 3. 获取产物
编译完成后，生成的 APK 文件位于：
`app\build\outputs\apk\debug\app-debug.apk`

---

## 💡 常见问题 (Troubleshooting)

### 1. 乱码问题
如果在命令行看到输出乱码，可以添加 `--console=plain` 参数：
```powershell
.\gradlew.bat assembleDebug --console=plain
```

### 2. 权限/签名问题
当前的编译命令使用的是自动生成的调试签名。如果需要发布 Release 版本，请配置 `signingConfigs` 并在 `build.gradle` 中设置。

### 3. Kotlin 编译错误 (Unresolved reference: Intent)
确保 `ScreenStreamer.kt` 中已包含以下导入：
```kotlin
import android.content.Intent
```
