package nl.requios.effortlessbuilding.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Arrays;
import java.util.Map;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.utilities.BreakDisplayTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import org.jetbrains.annotations.Nullable;

public class RenderHandler {
   private static int lastPreviewSize = 0;
   private static Component cachedFeedback = null;
   private static final Component PLACING_TEXT;
   private static final Component INTERACTING_TEXT;
   private static final Component BREAKING_TEXT;

    public static void onRenderLevel(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, org.joml.Matrix4f modelViewMatrix, org.joml.Matrix4f projectionMatrix) {
       ModifierRenderer.render(poseStack, bufferSource, camX, camY, camZ);
       BlockPreviewRenderer.render(poseStack, bufferSource, camX, camY, camZ, modelViewMatrix, projectionMatrix);
    }

   public static void onRenderGui(GuiGraphics graphics) {
      renderSubtitle(graphics);
      drawStacks(graphics);
   }

     /**
      * Action-bar count/dims + placement tick sound. Called by the preview cache
      * on shape change only — the result is cached for the per-frame redisplay.
      * Bounds arrive precomputed from the shape pass, so this never rescans a
      * position list.
      *
      * @param overLimit when true the count cap cut blocks out of this shape, so
      *                  the line renders red: the whole thing won't get built.
      */
     public static void updateFeedbackSimple(int count, BlockPos min, BlockPos max,
                                            boolean sequenceActive, BuildPipeline.BuildState pendingAction,
                                            boolean overLimit) {
       Minecraft mc = Minecraft.getInstance();
       if (mc.player != null && mc.level != null) {
          playTickSound(mc, min, count, sequenceActive, pendingAction);

          lastPreviewSize = count;
          cachedFeedback = null;
          if (sequenceActive) {
             cachedFeedback = buildCountMessage(count,
                     max.getX() - min.getX() + 1,
                     max.getY() - min.getY() + 1,
                     max.getZ() - min.getZ() + 1, overLimit);
             mc.player.displayClientMessage(cachedFeedback, true);
          }
       }
    }

    /**
     * Per-frame redisplay of the cached count/dims line. Zero scans, zero
     * string building — just re-shows the component the shape change computed.
     */
    public static void showCachedFeedback(boolean sequenceActive, BuildPipeline.BuildState pendingAction) {
       if (!sequenceActive || cachedFeedback == null) {
          return;
       }
       Minecraft mc = Minecraft.getInstance();
       if (mc.player != null) {
          mc.player.displayClientMessage(cachedFeedback, true);
       }
    }

    private static void playTickSound(Minecraft mc, BlockPos at, int count,
                                      boolean sequenceActive, BuildPipeline.BuildState pendingAction) {
       if (!sequenceActive || count == lastPreviewSize) {
          return;
       }
       boolean isBreaking = pendingAction == BuildPipeline.BuildState.BREAKING;
       SoundType soundType;
       if (isBreaking) {
          soundType = mc.level.getBlockState(at).getSoundType();
       } else {
          Item held = mc.player.getMainHandItem().getItem();
          if (held instanceof BlockItem blockItem) {
             soundType = blockItem.getBlock().defaultBlockState().getSoundType();
          } else {
             soundType = SoundType.STONE;
          }
       }

       SoundEvent sound = isBreaking ? soundType.getBreakSound() : soundType.getPlaceSound();
       mc.level.playLocalSound(at, sound, SoundSource.BLOCKS, soundType.getVolume() * 0.25F, soundType.getPitch(), false);
    }

    private static Component buildCountMessage(int count, int dx, int dy, int dz, boolean overLimit) {
       int[] dims = Arrays.stream(new int[]{dx, dy, dz}).filter((d) -> d > 1).toArray();
       String msg;
       if (dims.length <= 1) {
          msg = String.valueOf(count);
       } else {
          StringBuilder sb = (new StringBuilder()).append(count).append(" (");

          for(int i = 0; i < dims.length; ++i) {
             if (i > 0) {
                sb.append('×');
             }

             sb.append(dims[i]);
          }

          sb.append(')');
          msg = sb.toString();
       }

       return overLimit ? Component.literal(msg).withStyle(ChatFormatting.RED) : Component.literal(msg);
    }

    /** Resets the placement-tick baseline (mode off, preview empty). */
    public static void resetPreviewSize() {
      lastPreviewSize = 0;
      cachedFeedback = null;
   }

   private static void drawStacks(GuiGraphics guiGraphics) {
      BuildPipeline.BuildState state = BuildPipelineClient.getBuildState();
      if (state != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int x = screenWidth / 2 + 10;
            int y = screenHeight / 2 - 8;
            if (state == BuildPipeline.BuildState.PLACING) {
               drawPlacingStacks(guiGraphics, mc, x, y);
            } else {
               drawBreakingStacks(guiGraphics, mc, x, y);
            }

         }
      }
   }

   private static void drawPlacingStacks(GuiGraphics guiGraphics, Minecraft mc, int x, int y) {
//      ItemUsageTracker tracker = BuildPipelineClient.ITEM_USAGE;
//      Map<Item, Integer> stacks = tracker.total;
//      if (!mc.player.getAbilities().instabuild || stacks.size() > 1) {
//         int i = 0;
//
//         for(Map.Entry<Item, Integer> entry : stacks.entrySet()) {
//            int total = (Integer)entry.getValue();
//            int have = (Integer)tracker.inInventory.getOrDefault(entry.getKey(), 0);
//            int networkCount = (Integer)tracker.fromNetwork.getOrDefault(entry.getKey(), 0);
//            int missing = tracker.getMissingCount((Item)entry.getKey());
//            int available = Math.min(total - missing, total);
//            boolean usingAE2 = networkCount > 0 && have < total && missing == 0;
//            if (available > 0) {
//               if (usingAE2) {
//                  drawItemStack(guiGraphics, new ItemStack((ItemLike)entry.getKey(), available), x + i * 20, y, false, ChatFormatting.GREEN.getColor(), "AE2");
//               } else {
//                  drawItemStack(guiGraphics, new ItemStack((ItemLike)entry.getKey(), available), x + i * 20, y, false);
//               }
//
//               ++i;
//            }
//
//            if (missing > 0) {
//               drawItemStack(guiGraphics, new ItemStack((ItemLike)entry.getKey(), missing), x + i * 20, y, true);
//               ++i;
//            }
//         }
//
//      }
   }

   private static void drawItemStack(GuiGraphics guiGraphics, ItemStack stack, int x, int y, boolean missing, int textColor, @Nullable String suffix) {
      guiGraphics.renderItem(stack, x, y);
      Font font = Minecraft.getInstance().font;
      String count = String.valueOf(stack.getCount());
      String text = suffix != null ? count + suffix : count;
      int color = missing ? ChatFormatting.RED.getColor() : textColor;
      int textX = x + 19 - 2 - font.width(text);
      int textY = y + 6 + 3;
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
      guiGraphics.drawString(font, text, textX, textY, color, true);
      guiGraphics.pose().popPose();
   }

   private static void drawBreakingStacks(GuiGraphics guiGraphics, Minecraft mc, int x, int y) {
      BreakDisplayTracker tracker = BuildPipelineClient.BREAK_DISPLAY;
      if (!tracker.breakable.isEmpty() || !tracker.rejected.isEmpty()) {
         if (!mc.player.getAbilities().instabuild || !tracker.rejected.isEmpty()) {
            int i = 0;

            for(Map.Entry<Item, Integer> entry : tracker.breakable.entrySet()) {
               drawItemStack(guiGraphics, new ItemStack((ItemLike)entry.getKey(), (Integer)entry.getValue()), x + i * 20, y, false);
               ++i;
            }

            for(Map.Entry<Item, Integer> entry : tracker.rejected.entrySet()) {
               drawItemStack(guiGraphics, new ItemStack((ItemLike)entry.getKey(), (Integer)entry.getValue()), x + i * 20, y, true);
               ++i;
            }

            if (tracker.hasMissingTools()) {
               for(Item tool : tracker.missingTools) {
                  drawItemStack(guiGraphics, new ItemStack(tool), x + i * 20, y, true);
                  ++i;
               }
            }

         }
      }
   }

   private static void drawItemStack(GuiGraphics guiGraphics, ItemStack stack, int x, int y, boolean missing) {
      guiGraphics.renderItem(stack, x, y);
      Font font = Minecraft.getInstance().font;
      String text = String.valueOf(stack.getCount());
      int color = missing ? ChatFormatting.RED.getColor() : ChatFormatting.WHITE.getColor();
      int textX = x + 19 - 2 - font.width(text);
      int textY = y + 6 + 3;
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
      guiGraphics.drawString(font, text, textX, textY, color, true);
      guiGraphics.pose().popPose();
   }

   private static void renderSubtitle(GuiGraphics graphics) {
      BuildPipeline.BuildState pendingAction = BuildPipelineClient.getBuildState();
      if (pendingAction != null) {
         Minecraft mc = Minecraft.getInstance();
         boolean isToolInteraction = pendingAction == BuildPipeline.BuildState.PLACING && mc.player != null && BuildPipeline.isToolInteractionItem(mc.player.getMainHandItem());
         Component text = pendingAction == BuildPipeline.BuildState.BREAKING ? BREAKING_TEXT : (isToolInteraction ? INTERACTING_TEXT : PLACING_TEXT);
         int screenWidth = mc.getWindow().getGuiScaledWidth();
         int screenHeight = mc.getWindow().getGuiScaledHeight();
         Font font = mc.font;
         graphics.pose().pushPose();
         graphics.pose().translate((double)screenWidth / (double)2.0F, (double)(screenHeight - 54), (double)0.0F);
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         int w = font.width(text);
         graphics.drawString(font, text, -w / 2, -4, -1, true);
         RenderSystem.disableBlend();
         graphics.pose().popPose();
      }
   }

   static {
      PLACING_TEXT = Component.literal("Left-click to ").withStyle(ChatFormatting.WHITE).append(Component.literal("cancel").withStyle(ChatFormatting.DARK_AQUA)).append(Component.literal(", Right-click to ").withStyle(ChatFormatting.WHITE)).append(Component.literal("place").withStyle(ChatFormatting.DARK_AQUA));
      INTERACTING_TEXT = Component.literal("Left-click to ").withStyle(ChatFormatting.WHITE).append(Component.literal("cancel").withStyle(ChatFormatting.DARK_AQUA)).append(Component.literal(", Right-click to ").withStyle(ChatFormatting.WHITE)).append(Component.literal("interact").withStyle(ChatFormatting.DARK_AQUA));
      BREAKING_TEXT = Component.literal("Left-click to ").withStyle(ChatFormatting.WHITE).append(Component.literal("break").withStyle(ChatFormatting.RED)).append(Component.literal(", Right-click to ").withStyle(ChatFormatting.WHITE)).append(Component.literal("cancel").withStyle(ChatFormatting.RED));
   }
}
