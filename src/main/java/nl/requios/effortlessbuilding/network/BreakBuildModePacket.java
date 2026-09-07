package nl.requios.effortlessbuilding.network;

import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record BreakBuildModePacket(BuildModeEnum buildMode, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos, Direction firstClickFace, ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill, ModeOptions.ActionEnum raisedEdge, ModeOptions.ActionEnum circleStart, ModeOptions.ActionEnum pointBuild, boolean protectTileEntities, boolean angelPlacement) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<BreakBuildModePacket> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "break_build_mode"));
   public static final StreamCodec<FriendlyByteBuf, BreakBuildModePacket> STREAM_CODEC = StreamCodec.of(BreakBuildModePacket::encode, BreakBuildModePacket::decode);

   private static void encode(FriendlyByteBuf buf, BreakBuildModePacket p) {
      buf.writeVarInt(p.buildMode.ordinal());
      buf.writeLong(p.firstPos.asLong());
      buf.writeLong(p.secondPos.asLong());
      buf.writeBoolean(p.thirdPos != null);
      if (p.thirdPos != null) {
         buf.writeLong(p.thirdPos.asLong());
      }

      buf.writeBoolean(p.fourthPos != null);
      if (p.fourthPos != null) {
         buf.writeLong(p.fourthPos.asLong());
      }

      buf.writeVarInt(p.firstClickFace.ordinal());
      buf.writeVarInt(p.fill.ordinal());
      buf.writeVarInt(p.cubeFill.ordinal());
      buf.writeVarInt(p.raisedEdge.ordinal());
      buf.writeVarInt(p.circleStart.ordinal());
      buf.writeVarInt(p.pointBuild.ordinal());
      buf.writeBoolean(p.protectTileEntities);
      buf.writeBoolean(p.angelPlacement);
   }

   private static BreakBuildModePacket decode(FriendlyByteBuf buf) {
      BuildModeEnum buildMode = BuildModeEnum.values()[buf.readVarInt()];
      BlockPos firstPos = BlockPos.of(buf.readLong());
      BlockPos secondPos = BlockPos.of(buf.readLong());
      BlockPos thirdPos = buf.readBoolean() ? BlockPos.of(buf.readLong()) : null;
      BlockPos fourthPos = buf.readBoolean() ? BlockPos.of(buf.readLong()) : null;
      Direction firstClickFace = Direction.values()[buf.readVarInt()];
      ModeOptions.ActionEnum fill = ModeOptions.ActionEnum.values()[buf.readVarInt()];
      ModeOptions.ActionEnum cubeFill = ModeOptions.ActionEnum.values()[buf.readVarInt()];
      ModeOptions.ActionEnum raisedEdge = ModeOptions.ActionEnum.values()[buf.readVarInt()];
      ModeOptions.ActionEnum circleStart = ModeOptions.ActionEnum.values()[buf.readVarInt()];
      ModeOptions.ActionEnum pointBuild = ModeOptions.ActionEnum.values()[buf.readVarInt()];
      boolean protectTileEntities = buf.readBoolean();
      boolean angelPlacement = buf.readBoolean();
      return new BreakBuildModePacket(buildMode, firstPos, secondPos, thirdPos, fourthPos, firstClickFace, fill, cubeFill, raisedEdge, circleStart, pointBuild, protectTileEntities, angelPlacement);
   }

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
