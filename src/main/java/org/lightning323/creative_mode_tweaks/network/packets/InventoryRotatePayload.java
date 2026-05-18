package org.lightning323.creative_mode_tweaks.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;
import org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil;
import org.lightning323.creative_mode_tweaks.utils.InventoryUtils;

public record InventoryRotatePayload(int desiredHash, int offset,
                                     boolean keepSelection) implements CustomPacketPayload {

    public static final Type<InventoryRotatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    CreativeModeTweaks.MODID,
                    "inventory_rotate"
            ));

    public static final StreamCodec<FriendlyByteBuf, InventoryRotatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf
                            .writeVarInt(payload.desiredHash())
                            .writeVarInt(payload.offset())
                            .writeBoolean(payload.keepSelection()),
                    buf -> new InventoryRotatePayload(buf.readVarInt(), buf.readVarInt(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            //Step 1: update the inventory on the server
            HotbarUtil.rotateInventory(context.player(), offset(), keepSelection());
            int ourHash = InventoryUtils.computeInventoryHash(context.player().getInventory());

            if (context.player() instanceof ServerPlayer player) {
                //Step 2: Send the request to the client to rotate the inventory AFTER the server has done it already
                PacketDistributor.sendToServer(new InventoryRotatePayload(ourHash, offset, keepSelection));
            } else {
                //Step 3: After we have rotated the inventory on the client, force the server to match us (This should never happen)
                if (desiredHash != 0 && ourHash != desiredHash) { //Check the hashes and ensure they match
                    CreativeModeTweaks.LOGGER.error("Inventory Hash mismatch: {} != {}", ourHash, desiredHash);
                    PacketDistributor.sendToServer(new InventorySyncPayload(ourHash, context.player().getInventory().items));
                }
            }
        });
    }
}