package dev.chainvein;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ChainVeinExpansion extends PlaceholderExpansion {
    private final ChainVeinPlugin plugin;

    ChainVeinExpansion(ChainVeinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "chainvein";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ChainVein";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String parameter) {
        if (player == null || !parameter.equalsIgnoreCase("enabled")) {
            return null;
        }
        PlayerSettings settings = plugin.readSettings(player.getUniqueId());
        return Boolean.toString(settings != null && settings.enabled());
    }
}
