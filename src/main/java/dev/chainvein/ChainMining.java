package dev.chainvein;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;

final class ChainMining implements Listener {
    private static final int BREAKS_PER_TICK = 8;
    private static final int CHECKS_PER_TICK = 48;

    private final ChainVeinPlugin plugin;
    private final MiningBoundary boundary;
    private final int maxBlocks;
    private final long radiusSquared;
    private final boolean crossBlockTypes;
    private final Set<UUID> active = new HashSet<>();

    ChainMining(ChainVeinPlugin plugin, MiningBoundary boundary, int maxBlocks, int maxRadius,
            boolean crossBlockTypes) {
        this.plugin = plugin;
        this.boundary = boundary;
        this.maxBlocks = maxBlocks;
        this.radiusSquared = (long) maxRadius * maxRadius;
        this.crossBlockTypes = crossBlockTypes;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material material = event.getBlock().getType();
        if (maxBlocks <= 1 || radiusSquared == 0 || active.contains(player.getUniqueId())
                || player.getGameMode() != GameMode.SURVIVAL || !player.hasPermission(ChainVeinPlugin.USE_PERMISSION)
                || !plugin.allowedBlocks().contains(material)) {
            return;
        }

        PlayerSettings settings = plugin.readSettings(player.getUniqueId());
        if (settings == null || !settings.enabled() || !settings.blocks().contains(material)
                || (settings.requireSneak() && !player.isSneaking())) {
            return;
        }

        // Wait for the initiating vanilla break before attempting neighboring blocks.
        MiningJob job = new MiningJob(event, material);
        active.add(player.getUniqueId());
        job.runTaskTimer(plugin, 1L, 1L);
    }

    private static ItemStack toolSignature(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand().clone();
        if (item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(0);
            item.setItemMeta(damageable);
        }
        return item;
    }

    private final class MiningJob extends BukkitRunnable {
        private final BlockBreakEvent initialEvent;
        private final Player player;
        private final World world;
        private final Position origin;
        private final Material material;
        private final ItemStack tool;
        private final int slot;
        private final Queue<Position> pending = new ArrayDeque<>();
        private final Set<Position> visited = new HashSet<>();

        private Predicate<Block> withinBoundary;
        private int broken = 1;

        MiningJob(BlockBreakEvent initialEvent, Material material) {
            this.initialEvent = initialEvent;
            player = initialEvent.getPlayer();
            Block block = initialEvent.getBlock();
            world = block.getWorld();
            origin = new Position(block.getX(), block.getY(), block.getZ());
            this.material = material;
            tool = toolSignature(player);
            slot = player.getInventory().getHeldItemSlot();
        }

        @Override
        public void run() {
            try {
                if (activeSettings() == null || (withinBoundary == null && !begin())) {
                    finish();
                    return;
                }

                int checks = 0;
                int breaks = 0;
                while (!pending.isEmpty() && broken < maxBlocks && checks < CHECKS_PER_TICK
                        && breaks < BREAKS_PER_TICK) {
                    Position next = pending.remove();
                    checks++;
                    if (!loaded(next)) {
                        continue;
                    }

                    Block block = world.getBlockAt(next.x(), next.y(), next.z());
                    Material target = block.getType();
                    if (target != material && (!crossBlockTypes || !plugin.allowedBlocks().contains(target))) {
                        continue;
                    }
                    PlayerSettings settings = activeSettings();
                    if (settings == null) {
                        finish();
                        return;
                    }
                    if (!settings.blocks().contains(target) || !withinBoundary.test(block)) {
                        continue;
                    }
                    if (player.breakBlock(block)) {
                        broken++;
                        breaks++;
                        if (broken < maxBlocks) {
                            addNeighbors(next);
                        }
                    }
                }

                if (pending.isEmpty() || broken == maxBlocks) {
                    finish();
                }
            } catch (RuntimeException exception) {
                finish();
                plugin.getLogger().log(Level.WARNING, "Stopped a ChainVein mining job", exception);
            }
        }

        private boolean begin() {
            if (initialEvent.isCancelled() || !loaded(origin) || initialEvent.getBlock().getType() == material) {
                return false;
            }

            withinBoundary = boundary.forOrigin(initialEvent.getBlock());
            visited.add(origin);
            addNeighbors(origin);
            return true;
        }

        private PlayerSettings activeSettings() {
            if (!player.isOnline() || player.isDead() || !player.getWorld().equals(world)
                    || player.getGameMode() != GameMode.SURVIVAL || !player.hasPermission(ChainVeinPlugin.USE_PERMISSION)
                    || player.getInventory().getHeldItemSlot() != slot || !tool.equals(toolSignature(player))) {
                return null;
            }
            PlayerSettings settings = plugin.readSettings(player.getUniqueId());
            return settings != null && settings.enabled() && settings.blocks().contains(material) ? settings : null;
        }

        private boolean loaded(Position position) {
            return world.isChunkLoaded(position.x() >> 4, position.z() >> 4);
        }

        private void addNeighbors(Position position) {
            offer(position.x() + 1, position.y(), position.z());
            offer(position.x() - 1, position.y(), position.z());
            offer(position.x(), position.y() + 1, position.z());
            offer(position.x(), position.y() - 1, position.z());
            offer(position.x(), position.y(), position.z() + 1);
            offer(position.x(), position.y(), position.z() - 1);
        }

        private void offer(int x, int y, int z) {
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                return;
            }

            long dx = (long) x - origin.x();
            long dy = (long) y - origin.y();
            long dz = (long) z - origin.z();
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                Position position = new Position(x, y, z);
                if (visited.add(position)) {
                    pending.add(position);
                }
            }
        }

        private void finish() {
            cancel();
            active.remove(player.getUniqueId());
        }
    }

    private record Position(int x, int y, int z) {
    }
}
