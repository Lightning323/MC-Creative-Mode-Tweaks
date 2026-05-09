package org.lightning323.creative_mode_tweaks.utils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;

public class HotbarUtil {

    //The actual hotbar sprite
    public static final ResourceLocation HOTBAR_SPRITE_9 = ResourceLocation.withDefaultNamespace("hud/hotbar");
    public static final ResourceLocation HOTBAR_SPRITE_12 = CreativeModeTweaks.resource("hud/12_hotbar");
    public static final ResourceLocation HOTBAR_SPRITE_15 = CreativeModeTweaks.resource("hud/15_hotbar");
    //the hotbar selection box
    public static final ResourceLocation HOTBAR_SELECTION_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");


    public static final ResourceLocation HOTBAR_OFFHAND_LEFT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_left");
    public static final ResourceLocation HOTBAR_OFFHAND_RIGHT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_right");
    public static final ResourceLocation HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_background");
    public static final ResourceLocation HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_progress");

    public static final int HOTBAR_UNIT_SIZE = 20;

    final int INVENTORY_SIZE = 9 * 4;

    public static int getHotbarSlots(int guiWidth) {
        double availableSlots = ((double) guiWidth / HOTBAR_UNIT_SIZE) - 4;
        return (int) Math.floor(Mth.clamp(availableSlots, 9, 15) / 3) * 3;
    }
}
