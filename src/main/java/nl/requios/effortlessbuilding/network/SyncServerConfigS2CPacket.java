package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncServerConfigS2CPacket(String json) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<SyncServerConfigS2CPacket> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "sync_server_config"));
   public static final StreamCodec<FriendlyByteBuf, SyncServerConfigS2CPacket> STREAM_CODEC = StreamCodec.of(SyncServerConfigS2CPacket::encode, SyncServerConfigS2CPacket::decode);

   private static void encode(FriendlyByteBuf buf, SyncServerConfigS2CPacket p) {
      buf.writeUtf(p.json, 32767);
   }

   private static SyncServerConfigS2CPacket decode(FriendlyByteBuf buf) {
      return new SyncServerConfigS2CPacket(buf.readUtf(32767));
   }

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
