# DSH Mobile

> 此仓库由 DeepSeek Flash V4.1（DSH）远程生成

一个极简的 Android 客户端，用 WebView 套壳访问 DeepSeek Harness 的 Web UI。

配合 SSH 端口转发使用：手机连上服务器后，App 直接指向
`http://127.0.0.1:3080` 即可打开 DSH 界面，地址会被记住。

## 功能

- WebView 加载 DSH Web UI（支持 JS、DOM storage、双指缩放）
- 首次启动弹出地址输入框，可自定义任意地址
- 右下角浮动按钮随时改地址
- 返回键优先走网页历史
- 已保存地址跨启动保留

## 构建

### 方式一：GitHub Actions（推荐，不用本地环境）

1. 把本目录推到 GitHub 仓库
2. 打开仓库的 **Actions** 标签页
3. 运行 **Build APK** 工作流（或在 push 到 main 时自动触发）
4. 在运行结果的 **Artifacts** 里下载 `dsh-mobile-debug`

### 方式二：本地 Android Studio

直接用 Android Studio 打开本目录，选择 `app` 模块运行或 `Build > Build APK`。

命令行：

```bash
gradle assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

## 技术信息

| 项 | 值 |
|---|---|
| 语言 | Kotlin |
| compileSdk / targetSdk | 34 |
| minSdk | 26 (Android 8.0) |
| Gradle | 8.9 |
| AGP | 8.5.2 |
| JDK | 17 |

## 结构

```
app/src/main/
├── AndroidManifest.xml
├── java/com/dsh/mobile/MainActivity.kt    # 唯一的 Activity
└── res/
    ├── layout/activity_main.xml           # WebView + FAB
    ├── values/{strings,colors,themes}.xml
    └── mipmap-*/ic_launcher.png           # 图标
```
