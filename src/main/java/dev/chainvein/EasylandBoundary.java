package dev.chainvein;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

final class EasylandBoundary implements MiningBoundary {
    private final Plugin easyland;
    private final Object api;
    private final Method getLandAt;
    private final Method getId;

    EasylandBoundary(Plugin easyland) throws ReflectiveOperationException {
        this.easyland = easyland;
        // Resolve Easyland's public API only on servers that install it.
        ClassLoader loader = easyland.getClass().getClassLoader();
        Class<?> apiType = loader.loadClass("io.github.railgun19457.easyland.api.EasylandAPI");
        Class<?> landType = loader.loadClass("io.github.railgun19457.easyland.model.Land");
        api = easyland.getClass().getMethod("getAPI").invoke(null);
        if (!apiType.isInstance(api)) {
            throw new IllegalStateException("Easyland API is unavailable");
        }
        getLandAt = apiType.getMethod("getLandAt", Location.class);
        getId = landType.getMethod("getId");
    }

    @Override
    public Predicate<Block> forOrigin(Block origin) {
        OptionalInt originLand = landAt(origin.getLocation());
        return target -> originLand.equals(landAt(target.getLocation()));
    }

    private OptionalInt landAt(Location location) {
        if (!easyland.isEnabled()) {
            throw new IllegalStateException("Easyland is no longer enabled");
        }
        try {
            Optional<?> land = (Optional<?>) getLandAt.invoke(api, location);
            return land.isEmpty() ? OptionalInt.empty() : OptionalInt.of((int) getId.invoke(land.get()));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not query Easyland for " + location, exception);
        }
    }
}
