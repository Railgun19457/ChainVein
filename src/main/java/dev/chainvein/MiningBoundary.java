package dev.chainvein;

import java.util.function.Predicate;
import org.bukkit.block.Block;

@FunctionalInterface
interface MiningBoundary {
    MiningBoundary UNRESTRICTED = origin -> target -> true;

    /**
     * Creates a boundary check for one mining job. The origin and target are in the same world.
     */
    Predicate<Block> forOrigin(Block origin);
}
