package org.lightning323.creative_mode_tweaks.network.packets;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static org.lightning323.creative_mode_tweaks.utils.InventoryUtils.syncInventory;

public record InventorySyncPayload(NonNullList<ItemStack> targetItems) implements CustomPacketPayload {

    public static final Type<InventorySyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "inventory_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InventorySyncPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
        ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, payload.targetItems());
    }, buf -> {
        //Read the target inventory
        var list = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);
        NonNullList<ItemStack> nonNullList = NonNullList.withSize(list.size(), ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) {
            nonNullList.set(i, list.get(i));
        }
        // 3. Pass both parameters to compile correctly
        return new InventorySyncPayload(nonNullList);
    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(final IPayloadContext context) {
        context.enqueueWork(() -> {
            syncInventory(context.player().getInventory(), targetItems);
        });
    }
}