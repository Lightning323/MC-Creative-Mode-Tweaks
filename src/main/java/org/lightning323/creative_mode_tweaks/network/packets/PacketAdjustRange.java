package org.lightning323.creative_mode_tweaks.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;
import org.lightning323.creative_mode_tweaks.utils.ServerSettings;

public record PacketAdjustRange(double dist) implements CustomPacketPayload {

    public static final Type<PacketAdjustRange> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreativeModeTweaks.MODID, "adjust_range"));

    // StreamCodec replaces toBytes and the FriendlyByteBuf constructor
    public static final StreamCodec<FriendlyByteBuf, PacketAdjustRange> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, PacketAdjustRange::dist,
            PacketAdjustRange::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Called by the PacketHandler registration
     */
    public void handle(IPayloadContext context) {
        // Enqueue work to the main thread
        context.enqueueWork(() -> {
            if ((context.player() instanceof ServerPlayer player)) {
                if (!player.isCreative() || dist < 0) {
                    ServerSettings.clearRangeModifier(player);
                } else {
                    double clampedDist = Mth.clamp(dist, Config.REACH_MIN_RANGE.get(), Config.REACH_MAX_RANGE.get());
                    ServerSettings.changeRangeModifier(player, clampedDist);
                }
            }
        });
    }
}