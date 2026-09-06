package nl.requios.effortlessbuilding.network;

import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record PlaceBuildModePacket(BuildModeEnum buildMode, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, Direction hitFace, Vec3 hitLocation, ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill, ModeOptions.ActionEnum raisedEdge, ModeOptions.ActionEnum circleStart, BuildSettings.ReplaceMode replaceMode, boolean protectTileEntities) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<PlaceBuildModePacket> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "place_build_mode"));
   public static final StreamCodec<FriendlyByteBuf, PlaceBuildModePacket> STREAM_CODEC = StreamCodec.of(PlaceBuildModePacket::encode, PlaceBuildModePacket::decode);

   private static void encode(FriendlyByteBuf buf, PlaceBuildModePacket p) {
      buf.writeVarInt(p.buildMode.ordinal());
      buf.writeLong(p.firstPos.asLong());
      buf.writeLong(p.secondPos.asLong());
      buf.writeBoolean(p.thirdPos != null);
      if (p.thirdPos != null) {
         buf.writeLong(p.thirdPos.asLong());
      }

      buf.writeVarInt(p.hitFace.ordinal());
      buf.writeDouble(p.hitLocation.x);
      buf.writeDouble(p.hitLocation.y);
      buf.writeDouble(p.hitLocation.z);
      buf.writeVarInt(p.fill.ordinal());
      buf.writeVarInt(p.cubeFill.ordinal());
      buf.writeVarInt(p.raisedEdge.ordinal());
      buf.writeVarInt(p.circleStart.ordinal());
      buf.writeVarInt(p.replaceMode.ordinal());
      buf.writeBoolean(p.protectTileEntities);
   }

   private static PlaceBuildModePacket decode(FriendlyByteBuf buf) {
      BuildModeEnum buildMode = BuildModeEnum.values()[buf.readVarInt()];
      BlockPos firstPos = BlockPos.of(buf.readLong());
      BlockPos secondPos = BlockPos.of(buf.readLong());
      BlockPos thirdPos = buf.readBoolean() ? BlockPos.of(buf.readLong()) : null;
      Direction hitFace = Direction.values()[buf.readVarInt()];
      Vec3 hitLocation = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
      ModeOptions.ActionEnum fill = ModeOptions.ActionEnum.values()[buf.readVarInt()];
      ModeOptions.ActionEnum cubeFill = ModeOptions.ActionEnum.values()[buf.readVarInt()];
      ModeOptions.ActionEnum raisedEdge = ModeOptions.ActionEnum.values()[buf.readVarInt()];
      ModeOptions.ActionEnum circleStart = ModeOptions.ActionEnum.values()[buf.readVarInt()];
      BuildSettings.ReplaceMode replaceMode = BuildSettings.ReplaceMode.values()[buf.readVarInt()];
      boolean protectTileEntities = buf.readBoolean();
      return new PlaceBuildModePacket(buildMode, firstPos, secondPos, thirdPos, hitFace, hitLocation, fill, cubeFill, raisedEdge, circleStart, replaceMode, protectTileEntities);
   }

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
