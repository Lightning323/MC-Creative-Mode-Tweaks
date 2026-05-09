package org.lightning323.creative_mode_tweaks.network.packets;

import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;
import org.lightning323.creative_mode_tweaks.utils.HotbarUtil;

import java.util.Collections;

public record InventoryRotatePayload(int offset) implements CustomPacketPayload {

    public static final Type<InventoryRotatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    CreativeModeTweaks.MODID,
                    "inventory_rotate"
            ));

    public static final StreamCodec<FriendlyByteBuf, InventoryRotatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeVarInt(payload.offset()),
                    buf -> new InventoryRotatePayload(buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.isCreative()) return;
            HotbarUtil.rotateInventory(player,offset());
            player.inventoryMenu.broadcastChanges();
        });
    }
}