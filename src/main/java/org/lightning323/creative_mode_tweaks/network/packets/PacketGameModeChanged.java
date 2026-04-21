package org.lightning323.creative_mode_tweaks.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;
import org.lightning323.creative_mode_tweaks.client.ClientModEvents;

public record PacketGameModeChanged(int gameModeId) implements CustomPacketPayload {

    public static final Type<PacketGameModeChanged> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreativeModeTweaks.MODID, "gamemode_changed"));

    public static final StreamCodec<FriendlyByteBuf, PacketGameModeChanged> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PacketGameModeChanged::gameModeId,
            PacketGameModeChanged::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    // Helper to get the actual GameType enum
    public GameType getGameType() {
        return GameType.byId(gameModeId);
    }

    public void handle(IPayloadContext context) {
        // Enqueue work to the main thread
        context.enqueueWork(() -> {
            context.player();
            ClientModEvents.clientGameModeChanged(context.player(), getGameType());
        });
    }
}