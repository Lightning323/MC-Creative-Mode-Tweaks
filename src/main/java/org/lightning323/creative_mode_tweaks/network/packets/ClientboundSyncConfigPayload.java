package org.lightning323.creative_mode_tweaks.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lightning323.creative_mode_tweaks.Config;

public record ClientboundSyncConfigPayload(
        boolean enhanceCreativeHotbar,
        boolean enhanceSurvivalHotbar,
        boolean angelPlacementAllowed,
        int angelPlacementDistance
) implements CustomPacketPayload {

    public ClientboundSyncConfigPayload() {
        this(
                Config.enhanceCreativeHotbar,
                Config.enhanceSurvivalHotbar,
                Config.BUILDING_SURVIVAL_ALLOW_ANGEL_PLACEMENT.get(),
                Config.BUILDING_ANGEL_PLACEMENT_DISTANCE.get());
    }

    public static final Type<ClientboundSyncConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "sync_config"));

    // Codec to automatically serialize/deserialize packet data
    public static final StreamCodec<FriendlyByteBuf, ClientboundSyncConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ClientboundSyncConfigPayload::enhanceCreativeHotbar,
            ByteBufCodecs.BOOL, ClientboundSyncConfigPayload::enhanceSurvivalHotbar,
            ByteBufCodecs.BOOL, ClientboundSyncConfigPayload::angelPlacementAllowed,
            ByteBufCodecs.INT, ClientboundSyncConfigPayload::angelPlacementDistance,
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
        });
    }
}
