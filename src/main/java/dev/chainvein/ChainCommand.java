package dev.chainvein;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
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

    private final ChainVeinPlugin plugin;
    private final Set<UUID> editors = new HashSet<>();

    ChainCommand(ChainVeinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("此指令由玩家执行。");
            return true;
        }

        PlayerSettings settings = plugin.readSettings(player.getUniqueId());
        if (settings == null) {
            send(player, "无法读取连锁设置，请联系管理员。");
            return true;
        }

        String subcommand = args.length == 0 ? "settings" : lower(args[0]);
        int expectedLength = switch (subcommand) {
            case "settings" -> args.length == 0 ? 0 : 1;
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
            case "settings" -> showSettings(player, settings);
            case "on" -> setEnabled(player, settings, true);
            case "off" -> setEnabled(player, settings, false);
            case "toggle" -> setEnabled(player, settings, !settings.enabled());
            case "trigger" -> setTrigger(player, settings, lower(args[1]), lower(args[2]));
            case "edit" -> toggleEditor(player);
            case "add", "remove" -> {
                Material material = ChainVeinPlugin.parseBlock(args[1]);
                if (material == null) {
                    send(player, "无效的方块 ID。");
                } else {
                    changeBlock(player, settings, material, subcommand.equals("add"));
                }
            }
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
        if (subcommand.equals("trigger")) {
            if (args.length == 2) {
                return matching(TRIGGERS, args[1]);
            }
            if (args.length == 3 && TRIGGERS.contains(lower(args[1]))) {
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
        boolean adding = subcommand.equals("add");
        Set<Material> candidates = adding ? plugin.allowedBlocks() : settings.blocks();
        String prefix = lower(args[1]);
        return candidates.stream()
                .filter(material -> !adding || !settings.blocks().contains(material))
                .filter(material -> prefix.contains(":")
                        ? ChainVeinPlugin.blockKey(material).startsWith(prefix)
                        : material.getKey().getKey().startsWith(prefix))
                .map(ChainVeinPlugin::blockKey)
                .sorted()
                .toList();
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

    private void changeBlock(Player player, PlayerSettings settings, Material material, boolean adding) {
        if (adding && !plugin.allowedBlocks().contains(material)) {
            send(player, "该方块不在连锁白名单内，请联系管理员添加。");
            return;
        }
        String key = ChainVeinPlugin.blockKey(material);
        if (settings.blocks().contains(material) == adding) {
            send(player, adding ? key + " 已在个人列表中。" : key + " 未加入个人列表。");
            return;
        }
        if (plugin.saveSettings(player, settings.withBlock(material, adding))) {
            send(player, (adding ? "已添加 " : "已移除 ") + key + "。");
        }
    }

    private void toggleEditor(Player player) {
        if (editors.add(player.getUniqueId())) {
            send(player, "已进入点选模式。右键点击方块添加或移除，再运行 /chain edit 退出。");
        } else {
            editors.remove(player.getUniqueId());
            send(player, "已退出点选模式。");
        }
    }

    private void setEnabled(Player player, PlayerSettings settings, boolean enabled) {
        if (plugin.saveSettings(player, settings.withEnabled(enabled))) {
            send(player, "连锁采集已" + stateLabel(enabled) + "。");
        }
    }

    private void setTrigger(Player player, PlayerSettings settings, String trigger, String value) {
        if (!TRIGGERS.contains(trigger) || !VALUES.contains(value)) {
            sendUsage(player);
            return;
        }
        boolean enabled = value.equals("on");
        boolean sneak = trigger.equals("sneak");
        PlayerSettings updated = sneak ? settings.withRequireSneak(enabled) : settings.withSneakSwap(enabled);
        if (plugin.saveSettings(player, updated)) {
            send(player, (sneak ? "潜行要求" : "潜行加副手切换") + "已" + stateLabel(enabled) + "。");
        }
    }

    private static void showSettings(Player player, PlayerSettings settings) {
        showSetting(player, "连锁总开关", settings.enabled(), "/chain");
        showSetting(player, "潜行要求", settings.requireSneak(), "/chain trigger sneak");
        showSetting(player, "潜行加副手切换", settings.sneakSwap(), "/chain trigger sneak-swap");
    }

    private static void showSetting(Player player, String label, boolean enabled, String command) {
        String state = enabled ? "<green>开启</green>" : "<yellow>关闭</yellow>";
        String action = enabled ? "off" : "on";
        player.sendMessage(MINI_MESSAGE.deserialize("<gray>" + label + " </gray>" + state
                + " <click:run_command:'" + command + " " + action + "'><aqua>["
                + stateLabel(!enabled) + "]</aqua></click>"));
    }

    private static void sendUsage(Player player) {
        send(player, "/chain <on|off|toggle|settings|edit>");
        send(player, "/chain <add|remove> <方块>");
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
