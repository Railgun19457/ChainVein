package dev.chainvein;

import java.util.Map;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.Tag;

final class BlockGroups {
    static final Map<String, Predicate<Material>> BY_NAME = Map.of(
            "logs", material -> Tag.LOGS.isTagged(material),
            "ores", material -> material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS);

    private BlockGroups() {
    }
}
