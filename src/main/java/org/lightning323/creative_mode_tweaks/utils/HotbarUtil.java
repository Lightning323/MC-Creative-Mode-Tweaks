package org.lightning323.creative_mode_tweaks.utils;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;
import org.lightning323.creative_mode_tweaks.network.packets.InventoryRotatePayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public static final int HOTBAR_SLOT_GUI_SIZE = 20;
    public static int hotbarScroll = 0;

    public static void rotateInventoryAndSync(LocalPlayer player, int offset) {
        if (player == null || player.connection == null) return;

//        Collections.rotate(player.getInventory().items, -offset);
        player.getInventory().selected = Math.floorMod(
                player.getInventory().selected - offset,
                36
        ); //Do this to keep the selection consistent
        player.inventoryMenu.broadcastChanges();
        PacketDistributor.sendToServer(new InventoryRotatePayload(offset));
    }


    public static int getHotbarSlots(int guiWidth) {
        double availableSlots = ((double) guiWidth / HOTBAR_SLOT_GUI_SIZE) - 4;
        return (int) Math.floor(Mth.clamp(availableSlots, 9, 15) / 3) * 3;
    }
}
