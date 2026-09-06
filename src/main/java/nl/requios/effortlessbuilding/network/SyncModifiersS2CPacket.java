package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncModifiersS2CPacket(String json) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<SyncModifiersS2CPacket> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "sync_modifiers"));
   public static final StreamCodec<FriendlyByteBuf, SyncModifiersS2CPacket> STREAM_CODEC = StreamCodec.of(SyncModifiersS2CPacket::encode, SyncModifiersS2CPacket::decode);

   private static void encode(FriendlyByteBuf buf, SyncModifiersS2CPacket p) {
      buf.writeUtf(p.json);
   }

   private static SyncModifiersS2CPacket decode(FriendlyByteBuf buf) {
      return new SyncModifiersS2CPacket(buf.readUtf());
   }

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
