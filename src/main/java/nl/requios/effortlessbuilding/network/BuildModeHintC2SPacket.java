package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BuildModeHintC2SPacket() implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<BuildModeHintC2SPacket> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "build_mode_hint"));
   public static final StreamCodec<FriendlyByteBuf, BuildModeHintC2SPacket> STREAM_CODEC = StreamCodec.of((buf, packet) -> {
   }, (buf) -> new BuildModeHintC2SPacket());

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
