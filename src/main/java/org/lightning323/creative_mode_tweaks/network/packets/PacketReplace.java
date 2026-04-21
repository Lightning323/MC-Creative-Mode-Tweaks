package org.lightning323.creative_mode_tweaks.network.packets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;

public record PacketReplace(BlockPos pos, BlockState state, BlockState checkState) implements CustomPacketPayload {

    public static final Type<PacketReplace> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreativeModeTweaks.MODID, "replace"));

    /**
     * Using RegistryFriendlyByteBuf because BlockStates require registry access for ID mapping.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketReplace> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PacketReplace::pos,
            ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), PacketReplace::state,
            ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), PacketReplace::checkState,
            PacketReplace::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // Security & Validation
            if (!player.isCreative()) return;

            if (state.isAir()) {
                return;
            }

            // Using center of block for distance check is usually more accurate
            double dist = player.getEyePosition(1.0F).distanceTo(pos.getCenter());
            if (dist > Config.REACH_MAX_RANGE.get()) {
                return;
            }

            // Verify the block hasn't changed since the client sent the packet
            if (!player.level().getBlockState(pos).equals(checkState)) {
                return;
            }

            player.level().setBlock(pos, state, 2);
        });
    }
}