package org.lightning323.creative_mode_tweaks.hotbar;

import net.minecraft.world.entity.player.Player;
import org.lightning323.creative_mode_tweaks.Config;
import java.util.Collections;

/**
 * Because this class is used on both client and server, it cannot use client side classes!!!
 */
public class HotbarUtil {
    public static final int HOTBAR_SLOT_GUI_SIZE = 20;
    public static final int INVENTORY_SIZE = 36;

    //Available to other parts of the mod
    public static int hotbarScroll = 0;
    public static int hotbarSlots = 0;

    public static int getDistanceOnInvWheel(int a, int b) {
        int diff = a - b;
        // Wraps the difference into the range [-total/2, total/2]
        return Math.floorMod(diff + INVENTORY_SIZE / 2, INVENTORY_SIZE) - INVENTORY_SIZE / 2;
    }

    /**
     * Runs both client side and server side
     *
     * @param player
     * @param offset
     */
    public static void rotateInventory(Player player, int offset, boolean keepSelection) {
        Collections.rotate(player.getInventory().items, -offset);
        if (keepSelection) {
            int scrollOffsset = player.getInventory().selected - HotbarUtil.hotbarScroll;
            player.getInventory().selected = Math.floorMod(
                    player.getInventory().selected - offset,
                    36
            ); //Do this to keep the selection consistent
            //We CANNOT use client side classes in this since this method is used on both client and server
            if (player.level().isClientSide) HotbarUtil.hotbarScroll = player.getInventory().selected - scrollOffsset;
        }
    }

    public static boolean enableEnhancedHotbar(Player player) {
        return (player != null && player.isCreative() && Config.enhanceCreativeHotbar) || Config.enhanceSurvivalHotbar;
    }
}