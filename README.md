# OpennMyauu

Minecraft **1.8.9 Forge** 客户端。仓库名和构建产物是 `OpennMyauu`，游戏内仍使用 Myau 的配置目录和聊天前缀，旧配置可以直接接着用。

基于 Myau `250910` 的反编译源码整理，并加上一批本地模块与修复。没有 ClickGUI，全部用聊天命令配置。

## 使用

1. 安装 Minecraft 1.8.9 Forge。
2. 把 `OpennMyauu.jar` 放进 `.minecraft/mods`（自己构建，或从本仓库 Releases 下载）。
3. 进游戏后，模块默认关闭。命令以 `.` 开头，例如：

```
.help
.toggle hud
.bind killaura R
.config save
```

配置、好友和 SkinHider 贴图在：

```
.minecraft/config/Myau/
```

和 LabyMod 3.9.62 一起用时，下列模块按这个组合测过：ShowNick、HackerDetector、MouseDelayFix、SkinHider、InventoryFill、BlockESP、ItemESP、ChestStealer、InvManager。

## 命令

| 命令 | 作用 |
|---|---|
| `.help` | 常用命令列表 |
| `.<模块>` / `.toggle` | 开关或改模块选项 |
| `.bind` | 绑定按键 |
| `.config` | 读取 / 保存配置 |
| `.friend` / `.enemy` | 好友和敌人 |
| `.blockesp` / `.itemesp` | 自定义方块 / 掉落物高亮 |
| `.cheststealer` | 箱子过滤 |
| `.skinhider` | 本地皮肤和披风 |

`.help` 不会列出 `.blockesp`、`.itemesp`、`.cheststealer`、`.skinhider`，但这些命令可以直接用。

## 构建

需要 **JDK 8** 编译模组，Gradle 8.8 运行构建。

```bash
gradlew build
```

产物在 `build/libs/OpennMyauu.jar`。

## 说明

- 这是反编译后再改的源码，不是官方付费客户端的原始工程。
- 游戏内名称、`mcmod.info` 和 `config/Myau/` 都保持 Myau，避免旧配置失效。
- 本仓库只放源码。jar 请走 GitHub Releases。
