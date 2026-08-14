# Live2D in Minecraft

一个 Fabric 客户端模组，将 [Live2D Cubism 3](www.live2d.com) 渲染到 Minecraft 屏幕

![支持版本: Minecraft 1.21.11 · Fabric · Java 21](https://img.shields.io/badge/Minecraft-1.21.11-blue) ![Fabric Loader >= 0.19.3](https://img.shields.io/badge/Fabric%20Loader-%3E%3D0.19.3-orange) ![Java >= 21](https://img.shields.io/badge/Java-%3E%3D21-yellow)

## 特性

- 将 Live2D 模型作为 HUD 悬浮层渲染，通过 Minecraft 的 GUI 特殊元素进行渲染；
- 内置了 3 个模型：`kaguya`、`noir`、`yachi`；
- 支持加载你自己的 Live2D Cubism 3（`*.moc3`）模型，无需额外配置；
- 头部 / 视线会跟随你的视角移动，带有平滑的弹簧物理和可调节的摆动强度；
- 自动眨眼、呼吸和待机动作；
- 从模型的 `model3.json` 中加载表情（`.exp3.json`）和动作（`.motion3.json`）；
- 声音感应眨眼：当游戏播放的音量超过阈值时，角色会自动闭上眼睛；
- 事件系统：把游戏事件（聊天、受伤、天气、游泳等）绑定到动作、表情或参数覆盖；
- 支持裁剪遮罩渲染（Live2D 剪辑蒙版）、叠加 / 相乘混合模式；
- 打开聊天栏时，可以**拖动**角色移动位置，用**滚轮**调整大小；
- 编辑模式：方向键微调位置，`+` / `-` 调整大小；
- 模型加载失败时显示预览图占位；
- 所有设置都会持久化保存到 JSON 配置文件。

初次启动时，模组将 Live2D Cubism Core 原生库（Windows x86_64）打包在 jar 内，首次加载时会自动解压到 `<游戏目录>/live2d/native/`。

## 操作按键

| 按键 | 功能 |
| --- | --- |
| `F9` | 开关悬浮层 |
| `F10` | 切换模型 |
| `F11` | 切换表情 |
| `F12` | 开关编辑模式 |
| `方向键` | 移动位置（编辑模式） |
| `+` / `-` | 调整大小（编辑模式） |

在游戏中打开聊天栏后，可以直接**拖动**角色移动，用**鼠标滚轮**调整大小。

所有按键可在 *选项 → 控制 → 按键绑定 → 杂项* 中自定义。

## 命令

所有命令均为客户端命令，以 `/live2d` 开头。

| 命令 | 说明 |
| --- | --- |
| `/live2d toggle` | 开关悬浮层 |
| `/live2d status` | 查看当前状态（模型、大小、位置、开关、错误信息） |
| `/live2d models` | 列出可用模型 |
| `/live2d model <名称>` | 切换到指定模型 |
| `/live2d motions` | 列出当前模型的动作 |
| `/live2d expressions` | 列出当前模型的表情 |
| `/live2d expression <名称>` | 设置表情（`off` / `normal` / `none` 可清除） |
| `/live2d size <高度>` | 设置模型高度（像素，40–800） |
| `/live2d pos <x> <y>` | 设置绝对坐标 |
| `/live2d move <dx> <dy>` | 相对移动位置 |
| `/live2d sway <0.0–3.0>` | 视角追踪摆动强度 |
| `/live2d smoothing <0.02–0.6>` | 动画平滑系数 |
| `/live2d followpitch <true/false>` | 是否跟随垂直视角 |
| `/live2d blink <true/false>` | 是否自动眨眼 |
| `/live2d masks <true/false>` | 是否启用裁剪遮罩 |
| `/live2d chatdrag <true/false>` | 是否启用聊天栏拖动 / 滚轮缩放 |
| `/live2d soundblink <true/false>` | 是否启用声音感应眨眼 |
| `/live2d soundblink <true/false> <阈值>` | 设置音量阈值（0–1） |
| `/live2d events` | 列出已配置的事件绑定 |
| `/live2d event <名称> off` | 删除事件绑定 |
| `/live2d event <名称> <motion\|expression\|param> <目标> [值] [时长] [淡入淡出]` | 把游戏事件绑定到动作 |

### 可用事件

`chat_opened`（打开聊天）· `chat_closed`（关闭聊天）· `screen_open`（打开界面）· `screen_close`（关闭界面）· `join_world`（进入世界）· `hurt`（受伤）· `death`（死亡）· `respawn`（重生）· `attack`（攻击）· `jump`（跳跃）· `land`（落地）· `swim_start`（开始游泳）· `swim_end`（停止游泳）· `underwater`（潜水）· `surface`（浮出水面）· `sneak`（潜行）· `stand`（起身）· `sprint_start`（开始疾跑）· `sprint_end`（停止疾跑）· `rain`（下雨）· `clear`（雨停）· `thunder`（打雷）· `night`（夜晚）· `day`（白天）· `low_health`（低血量）· `high_health`（血量恢复）· `volume_high`（音量升高）· `volume_low`（音量降低）· `idle`（待机）

示例——让角色在玩家受伤时播放 `idle` 动作：

```
/live2d event hurt motion idle
```

## 配置文件

设置保存在 `<游戏目录>/config/live2d.json`，每次修改会自动保存。

```jsonc
{
  "enabled": true,              // 是否启用
  "model": "kaguya",            // 当前模型
  "size": 260,                  // 悬浮层高度（像素）
  "posX": 16,                   // 位置 X
  "posY": 110,                  // 位置 Y
  "swayStrength": 3.0,          // 视角追踪摆动强度
  "smoothing": 0.1,             // 动画平滑系数
  "followPitch": true,          // 是否跟随垂直视角
  "blinkEnabled": true,         // 自动眨眼
  "expressionsEnabled": true,   // 启用表情
  "masksEnabled": true,         // 裁剪遮罩渲染
  "showPreviewOnError": true,   // 加载失败时显示预览图
  "hiddenDrawables": [],        // 需要隐藏的 drawable 名称
  "chatDragEnabled": true,      // 聊天栏拖动 / 滚轮缩放
  "wheelResizeStep": 8,         // 滚轮缩放步长
  "soundBlinkEnabled": false,   // 声音感应眨眼
  "soundBlinkThreshold": 0.5,   // 声音感应阈值
  "events": { }                 // 事件绑定，见 /live2d event
}
```

## 添加你自己的模型

把模型文件夹放到 `<游戏目录>/config/live2d/<名称>/`，至少包含：

- `<名称>.model3.json`
- `<名称>.moc3`
- `FileReferences.Textures` 中引用的贴图文件

可选文件会自动识别：物理效果（`*.physics3.json`）、表情（`*.exp3.json`）、动作（`*.motion3.json`）、预览图（`preview.png`，加载失败时显示）。

模型会自动出现在 `/live2d models` 列表中，可用 `/live2d model <名称>` 切换。注意：本模组内置的 Live2D Cubism Core 是 **Windows x86_64** 版本，因此目前仅在 Windows 上测试通过。

## 从源码构建

环境要求：JDK 21。

```
git clone <本仓库地址>
cd Live2D
./gradlew build
```

模组 jar 生成在 `build/libs/live2d-<版本>.jar`。启动开发客户端：

```
./gradlew runClient
```

## 项目结构

```
src/main/java/com/ciallo/live2d/
├── client/          # Fabric 客户端入口、HUD 渲染器、命令、模型加载器、
│                    # 事件系统、特殊 GUI 元素渲染器
├── cubism/          # Live2D Cubism Core 的 JNA 绑定 + 原生模型 / 渲染器，
│                    # 动作与表情播放
└── config/          # JSON 配置（config/live2d.json）
src/main/resources/assets/live2d/
├── live2d/          # 内置模型（kaguya、noir、yachi）和原生 DLL
├── lang/            # 英文 / 简体中文翻译
└── icon.png
```

## 说明

- 本模组为实验性 / 开发中的项目（版本 `1b`）；渲染方式是先把模型绘制到离屏缓冲区，再由 GUI 渲染器合成显示。
- 仅支持 Live2D Cubism 3（`moc3`）格式。

## 许可证

MIT，详见 [LICENSE](LICENSE)。Live2D Cubism Core 归 Live2D 株式会社所有；内置模型资源归其原作者所有。 Mod By P1ay2r.
