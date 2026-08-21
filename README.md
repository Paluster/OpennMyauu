# OpennMyauu

This client is based on [OpenMyau](https://github.com/60124808866/OpenMyau). It improves existing features and adds new ones. All of the code was written by AI.

## Added

- **ShowNick**, **HackerDetector** — from [MWE](https://github.com/Alexdoru/MWE)
- **InventoryFill**, **MouseDelayFix** — from [VapeV4.21](https://github.com/OpenVapeCN/VapeV4.21)
- **SkinHider**, **BlockESP**

## Improved

- **InvManager**, **ChestStealer**, **ItemESP**

## Usage

Minecraft **1.8.9 Forge**. Put `OpennMyauu.jar` in `.minecraft/mods`. Modules default to off. Configure with `.` commands (no ClickGUI):

```
.help
.toggle hud
.bind killaura R
.config save
```

Configs stay in `.minecraft/config/Myau/`.

## Build

JDK 8 to compile the mod, Gradle 8.8 to run the build:

```bash
gradlew build
```

Output: `build/libs/OpennMyauu.jar`.
