# Remote Phone Control (Web RTC)

本项目实现了一个简单的远程控制系统，支持通过 Web 浏览器控制 Android 手机。

## 目录结构
*   `/server`: Node.js 信令服务器
*   `/web-client`: React 控制端（Vite + TS）
*   `/android-app`: Android 受控端（核心 Kotlin 代码示例）

## 快速开始 (跨网段控制)

### 1. 启动信令服务器
```bash
cd server
npm start # 默认运行在 3000 端口
```

### 2. 内网穿透 (重要)
快速上手步骤（以 cpolar 为例）：
1. 下载与安装
去 cpolar 官网 注册一个账号。
下载 Windows 版本的客户端并安装。
2. 授权认证
安装完成后，打开终端（CMD 或 PowerShell），输入你账号里提供的 authtoken（在 cpolar 官网后台可以获取）：

powershell
cpolar authtoken [你的Token]
3. 启动穿透
因为我们的信令服务器运行在 3000 端口，所以输入：

powershell
cpolar http 3000
4. 获取公网地址
输入命令后，你会看到类似这样的界面：


Forwarding http://xxxxxx.cpolar.cn -> http://localhost:3000
Forwarding https://xxxxxx.cpolar.cn -> http://localhost:3000
这里的 http://xxxxxx.cpolar.cn 就是你的公网临时地址。

由于没有云服务器，需要将本地 3000 端口映射到公网。可以使用 `cpolar`:
```bash
cpolar http 3000
```
记录下生成的公网 URL (例如: `http://xxxx.cpolar.cn`)。

### 3. 配置并启动 Web 客户端
修改 `web-client/src/App.tsx` 中的 `SIGNAL_SERVER` 为你的公网 URL。
```bash
cd web-client
npm run dev
```

### 4. 部署 Android 端
1. 将 `android-app` 中的核心代码集成到 Android Studio 项目。
2. 确保在 `AndroidManifest.xml` 中注册 `AccessibilityService`。
3. 赋予录屏权限和无障碍服务权限。

## 功能说明
*   **实时屏幕:** 采用 WebRTC 低延迟视频流。
*   **点击同步:** 网页端点击视频画面，手机端自动执行模拟点击。
*   **系统按键:** 支持模拟 Home、Back、Recents 按键。

## 注意事项
- Android 端务必开启“无障碍服务”权限，否则无法执行点击。
- WebRTC 首次连接可能需要几秒钟进行 ICE 握手。
