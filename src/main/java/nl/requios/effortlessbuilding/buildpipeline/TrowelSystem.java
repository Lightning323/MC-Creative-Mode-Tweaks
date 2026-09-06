package nl.requios.effortlessbuilding.buildpipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nl.requios.effortlessbuilding.item.TrowelItem;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Applies Trowel block selection to build-mode placement and its client preview. */
public final class TrowelSystem implements IBuildSystem {
    public static final TrowelSystem INSTANCE = new TrowelSystem();
    private static final int HOTBAR_SIZE = 9;
    private static final ResourceLocation QUARK_TROWEL = ResourceLocation.fromNamespaceAndPath("quark", "trowel");
    private static final TagKey<Item> QUARK_TROWEL_BLACKLIST = ItemTags.create(ResourceLocation.fromNamespaceAndPath("quark", "trowel_blacklist"));

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
        return isTrowel(player.getMainHandItem());
    }

    /** Recognizes this mod's trowel and Quark's optional trowel without a hard dependency on Quark. */
    public static boolean isTrowel(ItemStack stack) {
        return stack.getItem() instanceof TrowelItem
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(QUARK_TROWEL);
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
                if (!isUsableBlock(player, stack) || !stack.is(entry.getKey())) {
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
            if (isUsableBlock(player, stack)) {
                result.add(stack);
            }
        }
        return result;
    }

    private static boolean isUsableBlock(Player player, ItemStack stack) {
        return (!isQuarkTrowel(player.getMainHandItem()) || !stack.is(QUARK_TROWEL_BLACKLIST))
                && stack.getItem() instanceof BlockItem
                && stack.getComponentsPatch().isEmpty()
                && BuildPipeline.isBuildTriggerItem(stack);
    }

    private static boolean isQuarkTrowel(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(QUARK_TROWEL);
    }

    private static ItemStack chooseBlock(List<ItemStack> blocks, long position) {
        long value = position + -7046029254386353131L;
        value = (value ^ value >>> 30) * -4658895280553007687L;
        value = (value ^ value >>> 27) * -7723592293110705685L;
        value ^= value >>> 31;
        return blocks.get((int) Math.floorMod(value, blocks.size()));
    }
}
