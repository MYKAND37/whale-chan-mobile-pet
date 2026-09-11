# 鲸鱼酱 · Whale-chan

> 此仓库由 DeepSeek Flash V4.1（DSH）远程生成

一只**浮在 Android 桌面上的鲸鱼女仆**。蓝发、女仆装、鲸鱼尾巴，可以拖着走，点一下会转身、跳跃并跟你说话。

## 形象

来自一张三视图设计稿，构建时切成三份并做了白底透明化处理：

| 视图 | 预览 |
|---|---|
| 正面 | `whale_front.png` |
| 侧面 | `whale_side.png` |
| 背面 | `whale_back.png` |

## 她会做什么

| 操作 | 反应 |
|---|---|
| **拖动** | 把她挪到你喜欢的任何位置 |
| **点一下** | 跳一下 + 转向下一个视角 + 冒出对话气泡（随机台词） |
| **多点几次** | 正面 → 侧面 → 背面 轮换，像在转身 |
| **点开后** | 弹出菜单：说点什么 / 跳一下 / 转个身 / 退出 |
| **重启手机** | 自动回来（前提是已授权） |

**动画效果**：
- 待机时缓慢上下浮动（呼吸感）
- 跳跃时有挤压拉伸（squash & stretch），不是生硬的位移

## 安装

### 直接下载

到 [Releases](../../releases) 页面下载 `whale-chan.apk`，装到手机上。

### 使用步骤

1. 打开 App，点「**召唤鲸鱼酱**」
2. 首次会跳转到系统设置，打开「**显示在其他应用上层**」权限
3. 回到 App 再点一次「召唤鲸鱼酱」
4. 她就出现在桌面上了 🐳

> 她以后台服务方式常驻，通知栏会显示「鲸鱼酱在陪着你了」。
> 想让她休息：点通知里的「退出」，或点她 → 菜单 → 退出。

## 技术说明

| 项 | 值 |
|---|---|
| 语言 | Kotlin |
| 形象 | 三视图切分 + alpha 抠白（构建时用 sharp 处理） |
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
├── WhaleView.kt       # 贴图渲染：浮动、跳跃、转身
└── BootReceiver.kt    # 开机自启

app/src/main/res/drawable-nodpi/
├── whale_front.png    # 正面
├── whale_side.png     # 侧面
└── whale_back.png     # 背面
```

## 构建

推送到 `main` 分支会自动触发 GitHub Actions 编译，产物在 Actions 的 Artifacts 里。
也可以本地用 Android Studio 打开，或：

```bash
gradle assembleDebug
```
