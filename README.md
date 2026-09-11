# 鲸鱼酱 · Whale-chan

> 此仓库由 DeepSeek Flash V4.1（DSH）远程生成

一只**浮在 Android 桌面上的小鲸鱼**。她会呼吸、眨眼、摆尾、喷水，可以拖着走，点一下还会冒泡跟你说话。

整个形象**完全用代码绘制**（Android Canvas），不依赖任何图片素材。

## 她长什么样

- 圆滚滚的蓝色身体 + 浅色肚皮
- 会**呼吸**（缓慢起伏）
- 会**眨眼**（随机间隔）
- 尾巴**左右摆动**，胸鳍轻轻扇动
- 偶尔从头顶**喷出一道水柱**
- 被点的时候会**跳起来**，眼睛变成笑眼

## 会做什么

| 操作 | 反应 |
|---|---|
| **拖动** | 把她挪到你喜欢的任何位置 |
| **点一下** | 眨眼 + 跳一下 + 冒出对话气泡（随机台词） |
| **点开后** | 弹出小菜单：说点什么 / 跳一下 / 退出 |
| **重启手机** | 自动回来（前提是已授权） |

## 安装

### 直接下载

到 [Releases](../../releases) 页面下载 `whale-chan.apk`，装到手机上即可。

### 使用步骤

1. 打开 App，点「**召唤鲸鱼酱**」
2. 首次会跳转到系统设置，打开「**显示在其他应用上层**」权限
3. 回到 App 再点一次「召唤鲸鱼酱」
4. 她就出现在桌面上了 🐳

> 她以后台服务方式常驻，通知栏会显示一条「鲸鱼酱在陪着你了」的通知。
> 想让她回去休息，点通知里的「退出」，或者点她 → 菜单 → 退出。

## 技术说明

| 项 | 值 |
|---|---|
| 语言 | Kotlin |
| 形象绘制 | Android Canvas（纯代码，无素材） |
| 悬浮窗 | `TYPE_APPLICATION_OVERLAY` + `WindowManager` |
| 常驻 | 前台服务（`specialUse` 类型） |
| 动画帧率 | 25 fps |
| minSdk | 26 (Android 8.0) |
| compileSdk / targetSdk | 34 |
| Gradle / AGP | 8.9 / 8.5.2 |
| JDK | 17 |

## 代码结构

```
app/src/main/java/com/dsh/mobile/
├── MainActivity.kt    # 控制面板：申请权限、召唤/召回
├── PetService.kt      # 前台服务：悬浮窗、拖动、气泡、菜单
├── WhaleView.kt       # 鲸鱼本体：全部用 Canvas 画的
└── BootReceiver.kt    # 开机自启
```

## 构建

推送到 `main` 分支会自动触发 GitHub Actions 编译，产物在 Actions 的 Artifacts 里。
也可以本地用 Android Studio 打开，或：

```bash
gradle assembleDebug
```
