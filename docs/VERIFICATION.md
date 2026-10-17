# 验证结果

三个版本均构建成功，发行 JAR 位于 `dist/`。

| Minecraft | 单元测试 | 客户端与游戏内检查 | 截图 |
| --- | --- | --- | --- |
| 1.21.11 | 25 / 25 | 37 / 37 | [设置](verification/1.21.11/settings.png)、[诊断](verification/1.21.11/diagnostics.png)、[HUD](verification/1.21.11/hud-ring.png)、[拖拽编辑](verification/1.21.11/hud-editor.png) |
| 26.1.2 | 25 / 25 | 37 / 37 | [设置](verification/26.1.2/settings.png)、[诊断](verification/26.1.2/diagnostics.png)、[HUD](verification/26.1.2/hud-ring.png)、[拖拽编辑](verification/26.1.2/hud-editor.png) |

每个版本还保存了图标、进度条、仅时间样式的截图。截图显示原版设置／诊断控件、原版场景与冷却 HUD；测试数据只在各自 `build/smoke-run/` 的独立本地测试世界中使用。

检查覆盖 Apollo JSON／protobuf 显示、移除、重置、畸形数据容错、原版冷却 Mixin、真实动作栏入口、Boss 栏更新、物品完整模型绑定、物品消耗后的图标保留、就绪消息清除、原版计时器接入及去重、原版设置初始化、诊断报告导出。

1.1.0 新增 HUD 编辑检查：设置按钮入口、关闭 HUD／无冷却时的预览、鼠标双轴拖动、四边边界限制、右下角放大和缩小、滚轮放大和缩小、模块外滚轮不响应、位置与缩放持久化、返回设置和重置按钮。新增单元测试覆盖位置精度、边界、换列后的点击区域、窗口尺寸变化及冷却条目增减后的固定锚点。

发行包检查确认：Minecraft 版本精确匹配；1.21.11 为 Java 21 字节码，26.x 为 Java 25 字节码；只有四个冷却 Mixin；没有 EmbersClient／Live Hider 强制依赖，也没有打入开发验证模块。1.21.11 的发行 Mixin 描述符已重映射为 intermediary。

构建和启动验证使用 Windows、JDK 26。启动验证为 Fabric 开发环境的离线本地世界，未登录线上服务器，也未替换用户已有游戏 MOD。服务器专有协议和服务器未发送的冷却数据不在本次实测范围内。

复现构建及三个 `runSmoke` 命令后，执行 `python tools/verify_artifacts.py` 可重新检查发行包、汇总测试并复制截图。完整检查清单、SHA-256 和包大小见 [results.json](verification/results.json)。
