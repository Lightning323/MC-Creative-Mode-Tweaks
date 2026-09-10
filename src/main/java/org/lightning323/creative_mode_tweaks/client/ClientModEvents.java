package org.lightning323.creative_mode_tweaks.client;

import com.mojang.blaze3d.platform.InputConstants;
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
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.mixin.KeyMappingAccessor;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.client.keys.*;
import org.lightning323.creative_mode_tweaks.client.utils.ClientSettings;
import org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import static org.lightning323.creative_mode_tweaks.CreativeModeTweaks.LOG;
import static org.lightning323.creative_mode_tweaks.CreativeModeTweaks.MODID;
import static org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil.getDistanceOnInvWheel;


// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class ClientModEvents {

    public static final String DEFAULT_CATEGORY = "key." + MODID + ".default";
    public static final String BUILD_MODES_CATEGORY = "key." + MODID + ".build_modes";
    private static final Map<BuildModeEnum, KeyMapping> BUILD_MODE_KEYS = new EnumMap<>(BuildModeEnum.class);

    //Dumb keys
    public static final KeyMapping KEY_ROTATE_INV_UP = new KeyBase("key." + MODID + ".rotate_inv_up", GLFW.GLFW_KEY_UP, DEFAULT_CATEGORY) {
        @Override
        public void onKeyPress(LocalPlayer player) {
            ClientHotbarUtil.rotateUpHeld = true;
        }

        @Override
        public void onKeyRelease(LocalPlayer player) {
            ClientHotbarUtil.rotateUpHeld = false;
            if (player.isCreative() || Config.allowInventoryRotationInSurvival) {
                ClientHotbarUtil.rotateInventoryAndSync(player, 9, false);
            }
            ClientHotbarUtil.extendPreviewHold();
        }
    };

    public static final KeyMapping KEY_ROTATE_INV_DOWN = new KeyBase("key." + MODID + ".rotate_inv_down", GLFW.GLFW_KEY_DOWN, DEFAULT_CATEGORY) {
        @Override
        public void onKeyPress(LocalPlayer player) {
            ClientHotbarUtil.rotateDownHeld = true;
        }

        @Override
        public void onKeyRelease(LocalPlayer player) {
            ClientHotbarUtil.rotateDownHeld = false;
            if (player.isCreative() || Config.allowInventoryRotationInSurvival) {
                ClientHotbarUtil.rotateInventoryAndSync(player, -9, false);
            }
            ClientHotbarUtil.extendPreviewHold();
        }
    };

    //Smart keys
    public static final KeyMapping KEY_TOGGLE_NOCLIP = new ToggleNoclipKey(
            "key." + MODID + ".toggle_noclip",
            GLFW.GLFW_KEY_UNKNOWN, DEFAULT_CATEGORY);

//    public static final KeyMapping KEY_REPLACE = new ReplaceKey(
//            "key." + MODID + ".replace",
//            GLFW.GLFW_KEY_UNKNOWN, DEFAULT_CATEGORY);

//    public static final KeyMapping KEY_ADJUSTRANGE = new AdjustRangeKey(
//            "key." + MODID + ".adjustrange",
//            GLFW.GLFW_KEY_UNKNOWN, DEFAULT_CATEGORY);

    public static final KeyMapping KEY_NIGHTVISION = new NightVisionKey(
            "key." + MODID + ".nightvision",
            GLFW.GLFW_KEY_N, DEFAULT_CATEGORY);

    // Effortless Building controls are registered with the rest of Creative Mode Tweaks.
    public static final KeyMapping KEY_OPEN_RADIAL_MENU = new KeyMapping(
            "key." + MODID + ".open_radial_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_TAB, DEFAULT_CATEGORY);

    public static final KeyMapping KEY_OPEN_MODIFIERS_SCREEN = new KeyMapping(
            "key." + MODID + ".open_modifiers_screen", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ADD, DEFAULT_CATEGORY);

    public static final KeyMapping KEY_UNDO = new KeyMapping(
            "key." + MODID + ".undo.desc", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, DEFAULT_CATEGORY);

    public static final KeyMapping KEY_REDO = new KeyMapping(
            "key." + MODID + ".redo.desc", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, DEFAULT_CATEGORY);

    public static final KeyMapping KEY_TOGGLE_ANGEL_PLACEMENT = new KeyMapping(
            "key." + MODID + ".toggle_angel_placement", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), DEFAULT_CATEGORY);

    // Baseline for detecting hotbar selection changes (scroll / number keys) each tick.
    private static int lastPreviewSelectedSlot = -1;

    static {
        for (BuildModeEnum mode : BuildModeEnum.values()) {
            if (mode != BuildModeEnum.FLOOR && mode != BuildModeEnum.WALL) {
                BUILD_MODE_KEYS.put(mode, new KeyMapping(mode.getNameKey(), InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), BUILD_MODES_CATEGORY));
            }
        }
    }

    public static KeyMapping getBuildModeKey(BuildModeEnum mode) {
        return BUILD_MODE_KEYS.get(mode);
    }

    public static Collection<KeyMapping> getBuildModeKeys() {
        return BUILD_MODE_KEYS.values();
    }

    public static boolean isKeyDown(KeyMapping keyMapping) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, ((KeyMappingAccessor) keyMapping).effortlessbuilding$getKey().getValue());
    }

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
        // Opening a screen cancels any lingering post-release preview hold.
        ClientHotbarUtil.previewVisibleUntilMillis = 0;
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

        // Entering a world always starts from the single-block tool with
        // 2-point selections, never a leftover mode/option from a previous
        // world or session.
        BuildPipelineClient.cancelCurrentSequence();
        BuildModes.CLIENT.setBuildMode(BuildModeEnum.DISABLED);
        ModeOptions.resetPointBuildToDefault();

        if (Minecraft.getInstance().gameMode != null) {
            GameType currentMode = Minecraft.getInstance().gameMode.getPlayerMode();
            clientGameModeChanged(event.getPlayer(), currentMode);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(ClientPlayerNetworkEvent.Clone event) {
        // Dying resets the build tool to single-block, same as logging in, so a
        // half-finished multi-block selection cannot survive death.
        BuildPipelineClient.cancelCurrentSequence();
        BuildModes.CLIENT.setBuildMode(BuildModeEnum.DISABLED);
        ModeOptions.resetPointBuildToDefault();
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : KeyBase.keys) {
            event.register(key);
        }

        event.register(KEY_OPEN_RADIAL_MENU);
        event.register(KEY_OPEN_MODIFIERS_SCREEN);
        event.register(KEY_UNDO);
        event.register(KEY_REDO);
        event.register(KEY_TOGGLE_ANGEL_PLACEMENT);

        for (KeyMapping key : getBuildModeKeys()) {
            event.register(key);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
//        for (KeyMapping key : KeyBase.keys) {
//        ((KeyBase) KEY_REPLACE).onClientTick(event);
//        }
        // Self-heal held flags if the physical key is no longer down
        // (e.g. released while a screen was open, which skips onKeyInput).
        if (!KEY_ROTATE_INV_UP.isDown()) {
            ClientHotbarUtil.rotateUpHeld = false;
        }
        if (!KEY_ROTATE_INV_DOWN.isDown()) {
            ClientHotbarUtil.rotateDownHeld = false;
        }
        // Scrolling or picking another hotbar slot while the 4x9 preview is open
        // keeps it open by resetting the post-release hold timer.
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer tickPlayer = mc.player;
        if (tickPlayer != null) {
            int selected = tickPlayer.getInventory().selected;
            if (lastPreviewSelectedSlot != -1 && selected != lastPreviewSelectedSlot && isInventoryPreviewHeld()) {
                ClientHotbarUtil.extendPreviewHold();
            }
            lastPreviewSelectedSlot = selected;
        } else {
            lastPreviewSelectedSlot = -1;
        }
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

    /**
     * True while either Shift Inventory Rows key is held (or within the configured
     * post-release hold time) and the 4x9 preview should be shown.
     * Respects the key remapping (checks the actual bound keys, not hardcoded arrows)
     * and the {@code hotbar.show_full_inventory_while_rotating} config toggle.
     */
    public static boolean isInventoryPreviewHeld() {
        if (!Config.showFullInventoryWhileRotating) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return false;
        if (mc.player == null) return false;
        Player player = mc.player;
        if (!(player.isCreative() || Config.allowInventoryRotationInSurvival)) return false;
        if (KEY_ROTATE_INV_UP.isDown() || KEY_ROTATE_INV_DOWN.isDown()
                || ClientHotbarUtil.rotateUpHeld || ClientHotbarUtil.rotateDownHeld) {
            return true;
        }
        return ClientHotbarUtil.isPreviewHoldOpen();
    }

    private static boolean matchesKeyBinding(KeyMapping mapping, InputConstants.Key pressed) {
        return mapping.getKey().equals(pressed);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (Minecraft.getInstance().screen != null) {
            //Do nothing if the player is in a screen
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            InputConstants.Key pressed = InputConstants.getKey(event.getKey(), event.getScanCode());
            for (KeyMapping key : KeyBase.keys) {
                if (matchesKeyBinding(key, pressed)) {
                    if (event.getAction() == GLFW.GLFW_PRESS) ((KeyBase) key).onKeyPress(player);
                    else if (event.getAction() == GLFW.GLFW_RELEASE) ((KeyBase) key).onKeyRelease(player);
                    // Ignore GLFW_REPEAT so holding doesn't retrigger press/release.
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            InputConstants.Key pressed = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
            for (KeyMapping key : KeyBase.keys) {
                if (matchesKeyBinding(key, pressed)) {
                    if (event.getAction() == GLFW.GLFW_PRESS) ((KeyBase) key).onKeyPress(player);
                    else if (event.getAction() == GLFW.GLFW_RELEASE) ((KeyBase) key).onKeyRelease(player);
                }
            }
        }
    }
}
