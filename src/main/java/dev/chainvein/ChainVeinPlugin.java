package dev.chainvein;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChainVeinPlugin extends JavaPlugin {
    static final String USE_PERMISSION = "chainvein.use";

    private SettingsStore settings;
    private Set<Material> allowedBlocks;
    private Runnable unregisterExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new SettingsStore(getDataFolder().toPath().resolve("players"), getLogger());

        Set<Material> allowed = EnumSet.noneOf(Material.class);
        for (String name : getConfig().getStringList("allowed-blocks")) {
            Material material = parseBlock(name);
            if (material == null) {
                getLogger().warning("Invalid block in allowed-blocks: " + name);
            } else {
                allowed.add(material);
            }
        }
        allowedBlocks = Set.copyOf(allowed);

        ChainCommand command = new ChainCommand(this);
        PluginCommand registration = getCommand("chain");
        if (registration == null) {
            throw new IllegalStateException("Missing chain command in plugin.yml");
        }
        registration.setExecutor(command);
        registration.setTabCompleter(command);
        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(command, this);
        MiningBoundaries.load(plugins, getLogger()).ifPresent(boundary -> plugins.registerEvents(new ChainMining(
                this, boundary, Math.max(1, getConfig().getInt("max-blocks", 64)),
                Math.max(0, getConfig().getInt("max-radius", 16))), this));

        if (plugins.isPluginEnabled("PlaceholderAPI")) {
            registerExpansion();
        }
    }

    private void registerExpansion() {
        ChainVeinExpansion expansion = new ChainVeinExpansion(this);
        if (expansion.register()) {
            unregisterExpansion = expansion::unregister;
        } else {
            getLogger().warning("Could not register the chainvein PlaceholderAPI expansion.");
        }
    }

    @Override
    public void onDisable() {
        if (unregisterExpansion != null) {
            unregisterExpansion.run();
            unregisterExpansion = null;
        }
    }

    Set<Material> allowedBlocks() {
        return allowedBlocks;
    }

    PlayerSettings readSettings(UUID playerId) {
        return settings.get(playerId);
    }

    boolean saveSettings(Player player, PlayerSettings settings) {
        try {
            this.settings.save(player.getUniqueId(), settings);
            return true;
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Could not save ChainVein settings for " + player.getUniqueId(), exception);
            ChainCommand.send(player, "无法保存连锁设置，请联系管理员。");
            return false;
        }
    }

    static Material parseBlock(String name) {
        Material material = Material.matchMaterial(name);
        return material != null && material.isBlock() && !material.isAir() ? material : null;
    }

    static String blockKey(Material material) {
        return material.getKey().asString();
    }
}
