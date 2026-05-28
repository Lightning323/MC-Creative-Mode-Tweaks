package org.lightning323.creative_mode_tweaks.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.FogType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.client.keys.*;
import org.lightning323.creative_mode_tweaks.client.utils.ClientSettings;
import org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil;
import org.lwjgl.glfw.GLFW;

import static org.lightning323.creative_mode_tweaks.CreativeModeTweaks.LOG;
import static org.lightning323.creative_mode_tweaks.CreativeModeTweaks.MODID;
import static org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil.getDistanceOnInvWheel;


// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class ClientModEvents {

    public static final String DEFAULT_CATEGORY = "key." + MODID + ".default";

    //Dumb keys
    public static final KeyMapping KEY_ROTATE_INV_UP = new KeyBase("key." + MODID + ".rotate_inv_up", GLFW.GLFW_KEY_UP, DEFAULT_CATEGORY) {
        @Override
        public void onKeyRelease(LocalPlayer player) {
            if (player.isCreative() || Config.allowInventoryRotationInSurvival) {
                ClientHotbarUtil.rotateInventoryAndSync(player, 9, false);
            }
        }
    };

    public static final KeyMapping KEY_ROTATE_INV_DOWN = new KeyBase("key." + MODID + ".rotate_inv_down", GLFW.GLFW_KEY_DOWN, DEFAULT_CATEGORY) {
        @Override
        public void onKeyRelease(LocalPlayer player) {
            if (player.isCreative() || Config.allowInventoryRotationInSurvival) {
                ClientHotbarUtil.rotateInventoryAndSync(player, -9, false);
            }
        }
    };

    //Smart keys
    public static final KeyMapping KEY_TOGGLE_NOCLIP = new ToggleNoclipKey(
            "key." + MODID + ".toggle_noclip",
            GLFW.GLFW_KEY_UNKNOWN, DEFAULT_CATEGORY);

    public static final KeyMapping KEY_REPLACE = new ReplaceKey(
            "key." + MODID + ".replace",
            GLFW.GLFW_KEY_UNKNOWN, DEFAULT_CATEGORY);

    public static final KeyMapping KEY_ADJUSTRANGE = new AdjustRangeKey(
            "key." + MODID + ".adjustrange",
            GLFW.GLFW_KEY_UNKNOWN, DEFAULT_CATEGORY);

    public static final KeyMapping KEY_NIGHTVISION = new NightVisionKey(
            "key." + MODID + ".nightvision",
            GLFW.GLFW_KEY_N, DEFAULT_CATEGORY);

    /**
     * https://discord.com/channels/313125603924639766/1249305774987939900/1502692290601160895
     * The creative mode inventory is handled by the client
     *
     * @param event
     */
    @SubscribeEvent
    public static void onScreenEventOpening(ScreenEvent.Opening event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (
                (event.getNewScreen() instanceof CreativeModeInventoryScreen && Config.enhanceCreativeHotbar) ||
                        (event.getNewScreen() instanceof InventoryScreen && Config.enhanceSurvivalHotbar)
        ) {
            int shiftAmt = player.getInventory().selected - 4;//We want to get the center of the hotbar X X X X Y X X X X

            //Move the window to the left or right if we are going into slots we cant see
            int distToBackOfHotbar = Math.abs(getDistanceOnInvWheel(player.getInventory().selected, HotbarUtil.hotbarScroll));
            if (distToBackOfHotbar < 4) {
                shiftAmt = player.getInventory().selected - distToBackOfHotbar;
            } else {
                int distToFrontOfHotbar = Math.abs(getDistanceOnInvWheel(player.getInventory().selected, HotbarUtil.hotbarScroll + HotbarUtil.hotbarSlots));
                if (distToFrontOfHotbar <= 4) {
                    shiftAmt = (player.getInventory().selected - 9) + distToFrontOfHotbar;
                }
            }
            ClientHotbarUtil.rotateInventoryAndSync(player, shiftAmt, true);
        }
    }

//    @SubscribeEvent
//    public static void onScreenEventClosing(ScreenEvent.Opening event) {
//        LocalPlayer player = Minecraft.getInstance().player;
//        if (player != null && event.getScreen() instanceof CreativeModeInventoryScreen) {
//        }
//    }


    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientSettings.setNoClip(Config.NOCLIP_ON_LOGIN.get()); //Set noclip when we login to true or false

        if (Minecraft.getInstance().gameMode != null) {
            GameType currentMode = Minecraft.getInstance().gameMode.getPlayerMode();
            clientGameModeChanged(event.getPlayer(), currentMode);
        }
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : KeyBase.keys) {
            event.register(key);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
//        for (KeyMapping key : KeyBase.keys) {
        ((KeyBase) KEY_REPLACE).onClientTick(event);
//        }
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        for (KeyMapping key : KeyBase.keys) {
            if (key instanceof LayeredDraw.Layer layer) {
                event.registerAbove(VanillaGuiLayers.HOTBAR,
                        ResourceLocation.fromNamespaceAndPath(MODID, ((KeyBase) key).description),
                        layer);
            }
        }
    }


    public static void clientGameModeChanged(Player player, GameType gameType) {
        if (player == null) return;
        LOG.debug("Client game mode set to {}", gameType);
        if (gameType == GameType.CREATIVE || gameType == GameType.SPECTATOR) {
        } else {
            player.getInventory().selected = Mth.clamp(player.getInventory().selected, 0, 8);
            ClientSettings.setNightVision(false);
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (ClientSettings.isNightVision() &&
                event.getType() != FogType.POWDER_SNOW &&
                event.getType() != FogType.NONE) {//Air
            event.setFarPlaneDistance(200.0F);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (Minecraft.getInstance().screen != null) {
            //Do nothing if the player is in a screen
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            for (KeyMapping key : KeyBase.keys) {
                if (event.getKey() == key.getKey().getValue()) {
                    if (event.getAction() == GLFW.GLFW_PRESS) ((KeyBase) key).onKeyPress(player);
                    else ((KeyBase) key).onKeyRelease(player);
                }
            }
        }
    }
}