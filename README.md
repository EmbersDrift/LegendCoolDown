# Legend Cooldown

Track legendary weapon, artifact, and item cooldowns in Minecraft, with a HUD you can move and resize using your mouse.

Legend Cooldown is a standalone **Fabric client mod** extracted from EmbersClient. It supports **Minecraft 1.21.11, 26.1.2, and 26.2**, with vanilla settings widgets, fonts, and the original F3 debug screen.

**English** | [简体中文](README.zh-CN.md)

**[Download the latest release](https://github.com/EmbersDrift/LegendCoolDown/releases/latest)** · [Release notes](https://github.com/EmbersDrift/LegendCoolDown/releases) · [Report an issue](https://github.com/EmbersDrift/LegendCoolDown/issues)

## In-game screenshots

These screenshots were captured in an actual Minecraft 26.2 local test world, using the English interface and test cooldown data. The mod also includes Simplified Chinese translations.

### Four HUD styles

| Ring | Icon |
| --- | --- |
| ![Ring style with an item icon, circular progress indicator, and remaining time](docs/verification/26.2/hud-ring.png) | ![Icon style with the item name, icon, and remaining time](docs/verification/26.2/hud-icon.png) |
| **Progress bar** | **Time only** |
| ![Progress bar style with an item icon and a horizontal cooldown bar](docs/verification/26.2/hud-bar.png) | ![Compact time-only style showing the item name and remaining time](docs/verification/26.2/hud-text.png) |

Toggle names, decimal time, backgrounds, and sorting by remaining time independently.

### Move and resize the HUD

Press **H** to open the editor. Left-drag the module to move it in any direction. Drag the small handle in its lower-right corner, or scroll while hovering over the module, to resize it.

![HUD editor showing the selection outline, lower-right resize handle, reset button, and Done button](docs/verification/26.2/hud-editor.png)

- Scale from **50% to 300%**, subject to the available screen space.
- Adjust a fixed preview even when no cooldown is active or the HUD is disabled.
- Keep the edited top-left anchor as cooldown entries appear or expire, with screen-edge clamping.
- Save automatically when releasing the mouse or leaving the editor. Use **Reset position / scale** to restore the default layout.

<details>
<summary>Vanilla-style settings and diagnostics</summary>

Press **K** to configure HUD styles, names, time formatting, vanilla cooldown synchronization, and server text recognition. You can also open the HUD editor from this screen.

![Cooldown settings using vanilla buttons and sliders](docs/verification/26.2/settings.png)

Diagnostics are off by default. Start a temporary capture when investigating an unrecognized cooldown, then stop it to export a local report. Captures also stop and export automatically when they time out.

![Cooldown diagnostics with capture, export, and copy-path controls](docs/verification/26.2/diagnostics.png)

</details>

## Installation

1. Install **Fabric Loader 0.19.5 or newer** for your Minecraft version.
2. Install **Fabric API** for that same Minecraft version.
3. Download **one matching JAR** below, place it in your game instance's `mods` folder, and launch Minecraft.

| Minecraft | Java | Download 1.1.0 |
| --- | --- | --- |
| **1.21.11** | 21 or newer | [LegendCoolDown-1.1.0+mc1.21.11.jar](https://github.com/EmbersDrift/LegendCoolDown/releases/download/v1.1.0/LegendCoolDown-1.1.0%2Bmc1.21.11.jar) |
| **26.1.2** | 25 or newer | [LegendCoolDown-1.1.0+mc26.1.2.jar](https://github.com/EmbersDrift/LegendCoolDown/releases/download/v1.1.0/LegendCoolDown-1.1.0%2Bmc26.1.2.jar) |
| **26.2** | 25 or newer | [LegendCoolDown-1.1.0+mc26.2.jar](https://github.com/EmbersDrift/LegendCoolDown/releases/download/v1.1.0/LegendCoolDown-1.1.0%2Bmc26.2.jar) |

The release includes [SHA256SUMS.txt](https://github.com/EmbersDrift/LegendCoolDown/releases/download/v1.1.0/SHA256SUMS.txt) for checksum verification. Remove the previous Legend Cooldown JAR when updating.

Install this mod on the client only. EmbersClient, Live Hider, and TeamViewer are not required. If your EmbersClient installation already contains the same cooldown module, use the standalone mod in place of that module to avoid duplicate protocol registration.

## Controls

| Action | Default key or gesture |
| --- | --- |
| Open settings | **K** |
| Open the HUD editor | **H**, or **Adjust Cooldown HUD** in settings |
| Move the module | Hold the left mouse button inside the module and drag |
| Resize the module | Drag its lower-right handle, or scroll over the module |
| Save and return | **Done** or **Esc** |
| Hide the game HUD | Vanilla **F1** |

Change key bindings under **Options → Controls → Key Binds**. Position and scale sliders remain available in the settings screen.

## Supported cooldown sources

- **Apollo JSON / protobuf:** server messages that display, remove, or reset cooldowns.
- **Legacy Lunar protocol:** older cooldown messages.
- **Vanilla item cooldowns:** combined with server cooldowns, with duplicate cooldown groups suppressed.
- **Server text:** supported formats in action bars, system messages, boss bars, and changing item lore.
- **Item appearance:** matched item model components are preserved, including when the original item is moved or consumed during a cooldown.

When TeamViewer is installed, the mod shares its Apollo receiver. Timers use explicit server data, vanilla state, or confirmed durations. Item interaction alone does not create a timer without a duration and a confirmation signal.

## FAQ

### Why is no cooldown visible after installation?

The HUD appears when a supported cooldown is active. Press **H** to check its preview, position, and scale, then make sure **Cooldown HUD** and the relevant cooldown sources are enabled. If a server sends no cooldown data or uses an unsupported format, support requires the corresponding protocol or text samples.

### How do I report a weapon whose cooldown is missing?

Open **K → Cooldown Diagnostics**, start a capture, close the screen, and use the item. Return to stop and export the capture. Captures last up to three minutes and retain up to 2,000 records. Stopping, timing out, or disconnecting automatically exports a report to `logs/legendcooldown-diagnostics-*.json`.

Reports can contain server addresses, inventory items, and cooldown text. Review and redact anything you do not want to share before attaching a report to a public issue. Diagnostics are off by default, and the mod does not subscribe to player chat events.

### Where are settings stored?

In `config/legendcooldown.json`. Older integer position settings are still supported and convert to more precise screen anchors on the first mouse adjustment.

## Building and verification

Install **JDK 25 or newer**, then run from the project root:

```powershell
# Windows
.\gradlew.bat build
```

```sh
# Linux / macOS
bash ./gradlew build
```

The three release JARs are written to `dist/`. The version projects are `mc12111`, `mc2612`, and `mc262`; shared source code is in `src/client/java/`.

Each version passed **25 unit tests and 37 client / in-game checks**. Client verification used isolated local worlds; online servers were not tested.

| Documentation | Contents |
| --- | --- |
| [Verification and screenshots](docs/VERIFICATION.md) | Test scope, results, and screenshots for all three versions |
| [Migration notes](docs/MIGRATION.md) | Module origins, extraction scope, and version differences |
| [Sensitive information review](docs/SECURITY_REVIEW.md) | Pre-upload checks and excluded files |

## Origins and license

The cooldown logic comes from EmbersClient's complete cooldown module. The HUD editor's interactions are based on its editor, with the standalone interface rewritten using vanilla widgets.

The project retains the upstream **All-Rights-Reserved** designation. Publishing the source does not grant an open-source license or additional redistribution rights to upstream code.
