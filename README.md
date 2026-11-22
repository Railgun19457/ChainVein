# ChainVein

简体中文 · [English](README.en_US.md)

![ChainVein](https://count.getloli.com/@railgun19457_ChainVein?name=railgun19457_ChainVein&theme=minecraft&padding=6&offset=0&align=top&scale=1&pixelated=1&darkmode=auto)

ChainVein 是一个面向 Paper 的连锁采集插件。在生存模式下挖掉一块矿石或原木，即可连锁采集面相邻的方块。管理员可以选择只采集同种方块或跨种类采集。每名玩家可以自己选择方块和触发方式，使用原版 Java 客户端即可。

## 功能特性

- 自定义个人方块列表，支持 Tab 补全、按种类批量添加和右键点选
- 支持命令开关、潜行采集和潜行时交换主副手来切换连锁
- 点击四行聊天菜单中的状态修改个人设置
- 按原版工具等级触发连锁，保留经验、耐久、时运和精准采集，并自动合并可堆叠掉落
- 支持全服方块白名单、单次采集数量上限和搜索半径
- 兼容方块破坏事件保护，与 Easyland 联动时限制连锁跨越领地边界
- 提供 PlaceholderAPI 占位符，可与 DeluxeMenus 等菜单插件配合

## 运行环境

| Paper 版本 | 测试用 Java 版本 | 验证范围 |
| --- | --- | --- |
| 1.21.11 | 21 | 采集、命令、Easyland、PlaceholderAPI 和 DeluxeMenus |
| 26.1.2、26.2 | 25 | 启动及 Easyland、PlaceholderAPI 接入 |

## 安装

1. 将构建好的 `ChainVein-<版本>.jar` 放入 Paper 服务端的 `plugins` 目录。
2. 如需领地边界检查或菜单状态占位符，分别安装 Easyland 或 PlaceholderAPI。
3. 启动服务端。首次运行会生成 `plugins/ChainVein/config.yml`，玩家设置保存到 `plugins/ChainVein/players/<UUID>.yml`。

## 快速上手

默认开启连锁总开关、潜行要求和副手快捷键，初始列表包含白名单允许的原木与矿石。用符合原版采集等级的工具潜行挖掘即可触发。

用 `/chain settings` 查看个人设置。点击状态文字可以切换对应开关，点击「点选编辑」可右键添加或移除方块。

| 玩家设置 | 作用 |
| --- | --- |
| `enabled` | 连锁总开关。开启时才可能连锁 |
| `require-sneak` | 开启时，挖掉起点方块的那一刻必须在潜行 |
| `sneak-swap` | 开启时，潜行中按交换主副手键（默认 F）会切换总开关 |

设为 `enabled: true`、`require-sneak: false`、`sneak-swap: true` 时，站着挖掘已选方块即可连锁。潜行时按 F 会关闭连锁，再按一次会开启；站着按 F 仍会交换主副手。首块和后续方块还需符合白名单、个人列表及领地规则。

## 命令

| 命令 | 说明 |
| --- | --- |
| `/chain` 或 `/chain settings` | 查看个人设置，点击聊天按钮修改 |
| `/chain on`、`/chain off`、`/chain toggle` | 开启、关闭或切换个人连锁总开关 |
| `/chain trigger sneak <on\|off>` | 设置连锁是否要求潜行 |
| `/chain trigger sneak-swap <on\|off>` | 设置潜行时按交换主副手键能否切换总开关 |
| `/chain add <方块\|logs\|ores>` | 添加方块。Tab 补全白名单内尚未选中的方块 |
| `/chain remove <方块\|logs\|ores>` | 移除方块。Tab 补全个人列表中已选中的方块 |
| `/chain edit [on\|off]` | 切换或指定点选模式。右键点击方块切换该种类的启用状态 |
| `/chain reload` | 热加载配置，玩家和控制台均可执行 |

`logs` 匹配原版原木标签中的方块。`ores` 匹配矿石及远古残骸。批量添加只处理当次白名单内的种类，保存为单个方块条目。批量移除则会移除个人列表中属于该种类的全部方块。

## 权限

| 权限节点 | 说明 | 默认 |
| --- | --- | --- |
| `chainvein.use` | 允许使用连锁采集及个人设置命令 | 所有玩家 |
| `chainvein.reload` | 允许使用 `/chain reload` | 管理员 |

## 配置概览

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `allowed-blocks` | 原木、矿石及常见地层方块 | 支持方块 ID 和种类名 |
| `max-blocks` | `64` | 单次连锁最多采集的方块数，包含起点 |
| `max-radius` | `16` | 连锁范围，以起点为中心计算直线距离 |
| `cross-block-types` | `true` | 开启后，同一次连锁可以经过不同种类的面相邻方块，每块仍须同时在白名单和个人列表中 |
| `merge-drops` | `true` | 合并同一次连锁中可堆叠的掉落物，遵守物品的堆叠上限 |
| `player-defaults.enabled` | `true` | 个人总开关的初始值 |
| `player-defaults.require-sneak` | `true` | 默认要求潜行挖掘 |
| `player-defaults.sneak-swap` | `true` | 潜行加副手切换的初始开关 |
| `player-defaults.blocks` | `[logs, ores]` | 支持方块 ID 和种类名，限于 `allowed-blocks` 内的方块 |

修改配置后可执行 `/chain reload` 生效。连锁中的同类掉落会按堆叠上限合并。新玩家默认值适用于尚未保存过个人设置的玩家。

启动和热加载时会补齐缺失键、移除过时键，并保留已有有效项的值。迁移直接保存到 `config.yml`。

## 插件联动

### Easyland

连锁方块会触发标准的 `BlockBreakEvent`，保护插件可逐块取消破坏。安装 Easyland 时，连锁范围还会受起点所在领地限制。野外起挖只在野外扩展，父领地和子领地的交界也会截断连锁。

Easyland 边界接口初始化失败时，ChainVein 会暂停连锁采集。个人设置、命令和状态占位符仍可使用。

### PlaceholderAPI 和菜单

| 占位符 | 返回值 |
| --- | --- |
| `%chainvein_enabled%` | 当前玩家连锁总开关的值，`true` 或 `false` |

DeluxeMenus 的按钮可以用以下配置以玩家身份切换连锁，然后刷新显示。有独立的开启和关闭按钮时，可分别执行 `chain on` 和 `chain off`。

```yaml
lore:
  - '连锁状态 %chainvein_enabled%'
left_click_commands:
  - '[player] chain toggle'
  - '[refresh]'
```

## 本地构建

使用 JDK 21 或以上版本运行 Gradle Wrapper，生成的 JAR 位于 `build/libs/`。

```powershell
.\gradlew.bat build
```

```bash
./gradlew build
```

如果已经下载 PlaceholderAPI 的 JAR，可以通过 `-PplaceholderApiJar=<路径>` 指定本地编译依赖。
