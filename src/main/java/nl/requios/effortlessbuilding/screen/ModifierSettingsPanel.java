package nl.requios.effortlessbuilding.screen;

import org.lightning323.creative_mode_tweaks.Config;
import nl.requios.effortlessbuilding.modifier.ArrayModifier;
import nl.requios.effortlessbuilding.modifier.IModifier;
import nl.requios.effortlessbuilding.modifier.MirrorModifier;
import nl.requios.effortlessbuilding.modifier.RadialMirrorModifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public class ModifierSettingsPanel {
   private final ScreenWidgets widgets;
   private final Runnable rebuildWidgets;

   public ModifierSettingsPanel(ScreenWidgets widgets, Runnable rebuildWidgets) {
      this.widgets = widgets;
      this.rebuildWidgets = rebuildWidgets;
   }

   public void buildWidgets(IModifier modifier, int sx, int sy) {
      LocalPlayer player = Minecraft.getInstance().player;
      int maxMirrorSize = player != null ? Config.getBuildingMaxMirrorSize(player) : 256;
      int maxArrayCount = player != null ? Config.getBuildingMaxArrayCount(player) : 64;
      int maxArrayOffset = player != null ? Config.getBuildingMaxArrayOffset(player) : 64;
      if (modifier instanceof MirrorModifier mirror) {
         this.buildMirrorWidgets(mirror, sx, sy, maxMirrorSize);
      } else if (modifier instanceof ArrayModifier array) {
         this.buildArrayWidgets(array, sx, sy, maxArrayCount, maxArrayOffset);
      } else if (modifier instanceof RadialMirrorModifier radial) {
         this.buildRadialWidgets(radial, sx, sy, maxMirrorSize);
      }

   }

   public void renderLabels(GuiGraphics graphics, IModifier modifier, int sx, int sy) {
      if (modifier instanceof MirrorModifier) {
         int vecY = sy + 22 + 8;
         graphics.drawString(Minecraft.getInstance().font, "Axis", sx, sy + 3, 13421772);
         graphics.drawString(Minecraft.getInstance().font, "Position", sx, vecY + 4, 13421772);
         graphics.drawString(Minecraft.getInstance().font, "Size", sx, vecY + 22 + 4, 13421772);
         this.widgets.renderVec3Labels(graphics, sx, vecY);
      } else if (modifier instanceof ArrayModifier) {
         int vecY = sy + 22 + 8;
         graphics.drawString(Minecraft.getInstance().font, "Count", sx, sy + 4, 13421772);
         graphics.drawString(Minecraft.getInstance().font, "Offset", sx, vecY + 4, 13421772);
         this.widgets.renderVec3Labels(graphics, sx, vecY);
      } else if (modifier instanceof RadialMirrorModifier) {
         int vecY = sy + 44 + 8;
         graphics.drawString(Minecraft.getInstance().font, "Slices", sx, sy + 4, 13421772);
         graphics.drawString(Minecraft.getInstance().font, "Position", sx, vecY + 4, 13421772);
         graphics.drawString(Minecraft.getInstance().font, "Size", sx, vecY + 22 + 4, 13421772);
         this.widgets.renderVec3Labels(graphics, sx, vecY);
      }

   }

   private void buildMirrorWidgets(MirrorModifier mirror, int sx, int sy, int maxSize) {
      int cbX = sx + 62;
      this.widgets.addCheckbox(cbX, sy + 3, "X", mirror.mirrorX, () -> {
         mirror.mirrorX = !mirror.mirrorX;
         this.rebuildWidgets.run();
      });
      this.widgets.addCheckbox(cbX + 28, sy + 3, "Y", mirror.mirrorY, () -> {
         mirror.mirrorY = !mirror.mirrorY;
         this.rebuildWidgets.run();
      });
      this.widgets.addCheckbox(cbX + 56, sy + 3, "Z", mirror.mirrorZ, () -> {
         mirror.mirrorZ = !mirror.mirrorZ;
         this.rebuildWidgets.run();
      });
      int vecY = sy + 22 + 8;
      this.widgets.addVec3DoubleField(sx, vecY, mirror.originX, mirror.originY, mirror.originZ, (v) -> mirror.originX = v, (v) -> mirror.originY = v, (v) -> mirror.originZ = v);
      this.widgets.addSetToPlayerButton(sx, vecY, () -> {
         BlockPos pos = playerBlockPos();
         mirror.originX = (double)pos.getX();
         mirror.originY = (double)pos.getY();
         mirror.originZ = (double)pos.getZ();
         this.rebuildWidgets.run();
      });
      this.widgets.addIntField(sx, vecY + 22, String.valueOf(mirror.size), (v) -> mirror.size = Math.clamp((long)v, 1, maxSize));
   }

   private void buildArrayWidgets(ArrayModifier array, int sx, int sy, int maxCount, int maxOffset) {
      this.widgets.addIntField(sx, sy, String.valueOf(array.count), (v) -> array.count = Math.clamp((long)v, 0, maxCount));
      int vecY = sy + 22 + 8;
      this.widgets.addVec3IntField(sx, vecY, array.offsetX, array.offsetY, array.offsetZ, (v) -> array.offsetX = Math.clamp((long)v, -maxOffset, maxOffset), (v) -> array.offsetY = Math.clamp((long)v, -maxOffset, maxOffset), (v) -> array.offsetZ = Math.clamp((long)v, -maxOffset, maxOffset));
   }

   private void buildRadialWidgets(RadialMirrorModifier radial, int sx, int sy, int maxSize) {
      this.widgets.addIntField(sx, sy, String.valueOf(radial.slices), (v) -> radial.slices = Math.clamp((long)v, 2, 32));
      this.widgets.addCheckbox(sx, sy + 22 + 3, "Mirror slices", radial.mirrorSlices, () -> {
         radial.mirrorSlices = !radial.mirrorSlices;
         this.rebuildWidgets.run();
      });
      int vecY = sy + 44 + 8;
      this.widgets.addVec3DoubleField(sx, vecY, radial.originX, radial.originY, radial.originZ, (v) -> radial.originX = v, (v) -> radial.originY = v, (v) -> radial.originZ = v);
      this.widgets.addSetToPlayerButton(sx, vecY, () -> {
         BlockPos pos = playerBlockPos();
         radial.originX = (double)pos.getX();
         radial.originY = (double)pos.getY();
         radial.originZ = (double)pos.getZ();
         this.rebuildWidgets.run();
      });
      this.widgets.addIntField(sx, vecY + 22, String.valueOf(radial.size), (v) -> radial.size = Math.clamp((long)v, 1, maxSize));
   }

   private static BlockPos playerBlockPos() {
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null ? player.blockPosition() : BlockPos.ZERO;
   }
}
