# Project structure

This repository uses one Git working tree and three Gradle subprojects. Each subproject builds the mod for one exact Minecraft version. The version directories are build targets, rather than separate Git worktrees or copies of the shared implementation.

## Directory layout

```text
LegendCoolDown/
├── settings.gradle                  # Explicit project names and directory mappings
├── build.gradle                     # Aggregate build, checks, and distribution tasks
├── gradle/
│   ├── minecraft-version.gradle      # Shared Minecraft build and adaptation rules
│   └── wrapper/
├── versions/
│   ├── minecraft-1.21.11/
│   │   └── build.gradle             # Minecraft 1.21.11, Fabric API, Java 21 target
│   ├── minecraft-26.1.2/
│   │   └── build.gradle             # Minecraft 26.1.2, Fabric API, Java 25 target
│   └── minecraft-26.2/
│       └── build.gradle             # Minecraft 26.2, Fabric API, Java 25 target
├── src/                             # Shared client code, resources, and unit tests
├── tools/                           # Development smoke mod and artifact verification
├── docs/                            # Documentation and checked-in screenshots
└── dist/                            # Generated release JARs; ignored by Git
```

## Naming and responsibilities

- Use the complete Minecraft version, including dots and patch numbers: `minecraft-1.21.11`, `minecraft-26.1.2`, and `minecraft-26.2`.
- Keep version projects under `versions/`. Their Gradle paths use the same names, such as `:minecraft-26.1.2`.
- `settings.gradle` maps each logical project directly to its directory. It does not create a synthetic `:versions` project.
- Each version's `build.gradle` declares its Minecraft version, Fabric API version, Java bytecode target, and Loom plugin. The shared script rejects mismatched project names or directories.
- Shared source code stays in `src/`. Adapted source, compiled classes, test reports, and local game runs stay in each project's ignored `build/` directory.
- Release filenames use the existing `LegendCoolDown-<mod-version>+mc<minecraft-version>.jar` format. Screenshots remain organized by complete version under `docs/verification/`.

## Commands

Run commands from the repository root. On Windows, replace `./gradlew` with `.\gradlew.bat`.

```sh
# Build and check every target, then collect release JARs in dist/
./gradlew build

# Build one exact target
./gradlew :minecraft-1.21.11:build
./gradlew :minecraft-26.1.2:build
./gradlew :minecraft-26.2:build

# Launch an isolated client smoke run
./gradlew :minecraft-26.2:runSmoke

# Verify artifacts and collect evidence after all three smoke runs
python tools/verify_artifacts.py
```

For Minecraft 26.2, generated source is under `versions/minecraft-26.2/build/generated/legendcooldown/`, unit test results under `versions/minecraft-26.2/build/test-results/test/`, and smoke results under `versions/minecraft-26.2/build/smoke-run/`.

## Adding a Minecraft target

1. Add the exact version to the list in `settings.gradle`.
2. Create `versions/minecraft-<version>/build.gradle` with the correct dependencies, Java target, Loom plugin, and shared script reference.
3. Add any required API adaptation rules to `gradle/minecraft-version.gradle`.
4. Extend `tools/verify_artifacts.py`, the download tables, and the verification documentation for the new target.
5. Build the target and run its smoke task before publishing its artifact.

If separate Git worktrees are needed for concurrent branch development, give them branch/task-based directory names outside this checkout. Do not use the version target directories as Git worktrees.
