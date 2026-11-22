package dev.chainvein;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;

final class ChainMining implements Listener {
    private static final int BREAKS_PER_TICK = 8;
    private static final int CHECKS_PER_TICK = 48;

    private final ChainVeinPlugin plugin;
    private final MiningBoundary boundary;
    private final Map<UUID, MiningJob> active = new HashMap<>();

    ChainMining(ChainVeinPlugin plugin, MiningBoundary boundary) {
        this.plugin = plugin;
        this.boundary = boundary;
    }

    record Options(Set<Material> allowedBlocks, int maxBlocks, long radiusSquared, boolean crossBlockTypes,
            boolean mergeDrops) {
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();
        Options options = plugin.miningOptions();
        if (options.maxBlocks() <= 1 || options.radiusSquared() == 0 || active.containsKey(player.getUniqueId())
                || player.getGameMode() != GameMode.SURVIVAL || !player.hasPermission(ChainVeinPlugin.USE_PERMISSION)
                || !options.allowedBlocks().contains(material)) {
            return;
        }

        PlayerSettings settings = plugin.readSettings(player.getUniqueId());
        if (settings == null || !settings.enabled() || !settings.blocks().contains(material)
                || (settings.requireSneak() && !player.isSneaking())
                || !canHarvest(block, player.getInventory().getItemInMainHand())) {
            return;
        }

        // Wait for the initiating vanilla break before attempting neighboring blocks.
        MiningJob job = new MiningJob(event, material, options);
        active.put(player.getUniqueId(), job);
        job.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        MiningJob job = active.get(event.getPlayer().getUniqueId());
        if (job != null && job.options.mergeDrops() && event.getBlock().equals(job.dropSource)) {
            job.drops.addAll(event.getItems());
        }
    }

    private static boolean canHarvest(Block block, ItemStack tool) {
        return !block.getBlockData().requiresCorrectToolForDrops() || block.isPreferredTool(tool);
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
        private final Options options;
        private final ItemStack tool;
        private final int slot;
        private final Queue<Position> pending = new ArrayDeque<>();
        private final Set<Position> visited = new HashSet<>();
        private final Set<Item> drops = new LinkedHashSet<>();

        private Predicate<Block> withinBoundary;
        private Block dropSource;
        private int broken = 1;

        MiningJob(BlockBreakEvent initialEvent, Material material, Options options) {
            this.initialEvent = initialEvent;
            player = initialEvent.getPlayer();
            Block block = initialEvent.getBlock();
            world = block.getWorld();
            origin = new Position(block.getX(), block.getY(), block.getZ());
            this.material = material;
            this.options = options;
            dropSource = block;
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
                while (!pending.isEmpty() && broken < options.maxBlocks() && checks < CHECKS_PER_TICK
                        && breaks < BREAKS_PER_TICK) {
                    Position next = pending.remove();
                    checks++;
                    if (!loaded(next)) {
                        continue;
                    }

                    Block block = world.getBlockAt(next.x(), next.y(), next.z());
                    Material target = block.getType();
                    if (target != material && (!options.crossBlockTypes() || !options.allowedBlocks().contains(target))) {
                        continue;
                    }
                    PlayerSettings settings = activeSettings();
                    if (settings == null) {
                        finish();
                        return;
                    }
                    if (!settings.blocks().contains(target)
                            || !canHarvest(block, player.getInventory().getItemInMainHand())
                            || !withinBoundary.test(block)) {
                        continue;
                    }
                    if (breakBlock(block)) {
                        broken++;
                        breaks++;
                        if (broken < options.maxBlocks()) {
                            addNeighbors(next);
                        }
                    }
                }

                if (pending.isEmpty() || broken == options.maxBlocks()) {
                    finish();
                }
            } catch (RuntimeException exception) {
                finish();
                plugin.getLogger().log(Level.WARNING, "Stopped a ChainVein mining job", exception);
            }
        }

        private boolean breakBlock(Block block) {
            dropSource = block;
            try {
                return player.breakBlock(block);
            } finally {
                dropSource = null;
            }
        }

        private boolean begin() {
            dropSource = null;
            if (initialEvent.isCancelled() || !loaded(origin) || initialEvent.getBlock().getType() == material) {
                return false;
            }

            withinBoundary = boundary.forOrigin(initialEvent.getBlock());
            visited.add(origin);
            addNeighbors(origin);
            return true;
        }

        private PlayerSettings activeSettings() {
            if (options != plugin.miningOptions() || !player.isOnline() || player.isDead() || !player.getWorld().equals(world)
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
            if (dx * dx + dy * dy + dz * dz <= options.radiusSquared()) {
                Position position = new Position(x, y, z);
                if (visited.add(position)) {
                    pending.add(position);
                }
            }
        }

        private void finish() {
            if (active.remove(player.getUniqueId(), this)) {
                cancel();
                if (broken > 1 && options.mergeDrops()) {
                    ChainDrops.merge(drops);
                }
            }
        }
    }

    private record Position(int x, int y, int z) {
    }
}
