package nl.requios.effortlessbuilding.screen;

import nl.requios.effortlessbuilding.menu.RandomizerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class RandomizerScreen extends AbstractContainerScreen<RandomizerMenu> {
   private static final int RATIO_Y = 19;
   private static final int RATIO_WIDTH = 16;
   private static final int RATIO_HEIGHT = 14;
   private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/gui/container/randomizertool.png");

   public RandomizerScreen(RandomizerMenu menu, Inventory playerInventory, Component title) {
      super(menu, playerInventory, title);
      this.imageWidth = 176;
      this.imageHeight = 150;
      this.inventoryLabelY = 56;
   }

   protected void init() {
      super.init();
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.render(graphics, mouseX, mouseY, partialTick);
      this.renderRatios(graphics, mouseX, mouseY);
      this.renderTooltip(graphics, mouseX, mouseY);
      int hoveredRatio = this.getHoveredRatio((double)mouseX, (double)mouseY);
      if (hoveredRatio >= 0) {
         graphics.renderTooltip(this.font, Component.translatable("creative_mode_tweaks.screen.randomizer.ratio.tooltip"), mouseX, mouseY);
      }

   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int slot = this.getHoveredRatio(mouseX, mouseY);
      if (slot >= 0 && verticalAmount != (double)0.0F) {
         int delta = verticalAmount > (double)0.0F ? 1 : -1;
         if (((RandomizerMenu)this.menu).adjustRatioClient(slot, delta)) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gameMode != null) {
               minecraft.gameMode.handleInventoryButtonClick(((RandomizerMenu)this.menu).containerId, slot + (delta > 0 ? 0 : 9));
            }
         }

         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
      graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
   }

   private void renderRatios(GuiGraphics graphics, int mouseX, int mouseY) {
      int hoveredRatio = this.getHoveredRatio((double)mouseX, (double)mouseY);

      for(int i = 0; i < 9; ++i) {
         int x = this.leftPos + 8 + i * 18;
         int y = this.topPos + 19;
         if (i == hoveredRatio) {
            graphics.fill(x, y, x + 16, y + 14, -1718908021);
         }

         String text = Integer.toString(((RandomizerMenu)this.menu).getRatio(i));
         graphics.drawString(this.font, text, x + 8 - this.font.width(text) / 2 - 3, y + 4, 4210752, false);
      }

   }

   private int getHoveredRatio(double mouseX, double mouseY) {
      if (!(mouseY < (double)(this.topPos + 19)) && !(mouseY >= (double)(this.topPos + 19 + 14))) {
         int slot = (int)((mouseX - (double)this.leftPos - (double)8.0F) / (double)18.0F);
         if (slot >= 0 && slot < 9) {
            int x = this.leftPos + 8 + slot * 18;
            return mouseX >= (double)x && mouseX < (double)(x + 16) ? slot : -1;
         } else {
            return -1;
         }
      } else {
         return -1;
      }
   }
}
