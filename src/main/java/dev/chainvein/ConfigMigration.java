package dev.chainvein;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class ConfigMigration {
    private ConfigMigration() {
    }

    static YamlConfiguration load(ChainVeinPlugin plugin) throws IOException, InvalidConfigurationException {
        plugin.saveDefaultConfig();
        Path path = plugin.getDataFolder().toPath().resolve("config.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.load(path.toFile());

        YamlConfiguration template = new YamlConfiguration();
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                throw new IOException("Missing bundled config.yml");
            }
            template.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        if (syncKeys(config, template)) {
            YamlFiles.writeAtomically(path, config);
            plugin.getLogger().info("Updated config.yml keys.");
        }
        return config;
    }

    private static boolean syncKeys(ConfigurationSection current, ConfigurationSection template) {
        boolean changed = false;
        for (String key : current.getKeys(false)) {
            if (!template.contains(key)) {
                current.set(key, null);
                changed = true;
            }
        }

        for (String key : template.getKeys(false)) {
            if (template.get(key) instanceof ConfigurationSection expectedSection) {
                ConfigurationSection section = current.getConfigurationSection(key);
                if (section == null) {
                    section = current.createSection(key);
                    copyComments(current, template, key);
                    changed = true;
                }
                changed |= syncKeys(section, expectedSection);
            } else if (!current.contains(key) || current.isConfigurationSection(key)) {
                current.set(key, template.get(key));
                copyComments(current, template, key);
                changed = true;
            }
        }
        return changed;
    }

    private static void copyComments(ConfigurationSection current, ConfigurationSection template, String key) {
        current.setComments(key, template.getComments(key));
        current.setInlineComments(key, template.getInlineComments(key));
    }
}
