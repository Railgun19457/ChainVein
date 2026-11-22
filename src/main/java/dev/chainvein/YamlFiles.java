package dev.chainvein;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.bukkit.configuration.file.YamlConfiguration;

final class YamlFiles {
    private YamlFiles() {
    }

    static void writeAtomically(Path file, YamlConfiguration yaml) throws IOException {
        Path directory = file.getParent();
        Files.createDirectories(directory);
        Path pending = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(pending, yaml.saveToString());
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
    }
}
