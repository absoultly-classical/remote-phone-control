# Remote Phone Control (Universal Platform) 📱💻

这是一个**零代码改动**的通用远程手机控制方案。你只需要下载预编译的 APK，配合你自己的服务器，即可实现手机的远程控制，无需修改任何代码。

---

## 🚀 快速上手 (无需编程)

### 第一步：准备服务器 (Control Center)
你可以用自己的电脑作为中转节点。
1.  **运行信令服务器**:
    ```bash
    cd server
    npm install
    npm start 
    ```
2.  **开启内网穿透** (如使用 `cpolar`):
    ```bash
    cpolar http 3000
    ```
    记录生成的公网地址 (例如 `https://xxxx.cpolar.top`)。

### 第二步：部署 Web 控制端
为了方便，你可以直接本地运行 Web 端：
1.  **启动 Web 端**:
    ```bash
    cd web-client
    npm install
    npm run dev
    ```
2.  在浏览器打开显示的地址，在 **1. Server Setup** 区域填写 **Server URL** 和手机上显示的 **Device ID**。
3.  点击 **Connect to Server**，直到状态显示 `Server Connected`。

### 第三步：安装并运行手机 App (v1.6 稳定修复版)
1.  从项目根目录的 [`/release`](./release) 文件夹下载并安装 `RemoteControl-Universal-v1.6.apk`。
2.  **一键连接**:
    *   打开 App，填入你的 **Server URL**（如 cpolar 地址或局域网 IP）。
    *   点击 **Connect to Server**。App 会自动生成并显示一个 **Device ID** (如 `RC-A1B2`) 和一个 **Pairing Code**。
3.  **权限开启**:
    *   点击 **Start Media Projection** 开启录屏。
    *   前往设置开启 **RemoteControl 无障碍服务**。

> [!IMPORTANT]
> **小米/OPPO/VIVO 用户注意**：
> 如果点击“开启录制”后没反应，或者网页端看不到画面，请前往：**手机设置 -> 应用管理 -> RemoteControl -> 权限管理**，务必开启 **“后台弹出界面”** 或 **“在后台启动界面”** 权限。

### 第四步：开始控制
1.  回到 Web 端，在 **2. Device Pairing** 区域输入手机显示的 6 位 Code。
2.  点击 **Pair**，连接成功后即可进行远程操作。

---

## 🌟 为什么选择通用版？
*   **零编译**: 你不需要安装 Android Studio 或配置 Java 环境，下载 APK 就能用。
*   **私有化**: 服务器地址和连接密码完全由你控制，数据不经过第三方。
*   **灵活切换**: 随时更换穿透地址，只需在 App 和网页上重新填写，无需重新打包。

## 📁 核心目录
*   [`/release`](./release): 存放通用版 APK 安装包。
*   `/server`: 轻量级中转服务器，支持动态 Token 匹配。
*   `/web-client`: 响应式控制界面，支持配置持久化。
*   [`/android-app`](./android-app): Android 客户端源码，详见 [编译指南](./android-app/README.md)。

---

## 🛠️ 编译与开发
如果你需要修改代码或自己编译 APK，请参考 [Android 编译指南](./android-app/README.md)。

---
Enjoy your remote control experience! 🚀
