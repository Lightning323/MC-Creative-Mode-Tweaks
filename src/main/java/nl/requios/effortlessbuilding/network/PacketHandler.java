package nl.requios.effortlessbuilding.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.EffortlessBuilding;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.buildpipeline.AngelPlacement;
import nl.requios.effortlessbuilding.buildpipeline.BreakHunger;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.SableCompat;
import nl.requios.effortlessbuilding.buildpipeline.TrowelSystem;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;
import nl.requios.effortlessbuilding.modifier.IModifier;
import nl.requios.effortlessbuilding.modifier.ModifierSerializer;
import nl.requios.effortlessbuilding.modifier.ModifierServerStorage;
import nl.requios.effortlessbuilding.modifier.ModifierSystem;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import nl.requios.effortlessbuilding.utilities.BlockStatus;
import nl.requios.effortlessbuilding.utilities.InventoryHelper;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import nl.requios.effortlessbuilding.utilities.UndoManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import org.lightning323.creative_mode_tweaks.Config;
import org.jetbrains.annotations.Nullable;

public class PacketHandler {
   public static void sendToServer(PlaceBuildModePacket packet) {
      EffortlessBuilding.sendToServer(packet);
   }

   public static void sendToServer(BreakBuildModePacket packet) {
      EffortlessBuilding.sendToServer(packet);
   }

   public static void sendToServer(UndoPacket packet) {
      EffortlessBuilding.sendToServer(packet);
   }

   public static void sendToServer(RedoPacket packet) {
      EffortlessBuilding.sendToServer(packet);
   }

   public static void sendToServer(UpdateModifiersC2SPacket packet) {
      EffortlessBuilding.sendToServer(packet);
   }

   public static void sendToClient(ServerPlayer player, SyncModifiersS2CPacket packet) {
      EffortlessBuilding.sendToClient(player, packet);
   }

   /**
    * Mutes per-block placement sounds while a build-mode batch runs. Batch
    * loops call vanilla {@code useOn} per block, which plays a place sound
    * per block — thousands of blocks in one tick exhausts every nearby
    * client's 247-channel sound pool and kills all game audio. The click
    * already played the action's single sound client-side, so these are pure
    * spam. Always paired with try/finally: a leaked flag would silence the
    * handling thread indefinitely.
    */
   private static final ThreadLocal<Boolean> SUPPRESS_BATCH_SOUNDS = ThreadLocal.withInitial(() -> false);

   public static void onBatchSound(PlayLevelSoundEvent.AtPosition event) {
      if (SUPPRESS_BATCH_SOUNDS.get()) {
         event.setCanceled(true);
      }
   }

   private static InteractionResult mutedUseOn(Supplier<InteractionResult> useOn) {
      SUPPRESS_BATCH_SOUNDS.set(true);
      try {
         return useOn.get();
      } finally {
         SUPPRESS_BATCH_SOUNDS.remove();
      }
   }

   private static boolean validateAngelPlacement(boolean angelPlacement, BlockPos firstPos, ServerPlayer player) {
      if (!angelPlacement) {
         return true;
      }

      if (!Config.isAngelPlacementAllowed(player)) {
         player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.angel_placement_disabled"), true);
         return false;
      }

//      if (!player.serverLevel().getBlockState(firstPos).isAir() || !AngelPlacement.isWithinTargetDistance(player, firstPos)) {
//         Constants.LOG.warn("[EffortlessBuilding] Rejected Angel Placement from {} because its target was invalid", player.getGameProfile().getName());
//         return false;
//      }

      return true;
   }

    /** Adventure and spectator players get no build tools at all. */
    private static boolean isAdventureOrSpectator(ServerPlayer player) {
       GameType gameType = player.gameMode.getGameModeForPlayer();
       return gameType == GameType.ADVENTURE || gameType == GameType.SPECTATOR;
    }

    public static void handlePlaceBuildMode(PlaceBuildModePacket packet, ServerPlayer player) {
       if (!player.isCreative() && isAdventureOrSpectator(player)) {
//          player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.build_menu_unavailable"), true);
          return;
       }
       // DISABLED is plain single-block placement (trowel, Angel Placement),
       // governed by their own rules — only real build modes are gated here.
       if (!player.isCreative() && packet.buildMode() != BuildModeEnum.DISABLED
               && !Config.BUILDING_SURVIVAL_ALLOW_BUILD_MODES.get()) {
//          player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.build_modes_disabled"), true);
          return;
       }
       if (!validateSelection(packet.firstPos(), packet.secondPos(), packet.thirdPos(), packet.fourthPos(), player)
             || !validateAngelPlacement(packet.angelPlacement(), packet.firstPos(), player)) {
          return;
       }

      SableCompat.withSelection(player.serverLevel(), packet.firstPos(), () -> handlePlaceBuildModeInSelection(packet, player));
   }

    private static void handlePlaceBuildModeInSelection(PlaceBuildModePacket packet, ServerPlayer player) {
       ServerLevel level = player.serverLevel();
       BlockSet blockSet = BuildPipeline.SERVER.runServerPipeline(packet.buildMode(), packet.firstPos(), packet.secondPos(), packet.thirdPos(), packet.fourthPos(), packet.hitFace(), player, BuildPipeline.BuildState.PLACING, packet.fill(), packet.cubeFill(), packet.raisedEdge(), packet.circleStart(), packet.pointBuild(), packet.sides(), packet.planeAlign(), packet.protectTileEntities(), packet.replaceMode());
      if (blockSet == null) {
         Constants.LOG.warn("[EffortlessBuilding] Received PlaceBuildModePacket but mode {} returned no blocks", packet.buildMode());
      } else {
         if (blockSet.hasEntriesWithStatus(BlockStatus.OUTSIDE_REACH)) {
            player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.sublevel_out_of_bounds").withStyle(ChatFormatting.RED), true);
            return;
         }

         blockSet.sortByDistance();
         ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
         ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
         boolean creative = player.isCreative();
         BuildSettings.ReplaceMode replaceMode = packet.replaceMode();
         Map<BlockPos, UndoManager.BlockChange> undoChanges = new LinkedHashMap();
         int placed = 0;
         if (TrowelSystem.isTrowel(held)) {
            Map<Item, Integer> available = creative ? Map.of() : TrowelSystem.getHotbarBlockCounts(player);

            Map<Item, Integer> used = new HashMap();
            double yFrac = packet.hitLocation().y - Math.floor(packet.hitLocation().y);

            // Candidates include INSUFFICIENT_ITEMS entries past the stock cap:
            // the cap now runs after every other rejection, so those are
            // placeable positions cut only for stock. Placement-time skips
            // below (useOn failure, missing support) don't consume, so later
            // candidates must refill the stock instead of leaving leftovers.
            for (BlockEntry entry : placeableCandidates(blockSet)) {
               BlockPos pos = entry.blockPos;
               Item var22 = entry.item;
               if (!(var22 instanceof BlockItem blockItem)) {
                  continue;
               }
               if ((creative || used.getOrDefault(entry.item, 0) < available.getOrDefault(entry.item, 0)) && BuildSettings.canPlaceAt(level, pos, replaceMode, offHand)) {
                     BlockState oldState = level.getBlockState(pos);
                     if (!creative && !oldState.canBeReplaced()) {
                        ItemStack toolForDrops = Config.BUILDING_SURVIVAL_REQUIRE_TOOLS.get() ? InventoryHelper.findCorrectTool(player, oldState) : player.getMainHandItem();

                        for(ItemStack drop : Block.getDrops(oldState, level, pos, level.getBlockEntity(pos), player, toolForDrops)) {
                           InventoryHelper.giveOrDropItems(player, drop.getItem(), drop.getCount());
                        }

                        if (Config.BUILDING_SURVIVAL_USE_DURABILITY.get()) {
                           InventoryHelper.damageCorrectTool(player, oldState);
                        }
                     }

            // Random-block placement runs through the vanilla use
                      // channel per block (placement rules, block-entity data,
                      // setPlacedBy, stats, criteria) instead of a bare
                      // setBlock. The stack is detached: survival consumption
                      // stays on the manual hotbar accounting below.
                      // useOn plays a place sound per block — muted for the
                      // batch (see SUPPRESS_BATCH_SOUNDS): the click already
                      // played the action's single sound client-side.
                      ItemStack placementStack = new ItemStack(entry.item);
                      Vec3 localHit = new Vec3(packet.hitLocation().x, (double)pos.getY() + yFrac, packet.hitLocation().z);
                      BlockHitResult serverHit = new BlockHitResult(localHit, packet.hitFace(), pos, false);
                      UseOnContext useCtx = new OpenUseOnContext(level, player, InteractionHand.MAIN_HAND, placementStack, serverHit);
                      InteractionResult result = mutedUseOn(() -> blockItem.useOn(useCtx));
                     if (!result.consumesAction()) {
                        continue;
                     }

                     BlockState placedState = level.getBlockState(pos);
                     BlockState finalState = entry.applyTransforms(placedState);
                     if (!finalState.equals(placedState)) {
                        level.setBlock(pos, finalState, 3);
                     }
                     if (!finalState.canSurvive(level, pos)) {
                        // Would pop without support: revert the vanilla
                        // placement instead of leaving a breaking block.
                        level.setBlock(pos, oldState, 3);
                        continue;
                     }
                     if (!oldState.equals(finalState)) {
                        undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, finalState));
                        used.merge(entry.item, 1, Integer::sum);
                        ++placed;
                     }
                  }
            }

            if (!creative) {
               TrowelSystem.consumeHotbarItems(player, used);
            }
         } else {
            Item heldItem = held.getItem();
            if (heldItem instanceof BlockItem) {
               BlockItem blockItem = (BlockItem)heldItem;
               heldItem = held.getItem();
               boolean hasStackData = !held.getComponentsPatch().isEmpty();
               int available;
               if (creative) {
                  available = Integer.MAX_VALUE;
               } else if (hasStackData) {
                  available = held.getCount();
               } else {
                  int inventoryCount = InventoryHelper.findTotalItemsInInventory(player, heldItem);
                  int validCount = blockSet.validEntries().size();
                  int neededFromNetwork = Math.max(0, validCount - inventoryCount);
                  int ae2Extracted = 0;


                  available = inventoryCount + ae2Extracted;
               }

                double yFrac = packet.hitLocation().y - Math.floor(packet.hitLocation().y);

                // See trowel loop above: iterate past the stock cap so
                // placement-time skips (unreplaceable, missing support)
                // refill from later affordable positions instead of
                // leaving inventory unspent.
                for (BlockEntry entry : placeableCandidates(blockSet)) {
                  BlockPos pos = entry.blockPos;
                  if (!creative && placed >= available) {
                     break;
                  }

                  if (BuildSettings.canPlaceAt(level, pos, replaceMode, offHand)) {
                     BlockState oldState = level.getBlockState(pos);
                     if (!creative && !oldState.canBeReplaced()) {
                        ItemStack toolForDrops = Config.BUILDING_SURVIVAL_REQUIRE_TOOLS.get() ? InventoryHelper.findCorrectTool(player, oldState) : player.getMainHandItem();

                        for(ItemStack drop : Block.getDrops(oldState, level, pos, level.getBlockEntity(pos), player, toolForDrops)) {
                           InventoryHelper.giveOrDropItems(player, drop.getItem(), drop.getCount());
                        }

                        if (Config.BUILDING_SURVIVAL_USE_DURABILITY.get()) {
                           InventoryHelper.damageCorrectTool(player, oldState);
                        }
                     }

                     Vec3 localHit = new Vec3(packet.hitLocation().x, (double)pos.getY() + yFrac, packet.hitLocation().z);
                     BlockHitResult serverHit = new BlockHitResult(localHit, packet.hitFace(), pos, false);
                     BlockPlaceContext ctx = new OpenBlockPlaceContext(level, player, InteractionHand.MAIN_HAND, held, serverHit);
                     BlockState state = blockItem.getBlock().getStateForPlacement(ctx);
                     if (state == null) {
                        state = blockItem.getBlock().defaultBlockState();
                     }

                     state = entry.applyTransforms(state);

                     if (!state.canSurvive(level, pos)) {
                        // Would pop without support: skip it entirely —
                        // no world change, no consumption, no undo record.
                        continue;
                     }

                     level.setBlock(pos, state, 3);
                     transferBlockItemData(level, player, pos, held);
                     undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, state));
                     ++placed;
                  }
               }

               if (!creative && placed > 0) {
                  if (hasStackData) {
                     held.shrink(placed);
                  } else {
                     InventoryHelper.consumeItems(player, heldItem, placed);

                  }
               }
            } else {
               heldItem = held.getItem();
               if (heldItem instanceof BucketItem) {
                  BucketItem bucketItem = (BucketItem)heldItem;
                  Fluid fluid = ((BucketItemAccessor)bucketItem).effortlessbuilding$getFluid();
                  if (!fluid.isSame(Fluids.EMPTY)) {
                     BlockState fluidState = fluid.defaultFluidState().createLegacyBlock();
                     int maxPlace = creative ? Integer.MAX_VALUE : 1;

                     for (BlockEntry entry : blockSet.validEntries()) {
                        BlockPos pos = entry.blockPos;
                        if (placed >= maxPlace) {
                           break;
                        }

                        if (BuildSettings.canPlaceAt(level, pos, replaceMode, offHand)) {
                           BlockState oldState = level.getBlockState(pos);
                           if (!creative && !oldState.canBeReplaced()) {
                              ItemStack toolForDrops = Config.BUILDING_SURVIVAL_REQUIRE_TOOLS.get() ? InventoryHelper.findCorrectTool(player, oldState) : player.getMainHandItem();

                              for(ItemStack drop : Block.getDrops(oldState, level, pos, level.getBlockEntity(pos), player, toolForDrops)) {
                                 InventoryHelper.giveOrDropItems(player, drop.getItem(), drop.getCount());
                              }

                              if (Config.BUILDING_SURVIVAL_USE_DURABILITY.get()) {
                                 InventoryHelper.damageCorrectTool(player, oldState);
                              }
                           }

                           level.setBlock(pos, fluidState, 3);
                           undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, fluidState));
                           ++placed;
                        }
                     }

                     if (!creative && placed > 0) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                     }
                  }
               } else {
                  if (!(held.getItem() instanceof DiggerItem)) {
                     return;
                  }

                  Level worldLevel = level;

                  for (BlockEntry entry : blockSet.validEntries()) {
                     BlockPos pos = entry.blockPos;
                     BlockState oldState = level.getBlockState(pos);
                     Vec3 localHit = new Vec3((double)pos.getX() + (double)0.5F, (double)pos.getY() + (double)1.0F, (double)pos.getZ() + (double)0.5F);
                     BlockHitResult serverHit = new BlockHitResult(localHit, packet.hitFace(), pos, false);
                     UseOnContext useCtx = new OpenUseOnContext(worldLevel, player, InteractionHand.MAIN_HAND, held, serverHit);
                     InteractionResult result = mutedUseOn(() -> held.getItem().useOn(useCtx));
                     if (result.consumesAction()) {
                        BlockState newState = level.getBlockState(pos);
                        if (!oldState.equals(newState)) {
                           undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, newState));
                           ++placed;
                        }
                     }

                     if (held.isEmpty()) {
                        break;
                     }
                  }
               }
            }
         }

         if (!undoChanges.isEmpty()) {
            UndoManager.recordOperation(player, level.dimension(), undoChanges);
            PlacedBlockTracker.trackAll(player.getUUID(), level.dimension(), undoChanges.keySet());
         }

      }
   }

   private static void transferBlockItemData(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack stack) {
      Item var5 = stack.getItem();
      if (var5 instanceof BlockItem blockItem) {
         BlockState placedState = level.getBlockState(pos);
         if (placedState.is(blockItem.getBlock())) {
            BlockItem.updateCustomBlockEntityTag(level, player, pos, stack);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
               blockEntity.applyComponentsFromItemStack(stack);
               blockEntity.setChanged();
            }

            placedState.getBlock().setPlacedBy(level, pos, placedState, player, stack);
         }
      }
   }

    public static void handleBreakBuildMode(BreakBuildModePacket packet, ServerPlayer player) {
       boolean creative = player.isCreative();
       if (!creative && isAdventureOrSpectator(player)) {
          player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.build_menu_unavailable"), true);
       } else if (!creative && packet.buildMode() != BuildModeEnum.DISABLED
               && !Config.BUILDING_SURVIVAL_ALLOW_BUILD_MODES.get()) {
          player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.build_modes_disabled"), true);
       } else if (!validateSelection(packet.firstPos(), packet.secondPos(), packet.thirdPos(), packet.fourthPos(), player)
             || !validateAngelPlacement(packet.angelPlacement(), packet.firstPos(), player)) {
          return;
       } else if (!creative && !Config.BUILDING_SURVIVAL_ALLOW_BREAKING.get()) {
         player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.breaking_disabled"), true);
      } else {
         SableCompat.withSelection(player.serverLevel(), packet.firstPos(), () -> handleBreakBuildModeInSelection(packet, player, creative));
      }
   }

    private static void handleBreakBuildModeInSelection(BreakBuildModePacket packet, ServerPlayer player, boolean creative) {
          ServerLevel level = player.serverLevel();
          BlockSet blockSet = BuildPipeline.SERVER.runServerPipeline(packet.buildMode(), packet.firstPos(), packet.secondPos(), packet.thirdPos(), packet.fourthPos(), packet.firstClickFace(), player, BuildPipeline.BuildState.BREAKING, packet.fill(), packet.cubeFill(), packet.raisedEdge(), packet.circleStart(), packet.pointBuild(), packet.sides(), packet.planeAlign(), packet.protectTileEntities(), null);
         if (blockSet == null) {
            Constants.LOG.warn("[EffortlessBuilding] Received BreakBuildModePacket but mode {} returned no blocks", packet.buildMode());
         } else {
            if (blockSet.hasEntriesWithStatus(BlockStatus.OUTSIDE_REACH)) {
               player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.sublevel_out_of_bounds").withStyle(ChatFormatting.RED), true);
               return;
            }

            Map<BlockPos, UndoManager.BlockChange> undoChanges = new LinkedHashMap();
            BlockState airState = Blocks.AIR.defaultBlockState();
            int broken = 0;

            // Hunger cost for survival batch breaking (creative is exempt).
            // Batch breaking bypasses vanilla per-block mining (no time passes,
            // no exhaustion), so simulate what the held item would have spent.
            double hungerMult = creative ? 0.0D : Config.BUILDING_SURVIVAL_BREAK_HUNGER_HARDNESS_MULT.get();
            BreakHunger.Batch hunger = hungerMult > 0.0D
                    ? new BreakHunger.Batch(player, hungerMult, Config.BUILDING_SURVIVAL_BREAK_HUNGER_TOOL_MULT.get())
                    : null;

            for (BlockEntry entry : blockSet.validEntries()) {
               BlockPos pos = entry.blockPos;
               BlockState oldState = level.getBlockState(pos);
               if (!oldState.isAir()) {
                  if (creative) {
                     // Silent removal: destroyBlock fires a 2001 level event
                     // (break sound + particles) per block with no audience
                     // exclusion — thousands of blocks in one tick exhausts
                     // the client's 247-channel sound pool and kills all game
                     // audio. drop=false, so this is identical minus the
                     // event; fluids flow back exactly as destroyBlock does.
                     level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), 3);
                  } else {
                     ItemStack toolForDrops = Config.BUILDING_SURVIVAL_REQUIRE_TOOLS.get() ? InventoryHelper.findCorrectTool(player, oldState) : player.getMainHandItem();

                     for(ItemStack drop : Block.getDrops(oldState, level, pos, level.getBlockEntity(pos), player, toolForDrops)) {
                        InventoryHelper.giveOrDropItems(player, drop.getItem(), drop.getCount());
                     }

                     if (Config.BUILDING_SURVIVAL_USE_DURABILITY.get()) {
                        InventoryHelper.damageCorrectTool(player, oldState);
                     }

                     if (hunger != null) {
                        hunger.add(level, pos, oldState);
                     }
                     level.setBlock(pos, airState, 3);
                  }

                  undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, airState));
                  ++broken;
               }
            }

            if (!undoChanges.isEmpty()) {
               UndoManager.recordOperation(player, level.dimension(), undoChanges);
            }

            if (hunger != null && hunger.total() > 0.0F) {
               player.causeFoodExhaustion(hunger.total());
            }

         }
   }

   private static boolean validateSelection(BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos, ServerPlayer player) {
      if (SableCompat.areInSameSelection(player.serverLevel(), firstPos, secondPos, thirdPos, fourthPos)) {
         return true;
      }

      player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.sublevel_out_of_bounds").withStyle(ChatFormatting.RED), true);
      return false;
   }

   /**
    * Placement candidates in pipeline order: VALID entries plus
    * INSUFFICIENT_ITEMS entries past the stock cap. The cap runs after
    * every other rejection, so INSUFFICIENT entries are placeable
    * positions cut only for stock — safe to refill from when an earlier
    * candidate is skipped at placement time (unreplaceable, missing
    * support, failed useOn) without consuming. All other rejections
    * (tiles, hardness, borders, ...) stay excluded.
    */
   private static List<BlockEntry> placeableCandidates(BlockSet blockSet) {
      List<BlockEntry> result = new ArrayList<>(blockSet.size());
      for (BlockEntry entry : blockSet) {
         if (entry.isValid() || entry.getStatus() == BlockStatus.INSUFFICIENT_ITEMS) {
            result.add(entry);
         }
      }
      return result;
   }

    public static void handleUndo(ServerPlayer player) {
       if (!player.isCreative() && isAdventureOrSpectator(player)) {
          player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.build_menu_unavailable"), true);
          return;
       }
       if (!player.isCreative() && !Config.BUILDING_SURVIVAL_ALLOW_UNDO_REDO.get()) {
          player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.undo_redo_disabled"), true);
          return;
       }
       int count = UndoManager.undo(player);
      if (count >= 0) {
         player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.undo", new Object[]{count}), true);
      } else {
         player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.nothing_to_undo"), true);
      }

   }

    public static void handleRedo(ServerPlayer player) {
       if (!player.isCreative() && isAdventureOrSpectator(player)) {
          player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.build_menu_unavailable"), true);
          return;
       }
       if (!player.isCreative() && !Config.BUILDING_SURVIVAL_ALLOW_UNDO_REDO.get()) {
          player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.undo_redo_disabled"), true);
          return;
       }
       int count = UndoManager.redo(player);
      if (count >= 0) {
         player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.redo", new Object[]{count}), true);
      } else {
         player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.nothing_to_redo"), true);
      }

   }

   public static void handleUpdateModifiers(UpdateModifiersC2SPacket packet, ServerPlayer player) {
      List<IModifier> modifiers = ModifierSerializer.deserialize(packet.json());
      ModifierServerStorage.setModifiers(player.getUUID(), modifiers);
      ModifierServerStorage.savePlayer(player.server, player.getUUID());
      sendToClient(player, new SyncModifiersS2CPacket(ModifierServerStorage.serializePlayer(player.getUUID())));
   }

   public static void handleSyncModifiers(SyncModifiersS2CPacket packet) {
      List<IModifier> modifiers = ModifierSerializer.deserialize(packet.json());
      ModifierSystem.CLIENT.clearModifiers();

      for(IModifier m : modifiers) {
         ModifierSystem.CLIENT.addModifier(m);
      }

   }

   private static final class OpenBlockPlaceContext extends BlockPlaceContext {
      OpenBlockPlaceContext(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
         super(level, player, hand, stack, hit);
      }
   }

   private static final class OpenUseOnContext extends UseOnContext {
      OpenUseOnContext(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
         super(level, player, hand, stack, hit);
      }
   }
}
