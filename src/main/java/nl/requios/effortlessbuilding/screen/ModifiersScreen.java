package nl.requios.effortlessbuilding.screen;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.modifier.ArrayModifier;
import nl.requios.effortlessbuilding.modifier.IModifier;
import nl.requios.effortlessbuilding.modifier.MirrorModifier;
import nl.requios.effortlessbuilding.modifier.ModifierSerializer;
import nl.requios.effortlessbuilding.modifier.ModifierSystem;
import nl.requios.effortlessbuilding.modifier.RadialMirrorModifier;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.UpdateModifiersC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class ModifiersScreen extends Screen {
   private static final int PANEL_W = 390;
   private static final int PANEL_H = 246;
   private static final int LIST_W = 175;
   private static final int DIV_OX = 183;
   private static final int SET_OX = 189;
   private static final int ROW_H = 22;
   private int selectedIndex = -1;
   private List<IModifier> filteredModifiers = new ArrayList();
   private List<Integer> filteredToReal = new ArrayList();
   private ScreenWidgets widgets;
   private ModifierSettingsPanel settingsPanel;

   public ModifiersScreen() {
      super(Component.literal("Modifiers"));
   }

   protected void init() {
      this.widgets = new ScreenWidgets(this.font, (x$0) -> {
         AbstractWidget var10000 = (AbstractWidget)this.addRenderableWidget(x$0);
      });
      this.settingsPanel = new ModifierSettingsPanel(this.widgets, () -> this.rebuildWidgets());
      this.widgets.clear();
      this.rebuildFilteredList();
      int px = this.panelX();
      int py = this.panelY();
      this.buildListWidgets(px, py);
      if (this.selectedIndex >= 0 && this.selectedIndex < this.filteredModifiers.size()) {
         int sx = px + 189;
         int sy = py + 24;
         this.settingsPanel.buildWidgets((IModifier)this.filteredModifiers.get(this.selectedIndex), sx, sy);
      }

      this.addRenderableWidget(Button.builder(Component.literal("Close"), (btn) -> this.onClose()).bounds(px + 390 - 74, py + 246 - 22, 70, 16).build());
   }

   private void rebuildFilteredList() {
      this.filteredModifiers.clear();
      this.filteredToReal.clear();
      String currentDim = currentDimension();
      List<IModifier> all = ModifierSystem.CLIENT.getModifiers();

      for(int i = 0; i < all.size(); ++i) {
         IModifier m = (IModifier)all.get(i);
         String dim = m.getDimension();
         if (dim == null || dim.isEmpty() || dim.equals(currentDim)) {
            this.filteredModifiers.add(m);
            this.filteredToReal.add(i);
         }
      }

      if (this.selectedIndex >= this.filteredModifiers.size()) {
         this.selectedIndex = this.filteredModifiers.size() - 1;
      }

   }

   private static String currentDimension() {
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null ? player.level().dimension().location().toString() : "";
   }

   private void buildListWidgets(int px, int py) {
      List<IModifier> modifiers = this.filteredModifiers;
      int lx = px + 4;
      int rowBase = py + 24;
      int count = modifiers.size();

      for(int j = 0; j < count; ++j) {
         int realIdx = (Integer)this.filteredToReal.get(j);
         IModifier modifier = (IModifier)modifiers.get(j);
         int ry = rowBase + j * 22;
         this.widgets.addCheckbox(lx + 2, ry + 7, "", modifier.isEnabled(), () -> {
            modifier.setEnabled(!modifier.isEnabled());
            this.rebuildWidgets();
         });
         final int i=j;
         Button upBtn = (Button)this.addRenderableWidget(Button.builder(Component.literal("↑"), (btn) -> {
            ModifierSystem.CLIENT.moveModifier(realIdx, -1);
            this.selectedIndex = i - 1;
            this.rebuildWidgets();
         }).bounds(lx + 175 - 44, ry + 4, 12, 14).build());
         if (j == 0) {
            upBtn.active = false;
         }

         Button downBtn = (Button)this.addRenderableWidget(Button.builder(Component.literal("↓"), (btn) -> {
            ModifierSystem.CLIENT.moveModifier(realIdx, 1);
            this.selectedIndex = i + 1;
            this.rebuildWidgets();
         }).bounds(lx + 175 - 30, ry + 4, 12, 14).build());
         if (j == count - 1) {
            downBtn.active = false;
         }

         this.addRenderableWidget(Button.builder(Component.literal("×"), (btn) -> {
            ModifierSystem.CLIENT.removeModifier(realIdx);
            if (this.selectedIndex >= this.filteredModifiers.size() - 1) {
               this.selectedIndex = this.filteredModifiers.size() - 2;
            }

            this.rebuildWidgets();
         }).bounds(lx + 175 - 16, ry + 4, 14, 14).build());
      }

      int addY = py + 246 - 22;
      this.addRenderableWidget(Button.builder(Component.literal("+ Mirror"), (btn) -> {
         MirrorModifier mirror = new MirrorModifier();
         BlockPos pos = playerBlockPos();
         mirror.originX = (double)pos.getX();
         mirror.originY = (double)pos.getY();
         mirror.originZ = (double)pos.getZ();
         mirror.setDimension(currentDimension());
         ModifierSystem.CLIENT.addModifier(mirror);
         this.rebuildFilteredList();
         this.selectedIndex = this.filteredModifiers.size() - 1;
         this.rebuildWidgets();
      }).bounds(px + 4, addY, 58, 16).build());
      this.addRenderableWidget(Button.builder(Component.literal("+ Array"), (btn) -> {
         ArrayModifier array = new ArrayModifier();
         array.setDimension(currentDimension());
         ModifierSystem.CLIENT.addModifier(array);
         this.rebuildFilteredList();
         this.selectedIndex = this.filteredModifiers.size() - 1;
         this.rebuildWidgets();
      }).bounds(px + 66, addY, 52, 16).build());
      this.addRenderableWidget(Button.builder(Component.literal("+ Radial"), (btn) -> {
         RadialMirrorModifier radial = new RadialMirrorModifier();
         BlockPos pos = playerBlockPos();
         radial.originX = (double)pos.getX();
         radial.originY = (double)pos.getY();
         radial.originZ = (double)pos.getZ();
         radial.setDimension(currentDimension());
         ModifierSystem.CLIENT.addModifier(radial);
         this.rebuildFilteredList();
         this.selectedIndex = this.filteredModifiers.size() - 1;
         this.rebuildWidgets();
      }).bounds(px + 122, addY, 56, 16).build());
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.widgets.handleCheckboxClick(mouseX, mouseY)) {
         return true;
      } else {
         int px = this.panelX();
         int py = this.panelY();
         int lx = px + 4;
         int rowBase = py + 24;

         for(int i = 0; i < this.filteredModifiers.size(); ++i) {
            int ry = rowBase + i * 22;
            if (mouseX >= (double)(lx + 18) && mouseX < (double)(lx + 175 - 47) && mouseY >= (double)ry && mouseY < (double)(ry + 22) && this.selectedIndex != i) {
               this.selectedIndex = i;
               this.rebuildWidgets();
               return true;
            }
         }

         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      return this.widgets.handleScroll(mouseX, mouseY, scrollY) ? true : super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
   }

   public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      guiGraphics.fill(0, 0, this.width, this.height, -1778384896);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.render(graphics, mouseX, mouseY, partialTick);
      int px = this.panelX();
      int py = this.panelY();
      int divX = px + 183;
      graphics.fill(divX, py + 2, divX + 1, py + 246 - 2, -11184811);
      graphics.drawString(this.font, "Modifiers", px + 5, py + 8, 16777215);
      String settingsHeader = this.selectedIndex >= 0 && this.selectedIndex < this.filteredModifiers.size() ? ((IModifier)this.filteredModifiers.get(this.selectedIndex)).getDisplayName().getString() + " Settings" : "Settings";
      graphics.drawString(this.font, settingsHeader, divX + 5, py + 8, 16777215);
      int lx = px + 4;
      int rowBase = py + 24;
      if (this.filteredModifiers.isEmpty()) {
         graphics.drawString(this.font, "Add a modifier below.", lx + 2, rowBase + 7, 8947848);
      } else {
         for(int i = 0; i < this.filteredModifiers.size(); ++i) {
            int ry = rowBase + i * 22;
            if (i == this.selectedIndex) {
               graphics.fill(lx, ry, lx + 175, ry + 22 - 1, 1090519039);
            }

            graphics.drawString(this.font, ((IModifier)this.filteredModifiers.get(i)).getDisplayName().getString(), lx + 20, ry + 7, 15658734);
         }
      }

      this.widgets.renderCheckboxes(graphics, mouseX, mouseY);
      if (this.selectedIndex >= 0 && this.selectedIndex < this.filteredModifiers.size()) {
         int sx = px + 189;
         int sy = py + 24;
         this.settingsPanel.renderLabels(graphics, (IModifier)this.filteredModifiers.get(this.selectedIndex), sx, sy);
      } else if (!this.filteredModifiers.isEmpty()) {
         graphics.drawString(this.font, "Select a modifier", divX + 6, py + 32, 8947848);
      }

   }

   private int panelX() {
      return (this.width - 390) / 2;
   }

   private int panelY() {
      return (this.height - 246) / 2;
   }

   private static BlockPos playerBlockPos() {
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null ? player.blockPosition() : BlockPos.ZERO;
   }

   public void onClose() {
      String json = ModifierSerializer.serialize(ModifierSystem.CLIENT.getModifiers());
      PacketHandler.sendToServer(new UpdateModifiersC2SPacket(json));
      super.onClose();
   }

   public boolean isPauseScreen() {
      return false;
   }
}
