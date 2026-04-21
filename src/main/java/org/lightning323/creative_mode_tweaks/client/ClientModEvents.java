package org.lightning323.creative_mode_tweaks.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.telemetry.TelemetryProperty;
import net.minecraft.resources.ResourceLocation;
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
import org.lwjgl.glfw.GLFW;

import static org.lightning323.creative_mode_tweaks.CreativeModeTweaks.LOGGER;
import static org.lightning323.creative_mode_tweaks.CreativeModeTweaks.MODID;


// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class ClientModEvents {

    public static final String DEFAULT_CATEGORY = "key." + MODID + ".default";


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
        LOGGER.debug("Client game mode set to {}", gameType);
        if (gameType == GameType.CREATIVE || gameType == GameType.SPECTATOR) {

        } else {
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

        Player player = Minecraft.getInstance().player;
        if (player != null &&
                (player.isCreative() || player.isSpectator())
        ) {
            for (KeyMapping key : KeyBase.keys) {
                if (event.getKey() == key.getKey().getValue()) {
                    if (event.getAction() == GLFW.GLFW_PRESS) ((KeyBase) key).onKeyPress();
                    else ((KeyBase) key).onKeyRelease();
                }
            }
        }
    }
}