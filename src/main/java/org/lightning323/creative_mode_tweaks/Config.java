package org.lightning323.creative_mode_tweaks;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = CreativeModeTweaks.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    // Common
    public static final ModConfigSpec.BooleanValue ENHANCE_CREATIVE_HOTBAR =
            COMMON_BUILDER.comment("If we want to enhance creative hotbar")
                    .define("hotbar.common.ENHANCE_CREATIVE_HOTBAR", true);

    public static final ModConfigSpec.BooleanValue ENHANCE_SURVIVAL_HOTBAR =
            COMMON_BUILDER.comment("If we want to enhance survival hotbar")
                    .define("hotbar.common.ENHANCE_SURVIVAL_HOTBAR", false);

    public static final ModConfigSpec.BooleanValue HOTBAR_LOAD_ACTIVE_SELECTION_AS_ROW =
            COMMON_BUILDER.comment("If we want to load hotbar selection as the correct row in inventory")
                    .define("hotbar.common.HOTBAR_LOAD_ACTIVE_SELECTION_AS_ROW", false);

    public static final ModConfigSpec.BooleanValue ALLOW_INVENTORY_ROTATION_IN_SURVIVAL =
            COMMON_BUILDER.comment("If we want to allow inventory rotation in survival")
                    .define("hotbar.common.ALLOW_INVENTORY_ROTATION_IN_SURVIVAL", false);

    public static final ModConfigSpec.IntValue CREATIVE_HOTBAR_MAX_SIZE =
            COMMON_BUILDER.comment("Size of enhanced hotbar (valid values: 9,12,15,18)")
                    .defineInRange("hotbar.client.CREATIVE_HOTBAR_MAX_SIZE", 15, 9, 18);

    public static final ModConfigSpec.IntValue SURVIVAL_HOTBAR_MAX_SIZE =
            COMMON_BUILDER.comment("Size of enhanced hotbar (valid values: 9,12,15,18)")
                    .defineInRange("hotbar.client.SURVIVAL_HOTBAR_MAX_SIZE", 9, 9, 18);

    public static final ModConfigSpec.IntValue HOTBAR_MIN_SCROLL_MARGIN =
            COMMON_BUILDER.comment("Minimum number of preview slots on either side when scrolling (setting this to the max value will always keep the selector in the center)")
                    .defineInRange("hotbar.client.HOTBAR_MIN_SCROLL_MARGIN", 0, 0, 100);

    public static final ModConfigSpec.IntValue HOTBAR_MAX_SCROLL_MARGIN =
            COMMON_BUILDER.comment("Maximum number of preview slots on either side when scrolling (setting this to the max value will always keep the selector in the center)")
                    .defineInRange("hotbar.client.HOTBAR_MAX_SCROLL_MARGIN", 4, 0, 100);

    public static final ModConfigSpec.IntValue REACH_MIN_RANGE =
            COMMON_BUILDER.comment("Minimum reach distance")
                    .defineInRange("reach.common.REACH_MIN_RANGE", 5, 5, 128);

    public static final ModConfigSpec.IntValue REACH_MAX_RANGE =
            COMMON_BUILDER.comment("Maximum reach distance")
                    .defineInRange("reach.common.REACH_MAX_RANGE", 128, 5, 256);

    public static final ModConfigSpec.IntValue REACH_DEFAULT_RANGE =
            COMMON_BUILDER.comment("Default reach distance when we enter creative mode")
                    .defineInRange("reach.common.REACH_DEFAULT_RANGE", 32, 5, 256);

    public static final ModConfigSpec.BooleanValue INVERT_REPLACE_LOCK =
            COMMON_BUILDER.comment("If we want to invert replace lock. (Default: false)")
                    .define("replace.client.INVERT_REPLACE_LOCK", false);

    private static final ModConfigSpec.BooleanValue DISABLE_FLIGHT_INERTIA =
            COMMON_BUILDER.comment("Whether to disable flight inertia")
                    .define("flight.client.DISABLE_FLIGHT_INERTIA", true);

    private static final ModConfigSpec.DoubleValue FLIGHT_SPEED =
            COMMON_BUILDER.comment("Flight speed (vanilla is 0.05)")
                    .defineInRange("flight.client.FLIGHT_SPEED", 0.075, 0.05, 1.0);

    public static final ModConfigSpec.BooleanValue NOCLIP_ON_LOGIN =
            COMMON_BUILDER.comment("If No-Clip should be enabled or disabled by default when logging in")
                    .define("noclip.client.NOCLIP_ON_LOGIN", true);

    // --- 2. Build the SPEC LAST ---
    static final ModConfigSpec SPEC = COMMON_BUILDER.build();

    // Variables for cached access
    public static boolean disableFlightInertia ;
    public static float flightSpeed;
    public static boolean enhanceCreativeHotbar;
    public static boolean enhanceSurvivalHotbar;

    public static int creativeHotbarMaxSize;
    public static int survivalHotbarMaxSize;

    public static boolean allowInventoryRotationInSurvival;
    public static int hotbarMinScrollMargin;
    public static int hotbarMaxScrollMargin;


    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Only get values once the spec is built and loaded
        if (event.getConfig().getSpec() == SPEC) {
            disableFlightInertia = DISABLE_FLIGHT_INERTIA.get();
            flightSpeed = FLIGHT_SPEED.get().floatValue();
            enhanceCreativeHotbar = ENHANCE_CREATIVE_HOTBAR.get();
            enhanceSurvivalHotbar = ENHANCE_SURVIVAL_HOTBAR.get();
            creativeHotbarMaxSize = CREATIVE_HOTBAR_MAX_SIZE.get();
            survivalHotbarMaxSize = SURVIVAL_HOTBAR_MAX_SIZE.get();
            hotbarMinScrollMargin = HOTBAR_MIN_SCROLL_MARGIN.get();
            hotbarMaxScrollMargin = HOTBAR_MAX_SCROLL_MARGIN.get();
            allowInventoryRotationInSurvival = ALLOW_INVENTORY_ROTATION_IN_SURVIVAL.get();
        }
    }
}