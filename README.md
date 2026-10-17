# Legend Cooldown / 传奇武器冷却

从同级 `EmbersClient/client-mod-1.21.11` 的完整冷却模块拆出的独立客户端 Fabric MOD。保留 Apollo JSON / protobuf、旧版 Lunar、原版物品冷却以及服务器动作栏、系统消息、Boss 栏和动态物品说明中的冷却识别。

## 安装

从 [GitHub Releases](https://github.com/EmbersDrift/LegendCoolDown/releases/latest) 下载与 Minecraft **完全对应**的一个 JAR，放入游戏的 `mods/`，同时安装对应版本 Fabric Loader 和 Fabric API。本地构建的成品位于 `dist/`。

| Minecraft | 文件 | Java | Fabric API（构建使用） |
| --- | --- | --- | --- |
| 1.21.11 | `LegendCoolDown-1.1.0+mc1.21.11.jar` | 21+ | 0.141.6+1.21.11 |
| 26.1.2 | `LegendCoolDown-1.1.0+mc26.1.2.jar` | 25+ | 0.155.2+26.1.2 |
| 26.2 | `LegendCoolDown-1.1.0+mc26.2.jar` | 25+ | 0.161.0+26.2 |

Fabric Loader 最低版本为 0.19.5。不需要 EmbersClient、Live Hider 或 TeamViewer；如果装有 TeamViewer，会沿用原模块的 Apollo 接收器共享方式。独立版应替代 Embers 中的冷却模块，避免两个实现重复注册协议通道。

## 使用

进入世界后按 **K** 打开设置，可以在原版“选项 → 控制 → 按键绑定”中修改。

按 **H** 直接打开 HUD 调整界面，也可从设置页点击“调整冷却 HUD”：

- 按住鼠标左键拖动模块，可向任意方向移动。
- 拖动模块右下角的小方块可放大、缩小；鼠标放在模块上也可滚轮缩放。
- 缩放范围为 50%～300%，调整时自动限制在屏幕内。
- 没有冷却或 HUD 已关闭时仍显示固定预览。点击“重置位置与缩放”恢复默认布局。
- 松开鼠标或关闭界面后自动保存，Esc 返回上一界面；原有位置／缩放滑块仍可使用。

- 圆环、图标、进度条、仅时间四种样式。
- 名称、时间小数、原版冷却同步、剩余时间排序、服务器文本识别和背景开关。
- 使用水平位置、垂直位置和缩放滑块调整 HUD。拖动后的左上角采用屏幕百分比定位，冷却条目增减时保持锚点，并自动限制在屏幕内；旧配置在首次拖动时自动转换。F1 会隐藏 HUD。
- 设置与已确认的服务器冷却时长保存在 `config/legendcooldown.json`。
- “冷却诊断”可手动记录，最多 3 分钟或 2000 条；停止、超时或断开连接后导出到 `logs/legendcooldown-diagnostics-*.json`。报告包括服务器地址、背包物品、冷却文本和解析结果；默认不记录，未订阅玩家聊天事件。

设置、诊断使用原版 `Screen`、`Button`、`AbstractSliderButton`、原版字体及背景。不迁移 Embers 的全局界面美化、标题界面、动画、调试覆盖层、字体包、披风、账户、启动器或隐私路由。F3 调试界面沿用 Minecraft 原版。

冷却来自服务器明确发送的数据或原版冷却状态；只有使用事件、没有时长／确认信号时，不凭空生成传奇武器计时。

## 构建

在 Windows 安装 JDK 25 或更新版本，然后执行：

```powershell
.\gradlew.bat build
```

三个发行 JAR 统一输出到 `dist/`；源码 JAR 和各版本测试报告分别位于 `mc*/build/`。Linux/macOS 使用 `bash ./gradlew build`。

```powershell
# 只构建某个版本
.\gradlew.bat :mc12111:build
.\gradlew.bat :mc2612:build
.\gradlew.bat :mc262:build

# 开发环境启动验证，独立目录，自动退出
.\gradlew.bat :mc12111:runSmoke
.\gradlew.bat :mc2612:runSmoke
.\gradlew.bat :mc262:runSmoke
```

共享源码位于 `src/client/java/`。以 1.21.11 Mojang 命名为基准，`gradle/version.gradle` 中明确转换 26.x 的 Fabric API 和 GUI API 名称，生成到各版本 `build/generated/` 后分别编译。1.21.11 使用 Loom remap 和 Java 21 字节码，26.x 使用无需混淆映射的 Loom 和 Java 25 字节码。参见 [Fabric Loom 文档](https://docs.fabricmc.net/develop/loom/)。

开发验证模块位于 `tools/smoke/`，不会打入发行 JAR。结果、截图和诊断报告位于各版本 `build/smoke-run/`。保留了原项目六组解析／格式化／诊断缓冲区回归测试及服务器字形样本。

本次三个版本各通过 25 项单元测试和 37 项客户端／游戏内检查，详见 [验证结果与截图](docs/VERIFICATION.md)。

上传范围与凭证扫描记录见 [敏感信息检查](docs/SECURITY_REVIEW.md)。

## 来源

迁移清单与边界见 [docs/MIGRATION.md](docs/MIGRATION.md)。原项目元数据为 `All-Rights-Reserved`，此项目沿用该标记，不额外授予上游代码的再分发许可。
