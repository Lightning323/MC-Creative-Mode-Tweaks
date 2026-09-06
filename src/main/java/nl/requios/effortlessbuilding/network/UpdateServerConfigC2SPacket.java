package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateServerConfigC2SPacket(String json) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<UpdateServerConfigC2SPacket> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "update_server_config"));
   public static final StreamCodec<FriendlyByteBuf, UpdateServerConfigC2SPacket> STREAM_CODEC = StreamCodec.of(UpdateServerConfigC2SPacket::encode, UpdateServerConfigC2SPacket::decode);

   private static void encode(FriendlyByteBuf buf, UpdateServerConfigC2SPacket p) {
      buf.writeUtf(p.json, 32767);
   }

   private static UpdateServerConfigC2SPacket decode(FriendlyByteBuf buf) {
      return new UpdateServerConfigC2SPacket(buf.readUtf(32767));
   }

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
