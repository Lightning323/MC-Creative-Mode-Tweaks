package nl.requios.effortlessbuilding.buildpipeline;

import java.util.List;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class BuildModeSystem implements IBuildSystem {
   public static final BuildModeSystem INSTANCE = new BuildModeSystem();
   private static final ThreadLocal<Context> CONTEXT = new ThreadLocal();

   public static void setContext(Context context) {
      CONTEXT.set(context);
   }

   public static void clearContext() {
      CONTEXT.remove();
   }

   public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
      Context ctx = (Context)CONTEXT.get();
      if (ctx != null) {
         ModeOptions.applyForCalculation(ctx.fill(), ctx.cubeFill(), ctx.raisedEdge(), ctx.circleStart(), ctx.pointBuild(), ctx.pyramidSides());
         ctx.mode().instance.setFirstClickFace(ctx.firstClickFace());
         List<BlockPos> rawPositions = ctx.mode().instance.getServerBlocks(player, ctx.firstPos(), ctx.secondPos(), ctx.thirdPos(), ctx.fourthPos());
         if (!rawPositions.isEmpty()) {
            for(BlockPos pos : rawPositions) {
               blocks.add(new BlockEntry(pos));
            }

            if (!rawPositions.isEmpty()) {
               blocks.firstPos = (BlockPos)rawPositions.getFirst();
               blocks.lastPos = (BlockPos)rawPositions.getLast();
            }

         }
      }
   }

   public static record Context(BuildModeEnum mode, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos, Direction firstClickFace, ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill, ModeOptions.ActionEnum raisedEdge, ModeOptions.ActionEnum circleStart, ModeOptions.ActionEnum pointBuild, ModeOptions.ActionEnum pyramidSides) {
   }
}
