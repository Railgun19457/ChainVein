package dev.chainvein;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;

record PlayerSettings(boolean enabled, boolean requireSneak, boolean sneakSwap, Set<Material> blocks) {
    static final PlayerSettings DEFAULT = new PlayerSettings(false, true, false, Set.of());

    PlayerSettings {
        blocks = Set.copyOf(blocks);
    }

    PlayerSettings withEnabled(boolean value) {
        return new PlayerSettings(value, requireSneak, sneakSwap, blocks);
    }

    PlayerSettings withRequireSneak(boolean value) {
        return new PlayerSettings(enabled, value, sneakSwap, blocks);
    }

    PlayerSettings withSneakSwap(boolean value) {
        return new PlayerSettings(enabled, requireSneak, value, blocks);
    }

    PlayerSettings withBlocks(Set<Material> materials, boolean added) {
        Set<Material> updated = new HashSet<>(blocks);
        if (added) {
            updated.addAll(materials);
        } else {
            updated.removeAll(materials);
        }
        return new PlayerSettings(enabled, requireSneak, sneakSwap, updated);
    }
}
