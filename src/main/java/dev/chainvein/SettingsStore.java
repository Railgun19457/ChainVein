package dev.chainvein;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class SettingsStore {
    private final Path directory;
    private final Logger logger;
    private final ConcurrentMap<UUID, Optional<PlayerSettings>> cache = new ConcurrentHashMap<>();

    SettingsStore(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    PlayerSettings get(UUID playerId) {
        return cache.computeIfAbsent(playerId, id -> Optional.ofNullable(load(id))).orElse(null);
    }

    private PlayerSettings load(UUID playerId) {
        Path file = fileFor(playerId);
        if (Files.notExists(file)) {
            return PlayerSettings.DEFAULT;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            logger.log(Level.WARNING, "Could not read ChainVein settings for " + playerId, exception);
            return null;
        }

        Set<Material> blocks = new HashSet<>();
        for (String name : yaml.getStringList("blocks")) {
            Material material = ChainVeinPlugin.parseBlock(name);
            if (material != null) {
                blocks.add(material);
            }
        }

        return new PlayerSettings(
                yaml.getBoolean("enabled", PlayerSettings.DEFAULT.enabled()),
                yaml.getBoolean("require-sneak", PlayerSettings.DEFAULT.requireSneak()),
                yaml.getBoolean("sneak-swap", PlayerSettings.DEFAULT.sneakSwap()),
                blocks);
    }

    void save(UUID playerId, PlayerSettings settings) throws IOException {
        if (settings.equals(get(playerId))) {
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", settings.enabled());
        yaml.set("require-sneak", settings.requireSneak());
        yaml.set("sneak-swap", settings.sneakSwap());
        yaml.set("blocks", settings.blocks().stream().map(ChainVeinPlugin::blockKey).sorted().toList());

        Files.createDirectories(directory);
        Path file = fileFor(playerId);
        Path pending = Files.createTempFile(directory, playerId.toString(), ".tmp");
        try {
            Files.writeString(pending, yaml.saveToString());
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
            cache.put(playerId, Optional.of(settings));
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    private Path fileFor(UUID playerId) {
        return directory.resolve(playerId + ".yml");
    }
}
