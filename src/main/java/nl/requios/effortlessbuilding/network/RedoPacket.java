package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RedoPacket() implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<RedoPacket> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "redo"));
   public static final StreamCodec<FriendlyByteBuf, RedoPacket> STREAM_CODEC = StreamCodec.of((buf, p) -> {
   }, (buf) -> new RedoPacket());

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
