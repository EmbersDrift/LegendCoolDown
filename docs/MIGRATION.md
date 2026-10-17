# 拆分说明

## 选择的实现

`EmbersClient/client-mod` 的 26.2 主分支只有基础 Apollo 冷却实现；`client-mod-1.21.11` 已包含服务器动作栏入口修复、KingMC 字形清洗、Boss 栏、动态 lore、自定义物品模型绑定和临时诊断。因此采用后者作为共享业务逻辑，再分别适配三个目标版本。

## 迁移内容

| 原实现 | 独立版处理 |
| --- | --- |
| `ApolloCooldowns` | 保留协议接收、原版冷却合并、去重、模型快照及资源重载缓存；移除 AeroUi/HudRenderer 缓存依赖 |
| `ServerArtifactCooldowns`、`ServerCooldownText` | 保留系统消息／动作栏／Boss 栏／动态 lore 识别和确证时长学习 |
| `LegacyCooldownPacket`、`ConfirmedCooldownTracker` | 保留旧 Lunar 解码和确证时长追踪 |
| `CooldownFormat`、`CooldownTimeCache`、`BoundedCache` | 保留时间与名称处理及有界缓存 |
| `CooldownDiagnostics`、`CooldownTraceBuffer` | 保留手动、限时、限量、本地异步导出；独立文件名和配置上下文，去掉 Live Hider 路由信息 |
| `CooldownSettingsScreen`、`CooldownDiagnosticsScreen` | 重新实现为原版 Screen、Button、Slider，使用中英文语言文件 |
| `HudEditorScreen` 的移动／缩放交互 | 独立重实现为原版 Screen；模块任意方向拖动、右下角等比缩放、滚轮缩放、固定预览及重置。与 HUD 共用 `HudLayout` 几何，位置用高精度百分比保存，兼容旧整数配置 |
| `HudRenderer` 的冷却部分 | 提取为 `CooldownHud`，只绘制冷却；原版矩形背景、字体和物品绘制，保留四种布局 |
| `ClientConfig` | 精简为 `CooldownConfig`，独立文件和原子保存；不读取／改写 Embers 配置 |
| `EmbersClient` 入口 | 精简为 `LegendCooldown`，注册冷却、一个可重绑定设置键和一个 HUD 元素 |

只保留四个冷却相关 Mixin：动作栏只读监听、Boss 栏事件读取、原版冷却 tick 读取和增删冷却监听。所有包名、Mixin 方法前缀及 MOD ID 改为 `legendcooldown`。不修改原版消息内容，也不取消原版显示。

## 版本差异

| 差异 | 1.21.11 | 26.1.2 | 26.2 |
| --- | --- | --- | --- |
| Loom | `fabric-loom-remap` + Mojang mappings | `fabric-loom` | `fabric-loom` |
| Java 字节码 | 21 | 25 | 25 |
| GUI 提交 | `GuiGraphics` / `render` | `GuiGraphicsExtractor` / `extractRenderState` | 同 26.1.2 |
| 设置界面管理 | Minecraft | Minecraft | `Minecraft.gui` |
| 原版 HUD 与动作栏 | `Gui` | `Gui` | `Hud` |
| Fabric 按键助手 | KeyBindingHelper | KeyMappingHelper | KeyMappingHelper |
| Payload 注册 | `playS2C` | `clientboundPlay` | `clientboundPlay` |

物品模型匹配使用组件 patch 的 `entrySet` 检查显式 item_model，兼容三个版本的 DataComponentPatch API。

## 验证边界

单元回归覆盖文本、特殊资源包字形、旧协议、时间格式及诊断缓冲。开发 smoke 验证真实客户端初始化、Apollo 显示／移除／重置、畸形包容错、原版冷却 Mixin、动作栏 Mixin、Boss 栏访问、原版设置和诊断界面以及报告导出。启动验证不登录线上服务器；服务器专有的新协议或未发送的数据仍需对应服务器实测。
