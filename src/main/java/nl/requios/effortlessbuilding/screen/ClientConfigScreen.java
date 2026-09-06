package nl.requios.effortlessbuilding.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import nl.requios.effortlessbuilding.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClientConfigScreen extends Screen {
   private static final int PANEL_W = 320;
   private static final int ROW_H = 24;
   private static final int FIELD_W = 100;
   private static final int FIELD_H = 20;
   private static final String[] LABEL_KEYS = new String[]{"creative_mode_tweaks.config.preview_block_size", "creative_mode_tweaks.config.preview_block_transparency", "creative_mode_tweaks.config.max_block_previews", "creative_mode_tweaks.config.protect_tile_entities"};
   private float sizeValue;
   private float transparencyValue;
   private int maxBlockPreviews;
   private boolean protectTileEntities;
   private EditBox maxPreviewsField;

   public ClientConfigScreen() {
      super(Component.translatable("creative_mode_tweaks.screen.client_config"));
   }

   protected void init() {
      super.init();
      ClientConfig cfg = ClientConfig.INSTANCE;
      this.sizeValue = cfg.getPreviewBlockSize();
      this.transparencyValue = cfg.getPreviewBlockTransparency();
      this.maxBlockPreviews = cfg.getMaxBlockPreviews();
      this.protectTileEntities = cfg.shouldProtectTileEntities();
      int left = (this.width - 320) / 2;
      int totalH = LABEL_KEYS.length * 24 + 40;
      int top = (this.height - totalH) / 2;
      int fieldX = left + 320 - 100 - 10;
      this.addRenderableWidget(new AbstractSliderButton(fieldX, top, 100, 20, Component.literal(Math.round(this.sizeValue * 100.0F) + "%"), (double)sizeFraction(this.sizeValue)) {
         protected void updateMessage() {
            this.setMessage(Component.literal(Math.round(ClientConfigScreen.this.sizeValue * 100.0F) + "%"));
         }

         protected void applyValue() {
            ClientConfigScreen.this.sizeValue = ClientConfigScreen.sizeFromFraction((float)this.value);
         }
      });
      int rowY = top + 24;
      this.addRenderableWidget(new AbstractSliderButton(fieldX, rowY, 100, 20, Component.literal(Math.round(this.transparencyValue * 100.0F) + "%"), (double)this.transparencyValue) {
         protected void updateMessage() {
            this.setMessage(Component.literal(Math.round(ClientConfigScreen.this.transparencyValue * 100.0F) + "%"));
         }

         protected void applyValue() {
            ClientConfigScreen.this.transparencyValue = (float)Math.round((float)this.value * 20.0F) / 20.0F;
         }
      });
      rowY += 24;
      int btnW = 14;
      int editW = 100 - btnW * 2;
      this.addRenderableWidget(Button.builder(Component.literal("−"), (btn) -> this.stepMaxPreviews(-50)).bounds(fieldX, rowY, btnW, 20).build());
      this.maxPreviewsField = new EditBox(this.font, fieldX + btnW, rowY, editW, 20, Component.empty());
      this.maxPreviewsField.setValue(String.valueOf(this.maxBlockPreviews));
      this.maxPreviewsField.setFilter((s) -> s.isEmpty() || s.matches("\\d{0,5}"));
      this.maxPreviewsField.setResponder((s) -> {
         try {
            this.maxBlockPreviews = Integer.parseInt(s);
         } catch (NumberFormatException var3) {
         }

      });
      this.addRenderableWidget(this.maxPreviewsField);
      this.addRenderableWidget(Button.builder(Component.literal("+"), (btn) -> this.stepMaxPreviews(50)).bounds(fieldX + btnW + editW, rowY, btnW, 20).build());
      rowY += 24;
      this.addRenderableWidget(Button.builder(Component.literal(onOff(this.protectTileEntities)), (btn) -> {
         this.protectTileEntities = !this.protectTileEntities;
         btn.setMessage(Component.literal(onOff(this.protectTileEntities)));
      }).bounds(fieldX, rowY, 100, 20).build());
      rowY += 40;
      this.addRenderableWidget(Button.builder(Component.translatable("creative_mode_tweaks.button.save"), (btn) -> this.save()).bounds(left + 160 - 60, rowY, 55, 20).build());
      this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), (btn) -> this.onClose()).bounds(left + 160 + 5, rowY, 55, 20).build());
   }

   private void stepMaxPreviews(int delta) {
      this.maxBlockPreviews = Math.clamp((long)(this.maxBlockPreviews + delta), 50, 10000);
      this.maxPreviewsField.setValue(String.valueOf(this.maxBlockPreviews));
   }

   private void save() {
      ClientConfig cfg = ClientConfig.INSTANCE;
      cfg.setPreviewBlockSize(this.sizeValue);
      cfg.setPreviewBlockTransparency(this.transparencyValue);
      cfg.setMaxBlockPreviews(this.maxBlockPreviews);
      cfg.setProtectTileEntities(this.protectTileEntities);
      cfg.save();
      this.onClose();
   }

   public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      guiGraphics.fill(0, 0, this.width, this.height, -1778384896);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.render(graphics, mouseX, mouseY, partialTick);
      int left = (this.width - 320) / 2;
      int totalH = LABEL_KEYS.length * 24 + 40;
      int top = (this.height - totalH) / 2;
      int labelX = left + 10;
      graphics.drawCenteredString(this.font, this.title, this.width / 2, top - 16, 16777215);
      int rowY = top;

      for(String key : LABEL_KEYS) {
         graphics.drawString(this.font, Component.translatable(key), labelX, rowY + 6, 16777215);
         rowY += 24;
      }

      this.renderRowTooltips(graphics, mouseX, mouseY);
   }

   private void renderRowTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
      int left = (this.width - 320) / 2;
      int totalH = LABEL_KEYS.length * 24 + 40;
      int top = (this.height - totalH) / 2;
      int labelX = left + 10;
      int labelMaxX = left + 320 - 100 - 15;
      if (mouseX >= labelX && mouseX < labelMaxX) {
         int rowY = top;

         for(String key : LABEL_KEYS) {
            if (mouseY >= rowY && mouseY < rowY + 24) {
               String tooltipKey = key + ".tooltip";
               String text = Component.translatable(tooltipKey).getString();
               if (!text.equals(tooltipKey)) {
                  String[] lines = text.split("\n");
                  List<Component> components = new ArrayList();

                  for(String line : lines) {
                     components.add(Component.literal(line));
                  }

                  graphics.renderTooltip(this.font, components, Optional.empty(), mouseX, mouseY);
               }

               return;
            }

            rowY += 24;
         }

      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.maxPreviewsField != null && mouseX >= (double)this.maxPreviewsField.getX() && mouseX <= (double)(this.maxPreviewsField.getX() + this.maxPreviewsField.getWidth()) && mouseY >= (double)this.maxPreviewsField.getY() && mouseY <= (double)(this.maxPreviewsField.getY() + this.maxPreviewsField.getHeight())) {
         this.stepMaxPreviews(verticalAmount > (double)0.0F ? 50 : -50);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   private static float sizeFraction(float size) {
      return (size - 0.1F) / 0.9F;
   }

   private static float sizeFromFraction(float frac) {
      float raw = 0.1F + frac * 0.9F;
      return (float)Math.round(raw * 20.0F) / 20.0F;
   }

   private static String onOff(boolean value) {
      return value ? "ON" : "OFF";
   }

   public boolean isPauseScreen() {
      return false;
   }
}
