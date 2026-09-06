package nl.requios.effortlessbuilding.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateModifiersC2SPacket(String json) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<UpdateModifiersC2SPacket> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "update_modifiers"));
   public static final StreamCodec<FriendlyByteBuf, UpdateModifiersC2SPacket> STREAM_CODEC = StreamCodec.of(UpdateModifiersC2SPacket::encode, UpdateModifiersC2SPacket::decode);

   private static void encode(FriendlyByteBuf buf, UpdateModifiersC2SPacket p) {
      buf.writeUtf(p.json);
   }

   private static UpdateModifiersC2SPacket decode(FriendlyByteBuf buf) {
      return new UpdateModifiersC2SPacket(buf.readUtf());
   }

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
