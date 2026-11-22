# ChainVein

[简体中文](README.md) · English

![ChainVein](https://count.getloli.com/@railgun19457_ChainVein?name=railgun19457_ChainVein&theme=minecraft&padding=6&offset=0&align=top&scale=1&pixelated=1&darkmode=auto)

ChainVein is a Paper plugin for vein mining and tree felling. Break a block in Survival to mine face-adjacent blocks. Administrators can allow different block types in the same chain. Players can choose their own block list and controls using the vanilla Java client.

## Features

- Edit a personal block list with tab completion, bulk block groups, or by right-clicking blocks in the world
- Switch chain mining on or off by command or by swapping hands while sneaking
- Change personal settings by clicking their status in the four-line chat menu
- Check vanilla tool tiers and stack compatible drops while preserving experience, durability, Fortune, and Silk Touch
- Set a server-wide block allowlist, maximum chain size, and search radius
- Honor block protection events and enforce claim boundaries with Easyland
- Expose player state to DeluxeMenus and other menus through PlaceholderAPI

## Requirements

| Paper version | Java version tested | Verification |
| --- | --- | --- |
| 1.21.11 | 21 | Mining, commands, Easyland, PlaceholderAPI, and DeluxeMenus |
| 26.1.2 and 26.2 | 25 | Startup and Easyland and PlaceholderAPI integration |

## Installation

1. Copy the built `ChainVein-<version>.jar` into the Paper server's `plugins` directory.
2. Install Easyland for claim boundaries or PlaceholderAPI for the menu placeholder as needed.
3. Start the server. ChainVein creates `plugins/ChainVein/config.yml` and stores player settings under `plugins/ChainVein/players/<UUID>.yml`.

## Quick Start

Chain mining, the sneak requirement, and the sneak-and-swap shortcut are enabled by default. The starting block list contains all allowed logs and ores. Sneak and break an eligible block with a tool that can harvest it.

Run `/chain settings` to open the menu. Click a status to toggle it, or enter selection mode to add or remove block types by right-clicking.

| Player setting | Effect |
| --- | --- |
| `enabled` | Master switch. It must be on for a chain to start |
| `require-sneak` | Requires sneaking when you finish breaking the first block |
| `sneak-swap` | Lets you toggle the master switch by swapping hands while sneaking (F by default) |

With `enabled: true`, `require-sneak: false`, and `sneak-swap: true`, you can start a chain while standing. Pressing F while sneaking toggles the master switch. Standing and pressing F swaps hands as usual. The block allowlist, your selection, and claim protections still apply.

## Commands

| Command | Description |
| --- | --- |
| `/chain` or `/chain settings` | Show personal settings with clickable controls |
| `/chain on`, `/chain off`, `/chain toggle` | Set or toggle the personal master switch |
| `/chain trigger sneak <on\|off>` | Set whether mining a chain requires sneaking |
| `/chain trigger sneak-swap <on\|off>` | Set whether swapping hands while sneaking toggles the master switch |
| `/chain add <block\|logs\|ores>` | Add a block type. Tab suggests allowed blocks you have yet to add |
| `/chain remove <block\|logs\|ores>` | Remove a block type. Tab suggests blocks from your personal list |
| `/chain edit [on\|off]` | Toggle or set selection mode. Right-click a block to toggle its type in your list |
| `/chain reload` | Reload the configuration from the server console or in-game |

The `logs` group uses the vanilla logs tag. The `ores` group includes ores and ancient debris. Adding a group uses the current allowlist and saves individual block types. Removing a group clears every matching type from your personal list.

## Permissions

| Permission | Description | Default |
| --- | --- | --- |
| `chainvein.use` | Use chain mining and its personal settings commands | All players |
| `chainvein.reload` | Reload the configuration | Operators |

## Configuration

| Setting | Default | Description |
| --- | --- | --- |
| `allowed-blocks` | Logs, ores, and common terrain blocks | Accepts block IDs and group names |
| `max-blocks` | `64` | Maximum number of blocks per chain, including the first block |
| `max-radius` | `16` | Maximum straight-line distance from the first block |
| `cross-block-types` | `true` | Allow a chain to pass through different face-adjacent types that are both allowed and selected |
| `merge-drops` | `true` | Combine compatible drops from one chain up to the item's stack limit |
| `player-defaults.enabled` | `true` | Initial setting for the master switch |
| `player-defaults.require-sneak` | `true` | Require sneaking by default |
| `player-defaults.sneak-swap` | `true` | Enable the sneak-and-swap shortcut by default |
| `player-defaults.blocks` | `[logs, ores]` | Initial block IDs or group names, limited to types in `allowed-blocks` |

Run `/chain reload` after editing the configuration. Player defaults apply to players who have not saved personal settings.

At startup and on reload, ChainVein adds missing keys, removes obsolete keys, and preserves existing values for supported keys. Migration writes directly to `config.yml`.

## Integrations

### Easyland

Additional blocks fire the standard `BlockBreakEvent` so protection plugins can cancel individual breaks. With Easyland installed, a chain stays in the claim it started in. Chains started in the wilderness stay in the wilderness, and parent claims and subclaims have separate boundaries.

If the Easyland boundary integration fails to initialize, ChainVein pauses chain mining. Personal settings, commands, and the status placeholder remain available.

### PlaceholderAPI and Menus

| Placeholder | Value |
| --- | --- |
| `%chainvein_enabled%` | `true` or `false` for the current player's master switch |

A DeluxeMenus button can run the toggle command as the player and refresh the displayed state. For separate buttons, use `chain on` and `chain off`.

```yaml
lore:
  - 'Chain mining enabled %chainvein_enabled%'
left_click_commands:
  - '[player] chain toggle'
  - '[refresh]'
```

## Local Build

Run the Gradle Wrapper with JDK 21 or later. The JAR is written to `build/libs/`.

```powershell
.\gradlew.bat build
```

```bash
./gradlew build
```

If you have already downloaded a PlaceholderAPI JAR, pass `-PplaceholderApiJar=<path>` to use it as the compile-time dependency.
