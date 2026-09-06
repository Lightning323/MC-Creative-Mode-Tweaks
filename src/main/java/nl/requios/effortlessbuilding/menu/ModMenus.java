package nl.requios.effortlessbuilding.menu;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
   public static final MenuType<RandomizerMenu> RANDOMIZER;

   private ModMenus() {
   }

   static {
      RANDOMIZER = new MenuType(RandomizerMenu::new, FeatureFlags.VANILLA_SET);
   }
}
