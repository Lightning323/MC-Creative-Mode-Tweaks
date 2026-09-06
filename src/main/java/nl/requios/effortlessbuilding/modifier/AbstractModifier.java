package nl.requios.effortlessbuilding.modifier;

import net.minecraft.network.chat.Component;

public abstract class AbstractModifier implements IModifier {
   private boolean enabled = true;
   private String dimension = "";

   public abstract Component getDisplayName();

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public String getDimension() {
      return this.dimension;
   }

   public void setDimension(String dimension) {
      this.dimension = dimension != null ? dimension : "";
   }
}
