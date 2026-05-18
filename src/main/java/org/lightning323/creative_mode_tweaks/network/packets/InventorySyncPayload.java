package org.lightning323.creative_mode_tweaks.network.packets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;
import org.lightning323.creative_mode_tweaks.utils.InventoryHasher;

import java.util.Objects;

public record InventorySyncPayload(int hash, NonNullList<ItemStack> items) implements CustomPacketPayload {

    public static final Type<InventorySyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "inventory_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, InventorySyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.hash()); // 1. Write the desiredHash first
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, payload.items());
            },
            buf -> {
                int decodedHash = buf.readInt(); // 2. Read the desiredHash back first
                var list = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);

                NonNullList<ItemStack> nonNullList = NonNullList.withSize(list.size(), ItemStack.EMPTY);
                for (int i = 0; i < list.size(); i++) {
                    nonNullList.set(i, list.get(i));
                }
                // 3. Pass both parameters to compile correctly
                return new InventorySyncPayload(decodedHash, nonNullList);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(final IPayloadContext context) {
        context.enqueueWork(() -> {
            Inventory inv = context.player().getInventory();
            int ourHash = InventoryHasher.computeInventoryHash(inv);

            // 5. Only overwrite if the hashes do not match
            if (ourHash != this.hash) {
                int containerSize = inv.getContainerSize();
                int targetSize = Math.min(items().size(), containerSize);

                // 1. Gather all current items into a tracking list (the pool)
                java.util.List<ItemStack> itemPool = new java.util.ArrayList<>();
                for (int i = 0; i < targetSize; i++) {
                    ItemStack existing = inv.getItem(i);
                    if (!existing.isEmpty()) {
                        itemPool.add(existing); // Keep a reference to the real instances
                    }
                }

                // 2. Temporarily clear the slots we are rearranging so we don't duplicate items
                for (int i = 0; i < targetSize; i++) {
                    inv.setItem(i, ItemStack.EMPTY);
                }

                // 3. Reconstruct the inventory based on the incoming target items
                for (int i = 0; i < targetSize; i++) {
                    ItemStack targetStack = items().get(i);

                    if (targetStack.isEmpty()) {
                        continue; // Slot should stay empty
                    }

                    // Strategy A: Search for a PERFECT match (Type + Count + Component Data)
                    ItemStack perfectMatch = ItemStack.EMPTY;
                    for (ItemStack poolStack : itemPool) {
                        if (ItemStack.isSameItemSameComponents(poolStack, targetStack) && poolStack.getCount() == targetStack.getCount()) {
                            perfectMatch = poolStack;
                            break;
                        }
                    }

                    if (!perfectMatch.isEmpty()) {
                        itemPool.remove(perfectMatch);
                        inv.setItem(i, perfectMatch); // Rearrange existing instance
                        continue;
                    }

                    // Strategy B: Search for a DATA match but different stack count (e.g. split/combine)
                    ItemStack dataMatch = ItemStack.EMPTY;
                    for (ItemStack poolStack : itemPool) {
                        if (ItemStack.isSameItemSameComponents(poolStack, targetStack)) {
                            dataMatch = poolStack;
                            break;
                        }
                    }

                    if (!dataMatch.isEmpty()) {
                        if (dataMatch.getCount() > targetStack.getCount()) {
                            // The existing stack has MORE than we need; split it
                            ItemStack placedPiece = dataMatch.split(targetStack.getCount());
                            inv.setItem(i, placedPiece);
                        } else {
                            // The existing stack has LESS or EQUAL (but failed perfect match). Take all of it.
                            itemPool.remove(dataMatch);
                            inv.setItem(i, dataMatch);
                            // Note: If it's less, vanilla container logic or subsequent ticks will adjust it,
                            // but to guarantee exact matching when the pool falls short, we top it off below.
                            dataMatch.setCount(targetStack.getCount());
                        }
                        continue;
                    }

                    // Strategy C: Absolute fallback. No matching item data found in the pool, instantiate a copy.
                    inv.setItem(i, targetStack.copy());
                }

                ourHash = InventoryHasher.computeInventoryHash(inv);
                CreativeModeTweaks.LOGGER.debug("Inventory updated on {}. Our: {} {} Target: {}",
                        context.player() instanceof LocalPlayer ? "Client" : "Server",
                        ourHash, ourHash == hash ? "=" : "!=", hash);
            }
        });
    }
}