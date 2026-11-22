package dev.chainvein;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
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
        FileConfiguration config;
        try {
            config = ConfigMigration.load(this);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load ChainVein config.yml", exception);
        }
        allowedBlocks = Set.copyOf(readBlocks(config, "allowed-blocks"));
        Set<Material> initialBlocks = readBlocks(config, "player-defaults.blocks");
        if (initialBlocks.retainAll(allowedBlocks)) {
            getLogger().warning("Ignoring player-defaults.blocks entries outside allowed-blocks.");
        }

        PlayerSettings defaults = new PlayerSettings(
                config.getBoolean("player-defaults.enabled", PlayerSettings.DEFAULT.enabled()),
                config.getBoolean("player-defaults.require-sneak", PlayerSettings.DEFAULT.requireSneak()),
                config.getBoolean("player-defaults.sneak-swap", PlayerSettings.DEFAULT.sneakSwap()),
                initialBlocks);
        settings = new SettingsStore(getDataFolder().toPath().resolve("players"), getLogger(), defaults);

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
                this, boundary, Math.max(1, config.getInt("max-blocks", 64)),
                Math.max(0, config.getInt("max-radius", 16)), config.getBoolean("cross-block-types", false)), this));

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
            ChainCommand.send(player, "无法保存连锁设置，请联系管理员");
            return false;
        }
    }

    private Set<Material> readBlocks(FileConfiguration config, String path) {
        Set<Material> blocks = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList(path)) {
            Material block = parseBlock(name);
            if (block == null) {
                getLogger().warning("Invalid block '" + name + "' in " + path);
            } else {
                blocks.add(block);
            }
        }
        return blocks;
    }

    static Material parseBlock(String name) {
        Material material = Material.matchMaterial(name);
        return material != null && material.isBlock() && !material.isAir() ? material : null;
    }

    static String blockKey(Material material) {
        return material.getKey().asString();
    }
}
