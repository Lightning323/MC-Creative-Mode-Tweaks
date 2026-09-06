package nl.requios.effortlessbuilding.utilities;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public class KeyBindings {
   public static final String CATEGORY = "key.categories.creative_mode_tweaks";
   public static KeyMapping openRadialMenu;
   public static KeyMapping openModifiersScreen;
   public static KeyMapping undo;
   public static KeyMapping redo;
   public static KeyMapping togglePlaneType;
   public static KeyMapping toggleAngelPlacement;
   private static final Map<BuildModeEnum, KeyMapping> BUILD_MODE_KEYS = new EnumMap<>(BuildModeEnum.class);

   public static boolean isKeyDown(KeyMapping keyMapping) {
      long window = Minecraft.getInstance().getWindow().getWindow();
      return InputConstants.isKeyDown(window, ((KeyMappingAccessor)keyMapping).effortlessbuilding$getKey().getValue());
   }

   public static KeyMapping getBuildModeKey(BuildModeEnum mode) {
      return BUILD_MODE_KEYS.get(mode);
   }

   public static Collection<KeyMapping> getBuildModeKeys() {
      return BUILD_MODE_KEYS.values();
   }

   static {
      openRadialMenu = new KeyMapping("key.creative_mode_tweaks.open_radial_menu", Type.KEYSYM, 342, "key.categories.creative_mode_tweaks");
      openModifiersScreen = new KeyMapping("key.creative_mode_tweaks.open_modifiers_screen", Type.KEYSYM, 334, "key.categories.creative_mode_tweaks");
      undo = new KeyMapping("key.creative_mode_tweaks.undo.desc", Type.KEYSYM, 90, "key.categories.creative_mode_tweaks");
      redo = new KeyMapping("key.creative_mode_tweaks.redo.desc", Type.KEYSYM, 89, "key.categories.creative_mode_tweaks");
      togglePlaneType = new KeyMapping("key.creative_mode_tweaks.toggle_plane_type", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.creative_mode_tweaks");
      toggleAngelPlacement = new KeyMapping("key.creative_mode_tweaks.toggle_angel_placement", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.creative_mode_tweaks");

      for(BuildModeEnum mode : BuildModeEnum.values()) {
         BUILD_MODE_KEYS.put(mode, new KeyMapping(mode.getNameKey(), Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.creative_mode_tweaks"));
      }
   }
}
