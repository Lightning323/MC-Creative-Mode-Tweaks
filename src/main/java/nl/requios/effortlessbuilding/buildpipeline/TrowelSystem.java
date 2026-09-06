package nl.requios.effortlessbuilding.buildpipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nl.requios.effortlessbuilding.item.TrowelItem;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Applies Trowel block selection to build-mode placement and its client preview. */
public final class TrowelSystem implements IBuildSystem {
    public static final TrowelSystem INSTANCE = new TrowelSystem();
    private static final int HOTBAR_SIZE = 9;

    private TrowelSystem() {
    }

    @Override
    public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
        if (action != BuildPipeline.BuildState.PLACING || !isHoldingTrowel(player)) {
            return;
        }

        List<ItemStack> hotbarBlocks = getHotbarBlocks(player);
        if (hotbarBlocks.isEmpty()) {
            blocks.clear();
            return;
        }

        for (BlockEntry entry : blocks.values()) {
            BlockItem blockItem = (BlockItem) chooseBlock(hotbarBlocks, entry.blockPos.asLong()).getItem();
            entry.item = blockItem;
            entry.blockState = blockItem.getBlock().defaultBlockState();
        }
    }

    public static boolean isHoldingTrowel(Player player) {
        return player.getMainHandItem().getItem() instanceof TrowelItem;
    }

    public static Map<Item, Integer> getHotbarBlockCounts(Player player) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        for (ItemStack stack : getHotbarBlocks(player)) {
            result.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return result;
    }

    public static void consumeHotbarItems(Player player, Map<Item, Integer> usage) {
        for (Map.Entry<Item, Integer> entry : usage.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = 0; slot < HOTBAR_SIZE && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!isUsableBlock(stack) || !stack.is(entry.getKey())) {
                    continue;
                }

                int consumed = Math.min(remaining, stack.getCount());
                stack.shrink(consumed);
                remaining -= consumed;
            }
        }
        player.getInventory().setChanged();
    }

    private static List<ItemStack> getHotbarBlocks(Player player) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isUsableBlock(stack)) {
                result.add(stack);
            }
        }
        return result;
    }

    private static boolean isUsableBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem
                && stack.getComponentsPatch().isEmpty()
                && BuildPipeline.isBuildTriggerItem(stack);
    }

    private static ItemStack chooseBlock(List<ItemStack> blocks, long position) {
        long value = position + -7046029254386353131L;
        value = (value ^ value >>> 30) * -4658895280553007687L;
        value = (value ^ value >>> 27) * -7723592293110705685L;
        value ^= value >>> 31;
        return blocks.get((int) Math.floorMod(value, blocks.size()));
    }
}
