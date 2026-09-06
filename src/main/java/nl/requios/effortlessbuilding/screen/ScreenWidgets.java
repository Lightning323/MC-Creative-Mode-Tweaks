package nl.requios.effortlessbuilding.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class ScreenWidgets {
   public static final int LABEL_W = 62;
   public static final int EDIT_W = 40;
   public static final int VEC_EDIT_W = 38;
   public static final int FIELD_H = 16;
   public static final int ROW_GAP = 22;
   public static final int VEC3_EXTRA_Y = 8;
   private final List<IntFieldEntry> intFields = new ArrayList();
   private final List<DoubleFieldEntry> doubleFields = new ArrayList();
   private final List<CheckboxEntry> checkboxes = new ArrayList();
   private final Font font;
   private final Consumer<AbstractWidget> widgetAdder;

   public ScreenWidgets(Font font, Consumer<AbstractWidget> widgetAdder) {
      this.font = font;
      this.widgetAdder = widgetAdder;
   }

   public void clear() {
      this.intFields.clear();
      this.doubleFields.clear();
      this.checkboxes.clear();
   }

   public void addIntField(int x, int y, String value, IntConsumer setter) {
      EditBox field = new EditBox(this.font, x + 62 + 12, y, 40, 16, Component.empty());
      field.setValue(value);
      field.setFilter((s) -> s.matches("-?\\d*"));
      field.setResponder((s) -> {
         try {
            setter.accept(Integer.parseInt(s));
         } catch (NumberFormatException var3) {
         }

      });
      field.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));
      this.widgetAdder.accept(Button.builder(Component.literal("−"), (btn) -> this.stepInt(field, setter, -1)).bounds(x + 62, y, 12, 16).build());
      this.widgetAdder.accept(field);
      this.widgetAdder.accept(Button.builder(Component.literal("+"), (btn) -> this.stepInt(field, setter, 1)).bounds(x + 62 + 12 + 40, y, 12, 16).build());
      this.intFields.add(new IntFieldEntry(field, setter));
   }

   public void stepInt(EditBox field, IntConsumer setter, int delta) {
      int cur;
      try {
         cur = Integer.parseInt(field.getValue());
      } catch (NumberFormatException var6) {
         cur = 0;
      }

      int next = cur + delta;
      field.setValue(String.valueOf(next));
      setter.accept(next);
   }

   public void addDoubleField(int x, int y, String value, DoubleConsumer setter) {
      EditBox field = new EditBox(this.font, x + 62 + 16, y, 40, 16, Component.empty());
      field.setValue(value);
      field.setFilter((s) -> s.matches("-?\\d*\\.?\\d*"));
      field.setResponder((s) -> {
         try {
            setter.accept(Double.parseDouble(s));
         } catch (NumberFormatException var3) {
         }

      });
      this.widgetAdder.accept(Button.builder(Component.literal("−"), (btn) -> this.stepDouble(field, setter, (double)-0.5F)).bounds(x + 62 + 2, y, 12, 16).build());
      this.widgetAdder.accept(field);
      this.widgetAdder.accept(Button.builder(Component.literal("+"), (btn) -> this.stepDouble(field, setter, (double)0.5F)).bounds(x + 62 + 80, y, 12, 16).build());
      this.doubleFields.add(new DoubleFieldEntry(field, setter));
   }

   public void stepDouble(EditBox field, DoubleConsumer setter, double delta) {
      double cur;
      try {
         cur = Double.parseDouble(field.getValue());
      } catch (NumberFormatException var9) {
         cur = (double)0.0F;
      }

      double next = cur + delta;
      field.setValue(formatDouble(next));
      setter.accept(next);
   }

   public void addVec3DoubleField(int x, int y, double valX, double valY, double valZ, DoubleConsumer setX, DoubleConsumer setY, DoubleConsumer setZ) {
      int fieldStart = x + 62;
      int spacing = 42;
      this.addSmallDoubleField(fieldStart, y, formatDouble(valX), setX);
      this.addSmallDoubleField(fieldStart + spacing, y, formatDouble(valY), setY);
      this.addSmallDoubleField(fieldStart + spacing * 2, y, formatDouble(valZ), setZ);
   }

   public void addVec3IntField(int x, int y, int valX, int valY, int valZ, IntConsumer setX, IntConsumer setY, IntConsumer setZ) {
      int fieldStart = x + 62;
      int spacing = 42;
      this.addSmallIntField(fieldStart, y, String.valueOf(valX), setX);
      this.addSmallIntField(fieldStart + spacing, y, String.valueOf(valY), setY);
      this.addSmallIntField(fieldStart + spacing * 2, y, String.valueOf(valZ), setZ);
   }

   private void addSmallDoubleField(int x, int y, String value, DoubleConsumer setter) {
      EditBox field = new EditBox(this.font, x, y, 38, 16, Component.empty());
      field.setValue(value);
      field.setFilter((s) -> s.matches("-?\\d*\\.?\\d*"));
      field.setResponder((s) -> {
         try {
            setter.accept(Double.parseDouble(s));
         } catch (NumberFormatException var3) {
         }

      });
      field.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));
      this.widgetAdder.accept(field);
      this.doubleFields.add(new DoubleFieldEntry(field, setter));
   }

   private void addSmallIntField(int x, int y, String value, IntConsumer setter) {
      EditBox field = new EditBox(this.font, x, y, 38, 16, Component.empty());
      field.setValue(value);
      field.setFilter((s) -> s.matches("-?\\d*"));
      field.setResponder((s) -> {
         try {
            setter.accept(Integer.parseInt(s));
         } catch (NumberFormatException var3) {
         }

      });
      field.setTooltip(Tooltip.create(Component.literal("Scroll to adjust")));
      this.widgetAdder.accept(field);
      this.intFields.add(new IntFieldEntry(field, setter));
   }

   public void addSetToPlayerButton(int x, int y, Runnable action) {
      int fieldStart = x + 62;
      int spacing = 42;
      int btnX = fieldStart + spacing * 3 + 2;
      this.widgetAdder.accept(Button.builder(Component.literal("⌖"), (b) -> action.run()).bounds(btnX, y, 16, 16).tooltip(Tooltip.create(Component.literal("Set to player position"))).build());
   }

   public void addCheckbox(int x, int y, String label, boolean value, Runnable toggle) {
      String text = (value ? "☑" : "☐") + (label.isEmpty() ? "" : " " + label);
      int w = this.font.width(text) + 2;
      int h = 11;
      this.checkboxes.add(new CheckboxEntry(x, y, w, h, label, value, toggle));
   }

   public boolean handleCheckboxClick(double mouseX, double mouseY) {
      for(CheckboxEntry cb : this.checkboxes) {
         if (mouseX >= (double)cb.x() && mouseX < (double)(cb.x() + cb.w()) && mouseY >= (double)cb.y() && mouseY < (double)(cb.y() + cb.h())) {
            cb.toggle().run();
            return true;
         }
      }

      return false;
   }

   public boolean handleScroll(double mouseX, double mouseY, double scrollY) {
      int delta = scrollY > (double)0.0F ? 1 : -1;

      for(IntFieldEntry entry : this.intFields) {
         EditBox field = entry.field();
         if (mouseX >= (double)field.getX() && mouseX <= (double)(field.getX() + field.getWidth()) && mouseY >= (double)field.getY() && mouseY <= (double)(field.getY() + field.getHeight())) {
            this.stepInt(field, entry.setter(), delta);
            return true;
         }
      }

      double halfDelta = scrollY > (double)0.0F ? (double)0.5F : (double)-0.5F;

      for(DoubleFieldEntry entry : this.doubleFields) {
         EditBox field = entry.field();
         if (mouseX >= (double)field.getX() && mouseX <= (double)(field.getX() + field.getWidth()) && mouseY >= (double)field.getY() && mouseY <= (double)(field.getY() + field.getHeight())) {
            this.stepDouble(field, entry.setter(), halfDelta);
            return true;
         }
      }

      return false;
   }

   public void renderCheckboxes(GuiGraphics graphics, int mouseX, int mouseY) {
      for(CheckboxEntry cb : this.checkboxes) {
         String var10000 = cb.value() ? "☑" : "☐";
         String text = var10000 + (cb.label().isEmpty() ? "" : " " + cb.label());
         boolean hovered = mouseX >= cb.x() && mouseX < cb.x() + cb.w() && mouseY >= cb.y() && mouseY < cb.y() + cb.h();
         int color = hovered ? 16777215 : 13421772;
         graphics.drawString(this.font, text, cb.x(), cb.y(), color);
      }

   }

   public void renderVec3Labels(GuiGraphics graphics, int sx, int y) {
      int fieldStart = sx + 62;
      int spacing = 42;
      int labelY = y - 9;
      graphics.drawString(this.font, "X", fieldStart + 19 - 2, labelY, 13421772);
      graphics.drawString(this.font, "Y", fieldStart + spacing + 19 - 2, labelY, 13421772);
      graphics.drawString(this.font, "Z", fieldStart + spacing * 2 + 19 - 2, labelY, 13421772);
   }

   public static String formatDouble(double v) {
      return v == Math.floor(v) ? String.valueOf((int)v) : String.valueOf(v);
   }

   public static record IntFieldEntry(EditBox field, IntConsumer setter) {
   }

   public static record DoubleFieldEntry(EditBox field, DoubleConsumer setter) {
   }

   public static record CheckboxEntry(int x, int y, int w, int h, String label, boolean value, Runnable toggle) {
   }
}
