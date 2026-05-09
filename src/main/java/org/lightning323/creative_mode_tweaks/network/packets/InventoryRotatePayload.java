package org.lightning323.creative_mode_tweaks.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;
import org.lightning323.creative_mode_tweaks.utils.HotbarUtil;

public record InventoryRotatePayload(int offset, boolean keepSelection) implements CustomPacketPayload {

    public static final Type<InventoryRotatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    CreativeModeTweaks.MODID,
                    "inventory_rotate"
            ));

    public static final StreamCodec<FriendlyByteBuf, InventoryRotatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeVarInt(payload.offset()).writeBoolean(payload.keepSelection()),
                    buf -> new InventoryRotatePayload(buf.readVarInt(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            HotbarUtil.rotateInventory(player, offset(), keepSelection());
            player.inventoryMenu.broadcastChanges();
        });
    }
}