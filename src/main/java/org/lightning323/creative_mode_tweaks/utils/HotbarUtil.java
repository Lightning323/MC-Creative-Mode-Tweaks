package org.lightning323.creative_mode_tweaks.utils;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;
import org.lightning323.creative_mode_tweaks.network.ClientVanillaSender;
import org.lightning323.creative_mode_tweaks.network.packets.InventoryRotatePayload;
import org.lightning323.creative_mode_tweaks.network.packets.InventorySyncPayload;

import java.util.Collections;

public class HotbarUtil {

    //The actual hotbar sprite
    public static final ResourceLocation HOTBAR_SPRITE_9 = ResourceLocation.withDefaultNamespace("hud/hotbar");
    public static final ResourceLocation HOTBAR_SPRITE_12 = CreativeModeTweaks.resource("hud/12_hotbar");
    public static final ResourceLocation HOTBAR_SPRITE_15 = CreativeModeTweaks.resource("hud/15_hotbar");
    public static final ResourceLocation HOTBAR_SPRITE_18 = CreativeModeTweaks.resource("hud/18_hotbar");
    //the hotbar selection box
    public static final ResourceLocation HOTBAR_SELECTION_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");


    public static final ResourceLocation HOTBAR_OFFHAND_LEFT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_left");
    public static final ResourceLocation HOTBAR_OFFHAND_RIGHT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_right");
    public static final ResourceLocation HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_background");
    public static final ResourceLocation HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_progress");

    public static final int HOTBAR_SLOT_GUI_SIZE = 20;
    public static final int INVENTORY_SIZE = 36;

    //Available to other parts of the mod
    public static int hotbarScroll = 0;
    public static int hotbarSlots = 0;

    public static void rotateInventoryAndSync(LocalPlayer player, int offset, boolean keepSelection) {
        if (player == null) return;
        rotateInventory(player, offset, keepSelection);
        int hash = InventoryHasher.computeInventoryHash(player.getInventory());
        //Send a request for the server to rotate the inventory
        PacketDistributor.sendToServer(new InventoryRotatePayload(hash, offset, keepSelection));

    }


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
            if (player instanceof LocalPlayer) HotbarUtil.hotbarScroll = player.getInventory().selected - scrollOffsset;
        }
    }

    public static boolean enableEnhancedHotbar(Player player) {
        return (player != null && player.isCreative() && Config.enhanceCreativeHotbar) || Config.enhanceSurvivalHotbar;
    }


}
