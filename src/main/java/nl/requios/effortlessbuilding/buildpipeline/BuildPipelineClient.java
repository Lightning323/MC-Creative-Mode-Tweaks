package nl.requios.effortlessbuilding.buildpipeline;

import java.util.Map;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import org.lightning323.creative_mode_tweaks.Config;
import nl.requios.effortlessbuilding.item.TrowelItem;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;
import nl.requios.effortlessbuilding.modifier.ModifierSystem;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import nl.requios.effortlessbuilding.utilities.BlockStatus;
import nl.requios.effortlessbuilding.utilities.BreakDisplayTracker;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

public class BuildPipelineClient {
   public static final BuildPipeline CLIENT = createClientPipeline();
   public static final BreakDisplayTracker BREAK_DISPLAY = new BreakDisplayTracker();
   private static BuildPipeline.@Nullable BuildState buildState = null;
   private static @Nullable BlockHitResult firstClickHit = null;
   private static @Nullable BlockPos selectionOrigin = null;
   private static boolean angelPlacementSequence = false;

   private static BuildPipeline createClientPipeline() {
      BuildPipeline pipeline = new BuildPipeline();
      pipeline.addSystem(ModifierSystem.CLIENT);
      pipeline.addSystem(TrowelSystem.INSTANCE);
      pipeline.addSystem(ConstraintSystem.INSTANCE);
      return pipeline;
   }

   public static BuildPipeline.@Nullable BuildState getBuildState() {
      return buildState;
   }

   public static @Nullable BlockHitResult getFirstClickHit() {
      return firstClickHit;
   }

   public static boolean isAngelPlacementActive(Player player) {
      return BuildSettings.CLIENT.isAngelPlacementEnabled() && Config.isAngelPlacementAllowed(player);
   }

   public static boolean shouldRenderAngelPlacementCursor(Player player) {
      return isAngelPlacementActive(player) && buildState == null;
   }

   public static @Nullable AngelPlacement.Target getCurrentTarget(Minecraft mc) {
      Player player = mc.player;
      Level level = mc.level;
      return player != null && level != null ? AngelPlacement.findTarget(level, player, isAngelPlacementActive(player)) : null;
   }

   public static @Nullable BlockHitResult getCurrentTargetHit(Minecraft mc) {
      AngelPlacement.Target target = getCurrentTarget(mc);
      return target != null ? target.hit() : null;
   }

   public static boolean shouldInterceptPlacing() {
      Minecraft mc = Minecraft.getInstance();
      return BuildModes.CLIENT.getBuildMode() != BuildModeEnum.DISABLED || mc.player != null && (mc.player.getMainHandItem().getItem() instanceof TrowelItem || isAngelPlacementActive(mc.player));
   }

   public static boolean shouldInterceptBreaking() {
      if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         Player player = mc.player;
         if (player == null) {
            return false;
         } else if (!player.getAbilities().instabuild && !Config.BUILDING_SURVIVAL_ALLOW_BREAKING.get()) {
            return false;
         } else if (buildState == null && !player.getAbilities().instabuild) {
            if (mc.level != null) {
               HitResult var3 = mc.hitResult;
               if (var3 instanceof BlockHitResult) {
                  BlockHitResult hit = (BlockHitResult)var3;
                  BlockPos target = hit.getBlockPos();
                  BlockSet singleTarget = new BlockSet();
                  singleTarget.add(new BlockEntry(target));
                  ConstraintSystem.INSTANCE.processBlocks(singleTarget, player, BuildPipeline.BuildState.BREAKING);
                  BlockEntry entry = (BlockEntry)singleTarget.get(target);
                  if (entry != null && !entry.isValid()) {
                     return false;
                  }
               }
            }

            return true;
         } else {
            return true;
         }
      }
   }

   public static void handleRightClick(Minecraft mc) {
      handleClick(mc, BuildPipeline.BuildState.PLACING);
   }

   public static void handleLeftClick(Minecraft mc) {
      handleClick(mc, BuildPipeline.BuildState.BREAKING);
   }

   private static void handleClick(Minecraft mc, BuildPipeline.BuildState action) {
      BuildModeEnum mode = BuildModes.CLIENT.getBuildMode();
      Player player = mc.player;
      if (player != null && mc.level != null) {
         BlockPos clickedPos;
         if (mode.instance.isFirstClick()) {
            AngelPlacement.Target target = getCurrentTarget(mc);
            if (target == null) {
               return;
            }

            BlockHitResult hit = target.hit();
            BlockPos markerPos = mode.instance.getSelectionMarker(hit.getBlockPos());
            clickedPos = markerPos != null ? markerPos : resolveFirstClickPos(hit, action, mc.level);
            buildState = action;
            firstClickHit = hit;
            angelPlacementSequence = target.isAngelTarget();
            mode.instance.setFirstClickFace(hit.getDirection());
            selectionOrigin = clickedPos;
         } else if (mode.instance.usesDirectSecondPoint()) {
            AngelPlacement.Target target = getCurrentTarget(mc);
            if (target == null) {
               return;
            }

            BlockHitResult hit = target.hit();
            BlockPos markerPos = mode.instance.getSelectionMarker(hit.getBlockPos());
            clickedPos = markerPos != null ? markerPos : resolveFirstClickPos(hit, action, mc.level);
            if (selectionOrigin != null && !SableCompat.isInSameSelection(mc.level, selectionOrigin, clickedPos)) {
               rejectMixedSelection(player);
               return;
            }
         } else {
            clickedPos = player.blockPosition();
         }

         BlockPos selectionAnchor = selectionOrigin != null ? selectionOrigin : clickedPos;
         try (SableCompat.SelectionScope ignored = SableCompat.pushSelection(mc.level, selectionAnchor)) {
            BlockSet blocks = new BlockSet();
            boolean shouldPlace = mode.instance.onClick(blocks, clickedPos, player);
            if (shouldPlace) {
               mode.instance.findCoordinates(blocks, player);
               CLIENT.processBlocks(blocks, player, action);
               if (blocks.firstPos != null && blocks.lastPos != null) {
               if (blocks.hasEntriesWithStatus(BlockStatus.OUTSIDE_REACH)) {
                  rejectMixedSelection(player);
                  return;
               }

               if (action == BuildPipeline.BuildState.PLACING) {
                  ItemStack held = player.getMainHandItem();
                  BlockEntry firstEntry = (BlockEntry)blocks.get(blocks.firstPos);
                  SoundType soundType;
                  if (firstEntry != null && firstEntry.blockState != null) {
                     soundType = firstEntry.blockState.getSoundType();
                  } else {
                     Item var11 = held.getItem();
                     SoundType var10000;
                     if (var11 instanceof BlockItem) {
                        BlockItem blockItem = (BlockItem)var11;
                        var10000 = blockItem.getBlock().defaultBlockState().getSoundType();
                     } else {
                        var10000 = SoundType.STONE;
                     }

                     soundType = var10000;
                  }

                  mc.level.playLocalSound(blocks.firstPos, soundType.getPlaceSound(), SoundSource.BLOCKS, soundType.getVolume(), soundType.getPitch(), false);
               } else {
                  SoundType soundType = mc.level.getBlockState(blocks.firstPos).getSoundType();
                  mc.level.playLocalSound(blocks.firstPos, soundType.getBreakSound(), SoundSource.BLOCKS, soundType.getVolume(), soundType.getPitch(), false);
               }

               BlockPos intermediate = mode.instance.getIntermediatePos();
               BlockPos explicitThirdPos = mode.instance.getThirdSelectionPos();
               BlockPos secondPos = intermediate != null ? intermediate : blocks.lastPos;
               BlockPos thirdPos = explicitThirdPos != null ? explicitThirdPos : intermediate != null ? blocks.lastPos : null;
               BlockPos fourthPos = mode.instance.getFourthSelectionPos();
               ModeOptions.ActionEnum pointBuild = mode.instance.getPointBuildAction();
               if (action == BuildPipeline.BuildState.PLACING) {
                  if (!blocks.rejectedEntries().isEmpty()) {
                     BlockStatus firstRejection = ((BlockEntry)((Map.Entry)blocks.rejectedEntries().getFirst()).getValue()).getStatus();
                     if (firstRejection == BlockStatus.WORLD_BORDER) {
                        player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.world_border"), true);
                     } else if (!player.getAbilities().instabuild) {
                        if (firstRejection == BlockStatus.NOT_PLACED_BY_PLAYER) {
                           player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.only_replace_placed"), true);
                        } else if (firstRejection == BlockStatus.TOO_HARD) {
                           player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.too_hard"), true);
                        } else if (firstRejection == BlockStatus.PROTECTED_TILE_ENTITY) {
                           player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.protected_tile_entity"), true);
                        }
                     }
                  }

                  Direction hitFace = firstClickHit != null ? firstClickHit.getDirection() : Direction.UP;
                  Vec3 hitLocation = firstClickHit != null ? firstClickHit.getLocation() : Vec3.atCenterOf(blocks.firstPos);
                  PacketHandler.sendToServer(new PlaceBuildModePacket(mode, blocks.firstPos, secondPos, thirdPos, fourthPos, hitFace, hitLocation, ModeOptions.getFill(), ModeOptions.getCubeFill(), ModeOptions.getRaisedEdge(), ModeOptions.getCircleStart(), pointBuild, ModeOptions.getSides(), BuildSettings.CLIENT.getReplaceMode(), Config.BUILDING_PROTECT_TILE_ENTITIES.get(), angelPlacementSequence));
                  PlacedBlockTracker.clientTrackAll(mc.level.dimension(), blocks.keySet());
               } else {
                  if (!blocks.rejectedEntries().isEmpty()) {
                     BlockStatus firstRejection = ((BlockEntry)((Map.Entry)blocks.rejectedEntries().getFirst()).getValue()).getStatus();
                     if (firstRejection == BlockStatus.WORLD_BORDER) {
                        player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.world_border"), true);
                     } else if (!player.getAbilities().instabuild) {
                        if (firstRejection == BlockStatus.NOT_PLACED_BY_PLAYER) {
                           player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.only_break_placed"), true);
                        } else if (firstRejection == BlockStatus.TOO_HARD) {
                           player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.too_hard"), true);
                        } else if (firstRejection == BlockStatus.PROTECTED_TILE_ENTITY) {
                           player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.protected_tile_entity"), true);
                        }
                     }
                  }

                  Direction hitFace = firstClickHit != null ? firstClickHit.getDirection() : Direction.UP;
                  PacketHandler.sendToServer(new BreakBuildModePacket(mode, blocks.firstPos, secondPos, thirdPos, fourthPos, hitFace, ModeOptions.getFill(), ModeOptions.getCubeFill(), ModeOptions.getRaisedEdge(), ModeOptions.getCircleStart(), pointBuild, ModeOptions.getSides(), Config.BUILDING_PROTECT_TILE_ENTITIES.get(), angelPlacementSequence));
               }
            } else {
               Constants.LOG.warn("[EffortlessBuilding] Build mode {} produced no block positions", mode);
               }

               mode.instance.initialize();
               buildState = null;
               firstClickHit = null;
               selectionOrigin = null;
               angelPlacementSequence = false;
            }
         }
      }
   }

   public static BlockSet getPreviewBlocks(Minecraft mc) {
      Player player = mc.player;
      if (player != null && mc.level != null) {
         BuildModeEnum mode = BuildModes.CLIENT.getBuildMode();
         BlockSet result;
         if (!mode.instance.isFirstClick()) {
            BlockPos selectionAnchor = selectionOrigin != null ? selectionOrigin : player.blockPosition();
            try (SableCompat.SelectionScope ignored = SableCompat.pushSelection(mc.level, selectionAnchor)) {
               BlockSet previewBlocks = new BlockSet();
               if (mode.instance.usesDirectSecondPoint()) {
                  BlockHitResult hit = getCurrentTargetHit(mc);
                  BuildPipeline.BuildState action = buildState != null ? buildState : BuildPipeline.BuildState.PLACING;
                  BlockPos previewPoint = hit != null ? resolveFirstClickPos(hit, action, mc.level) : null;
                  if (previewPoint != null && !SableCompat.isInSameSelection(mc.level, selectionAnchor, previewPoint)) {
                     mode.instance.setPreviewSecondPoint(null);
                     return null;
                  }

                  mode.instance.setPreviewSecondPoint(previewPoint);
               }

               mode.instance.findCoordinates(previewBlocks, player);
               BuildPipeline.BuildState action = buildState != null ? buildState : BuildPipeline.BuildState.PLACING;
               CLIENT.processBlocks(previewBlocks, player, action);
               if (previewBlocks.isEmpty()) {
                  return null;
               }

               previewBlocks.sortByDistance();
               previewBlocks.truncate(Config.getBuildingMaxBlocksPlaced(player));
               result = previewBlocks;
            }
         } else {
            BlockHitResult hit = getCurrentTargetHit(mc);
            if (hit == null) {
               return null;
            }

            BlockPos targetPos = resolveFirstClickPos(hit, BuildPipeline.BuildState.PLACING, mc.level);
            BlockSet blockSet = new BlockSet();
            blockSet.add(new BlockEntry(targetPos));
            blockSet.firstPos = targetPos;
            blockSet.lastPos = targetPos;
            try (SableCompat.SelectionScope ignored = SableCompat.pushSelection(mc.level, targetPos)) {
               CLIENT.processBlocks(blockSet, player, BuildPipeline.BuildState.PLACING);
            }
            result = blockSet;
         }

         updateDisplayTrackers(player, result);
         return result;
      } else {
         return null;
      }
   }

   private static void updateDisplayTrackers(Player player, BlockSet blockSet) {
      BuildPipeline.BuildState action = buildState != null ? buildState : BuildPipeline.BuildState.PLACING;
      if (action == BuildPipeline.BuildState.BREAKING) {
         BREAK_DISPLAY.compute(player, blockSet);

      } else {
         ItemStack held = player.getMainHandItem();
         Item heldItem = null;
         if (held.getItem() instanceof BlockItem) {
            heldItem = held.getItem();
         } else {
            Item var6 = held.getItem();
            if (var6 instanceof BucketItem) {
               BucketItem bucketItem = (BucketItem)var6;
               net.minecraft.world.level.material.Fluid fluid = ((BucketItemAccessor)bucketItem).effortlessbuilding$getFluid();
               if (!fluid.isSame(Fluids.EMPTY)) {
                  heldItem = held.getItem();
               }
            }
         }



         BREAK_DISPLAY.initialize();
      }

   }

   public static void cancelCurrentSequence() {
      if (buildState != null) {
         Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_OUT, 1.0F));
      }

      BuildModes.CLIENT.getBuildMode().instance.initialize();
      buildState = null;
      firstClickHit = null;
      selectionOrigin = null;
      angelPlacementSequence = false;
   }

   private static void rejectMixedSelection(Player player) {
      player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.sublevel_out_of_bounds"), true);
      cancelCurrentSequence();
   }

   private static BlockPos resolveFirstClickPos(BlockHitResult hit, BuildPipeline.BuildState action, Level level) {
      BlockPos hitPos = hit.getBlockPos();
      if (action == BuildPipeline.BuildState.BREAKING) {
         return hitPos;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && BuildPipeline.isToolInteractionItem(mc.player.getMainHandItem())) {
            return hitPos;
         } else if (BuildSettings.CLIENT.shouldOffsetStartPosition()) {
            return hitPos;
         } else {
            return level.getBlockState(hitPos).canBeReplaced() ? hitPos : hitPos.relative(hit.getDirection());
         }
      }
   }

   static {
      BuildModes.CLIENT.setBeforeDisable(BuildPipelineClient::cancelCurrentSequence);
   }
}
