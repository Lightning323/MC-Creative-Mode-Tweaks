package org.lightning323.creative_mode_tweaks.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;

public record DeleteItemPayload(int slotId) implements CustomPacketPayload {

    public static final Type<DeleteItemPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    CreativeModeTweaks.MODID,
                    "delete_item"
            ));

    public static final StreamCodec<FriendlyByteBuf, DeleteItemPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeVarInt(payload.slotId()),
                    buf -> new DeleteItemPayload(buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // Safety check: Only allow this if the player is actually in creative
            if (player.isCreative()) {
                player.getInventory().items.set(slotId, ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
            }
        });
    }
}