package dev.chainvein;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.inventory.ItemStack;

final class ChainDrops {
    private ChainDrops() {
    }

    static void merge(Collection<Item> drops) {
        Map<ItemStack, List<Item>> partial = new HashMap<>();
        for (Item item : drops) {
            if (!canMerge(item)) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            if (stack.getAmount() >= stack.getMaxStackSize()) {
                continue;
            }
            List<Item> targets = partial.computeIfAbsent(stack.asOne(), key -> new ArrayList<>());
            var iterator = targets.iterator();
            while (canMerge(item) && iterator.hasNext()) {
                Item target = iterator.next();
                if (!canMerge(target) || full(target)) {
                    iterator.remove();
                    continue;
                }
                if (compatible(item, target) && new ItemMergeEvent(item, target).callEvent()
                        && compatible(item, target)) {
                    transfer(item, target);
                    if (full(target)) {
                        iterator.remove();
                    }
                }
            }
            if (canMerge(item) && !full(item)) {
                targets.add(item);
            }
        }
    }

    private static boolean canMerge(Item item) {
        return item.isValid() && !item.getItemStack().isEmpty() && item.getPersistentDataContainer().isEmpty()
                && item.getScoreboardTags().isEmpty() && item.customName() == null;
    }

    private static boolean full(Item item) {
        ItemStack stack = item.getItemStack();
        return stack.getAmount() >= stack.getMaxStackSize();
    }

    private static boolean compatible(Item source, Item target) {
        return canMerge(source) && canMerge(target) && source.getWorld().equals(target.getWorld())
                && source.getItemStack().isSimilar(target.getItemStack())
                && Objects.equals(source.getOwner(), target.getOwner())
                && Objects.equals(source.getThrower(), target.getThrower())
                && source.canMobPickup() == target.canMobPickup()
                && source.canPlayerPickup() == target.canPlayerPickup()
                && source.isUnlimitedLifetime() == target.isUnlimitedLifetime();
    }

    private static void transfer(Item source, Item target) {
        ItemStack from = source.getItemStack();
        ItemStack to = target.getItemStack();
        int amount = Math.min(from.getAmount(), to.getMaxStackSize() - to.getAmount());
        if (amount <= 0) {
            return;
        }

        to.setAmount(to.getAmount() + amount);
        target.setItemStack(to);
        target.setPickupDelay(Math.max(source.getPickupDelay(), target.getPickupDelay()));
        if (amount == from.getAmount()) {
            source.remove();
        } else {
            from.setAmount(from.getAmount() - amount);
            source.setItemStack(from);
        }
    }
}
