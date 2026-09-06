package nl.requios.effortlessbuilding.modifier;

import nl.requios.effortlessbuilding.buildpipeline.IBuildSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public interface IModifier extends IBuildSystem {
   Component getDisplayName();

   boolean isEnabled();

   void setEnabled(boolean var1);

   String getDimension();

   void setDimension(String var1);

   default boolean matchesDimension(Player player) {
      String dim = this.getDimension();
      return dim != null && !dim.isEmpty() ? dim.equals(player.level().dimension().location().toString()) : true;
   }
}
