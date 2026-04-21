package org.lightning323.creative_mode_tweaks;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
@EventBusSubscriber(modid = CreativeModeTweaks.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    // --- 1. Define everything FIRST ---

    // Common
    public static final ModConfigSpec.IntValue REACH_MIN_RANGE =
            COMMON_BUILDER.comment("Minimum reach distance")
                    .defineInRange("common.REACH_MIN_RANGE", 5, 5, 128);

    public static final ModConfigSpec.IntValue REACH_MAX_RANGE =
            COMMON_BUILDER.comment("Maximum reach distance")
                    .defineInRange("common.REACH_MAX_RANGE", 128, 5, 256);

    public static final ModConfigSpec.IntValue REACH_DEFAULT_RANGE =
            COMMON_BUILDER.comment("Default reach distance when we enter creative mode")
                    .defineInRange("common.REACH_DEFAULT_RANGE", 32, 5, 256);


    public static final ModConfigSpec.BooleanValue INVERT_REPLACE_LOCK =
            COMMON_BUILDER.comment("If we want to invert replace lock. (Default: false)")
                    .define("client.INVERT_REPLACE_LOCK", false);

    private static final ModConfigSpec.BooleanValue DISABLE_FLIGHT_INERTIA =
            COMMON_BUILDER.comment("Whether to disable flight inertia")
                    .define("client.DISABLE_FLIGHT_INERTIA", true);

    private static final ModConfigSpec.DoubleValue FLIGHT_SPEED =
            COMMON_BUILDER.comment("Flight speed (vanilla is 0.05)")
                    .defineInRange("client.FLIGHT_SPEED", 0.075, 0.05, 1.0);

    public static final ModConfigSpec.BooleanValue NOCLIP_ON_LOGIN =
            COMMON_BUILDER.comment("If No-Clip should be enabled or disabled by default when logging in")
                    .define("client.NOCLIP_ON_LOGIN", true);

    // --- 2. Build the SPEC LAST ---
    static final ModConfigSpec SPEC = COMMON_BUILDER.build();

    // Variables for cached access
    public static boolean disableFlightInertia = false;
    public static float flightSpeed = 0.05f;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Only get values once the spec is built and loaded
        if (event.getConfig().getSpec() == SPEC) {
            disableFlightInertia = DISABLE_FLIGHT_INERTIA.get();
            flightSpeed = FLIGHT_SPEED.get().floatValue();
        }
    }
}