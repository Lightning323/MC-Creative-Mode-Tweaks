package nl.requios.effortlessbuilding.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.UpdateServerConfigC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ServerConfigScreen extends Screen {
   private static final int PANEL_W = 320;
   private static final int ROW_H = 24;
   private static final int SECTION_GAP = 12;
   private static final int BOTTOM_BAR_H = 30;
   private static final String[] SURVIVAL_TOOLTIP_KEYS = new String[]{"creative_mode_tweaks.config.reach.tooltip", "creative_mode_tweaks.config.max_blocks_placed.tooltip", "creative_mode_tweaks.config.max_blocks_per_axis.tooltip", "creative_mode_tweaks.config.max_mirror_size.tooltip", "creative_mode_tweaks.config.max_array_count.tooltip", "creative_mode_tweaks.config.max_array_offset.tooltip", "creative_mode_tweaks.config.allow_breaking.tooltip", "creative_mode_tweaks.config.only_placed_blocks.tooltip", "creative_mode_tweaks.config.max_hardness.tooltip", "creative_mode_tweaks.config.require_tools.tooltip", "creative_mode_tweaks.config.use_durability.tooltip"};
   private static final String[] CREATIVE_TOOLTIP_KEYS = new String[]{"creative_mode_tweaks.config.reach.tooltip", "creative_mode_tweaks.config.max_blocks_placed.tooltip", "creative_mode_tweaks.config.max_blocks_per_axis.tooltip", "creative_mode_tweaks.config.max_mirror_size.tooltip", "creative_mode_tweaks.config.max_array_count.tooltip", "creative_mode_tweaks.config.max_array_offset.tooltip"};
   private static final String[] GENERAL_TOOLTIP_KEYS = new String[]{"creative_mode_tweaks.config.show_welcome_message.tooltip", "creative_mode_tweaks.config.show_build_mode_hint.tooltip"};
   private final ServerConfig scratch;
   private EditBox survReachField;
   private EditBox survMaxPlacedField;
   private EditBox survAxisField;
   private EditBox survMirrorSizeField;
   private EditBox survArrayCountField;
   private EditBox survArrayOffsetField;
   private boolean survAllowBreaking;
   private boolean survOnlyPlacedBlocks;
   private EditBox survMaxHardnessField;
   private boolean survRequireTools;
   private boolean survUseDurability;
   private EditBox creReachField;
   private EditBox creMaxPlacedField;
   private EditBox creAxisField;
   private EditBox creMirrorSizeField;
   private EditBox creArrayCountField;
   private EditBox creArrayOffsetField;
   private boolean showWelcomeMessage;
   private boolean showBuildModeHint;
   private final List<Object> widgetOrder = new ArrayList();
   private Button saveBtn;
   private Button cancelBtn;
   private int contentHeight;
   private int scrollOffset;
   private int maxScroll;

   public ServerConfigScreen() {
      super(Component.translatable("creative_mode_tweaks.screen.server_config"));
      this.scratch = ServerConfig.fromJson(ServerConfig.INSTANCE.toJson());
   }

   protected void init() {
      super.init();
      this.scrollOffset = 0;
      this.widgetOrder.clear();
      this.survAllowBreaking = this.scratch.survivalAllowBreaking;
      this.survOnlyPlacedBlocks = this.scratch.survivalOnlyPlacedBlocks;
      this.survRequireTools = this.scratch.survivalRequireTools;
      this.survUseDurability = this.scratch.survivalUseDurability;
      this.showWelcomeMessage = this.scratch.showWelcomeMessage;
      this.showBuildModeHint = this.scratch.showBuildModeHint;
      int left = (this.width - 320) / 2;
      int fieldX = left + 220;
      int fieldW = 60;
      int y = 0;
      y += 24;
      y += 24;
      this.survReachField = this.addIntField(fieldX, fieldW, this.scratch.survivalReach);
      y += 24;
      this.survMaxPlacedField = this.addIntField(fieldX, fieldW, this.scratch.survivalMaxBlocksPlaced);
      y += 24;
      this.survAxisField = this.addIntField(fieldX, fieldW, this.scratch.survivalMaxBlocksPerAxis);
      y += 24;
      this.survMirrorSizeField = this.addIntField(fieldX, fieldW, this.scratch.survivalMaxMirrorSize);
      y += 24;
      this.survArrayCountField = this.addIntField(fieldX, fieldW, this.scratch.survivalMaxArrayCount);
      y += 24;
      this.survArrayOffsetField = this.addIntField(fieldX, fieldW, this.scratch.survivalMaxArrayOffset);
      y += 24;
      Button btnAllowBreaking = (Button)this.addRenderableWidget(Button.builder(Component.literal(onOff(this.survAllowBreaking)), (btn) -> {
         this.survAllowBreaking = !this.survAllowBreaking;
         btn.setMessage(Component.literal(onOff(this.survAllowBreaking)));
      }).bounds(fieldX, 0, fieldW, 18).build());
      this.widgetOrder.add(btnAllowBreaking);
      y += 24;
      Button btnOnlyPlaced = (Button)this.addRenderableWidget(Button.builder(Component.literal(onOff(this.survOnlyPlacedBlocks)), (btn) -> {
         this.survOnlyPlacedBlocks = !this.survOnlyPlacedBlocks;
         btn.setMessage(Component.literal(onOff(this.survOnlyPlacedBlocks)));
      }).bounds(fieldX, 0, fieldW, 18).build());
      this.widgetOrder.add(btnOnlyPlaced);
      y += 24;
      this.survMaxHardnessField = new EditBox(this.font, fieldX, 0, fieldW, 18, Component.literal(""));
      this.survMaxHardnessField.setValue(formatFloat(this.scratch.survivalMaxHardness));
      this.survMaxHardnessField.setFilter((s) -> s.isEmpty() || s.matches("-?\\d{0,5}\\.?\\d{0,2}"));
      this.addRenderableWidget(this.survMaxHardnessField);
      this.widgetOrder.add(this.survMaxHardnessField);
      y += 24;
      Button btnRequireTools = (Button)this.addRenderableWidget(Button.builder(Component.literal(onOff(this.survRequireTools)), (btn) -> {
         this.survRequireTools = !this.survRequireTools;
         btn.setMessage(Component.literal(onOff(this.survRequireTools)));
      }).bounds(fieldX, 0, fieldW, 18).build());
      this.widgetOrder.add(btnRequireTools);
      y += 24;
      Button btnUseDurability = (Button)this.addRenderableWidget(Button.builder(Component.literal(onOff(this.survUseDurability)), (btn) -> {
         this.survUseDurability = !this.survUseDurability;
         btn.setMessage(Component.literal(onOff(this.survUseDurability)));
      }).bounds(fieldX, 0, fieldW, 18).build());
      this.widgetOrder.add(btnUseDurability);
      y += 24;
      y += 12;
      y += 24;
      this.creReachField = this.addIntField(fieldX, fieldW, this.scratch.creativeReach);
      y += 24;
      this.creMaxPlacedField = this.addIntField(fieldX, fieldW, this.scratch.creativeMaxBlocksPlaced);
      y += 24;
      this.creAxisField = this.addIntField(fieldX, fieldW, this.scratch.creativeMaxBlocksPerAxis);
      y += 24;
      this.creMirrorSizeField = this.addIntField(fieldX, fieldW, this.scratch.creativeMaxMirrorSize);
      y += 24;
      this.creArrayCountField = this.addIntField(fieldX, fieldW, this.scratch.creativeMaxArrayCount);
      y += 24;
      this.creArrayOffsetField = this.addIntField(fieldX, fieldW, this.scratch.creativeMaxArrayOffset);
      y += 24;
      y += 12;
      Button btnWelcomeMessage = (Button)this.addRenderableWidget(Button.builder(Component.literal(onOff(this.showWelcomeMessage)), (btn) -> {
         this.showWelcomeMessage = !this.showWelcomeMessage;
         btn.setMessage(Component.literal(onOff(this.showWelcomeMessage)));
      }).bounds(fieldX, 0, fieldW, 18).build());
      this.widgetOrder.add(btnWelcomeMessage);
      y += 24;
      Button btnBuildModeHint = (Button)this.addRenderableWidget(Button.builder(Component.literal(onOff(this.showBuildModeHint)), (btn) -> {
         this.showBuildModeHint = !this.showBuildModeHint;
         btn.setMessage(Component.literal(onOff(this.showBuildModeHint)));
      }).bounds(fieldX, 0, fieldW, 18).build());
      this.widgetOrder.add(btnBuildModeHint);
      y += 24;
      y += 12;
      this.contentHeight = y;
      int bottomY = this.height - 30 + 5;
      this.saveBtn = (Button)this.addRenderableWidget(Button.builder(Component.translatable("creative_mode_tweaks.button.save"), (btn) -> this.save()).bounds(this.width / 2 - 60, bottomY, 55, 20).build());
      this.cancelBtn = (Button)this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), (btn) -> this.onClose()).bounds(this.width / 2 + 5, bottomY, 55, 20).build());
      int scrollableArea = this.height - 20 - 30;
      this.maxScroll = Math.max(0, this.contentHeight - scrollableArea);
      this.repositionWidgets();
   }

   private EditBox addIntField(int x, int w, int value) {
      EditBox field = new EditBox(this.font, x, 0, w, 18, Component.literal(""));
      field.setValue(String.valueOf(value));
      field.setFilter((s) -> s.isEmpty() || s.matches("\\d{0,6}"));
      this.addRenderableWidget(field);
      this.widgetOrder.add(field);
      return field;
   }

   private void repositionWidgets() {
      int left = (this.width - 320) / 2;
      int fieldX = left + 220;
      int top = 20 - this.scrollOffset;
      int y = top + 24;
      int i = 0;
      y += 24;

      for(int n = 0; n < 11; ++n) {
         this.setPos(i++, fieldX, y);
         y += 24;
      }

      y += 12;
      y += 24;

      for(int n = 0; n < 6; ++n) {
         this.setPos(i++, fieldX, y);
         y += 24;
      }

      y += 12;

      for(int n = 0; n < 2; ++n) {
         this.setPos(i++, fieldX, y);
         y += 24;
      }

   }

   private void setPos(int index, int x, int y) {
      if (index < this.widgetOrder.size()) {
         Object w = this.widgetOrder.get(index);
         if (w instanceof EditBox) {
            EditBox eb = (EditBox)w;
            eb.setPosition(x, y);
         } else if (w instanceof Button) {
            Button btn = (Button)w;
            btn.setPosition(x, y);
         }

      }
   }

   private void save() {
      this.scratch.survivalReach = this.parseOrDefault(this.survReachField.getValue(), 20);
      this.scratch.survivalMaxBlocksPlaced = this.parseOrDefault(this.survMaxPlacedField.getValue(), 200);
      this.scratch.survivalMaxBlocksPerAxis = this.parseOrDefault(this.survAxisField.getValue(), 12);
      this.scratch.survivalMaxMirrorSize = this.parseOrDefault(this.survMirrorSizeField.getValue(), 32);
      this.scratch.survivalMaxArrayCount = this.parseOrDefault(this.survArrayCountField.getValue(), 10);
      this.scratch.survivalMaxArrayOffset = this.parseOrDefault(this.survArrayOffsetField.getValue(), 32);
      this.scratch.survivalAllowBreaking = this.survAllowBreaking;
      this.scratch.survivalOnlyPlacedBlocks = this.survOnlyPlacedBlocks;
      this.scratch.survivalMaxHardness = this.parseFloatOrDefault(this.survMaxHardnessField.getValue(), -1.0F);
      this.scratch.survivalRequireTools = this.survRequireTools;
      this.scratch.survivalUseDurability = this.survUseDurability;
      this.scratch.creativeReach = this.parseOrDefault(this.creReachField.getValue(), 64);
      this.scratch.creativeMaxBlocksPlaced = this.parseOrDefault(this.creMaxPlacedField.getValue(), 10000);
      this.scratch.creativeMaxBlocksPerAxis = this.parseOrDefault(this.creAxisField.getValue(), 64);
      this.scratch.creativeMaxMirrorSize = this.parseOrDefault(this.creMirrorSizeField.getValue(), 128);
      this.scratch.creativeMaxArrayCount = this.parseOrDefault(this.creArrayCountField.getValue(), 64);
      this.scratch.creativeMaxArrayOffset = this.parseOrDefault(this.creArrayOffsetField.getValue(), 128);
      this.scratch.showWelcomeMessage = this.showWelcomeMessage;
      this.scratch.showBuildModeHint = this.showBuildModeHint;
      this.scratch.clampAll();
      PacketHandler.sendToServer(new UpdateServerConfigC2SPacket(this.scratch.toJson()));
      this.onClose();
   }

   private int parseOrDefault(String s, int def) {
      try {
         return Integer.parseInt(s);
      } catch (NumberFormatException var4) {
         return def;
      }
   }

   private float parseFloatOrDefault(String s, float def) {
      try {
         return Float.parseFloat(s);
      } catch (NumberFormatException var4) {
         return def;
      }
   }

   public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      guiGraphics.fill(0, 0, this.width, this.height, -1778384896);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.render(graphics, mouseX, mouseY, partialTick);
      int left = (this.width - 320) / 2;
      int top = 20 - this.scrollOffset;
      int labelX = left + 10;
      graphics.enableScissor(0, 0, this.width, this.height - 30);
      graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 6, 16777215);
      int y = top + 24;
      graphics.drawString(this.font, Component.translatable("creative_mode_tweaks.config.section_survival"), labelX, y + 5, 5635925);
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.reach");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.max_blocks_placed");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.max_blocks_per_axis");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.max_mirror_size");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.max_array_count");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.max_array_offset");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.allow_breaking");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.only_placed_blocks");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.max_hardness");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.require_tools");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.use_durability");
      y += 24;
      y += 12;
      graphics.drawString(this.font, Component.translatable("creative_mode_tweaks.config.section_creative"), labelX, y + 5, 16777045);
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.reach");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.max_blocks_placed");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.max_blocks_per_axis");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.max_mirror_size");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.max_array_count");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.max_array_offset");
      y += 24;
      y += 12;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.show_welcome_message");
      y += 24;
      this.drawLabel(graphics, labelX + 8, y, "creative_mode_tweaks.config.show_build_mode_hint");
      y += 24;
      graphics.disableScissor();
      graphics.fill(0, this.height - 30, this.width, this.height, -16777216);
      this.saveBtn.render(graphics, mouseX, mouseY, partialTick);
      this.cancelBtn.render(graphics, mouseX, mouseY, partialTick);
      this.renderRowTooltips(graphics, mouseX, mouseY);
   }

   private void renderRowTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
      if (mouseY < this.height - 30) {
         int left = (this.width - 320) / 2;
         int labelX = left + 10 + 8;
         int labelMaxX = left + 215;
         int top = 20 - this.scrollOffset;
         int y = top + 24;
         if (mouseX >= labelX && mouseX < labelMaxX) {
            y += 24;

            for(String key : SURVIVAL_TOOLTIP_KEYS) {
               if (mouseY >= y && mouseY < y + 24) {
                  this.renderMultiLineTooltip(graphics, key, mouseX, mouseY);
                  return;
               }

               y += 24;
            }

            y += 12;
            y += 24;

            for(String key : CREATIVE_TOOLTIP_KEYS) {
               if (mouseY >= y && mouseY < y + 24) {
                  this.renderMultiLineTooltip(graphics, key, mouseX, mouseY);
                  return;
               }

               y += 24;
            }

            y += 12;

            for(String key : GENERAL_TOOLTIP_KEYS) {
               if (mouseY >= y && mouseY < y + 24) {
                  this.renderMultiLineTooltip(graphics, key, mouseX, mouseY);
                  return;
               }

               y += 24;
            }

         }
      }
   }

   private void renderMultiLineTooltip(GuiGraphics graphics, String key, int mouseX, int mouseY) {
      String text = Component.translatable(key).getString();
      String[] lines = text.split("\n");
      List<Component> components = new ArrayList();

      for(String line : lines) {
         components.add(Component.literal(line));
      }

      graphics.renderTooltip(this.font, components, Optional.empty(), mouseX, mouseY);
   }

   private boolean isInRow(int mouseX, int mouseY, int left, int rowY) {
      return mouseX >= left && mouseX < left + 320 && mouseY >= rowY && mouseY < rowY + 24;
   }

   private void drawLabel(GuiGraphics graphics, int x, int y, String key) {
      graphics.drawString(this.font, Component.translatable(key), x, y + 5, 16777215);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.scrollOffset = Math.clamp((long)((int)((double)this.scrollOffset - verticalAmount * (double)10.0F)), 0, this.maxScroll);
      this.repositionWidgets();
      return true;
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static String onOff(boolean value) {
      return value ? "ON" : "OFF";
   }

   private static String formatFloat(float v) {
      return v == (float)((int)v) ? String.valueOf((int)v) : String.valueOf(v);
   }
}
