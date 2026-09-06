package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UndoPacket() implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<UndoPacket> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "undo"));
   public static final StreamCodec<FriendlyByteBuf, UndoPacket> STREAM_CODEC = StreamCodec.of((buf, p) -> {
   }, (buf) -> new UndoPacket());

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
