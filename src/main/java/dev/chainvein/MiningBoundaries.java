package dev.chainvein;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

final class MiningBoundaries {
    private MiningBoundaries() {
    }

    static Optional<MiningBoundary> load(PluginManager plugins, Logger logger) {
        Plugin easyland = plugins.getPlugin("Easyland");
        if (easyland == null) {
            return Optional.of(MiningBoundary.UNRESTRICTED);
        }

        try {
            if (!easyland.isEnabled()) {
                throw new IllegalStateException("Easyland failed to enable");
            }
            MiningBoundary boundary = new EasylandBoundary(easyland);
            logger.info("Easyland boundary checks enabled.");
            return Optional.of(boundary);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logger.log(Level.SEVERE, "Could not initialize Easyland boundary checks; chain mining is disabled.",
                    exception);
            return Optional.empty();
        }
    }
}
