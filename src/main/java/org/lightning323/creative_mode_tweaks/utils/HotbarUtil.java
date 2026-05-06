package org.lightning323.creative_mode_tweaks.utils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class HotbarUtil {
   //The actual hotbar sprite
   public static final ResourceLocation HOTBAR_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar");
   //the hotbar selection box
   public static final ResourceLocation HOTBAR_SELECTION_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");


   public static final ResourceLocation HOTBAR_OFFHAND_LEFT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_left");
   public static final ResourceLocation HOTBAR_OFFHAND_RIGHT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_right");
   public static final ResourceLocation HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_background");
   public static final ResourceLocation HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_progress");
   public static final int HOTBAR_UNIT_LENGTH = 182;
   public static final int HOTBAR_UNIT_HEIGHT = 22;


   public static int getBarCount(int guiWidth) {
      return Mth.clamp(guiWidth / 182, 1, 4);
   }
}
