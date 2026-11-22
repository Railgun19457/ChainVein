package dev.chainvein;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

final class ChainCommand implements TabExecutor, Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMANDS = List.of("on", "off", "toggle", "trigger", "settings", "add",
            "remove", "edit");
    private static final List<String> TRIGGERS = List.of("sneak", "sneak-swap");
    private static final List<String> VALUES = List.of("on", "off");
    private static final List<String> SETTINGS = List.of("enabled", "sneak", "sneak-swap");
    private static final Map<String, Predicate<Material>> BLOCK_GROUPS = Map.of(
            "logs", material -> Tag.LOGS.isTagged(material),
            "ores", material -> material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS);

    private final ChainVeinPlugin plugin;
    private final Set<UUID> editors = new HashSet<>();

    ChainCommand(ChainVeinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("此指令由玩家执行");
            return true;
        }

        PlayerSettings settings = plugin.readSettings(player.getUniqueId());
        if (settings == null) {
            send(player, "无法读取连锁设置，请联系管理员");
            return true;
        }

        String subcommand = args.length == 0 ? "settings" : lower(args[0]);
        int expectedLength = switch (subcommand) {
            case "settings" -> args.length == 3 ? 3 : Math.min(args.length, 1);
            case "on", "off", "toggle", "edit" -> 1;
            case "add", "remove" -> 2;
            case "trigger" -> 3;
            default -> -1;
        };
        if (args.length != expectedLength) {
            sendUsage(player);
            return true;
        }

        switch (subcommand) {
            case "settings" -> handleSettings(player, settings, args);
            case "on" -> setEnabled(player, settings, true);
            case "off" -> setEnabled(player, settings, false);
            case "toggle" -> setEnabled(player, settings, !settings.enabled());
            case "trigger" -> setTrigger(player, settings, lower(args[1]), lower(args[2]));
            case "edit" -> toggleEditor(player);
            case "add", "remove" -> changeSelection(player, settings, args[1], subcommand.equals("add"));
            default -> sendUsage(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission(ChainVeinPlugin.USE_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return matching(SUBCOMMANDS, args[0]);
        }

        String subcommand = lower(args[0]);
        if (subcommand.equals("trigger") || subcommand.equals("settings")) {
            List<String> keys = subcommand.equals("trigger") ? TRIGGERS : SETTINGS;
            if (args.length == 2) {
                return matching(keys, args[1]);
            }
            if (args.length == 3 && keys.contains(lower(args[1]))) {
                return matching(VALUES, args[2]);
            }
        }
        if (args.length != 2 || !(subcommand.equals("add") || subcommand.equals("remove"))) {
            return List.of();
        }

        PlayerSettings settings = plugin.readSettings(player.getUniqueId());
        if (settings == null) {
            return List.of();
        }
        Set<Material> candidates = changeableBlocks(settings, subcommand.equals("add"));
        String prefix = lower(args[1]);
        List<String> suggestions = new ArrayList<>();
        for (Material material : candidates) {
            String key = ChainVeinPlugin.blockKey(material);
            if ((prefix.contains(":") ? key : material.getKey().getKey()).startsWith(prefix)) {
                suggestions.add(key);
            }
        }
        BLOCK_GROUPS.forEach((name, group) -> {
            if (name.startsWith(prefix) && candidates.stream().anyMatch(group)) {
                suggestions.add(name);
            }
        });
        suggestions.sort(String::compareTo);
        return suggestions;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEditClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !editors.contains(player.getUniqueId()) || !player.hasPermission(ChainVeinPlugin.USE_PERMISSION)) {
            return;
        }

        // Both hands must be denied, but only the main hand edits the selection.
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        PlayerSettings settings = plugin.readSettings(player.getUniqueId());
        if (settings != null) {
            Material material = event.getClickedBlock().getType();
            changeBlock(player, settings, material, !settings.blocks().contains(material));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSneakSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.hasPermission(ChainVeinPlugin.USE_PERMISSION)) {
            return;
        }
        PlayerSettings settings = plugin.readSettings(player.getUniqueId());
        if (settings != null && settings.sneakSwap()) {
            event.setCancelled(true);
            setEnabled(player, settings, !settings.enabled());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        editors.remove(event.getPlayer().getUniqueId());
    }

    private void changeSelection(Player player, PlayerSettings settings, String argument, boolean adding) {
        String groupName = lower(argument);
        Predicate<Material> group = BLOCK_GROUPS.get(groupName);
        if (group == null) {
            Material material = ChainVeinPlugin.parseBlock(argument);
            if (material == null) {
                send(player, "无效的方块 ID 或种类。可用种类为 logs、ores");
            } else {
                changeBlock(player, settings, material, adding);
            }
            return;
        }

        Set<Material> blocks = changeableBlocks(settings, adding);
        blocks.removeIf(group.negate());
        if (blocks.isEmpty()) {
            send(player, adding ? "该种类没有可新增的白名单方块" : "个人列表中没有该种类的方块");
            return;
        }
        if (plugin.saveSettings(player, settings.withBlocks(blocks, adding))) {
            send(player, (adding ? "已添加 " : "已移除 ") + blocks.size() + " 种方块（" + groupName + "）");
        }
    }

    private Set<Material> changeableBlocks(PlayerSettings settings, boolean adding) {
        Set<Material> candidates = new HashSet<>(adding ? plugin.allowedBlocks() : settings.blocks());
        if (adding) {
            candidates.removeAll(settings.blocks());
        }
        return candidates;
    }

    private void changeBlock(Player player, PlayerSettings settings, Material material, boolean adding) {
        if (adding && !plugin.allowedBlocks().contains(material)) {
            send(player, "该方块不在连锁白名单内，请联系管理员添加");
            return;
        }
        String key = ChainVeinPlugin.blockKey(material);
        if (settings.blocks().contains(material) == adding) {
            send(player, adding ? key + " 已在个人列表中" : key + " 未加入个人列表");
            return;
        }
        if (plugin.saveSettings(player, settings.withBlocks(Set.of(material), adding))) {
            send(player, (adding ? "已添加 " : "已移除 ") + key + "");
        }
    }

    private void toggleEditor(Player player) {
        if (editors.add(player.getUniqueId())) {
            send(player, "已进入点选模式。右键点击方块添加或移除，再运行 /chain edit 退出");
        } else {
            editors.remove(player.getUniqueId());
            send(player, "已退出点选模式");
        }
    }

    private void setEnabled(Player player, PlayerSettings settings, boolean enabled) {
        if (plugin.saveSettings(player, settings.withEnabled(enabled))) {
            send(player, "连锁采集已" + stateLabel(enabled) + "");
        }
    }

    private void setTrigger(Player player, PlayerSettings settings, String trigger, String value) {
        if (!TRIGGERS.contains(trigger) || !VALUES.contains(value)) {
            sendUsage(player);
            return;
        }
        boolean enabled = value.equals("on");
        if (plugin.saveSettings(player, withSetting(settings, trigger, enabled))) {
            send(player, (trigger.equals("sneak") ? "潜行要求" : "潜行加副手切换") + "已" + stateLabel(enabled) + "");
        }
    }

    private void handleSettings(Player player, PlayerSettings settings, String[] args) {
        if (args.length == 3) {
            String key = lower(args[1]);
            String value = lower(args[2]);
            if (!SETTINGS.contains(key) || !VALUES.contains(value)) {
                sendUsage(player);
                return;
            }
            settings = withSetting(settings, key, value.equals("on"));
            if (!plugin.saveSettings(player, settings)) {
                return;
            }
        }
        showSettings(player, settings);
    }

    private static PlayerSettings withSetting(PlayerSettings settings, String key, boolean enabled) {
        return switch (key) {
            case "enabled" -> settings.withEnabled(enabled);
            case "sneak" -> settings.withRequireSneak(enabled);
            case "sneak-swap" -> settings.withSneakSwap(enabled);
            default -> throw new IllegalArgumentException("Unknown setting " + key);
        };
    }

    private void showSettings(Player player, PlayerSettings settings) {
        long selected = settings.blocks().stream().filter(plugin.allowedBlocks()::contains).count();
        String summary;
        if (!settings.enabled()) {
            summary = "连锁已关闭。";
        } else if (selected == 0) {
            summary = "连锁已开启，请先添加可连锁方块。";
        } else {
            summary = settings.requireSneak() ? "潜行挖掘已选方块时触发连锁。" : "挖掘已选方块时触发连锁。";
        }
        send(player, summary + " 已选 " + selected + " 种");
        showSetting(player, "连锁总开关", settings.enabled(), "enabled", "总开关开启后才可触发连锁");
        showSetting(player, "潜行要求", settings.requireSneak(), "sneak", "开启后需要潜行挖掘");
        showSetting(player, "潜行加副手切换", settings.sneakSwap(), "sneak-swap", "潜行时按交换主副手键来切换总开关");

        boolean editing = editors.contains(player.getUniqueId());
        player.sendMessage(menuButton("添加原木", "/chain add logs", "添加白名单内的原木")
                .append(Component.space())
                .append(menuButton("添加矿石", "/chain add ores", "添加白名单内的矿石"))
                .append(Component.space())
                .append(menuButton(editing ? "退出点选" : "进入点选", "/chain edit", "右键点击方块以添加或移除"))
                .append(Component.space())
                .append(menuButton("刷新", "/chain settings", "查看最新设置")));
    }

    private static void showSetting(Player player, String label, boolean enabled, String key, String help) {
        String state = enabled ? "<green>开启</green>" : "<yellow>关闭</yellow>";
        String action = enabled ? "off" : "on";
        player.sendMessage(MINI_MESSAGE.deserialize("<gray>" + label + " </gray>" + state + " ")
                .append(menuButton(stateLabel(!enabled) + label, "/chain settings " + key + " " + action, help)));
    }

    private static Component menuButton(String label, String command, String help) {
        return MINI_MESSAGE.deserialize("<aqua>[" + label + "]</aqua>")
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(help)));
    }

    private static void sendUsage(Player player) {
        send(player, "/chain <on|off|toggle|settings|edit>");
        send(player, "/chain <add|remove> <方块|logs|ores>");
        send(player, "/chain trigger <sneak|sneak-swap> <on|off>");
    }

    private static String stateLabel(boolean enabled) {
        return enabled ? "开启" : "关闭";
    }

    static void send(Player player, String message) {
        player.sendMessage(Component.text("[ChainVein] " + message));
    }

    private static List<String> matching(List<String> values, String input) {
        String prefix = lower(input);
        return values.stream().filter(value -> value.startsWith(prefix)).toList();
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
