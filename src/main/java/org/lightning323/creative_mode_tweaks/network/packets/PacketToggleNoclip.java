package org.lightning323.creative_mode_tweaks.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;
import org.lightning323.creative_mode_tweaks.utils.mixin.Player_I;

public record PacketToggleNoclip(boolean enabled) implements CustomPacketPayload {

    public static final Type<PacketToggleNoclip> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreativeModeTweaks.MODID, "toggle_noclip"));

    // Modern StreamCodec replaces encode and decode methods
    public static final StreamCodec<FriendlyByteBuf, PacketToggleNoclip> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PacketToggleNoclip::enabled,
            PacketToggleNoclip::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // Context.player() in playToServer is always the sending ServerPlayer
            if (context.player() instanceof ServerPlayer player) {
                // Ensure the player is in creative (safety check)
                if (!player.isCreative()) return;

                Player_I player_i = (Player_I) player;
                player_i.setNoClip(this.enabled);

                // Replaced System.out with a proper logger if available,
                // but kept the logic for parity with your snippet
                CreativeModeTweaks.LOGGER.debug("SERVER: NoClip: {} player: {}",
                        player_i.isNoClip(),
                        player.getName().getString());
            }
        });
    }
}