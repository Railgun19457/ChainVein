# ChainVein

简体中文 · [English](README.en_US.md)

![ChainVein](https://count.getloli.com/@railgun19457_ChainVein?name=railgun19457_ChainVein&theme=minecraft&padding=6&offset=0&align=top&scale=1&pixelated=1&darkmode=auto)

ChainVein 是一个面向 Paper 的连锁采集插件。在生存模式下挖掉一块矿石或原木，即可连锁采集与它面相邻的同种方块。每名玩家可以自己选择方块和触发方式，使用原版 Java 客户端即可。

## 功能特性

- 自定义个人方块列表，支持命令 Tab 补全和右键点选
- 支持命令开关、潜行采集和潜行时交换主副手来切换连锁
- 通过 MiniMessage 聊天按钮修改个人快捷设置，状态按玩家保存
- 保留原版掉落、经验、耐久、时运和精准采集
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

新玩家的连锁总开关和副手快捷切换默认关闭，潜行要求默认开启，个人方块列表默认为空。添加铁矿石并开启连锁后，潜行挖掘铁矿石即可触发。

```text
/chain add iron_ore
/chain on
```

用 `/chain settings` 点击修改开关。关闭潜行要求和副手快捷切换后，可以完全通过指令或菜单控制连锁。

## 命令

| 命令 | 说明 |
| --- | --- |
| `/chain` 或 `/chain settings` | 查看个人设置，点击聊天按钮修改 |
| `/chain on`、`/chain off`、`/chain toggle` | 开启、关闭或切换个人连锁总开关 |
| `/chain trigger sneak <on\|off>` | 设置连锁是否要求潜行 |
| `/chain trigger sneak-swap <on\|off>` | 设置潜行时按交换主副手键能否切换总开关 |
| `/chain add <方块>` | 添加方块。Tab 补全白名单内尚未选中的方块 |
| `/chain remove <方块>` | 移除方块。Tab 补全个人列表中已选中的方块 |
| `/chain edit` | 进入或退出右键点选模式。点击方块切换该种类的启用状态 |

## 权限

| 权限节点 | 说明 | 默认 |
| --- | --- | --- |
| `chainvein.use` | 允许使用连锁采集及个人设置命令 | 所有玩家 |

## 配置概览

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `allowed-blocks` | 常见矿石、原木和菌柄 | 管理员允许连锁的方块种类 |
| `max-blocks` | `64` | 单次连锁最多采集的方块数，包含起点 |
| `max-radius` | `16` | 连锁范围，以起点为中心计算直线距离 |

修改配置后重启服务端。采集搜索仅访问已加载的区块。

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
