# Migration notes

## Implementation selected

The 26.2 branch in `EmbersClient/client-mod` contained the basic Apollo implementation. The `client-mod-1.21.11` branch also included the dedicated action-bar fix, KingMC font-glyph cleanup, boss-bar and dynamic-lore readers, custom item model binding, and temporary diagnostics. The latter became the shared implementation, adapted separately to all three Minecraft versions.

## Extraction scope

| Original implementation | Standalone implementation |
| --- | --- |
| `ApolloCooldowns` | Preserves protocol receivers, vanilla cooldown merging, deduplication, model snapshots, and resource-reload caches; removes AeroUi/HudRenderer cache dependencies |
| `ServerArtifactCooldowns`, `ServerCooldownText` | Preserves system-message, action-bar, boss-bar, and dynamic-lore recognition, plus confirmed duration learning |
| `LegacyCooldownPacket`, `ConfirmedCooldownTracker` | Preserves legacy Lunar decoding and confirmed duration tracking |
| `CooldownFormat`, `CooldownTimeCache`, `BoundedCache` | Preserves time and name processing and bounded caching |
| `CooldownDiagnostics`, `CooldownTraceBuffer` | Preserves manual, bounded, temporary capture and asynchronous local export; uses standalone filenames and configuration context, without Live Hider routing information |
| `CooldownSettingsScreen`, `CooldownDiagnosticsScreen` | Rewritten with vanilla Screen, Button, and Slider widgets and English / Simplified Chinese translations |
| `HudEditorScreen` movement and scaling | Reimplemented with a vanilla Screen: dragging, proportional lower-right resizing, wheel scaling, a fixed preview, and reset. Shares `HudLayout` geometry with the HUD and saves precise percentage positions while accepting older integer settings |
| Cooldown rendering in `HudRenderer` | Extracted into `CooldownHud`, retaining four layouts with rectangular backgrounds, vanilla fonts, and item rendering |
| `ClientConfig` | Reduced to `CooldownConfig` with its own file and atomic saving; does not read or modify Embers configuration |
| `EmbersClient` entry point | Reduced to `LegendCooldown`, registering cooldowns, settings/editor key bindings, and one HUD element |

Only four cooldown-related Mixins remain: a read-only action-bar listener, boss-bar event access, vanilla cooldown tick access, and cooldown add/remove listeners. Package names, Mixin method prefixes, and the mod ID use `legendcooldown`. Server messages are observed without changing their content or canceling their original display.

## Version differences

| Area | 1.21.11 | 26.1.2 | 26.2 |
| --- | --- | --- | --- |
| Loom | `fabric-loom-remap` + Mojang mappings | `fabric-loom` | `fabric-loom` |
| Java bytecode target | 21 | 25 | 25 |
| GUI submission | `GuiGraphics` / `render` | `GuiGraphicsExtractor` / `extractRenderState` | Same as 26.1.2 |
| Screen management | Minecraft | Minecraft | `Minecraft.gui` |
| Vanilla HUD and action bar | `Gui` | `Gui` | `Hud` |
| Fabric key helper | KeyBindingHelper | KeyMappingHelper | KeyMappingHelper |
| Payload registration | `playS2C` | `clientboundPlay` | `clientboundPlay` |

Item model matching inspects the component patch's `entrySet` for an explicitly supplied `item_model`, accommodating the DataComponentPatch APIs in all three versions.

## Verification scope

Unit tests cover text parsing, resource-pack font glyphs, legacy packets, time formatting, trace buffering, and HUD geometry. Development smoke checks exercise real client initialization, Apollo display/remove/reset messages, malformed payload handling, vanilla cooldown Mixins, the action-bar listener, boss-bar access, vanilla settings and diagnostics, HUD editing, and report export.

Client verification uses isolated local worlds and does not connect to online servers. Server-specific protocols and data that a server does not send still require testing on the corresponding server.
