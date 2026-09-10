package org.lightning323.creative_mode_tweaks.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lightning323.creative_mode_tweaks.Config;

public record ClientboundSyncConfigPayload(
        boolean enhanceCreativeHotbar,
        boolean enhanceSurvivalHotbar,
        boolean angelPlacementAllowed,
        int angelPlacementDistance,
        int creativeSingleReach
) implements CustomPacketPayload {

    /** Sentinel meaning "no per-player override, use the config value". */
    public static final int NO_REACH_OVERRIDE = -1;

    public ClientboundSyncConfigPayload(ServerPlayer player) {
        this(
                Config.enhanceCreativeHotbar,
                Config.enhanceSurvivalHotbar,
                Config.BUILDING_SURVIVAL_ALLOW_ANGEL_PLACEMENT.get(),
                Config.BUILDING_ANGEL_PLACEMENT_DISTANCE.get(),
                resolveSingleReach(player));
    }

    private static int resolveSingleReach(ServerPlayer player) {
        Integer override = Config.getServerSingleReachOverride(player.getUUID());
        return override != null ? override : NO_REACH_OVERRIDE;
    }

    public static final Type<ClientboundSyncConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "sync_config"));

    // Codec to automatically serialize/deserialize packet data
    public static final StreamCodec<FriendlyByteBuf, ClientboundSyncConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ClientboundSyncConfigPayload::enhanceCreativeHotbar,
            ByteBufCodecs.BOOL, ClientboundSyncConfigPayload::enhanceSurvivalHotbar,
            ByteBufCodecs.BOOL, ClientboundSyncConfigPayload::angelPlacementAllowed,
            ByteBufCodecs.INT, ClientboundSyncConfigPayload::angelPlacementDistance,
            ByteBufCodecs.INT, ClientboundSyncConfigPayload::creativeSingleReach,
            ClientboundSyncConfigPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // This handles the packet logic when it arrives on the client
    public static void handle(final ClientboundSyncConfigPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // Forward data to your Client-Side Config
            Config.enhanceCreativeHotbar = payload.enhanceCreativeHotbar();
            Config.enhanceSurvivalHotbar = payload.enhanceSurvivalHotbar();
            Config.updateClientAngelPlacementSettings(payload.angelPlacementAllowed(), payload.angelPlacementDistance());
            Config.updateClientSingleReach(
                    payload.creativeSingleReach() == NO_REACH_OVERRIDE ? null : payload.creativeSingleReach());
        });
    }
}
