package nl.requios.effortlessbuilding.screen;

import nl.requios.effortlessbuilding.item.RandomizerTooltipData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public final class RandomizerTooltipComponent implements ClientTooltipComponent {
   private static final int COLUMNS = 9;
   private static final int SLOT_SIZE = 18;
   private static final int PADDING = 2;
   private final RandomizerTooltipData data;

   public RandomizerTooltipComponent(RandomizerTooltipData data) {
      this.data = data;
   }

   public int getHeight() {
      return 22;
   }

   public int getWidth(Font font) {
      return 166;
   }

   public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource bufferSource) {
   }

   public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
      this.getWidth(font);
      int height = this.getHeight();

      for(int i = 0; i < this.data.stacks().size(); ++i) {
         int itemX = x + 2 + i % 9 * 18;
         int itemY = y + 2 + i / 9 * 18;
         ItemStack stack = (ItemStack)this.data.stacks().get(i);
         if (!stack.isEmpty()) {
            graphics.renderItem(stack, itemX, itemY);
            graphics.renderItemDecorations(font, stack, itemX, itemY);
         }
      }

   }
}
