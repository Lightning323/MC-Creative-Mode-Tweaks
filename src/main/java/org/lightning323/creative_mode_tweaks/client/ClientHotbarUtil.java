package org.lightning323.creative_mode_tweaks.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;
import org.lightning323.creative_mode_tweaks.network.packets.InventoryRotatePayload;

public class ClientHotbarUtil {
    //The actual hotbar sprite
    public static final ResourceLocation HOTBAR_SPRITE_9 = ResourceLocation.withDefaultNamespace("hud/hotbar");
    public static final ResourceLocation HOTBAR_SPRITE_12 = CreativeModeTweaks.resource("hud/12_hotbar");
    public static final ResourceLocation HOTBAR_SPRITE_15 = CreativeModeTweaks.resource("hud/15_hotbar");
    public static final ResourceLocation HOTBAR_SPRITE_18 = CreativeModeTweaks.resource("hud/18_hotbar");
    public static final ResourceLocation HOTBAR_SELECTION_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
    public static final ResourceLocation HOTBAR_OFFHAND_LEFT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_left");
    public static final ResourceLocation HOTBAR_OFFHAND_RIGHT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_right");
    public static final ResourceLocation HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_background");
    public static final ResourceLocation HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_progress");

    public static void rotateInventoryAndSync(LocalPlayer player, int offset, boolean keepSelection) {
        if (player == null) return;
        //The server has to update the inventory first and then update the client
        PacketDistributor.sendToServer(new InventoryRotatePayload(0, offset, keepSelection));
    }
}
