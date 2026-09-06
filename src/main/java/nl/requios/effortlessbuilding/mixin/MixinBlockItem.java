package nl.requios.effortlessbuilding.mixin;

import java.util.List;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockItem.class})
public class MixinBlockItem {
   @Inject(
      method = {"place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
      if (context.getLevel().isClientSide()) {
         if (BuildPipelineClient.shouldInterceptPlacing()) {
            cir.setReturnValue(InteractionResult.sidedSuccess(true));
            cir.cancel();
         }
      }
   }

   @Inject(
      method = {"place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;"},
      at = {@At("TAIL")}
   )
   private void effortlessbuilding$trackPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
      InteractionResult result = (InteractionResult)cir.getReturnValue();
      if (result.consumesAction()) {
         Player player = context.getPlayer();
         if (player != null) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            if (level.isClientSide()) {
               PlacedBlockTracker.clientTrackAll(level.dimension(), List.of(pos));
            } else if (player instanceof ServerPlayer) {
               PlacedBlockTracker.trackAll(player.getUUID(), level.dimension(), List.of(pos));
            }

         }
      }
   }
}
