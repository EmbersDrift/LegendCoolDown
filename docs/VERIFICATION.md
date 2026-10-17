# Verification results

All three versions built successfully. Release JARs are available in `dist/` and [GitHub Releases](https://github.com/EmbersDrift/LegendCoolDown/releases/latest).

| Minecraft | Unit tests | Client / in-game checks | Screenshots |
| --- | --- | --- | --- |
| 1.21.11 | 25 / 25 | 37 / 37 | [Settings](verification/1.21.11/settings.png), [Diagnostics](verification/1.21.11/diagnostics.png), [HUD](verification/1.21.11/hud-ring.png), [Editor](verification/1.21.11/hud-editor.png) |
| 26.1.2 | 25 / 25 | 37 / 37 | [Settings](verification/26.1.2/settings.png), [Diagnostics](verification/26.1.2/diagnostics.png), [HUD](verification/26.1.2/hud-ring.png), [Editor](verification/26.1.2/hud-editor.png) |
| 26.2 | 25 / 25 | 37 / 37 | [Settings](verification/26.2/settings.png), [Diagnostics](verification/26.2/diagnostics.png), [HUD](verification/26.2/hud-ring.png), [Editor](verification/26.2/hud-editor.png) |

Each version also has icon, progress-bar, and time-only screenshots. Images show the vanilla world, settings/diagnostics widgets, and cooldown HUD. Test data was used only in isolated worlds under each project's `build/smoke-run/` directory.

## Coverage

Checks cover Apollo JSON/protobuf display, removal and reset, malformed payload handling, vanilla cooldown Mixins, the dedicated action-bar entry point, boss-bar updates, full item model binding, icon retention after item consumption, ready-message removal, vanilla timer integration and deduplication, vanilla settings initialization, and diagnostic export.

Version 1.1.0 adds editor checks for the settings entry point, previews with the HUD disabled and no active cooldown, movement on both axes, all four screen edges, resizing in both directions, wheel scaling in both directions, scrolling outside the module, configuration persistence, returning to settings, and reset. Geometry tests cover position precision, bounds, wrapped-column hit areas, window resizing, and stable anchors as cooldown entries appear or expire.

## Artifact checks

Release artifacts declare an exact Minecraft version. The 1.21.11 JAR uses Java 21 bytecode, while the 26.x JARs use Java 25 bytecode. Each contains only the four cooldown Mixins, has no required EmbersClient or Live Hider dependency, and excludes the development smoke mod. Mixin descriptors in the 1.21.11 release are remapped to intermediary names.

## Environment and limits

Build and client verification used Windows and JDK 26. Client checks ran in the Fabric development environment with offline local worlds. They did not connect to online servers or replace mods in existing user game instances. Server-specific protocols and cooldown data not sent by a server are outside this verification scope.

After reproducing the build and the three `runSmoke` tasks, run `python tools/verify_artifacts.py` to check the artifacts, collect test results, and copy screenshots. The full check lists, SHA-256 hashes, and artifact sizes are recorded in [results.json](verification/results.json).
