# Remote Phone Control (WebRTC) �💻

这是一个功能完整的远程手机控制系统。你可以直接使用本项目提供的 **预编译 APK** 安装到 Android 手机，然后按照以下步骤部署你的专属控制中心（Web 端）。

---

## 🚀 快速上手 (只需 3 步)

### 第一步：安装 Android 受控端
1.  进入项目根目录的 [`/release`](./release) 文件夹。
2.  下载并安装 `RemoteControl-v1.0.apk` 到你的 Android 手机。
3.  **权限授予（至关重要）**:
    *   打开 App，点击 **Start Remote Control** 并允许屏幕录制权限。
    *   前往手机 **设置 -> 辅助功能 (无障碍) -> 已安装的服务**。
    *   找到并开启 **RemoteControl** 服务（这样电脑端才能模拟点击）。

### 第二步：部署控制中心 (Server & Web)
你需要一台有 Node.js 环境的电脑来运行控制端。

#### 1. 启动信令服务器 (Signaling Server)
```bash
cd server
npm install
npm start 
```
*   默认运行在 `3000` 端口。它负责中转指令和建立 WebRTC 连接。

#### 2. 启动控制网页 (Web Client)
```bash
cd web-client
npm install
npm run dev
```
*   访问终端提供的地址（通常是 `http://localhost:5173`）即可看到控制界面。

### 第三步：建立连接
1.  确保手机和电脑在同一个网络，或者使用下方的**内网穿透**方案。
2.  在手机 App 界面查看显示的 **Code**。
3.  在 Web 界面输入该 Code，点击 **Connect** 即可看到手机画面并进行远程操作！

---

## 🌐 远程控制 (不在同一个 WiFi 怎么办？)

如果你想在公司控制家里的手机，可以使用 `cpolar` 进行内网穿透：

1.  启动穿透：`cpolar http 3000`。
2.  获取生成的公网 URL（例如 `https://xxxx.cpolar.top`）。
3.  **更新地址**:
    *   **Android 端**: 开发者需修改 `MainActivity.kt` 中的 `SERVER_URL` 并重新打包（本项目 release 默认为本地测试版）。
    *   **Web 端**: 修改 `web-client/src/App.tsx` 中的 `SIGNAL_SERVER`。

---

## � 目录结构说明
*   [`/release`](./release): 存放已编译好的安装包，下载即用。
*   `/server`: Node.js 信令服务器源代码。
*   `/web-client`: React 控制端前端代码。
*   `/android-app`: Android 端完整源码（供开发者参考或二次开发）。

## 🔒 安全性
*   项目内置了简单的 **Auth Token** 验证。
*   默认 Token 为 `your_secret_password`。你可以在 `server/index.js` 和 Web 端的配置中自行修改。

## ⚠️ 注意事项
*   **网络延迟**: 画面传输质量取决于你的网络带宽。
*   **兼容性**: 模拟点击功能依赖 Android 无障碍服务，部分手机系统（如小米、华为）可能需要额外在权限管理中开启“后台弹出界面”或“允许模拟点击”。

---
如果有任何问题，欢迎提交 Issue 或二次开发！
