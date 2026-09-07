package nl.requios.effortlessbuilding.buildpipeline;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class BuildPipeline {
   public static final BuildPipeline SERVER = createServerPipeline();
   private final List<IBuildSystem> systems = new ArrayList();

   private static BuildPipeline createServerPipeline() {
      BuildPipeline pipeline = new BuildPipeline();
      pipeline.addSystem(BuildModeSystem.INSTANCE);
      pipeline.addSystem(ModifierSystemServer.INSTANCE);
      pipeline.addSystem(TrowelSystem.INSTANCE);
      pipeline.addSystem(ConstraintSystem.INSTANCE);
      return pipeline;
   }

   public static boolean isBuildTriggerItem(ItemStack stack) {
      if (TrowelSystem.isTrowel(stack)) {
         return true;
      } else {
         Item var2 = stack.getItem();
         if (!(var2 instanceof BlockItem)) {
            if (stack.getItem() instanceof BucketItem) {
               return !((BucketItemAccessor)stack.getItem()).effortlessbuilding$getFluid().isSame(Fluids.EMPTY);
            } else {
               return isToolInteractionItem(stack);
            }
         } else {
            BlockItem blockItem = (BlockItem)var2;
            BlockState defaultState = blockItem.getBlock().defaultBlockState();

            for(Property<?> property : defaultState.getProperties()) {
               if (property.getValueClass() == DoubleBlockHalf.class || property.getValueClass() == BedPart.class) {
                  return false;
               }
            }

            return true;
         }
      }
   }

   public static boolean isToolInteractionItem(ItemStack stack) {
      return stack.getItem() instanceof DiggerItem;
   }

   public static Vec3 getPlayerLookVec(Player player) {
      Vec3 lookVec = SableCompat.getPlayerLookVector(player);
      double x = lookVec.x;
      double y = lookVec.y;
      double z = lookVec.z;
      if (Math.abs(x) < 1.0E-4) {
         x = 1.0E-4;
      }

      if (Math.abs(x - (double)1.0F) < 1.0E-4) {
         x = 0.9999;
      }

      if (Math.abs(x + (double)1.0F) < 1.0E-4) {
         x = -0.9999;
      }

      if (Math.abs(y) < 1.0E-4) {
         y = 1.0E-4;
      }

      if (Math.abs(y - (double)1.0F) < 1.0E-4) {
         y = 0.9999;
      }

      if (Math.abs(y + (double)1.0F) < 1.0E-4) {
         y = -0.9999;
      }

      if (Math.abs(z) < 1.0E-4) {
         z = 1.0E-4;
      }

      if (Math.abs(z - (double)1.0F) < 1.0E-4) {
         z = 0.9999;
      }

      if (Math.abs(z + (double)1.0F) < 1.0E-4) {
         z = -0.9999;
      }

      return new Vec3(x, y, z);
   }

   public static Vec3 getPlayerEyePosition(Player player) {
      return SableCompat.getPlayerEyePosition(player);
   }

   public void addSystem(IBuildSystem system) {
      this.systems.add(system);
   }

   public @Nullable BlockSet runServerPipeline(BuildModeEnum mode, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos, Direction firstClickFace, Player player, BuildState action, ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill, ModeOptions.ActionEnum raisedEdge, ModeOptions.ActionEnum circleStart, ModeOptions.ActionEnum pointBuild, ModeOptions.ActionEnum pyramidSides, boolean protectTileEntities) {
      BuildModeSystem.setContext(new BuildModeSystem.Context(mode, firstPos, secondPos, thirdPos, fourthPos, firstClickFace, fill, cubeFill, raisedEdge, circleStart, pointBuild, pyramidSides));
      ConstraintSystem.setPlacementContext(new ConstraintSystem.PlacementContext(protectTileEntities));

      BlockSet var13;
      try {
         BlockSet blockSet = new BlockSet();
         this.processBlocks(blockSet, player, action);
         if (!blockSet.isEmpty()) {
            var13 = blockSet;
            return var13;
         }

         var13 = null;
      } finally {
         BuildModeSystem.clearContext();
         ConstraintSystem.clearPlacementContext();
      }

      return var13;
   }

   public static BlockSet toBlockSet(List<BlockPos> positions) {
      BlockSet blockSet = new BlockSet();

      for(BlockPos pos : positions) {
         blockSet.add(new BlockEntry(pos));
      }

      if (!positions.isEmpty()) {
         blockSet.firstPos = (BlockPos)positions.getFirst();
         blockSet.lastPos = (BlockPos)positions.getLast();
      }

      return blockSet;
   }

   public void processBlocks(BlockSet blocks, Player player, BuildState action) {
      for(IBuildSystem system : this.systems) {
         system.processBlocks(blocks, player, action);
      }

   }

   public static enum BuildState {
      PLACING,
      BREAKING;

      // $FF: synthetic method
      private static BuildState[] $values() {
         return new BuildState[]{PLACING, BREAKING};
      }
   }
}
