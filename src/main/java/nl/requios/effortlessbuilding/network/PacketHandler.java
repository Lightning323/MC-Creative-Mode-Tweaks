package nl.requios.effortlessbuilding.network;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.EffortlessBuilding;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lightning323.creative_mode_tweaks.Config;

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

   public static void handlePlaceBuildMode(PlaceBuildModePacket packet, ServerPlayer player) {
      SableCompat.withSelection(player.serverLevel(), packet.firstPos(), () -> handlePlaceBuildModeInSelection(packet, player));
   }

   private static void handlePlaceBuildModeInSelection(PlaceBuildModePacket packet, ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      BlockSet blockSet = BuildPipeline.SERVER.runServerPipeline(packet.buildMode(), packet.firstPos(), packet.secondPos(), packet.thirdPos(), player, BuildPipeline.BuildState.PLACING, packet.fill(), packet.cubeFill(), packet.raisedEdge(), packet.circleStart(), packet.protectTileEntities());
      if (blockSet == null) {
         Constants.LOG.warn("[EffortlessBuilding] Received PlaceBuildModePacket but mode {} returned no blocks", packet.buildMode());
      } else {
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

            for(Map.Entry<BlockPos, BlockEntry> mapEntry : blockSet.validEntries()) {
               BlockPos pos = (BlockPos)mapEntry.getKey();
               BlockEntry entry = (BlockEntry)mapEntry.getValue();
               Item var22 = entry.item;
               if (var22 instanceof BlockItem) {
                  BlockItem blockItem = (BlockItem)var22;
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

                     ItemStack placementStack = new ItemStack(entry.item);
                     Vec3 localHit = new Vec3(packet.hitLocation().x, (double)pos.getY() + yFrac, packet.hitLocation().z);
                     BlockHitResult serverHit = new BlockHitResult(localHit, packet.hitFace(), pos, false);
                     BlockPlaceContext ctx = new OpenBlockPlaceContext(level, player, InteractionHand.MAIN_HAND, placementStack, serverHit);
                     BlockState state = blockItem.getBlock().getStateForPlacement(ctx);
                     if (state == null) {
                        state = blockItem.getBlock().defaultBlockState();
                     }

                     state = entry.applyTransforms(state);
                     level.setBlock(pos, state, 3);
                     undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, state));
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

               for(Map.Entry<BlockPos, BlockEntry> mapEntry : blockSet.validEntries()) {
                  BlockPos pos = (BlockPos)mapEntry.getKey();
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

                     BlockEntry entry = (BlockEntry)blockSet.get(pos);
                     if (entry != null) {
                        state = entry.applyTransforms(state);
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

                     for(Map.Entry<BlockPos, BlockEntry> mapEntry : blockSet.validEntries()) {
                        BlockPos pos = (BlockPos)mapEntry.getKey();
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

                  for(Map.Entry<BlockPos, BlockEntry> mapEntry : blockSet.validEntries()) {
                     BlockPos pos = (BlockPos)mapEntry.getKey();
                     BlockState oldState = level.getBlockState(pos);
                     Vec3 localHit = new Vec3((double)pos.getX() + (double)0.5F, (double)pos.getY() + (double)1.0F, (double)pos.getZ() + (double)0.5F);
                     BlockHitResult serverHit = new BlockHitResult(localHit, packet.hitFace(), pos, false);
                     UseOnContext useCtx = new OpenUseOnContext(worldLevel, player, InteractionHand.MAIN_HAND, held, serverHit);
                     InteractionResult result = held.getItem().useOn(useCtx);
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
      if (!creative && !Config.BUILDING_SURVIVAL_ALLOW_BREAKING.get()) {
         player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.breaking_disabled"), true);
      } else {
         SableCompat.withSelection(player.serverLevel(), packet.firstPos(), () -> handleBreakBuildModeInSelection(packet, player, creative));
      }
   }

   private static void handleBreakBuildModeInSelection(BreakBuildModePacket packet, ServerPlayer player, boolean creative) {
         ServerLevel level = player.serverLevel();
         BlockSet blockSet = BuildPipeline.SERVER.runServerPipeline(packet.buildMode(), packet.firstPos(), packet.secondPos(), packet.thirdPos(), player, BuildPipeline.BuildState.BREAKING, packet.fill(), packet.cubeFill(), packet.raisedEdge(), packet.circleStart(), packet.protectTileEntities());
         if (blockSet == null) {
            Constants.LOG.warn("[EffortlessBuilding] Received BreakBuildModePacket but mode {} returned no blocks", packet.buildMode());
         } else {
            Map<BlockPos, UndoManager.BlockChange> undoChanges = new LinkedHashMap();
            BlockState airState = Blocks.AIR.defaultBlockState();
            int broken = 0;

            for(Map.Entry<BlockPos, BlockEntry> mapEntry : blockSet.validEntries()) {
               BlockPos pos = (BlockPos)mapEntry.getKey();
               BlockState oldState = level.getBlockState(pos);
               if (!oldState.isAir()) {
                  if (creative) {
                     level.destroyBlock(pos, false, player);
                  } else {
                     ItemStack toolForDrops = Config.BUILDING_SURVIVAL_REQUIRE_TOOLS.get() ? InventoryHelper.findCorrectTool(player, oldState) : player.getMainHandItem();

                     for(ItemStack drop : Block.getDrops(oldState, level, pos, level.getBlockEntity(pos), player, toolForDrops)) {
                        InventoryHelper.giveOrDropItems(player, drop.getItem(), drop.getCount());
                     }

                     if (Config.BUILDING_SURVIVAL_USE_DURABILITY.get()) {
                        InventoryHelper.damageCorrectTool(player, oldState);
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

         }
   }

   public static void handleUndo(ServerPlayer player) {
      int count = UndoManager.undo(player);
      if (count >= 0) {
         player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.undo", new Object[]{count}), true);
      } else {
         player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.nothing_to_undo"), true);
      }

   }

   public static void handleRedo(ServerPlayer player) {
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
