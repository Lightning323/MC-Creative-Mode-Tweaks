package nl.requios.effortlessbuilding;

import com.mojang.blaze3d.platform.InputConstants;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.buildmode.buildmodes.Plane;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.render.BlockPreviewRenderer;
import nl.requios.effortlessbuilding.render.RenderHandler;
import nl.requios.effortlessbuilding.screen.ModifiersScreen;
import nl.requios.effortlessbuilding.screen.RadialMenu;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.client.ClientModEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class NeoForgeClientSetup {
   @EventBusSubscriber(
      modid = "creative_mode_tweaks",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static class ModEvents {
      @SubscribeEvent
      public static void onModelsBaked(ModelEvent.BakingCompleted event) {
         BlockPreviewRenderer.clearCachedModels();
      }
   }

   @EventBusSubscriber(
      modid = "creative_mode_tweaks",
      value = {Dist.CLIENT},
      bus = Bus.GAME
   )
   public static class GameEvents {
      private static boolean prevRightDown = false;
      private static boolean prevLeftDown = false;

      @SubscribeEvent
      public static void onClientTick(ClientTickEvent.Post event) {
         if (ClientModEvents.KEY_OPEN_MODIFIERS_SCREEN.consumeClick()) {
            Minecraft.getInstance().setScreen(new ModifiersScreen());
         }

         Minecraft mc = Minecraft.getInstance();

         while(ClientModEvents.KEY_UNDO.consumeClick()) {
            if (InputConstants.isKeyDown(mc.getWindow().getWindow(), 341) || InputConstants.isKeyDown(mc.getWindow().getWindow(), 345)) {
               PacketHandler.sendToServer(new UndoPacket());
            }
         }

         while(ClientModEvents.KEY_REDO.consumeClick()) {
            if (InputConstants.isKeyDown(mc.getWindow().getWindow(), 341) || InputConstants.isKeyDown(mc.getWindow().getWindow(), 345)) {
               PacketHandler.sendToServer(new RedoPacket());
            }
         }

         if (mc.screen == null) {
            for(BuildModeEnum mode : BuildModeEnum.values()) {
               KeyMapping key = ClientModEvents.getBuildModeKey(mode);
               if (key == null) {
                  continue;
               }

               while(key.consumeClick()) {
                  BuildModes.CLIENT.setBuildMode(mode);
                  if (mc.player != null) {
                     mc.player.displayClientMessage(Component.translatable(mode.getNameKey()), true);
                  }
               }
            }

            while(ClientModEvents.KEY_TOGGLE_ANGEL_PLACEMENT.consumeClick()) {
               if (mc.player != null) {
                  if (Config.isAngelPlacementAllowed(mc.player)) {
                     boolean enabled = BuildSettings.CLIENT.toggleAngelPlacement();
                     mc.player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.angel_placement", Component.translatable(enabled ? "options.on" : "options.off")), true);
                  } else {
                     mc.player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.angel_placement_disabled"), true);
                  }
               }
            }

            if (ClientModEvents.isKeyDown(ClientModEvents.KEY_OPEN_RADIAL_MENU)) {
               mc.setScreen(RadialMenu.instance);
            }

            if (mc.player != null && mc.level != null && BuildPipelineClient.shouldInterceptPlacing()) {
               boolean rightDown = mc.options.keyUse.isDown();
               boolean leftDown = mc.options.keyAttack.isDown();
               boolean rightJustPressed = rightDown && !prevRightDown;
               boolean leftJustPressed = leftDown && !prevLeftDown;
               if (rightJustPressed) {
                  if (BuildPipelineClient.getBuildState() == BuildPipeline.BuildState.BREAKING) {
                     BuildPipelineClient.cancelCurrentSequence();
                  } else if (BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem()) || BuildPipelineClient.getBuildState() == BuildPipeline.BuildState.PLACING) {
                     BuildPipelineClient.handleRightClick(mc);
                  }
               }

               if (leftJustPressed && BuildPipelineClient.shouldInterceptBreaking()) {
                  if (BuildPipelineClient.getBuildState() == BuildPipeline.BuildState.PLACING) {
                     BuildPipelineClient.cancelCurrentSequence();
                  } else if (mc.player.getMainHandItem().isEmpty() || BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem()) || BuildPipelineClient.getBuildState() != null) {
                     BuildPipelineClient.handleLeftClick(mc);
                  }
               }

               prevRightDown = rightDown;
               prevLeftDown = leftDown;
            }
         } else {
            prevRightDown = false;
            prevLeftDown = false;
         }

      }

      @SubscribeEvent
      public static void onRenderGui(RenderGuiEvent.Post event) {
         RenderHandler.onRenderGui(event.getGuiGraphics());
      }

      @SubscribeEvent
      public static void onRenderLevel(RenderLevelStageEvent event) {
         if (event.getStage() == Stage.AFTER_ENTITIES) {
            Vec3 camPos = event.getCamera().getPosition();
            MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            RenderHandler.onRenderLevel(event.getPoseStack(), bufferSource, camPos.x, camPos.y, camPos.z);
         }
      }

      @SubscribeEvent
      public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
         if (event.getEntity().level().isClientSide()) {
            if (BuildPipelineClient.shouldInterceptPlacing()) {
               Player player = event.getEntity();
               if (player.getMainHandItem().isEmpty() || BuildPipeline.isBuildTriggerItem(player.getMainHandItem()) || BuildPipelineClient.getBuildState() != null) {
                  event.setCanceled(true);
               }

            }
         }
      }
   }
}
