package org.lightning323.creative_mode_tweaks;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.render.preview.PreviewRenderCache;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = CreativeModeTweaks.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    // Common
    public static final ModConfigSpec.BooleanValue ENHANCE_CREATIVE_HOTBAR =
            COMMON_BUILDER.comment("If we want to enhance creative hotbar")
                    .define("hotbar.enhance_creative_hotbar", true);

    public static final ModConfigSpec.BooleanValue ENHANCE_SURVIVAL_HOTBAR =
            COMMON_BUILDER.comment("If we want to enhance survival hotbar")
                    .define("hotbar.enhance_survival_hotbar", false);

    public static final ModConfigSpec.BooleanValue ALLOW_INVENTORY_ROTATION_IN_SURVIVAL =
            COMMON_BUILDER.comment("If we want to allow inventory rotation in survival")
                    .define("hotbar.allow_inventory_rotation_in_survival", true);

    public static final ModConfigSpec.IntValue CREATIVE_HOTBAR_MAX_SIZE =
            CLIENT_BUILDER.comment("Size of enhanced hotbar (valid values: 9,12,15,18)")
                    .defineInRange("hotbar.creative_hotbar_max_size", 9, 9, 18);

    public static final ModConfigSpec.IntValue SURVIVAL_HOTBAR_MAX_SIZE =
            CLIENT_BUILDER.comment("Size of enhanced hotbar (valid values: 9,12,15,18)")
                    .defineInRange("hotbar.survival_hotbar_max_size", 9, 9, 18);

    public static final ModConfigSpec.IntValue HOTBAR_MIN_SCROLL_MARGIN =
            CLIENT_BUILDER.comment("Minimum number of preview slots on either side when scrolling (setting this to the max value will always keep the selector in the center)")
                    .defineInRange("hotbar.hotbar_min_scroll_margin", 0, 0, 100);

    public static final ModConfigSpec.IntValue HOTBAR_MAX_SCROLL_MARGIN =
            CLIENT_BUILDER.comment("Maximum number of preview slots on either side when scrolling (setting this to the max value will always keep the selector in the center)")
                    .defineInRange("hotbar.hotbar_max_scroll_margin", 4, 0, 100);

    public static final ModConfigSpec.BooleanValue HOTBAR_SHOW_FULL_INVENTORY_WHILE_ROTATING =
            CLIENT_BUILDER.comment("While a Shift Inventory Rows key is held, expand the hotbar into a 4x9 grid showing the full inventory. Releases back to a single row on key release.")
                    .define("hotbar.show_full_inventory_while_rotating", true);

    public static final ModConfigSpec.DoubleValue HOTBAR_FULL_INVENTORY_PREVIEW_HOLD_SECONDS =
            CLIENT_BUILDER.comment("How long (in seconds) the 4x9 full-inventory preview stays open after releasing a Shift Inventory Rows key. 0 closes immediately on release.")
                    .defineInRange("hotbar.full_inventory_preview_hold_seconds", 2.5, 0.0, 10.0);

    public static final ModConfigSpec.IntValue CREATIVE_BUILDING_REACH =
            COMMON_BUILDER.comment("How far creative players can place and break blocks with build modes.")
                    .defineInRange("reach.creative_building_reach", 64, 1, 256);

    public static final ModConfigSpec.IntValue CREATIVE_SINGLE_REACH =
            COMMON_BUILDER.comment("How far creative players can place and break blocks in single building mode.")
                    .defineInRange("reach.creative_reach", 60, 1, 256);

    public static final ModConfigSpec.IntValue SURVIVAL_BUILDING_REACH =
            COMMON_BUILDER.comment("How far survival players can place and break blocks with build modes.")
                    .defineInRange("reach.survival_building_reach", 6, 1, 256);

    public static final ModConfigSpec.IntValue BUILDING_ANGEL_PLACEMENT_DISTANCE =
            COMMON_BUILDER.comment("Distance at which Angel Placement targets an air block.")
                    .translation("creative_mode_tweaks.config.angel_placement_distance")
                    .defineInRange("building.angel_placement.distance", 6, 5, 256);

    private static final ModConfigSpec.BooleanValue DISABLE_FLIGHT_INERTIA =
            COMMON_BUILDER.comment("Whether to disable flight inertia")
                    .define("flight.disable_flight_inertia", true);

    private static final ModConfigSpec.DoubleValue FLIGHT_SPEED =
            COMMON_BUILDER.comment("Flight speed (vanilla is 0.05)")
                    .defineInRange("flight.flight_speed", 0.08, 0.05, 1.0);

    public static final ModConfigSpec.BooleanValue NOCLIP_ON_LOGIN =
            COMMON_BUILDER.comment("If No-Clip should be enabled or disabled by default when logging in")
                    .define("noclip.noclip_enabled_by_default", true);

    public static final ModConfigSpec.IntValue BUILDING_PREVIEW_RENDER_THROTTLE_BLOCKS =
            CLIENT_BUILDER.comment("How big the number of blocks should be when throttling the preview rendering.")
                    .translation("creative_mode_tweaks.config.render_throttle_blocks")
                    .defineInRange("building.creative.render_throttle_blocks", 200000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue BUILDING_PREVIEW_BLOCK_TRANSPARENCY =
            CLIENT_BUILDER.comment("Transparency of block previews. 0% = invisible, 100% = solid.")
                    .translation("creative_mode_tweaks.config.preview_block_transparency")
                    .defineInRange("building.preview_block_transparency", 0.8D, 0.0D, 1.0D);

    public static final ModConfigSpec.BooleanValue BUILDING_PROTECT_TILE_ENTITIES =
            CLIENT_BUILDER.comment("Prevent build modes from replacing or breaking blocks with block entities.")
                    .translation("creative_mode_tweaks.config.protect_tile_entities")
                    .define("building.protect_tile_entities", true);

    public static final ModConfigSpec.BooleanValue SHOW_PLACEMENT_REJECTION_MESSAGES =
            CLIENT_BUILDER.comment("Show messages when blocks are rejected due to placement rules.")
                    .translation("creative_mode_tweaks.config.show_placement_rejection_messages")
                    .define("building.show_placement_rejection_messages", false);

    public static final ModConfigSpec.BooleanValue BUILDING_ASYNC_BOUNDARY =
            CLIENT_BUILDER.comment("Where the preview border bakes. Both options stay off the render thread.",
                            "On: border rides the ghost-block worker (one bake, one handoff; lags behind slow ghosts).",
                            "Off: border bakes on its own dedicated worker; the render loop picks it up from there.")
                    .translation("creative_mode_tweaks.config.async_boundary")
                    .define("building.async_boundary", false);

    public static final ModConfigSpec.BooleanValue BUILDING_SURVIVAL_ALLOW_ANGEL_PLACEMENT =
            COMMON_BUILDER.comment("Allow survival players to use Angel Placement to target air blocks at a distance.")
                    .translation("creative_mode_tweaks.config.allow_angel_placement")
                    .define("building.survival.allow_angel_placement", false);

    public static final ModConfigSpec.BooleanValue BUILDING_SURVIVAL_ALLOW_BUILD_MODES =
            COMMON_BUILDER.comment("Allow survival players to use build modes and open the build mode radial menu at all.",
                            "When off, build mode selection, placement and breaking are all disabled in survival.")
                    .translation("creative_mode_tweaks.config.allow_build_modes")
                    .define("building.survival.allow_build_modes", true);

    public static final ModConfigSpec.BooleanValue BUILDING_SURVIVAL_ALLOW_UNDO_REDO =
            COMMON_BUILDER.comment("Allow survival players to undo and redo build mode actions.",
                            "When off, the undo/redo buttons are hidden in survival and the actions are rejected.")
                    .translation("creative_mode_tweaks.config.allow_undo_redo")
                    .define("building.survival.allow_undo_redo", true);

    public static final ModConfigSpec.IntValue BUILDING_SURVIVAL_MAX_BLOCKS_PLACED =
            COMMON_BUILDER.comment("Maximum number of blocks survival players can place or break in a single action.")
                    .translation("creative_mode_tweaks.config.max_blocks_placed")
                    .defineInRange("building.survival.max_blocks_placed", 2000, 1, 100000);

    public static final ModConfigSpec.IntValue BUILDING_SURVIVAL_MAX_BLOCKS_PER_AXIS =
            COMMON_BUILDER.comment("Maximum size of a survival build-mode shape along any axis.")
                    .translation("creative_mode_tweaks.config.max_blocks_per_axis")
                    .defineInRange("building.survival.max_blocks_per_axis", 64, 1, 1000);

    public static final ModConfigSpec.IntValue BUILDING_SURVIVAL_MAX_MIRROR_SIZE =
            COMMON_BUILDER.comment("Maximum diameter for survival mirror and radial mirror modifiers.")
                    .translation("creative_mode_tweaks.config.max_mirror_size")
                    .defineInRange("building.survival.max_mirror_size", 256, 1, 1000);

    public static final ModConfigSpec.IntValue BUILDING_SURVIVAL_MAX_ARRAY_COUNT =
            COMMON_BUILDER.comment("Maximum number of copies a survival array modifier can produce.")
                    .translation("creative_mode_tweaks.config.max_array_count")
                    .defineInRange("building.survival.max_array_count", 64, 1, 1000);

    public static final ModConfigSpec.IntValue BUILDING_SURVIVAL_MAX_ARRAY_OFFSET =
            COMMON_BUILDER.comment("Maximum offset distance for each survival array modifier axis.")
                    .translation("creative_mode_tweaks.config.max_array_offset")
                    .defineInRange("building.survival.max_array_offset", 64, 1, 1000);

    public static final ModConfigSpec.BooleanValue BUILDING_SURVIVAL_ALLOW_BREAKING =
            COMMON_BUILDER.comment("Allow survival players to use build modes to break blocks.")
                    .translation("creative_mode_tweaks.config.allow_breaking")
                    .define("building.survival.allow_breaking", true);

    public static final ModConfigSpec.BooleanValue BUILDING_SURVIVAL_ONLY_PLACED_BLOCKS =
            COMMON_BUILDER.comment("Allow survival players to break only blocks they placed during the current session.")
                    .translation("creative_mode_tweaks.config.only_placed_blocks")
                    .define("building.survival.only_placed_blocks", true);

    public static final ModConfigSpec.DoubleValue BUILDING_SURVIVAL_MAX_HARDNESS =
            COMMON_BUILDER.comment("Maximum block hardness survival players can break. -1 disables the limit.")
                    .translation("creative_mode_tweaks.config.max_hardness")
                    .defineInRange("building.survival.max_hardness", -1.0D, -1.0D, Double.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue BUILDING_SURVIVAL_MAX_HARDNESS_TO_BREAK_ALL_BLOCKS =
            COMMON_BUILDER.comment("Blocks softer than this hardness can always be broken with build modes in survival, ignoring the placed-only, hardness and tool restrictions. -1 disables it.")
                    .translation("creative_mode_tweaks.config.max_hardness_to_break_all_blocks")
                    .defineInRange("building.survival.max_hardness_to_break_all_blocks", 0.3D, -1.0D, Double.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue BUILDING_SURVIVAL_REQUIRE_TOOLS =
            COMMON_BUILDER.comment("Require survival players to have the correct tools to break blocks with build modes.")
                    .translation("creative_mode_tweaks.config.require_tools")
                    .define("building.survival.require_tools", false);

    public static final ModConfigSpec.BooleanValue BUILDING_SURVIVAL_USE_DURABILITY =
            COMMON_BUILDER.comment("Consume tool durability when survival players break blocks with build modes.")
                    .translation("creative_mode_tweaks.config.use_durability")
                    .define("building.survival.use_durability", false);

    public static final ModConfigSpec.DoubleValue BUILDING_SURVIVAL_BREAK_HUNGER_HARDNESS_MULT =
            COMMON_BUILDER.comment("Hunger-exhaustion multiplier for survival build-mode breaking.",
                            "Each broken block costs exhaustion = hardness x simulated vanilla mine ticks with the held item, scaled by this.",
                            "Faster tools take fewer ticks, so they deplete less hunger. 0 disables hunger from breaking.",
                            "4 exhaustion = 1 hunger point.")
                    .translation("creative_mode_tweaks.config.break_hunger_hardness_mult")
                    .defineInRange("building.survival.break_hunger_hardness_mult", 1.0D, 0.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue BUILDING_SURVIVAL_BREAK_HUNGER_TOOL_MULT =
            COMMON_BUILDER.comment("Extra exhaustion multiplier applied per broken block when the held item is the correct tool for that block.",
                            "Lower it to reduce (0 to eliminate) hunger depletion while using proper tools.")
                    .translation("creative_mode_tweaks.config.break_hunger_tool_mult")
                    .defineInRange("building.survival.break_hunger_tool_mult", 1.0D, 0.0D, 1000.0D);

    public static final ModConfigSpec.IntValue BUILDING_CREATIVE_MAX_BLOCKS_PLACED =
            COMMON_BUILDER.comment("Maximum number of blocks creative players can place or break in a single action.")
                    .translation("creative_mode_tweaks.config.max_blocks_placed")
                    .defineInRange("building.creative.max_blocks_placed", 500000, 1, Integer.MAX_VALUE);



    public static final ModConfigSpec.IntValue BUILDING_CREATIVE_MAX_BLOCKS_PER_AXIS =
            COMMON_BUILDER.comment("Maximum size of a creative build-mode shape along any axis.")
                    .translation("creative_mode_tweaks.config.max_blocks_per_axis")
                    .defineInRange("building.creative.max_blocks_per_axis", 1000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue BUILDING_CREATIVE_MAX_MIRROR_SIZE =
            COMMON_BUILDER.comment("Maximum diameter for creative mirror and radial mirror modifiers.")
                    .translation("creative_mode_tweaks.config.max_mirror_size")
                    .defineInRange("building.creative.max_mirror_size", 256, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue BUILDING_CREATIVE_MAX_ARRAY_COUNT =
            COMMON_BUILDER.comment("Maximum number of copies a creative array modifier can produce.")
                    .translation("creative_mode_tweaks.config.max_array_count")
                    .defineInRange("building.creative.max_array_count", 256, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue BUILDING_CREATIVE_MAX_ARRAY_OFFSET =
            COMMON_BUILDER.comment("Maximum offset distance for each creative array modifier axis.")
                    .translation("creative_mode_tweaks.config.max_array_offset")
                    .defineInRange("building.creative.max_array_offset", 256, 1, Integer.MAX_VALUE);

    // --- 2. Build the SPEC LAST ---
    static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();
    static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    // Variables for cached access
    public static boolean disableFlightInertia;
    public static float flightSpeed;
    public static boolean enhanceCreativeHotbar;
    public static boolean enhanceSurvivalHotbar;

    public static int creativeHotbarMaxSize;
    public static int survivalHotbarMaxSize;

    public static boolean allowInventoryRotationInSurvival;
    public static int hotbarMinScrollMargin;
    public static int hotbarMaxScrollMargin;
    public static boolean showFullInventoryWhileRotating = true;
    public static double fullInventoryPreviewHoldSeconds = 1.0;
    private static boolean clientAngelPlacementAllowed;
    private static int clientAngelPlacementDistance = 8;
    private static boolean clientBuildModesAllowed = true;
    private static boolean clientUndoRedoAllowed = true;

    /** Bounds for reach, matching the reach.* config ranges (1..256). */
    public static final int REACH_MIN = 1;
    public static final int REACH_MAX = 256;

    /**
     * Server-side per-player creative single-reach overrides set via the
     * Adjust Range key. Keyed by UUID so they survive death/respawn (unlike
     * the old transient attribute modifiers); cleared on logout and when
     * leaving creative, matching the old semantics.
     */
    private static final Map<UUID, Integer> serverSingleReachOverrides = new ConcurrentHashMap<>();

    /**
     * Client-side mirror of this client's override, synced from the server.
     * Null means "no override, use the config value".
     */
    private static Integer clientSingleReachOverride = null;

    /**
     * Gap between creative building reach and creative single reach, as set
     * in the configs. The Adjust Range key preserves this offset when it
     * moves both ranges.
     */
    public static int getCreativeReachOffset() {
        return CREATIVE_BUILDING_REACH.get() - CREATIVE_SINGLE_REACH.get();
    }

    public static int clampReach(double dist) {
        return Mth.clamp((int) Math.round(dist), REACH_MIN, REACH_MAX);
    }

    /** Building reach derived from a single reach via the configured offset. */
    public static int deriveBuildingReach(int singleReach) {
        return Mth.clamp(singleReach + getCreativeReachOffset(), REACH_MIN, REACH_MAX);
    }

    public static int getBuildingReach(Player player) {
        if (!player.isCreative()) {
            return SURVIVAL_BUILDING_REACH.get();
        }
        return deriveBuildingReach(getSingleCreativeReach(player));
    }

    public static int getSingleCreativeReach(Player player) {
        if (player.level().isClientSide()) {
            return clientSingleReachOverride != null ? clientSingleReachOverride : CREATIVE_SINGLE_REACH.get();
        }
        return serverSingleReachOverrides.getOrDefault(player.getUUID(), CREATIVE_SINGLE_REACH.get());
    }

    /** Server-side only. */
    public static void setServerSingleReach(UUID playerId, int singleReach) {
        serverSingleReachOverrides.put(playerId, Mth.clamp(singleReach, REACH_MIN, REACH_MAX));
    }

    /** Server-side only. */
    public static void clearServerSingleReach(UUID playerId) {
        serverSingleReachOverrides.remove(playerId);
    }

    /** Server-side only. Null = no override. */
    public static Integer getServerSingleReachOverride(UUID playerId) {
        return serverSingleReachOverrides.get(playerId);
    }

    /** Client-side only. Null = no override, use the config value. */
    public static void updateClientSingleReach(Integer singleReach) {
        clientSingleReachOverride = singleReach == null ? null : Mth.clamp(singleReach, REACH_MIN, REACH_MAX);
    }

    public static int getAngelPlacementDistance(Player player) {
        return player.level().isClientSide() ? clientAngelPlacementDistance : BUILDING_ANGEL_PLACEMENT_DISTANCE.get();
    }

    public static boolean isAngelPlacementAllowed(Player player) {
        return player.isCreative() || (player.level().isClientSide() ? clientAngelPlacementAllowed : BUILDING_SURVIVAL_ALLOW_ANGEL_PLACEMENT.get());
    }

    public static void updateClientAngelPlacementSettings(boolean allowed, int distance) {
        clientAngelPlacementAllowed = allowed;
        clientAngelPlacementDistance = distance;
    }

    public static boolean isSurvivalBuildModesAllowed(Player player) {
        return player.isCreative() || player.isSpectator()
                || (player.level().isClientSide() ? clientBuildModesAllowed : BUILDING_SURVIVAL_ALLOW_BUILD_MODES.get());
    }

    public static boolean isSurvivalUndoRedoAllowed(Player player) {
        return player.isCreative() || player.isSpectator()
                || (player.level().isClientSide() ? clientUndoRedoAllowed : BUILDING_SURVIVAL_ALLOW_UNDO_REDO.get());
    }

    public static void updateClientSurvivalPermissions(boolean buildModesAllowed, boolean undoRedoAllowed) {
        clientBuildModesAllowed = buildModesAllowed;
        clientUndoRedoAllowed = undoRedoAllowed;
    }

    public static int getBuildingMaxBlocksPlaced(Player player) {
        return player.isCreative() ? BUILDING_CREATIVE_MAX_BLOCKS_PLACED.get() : BUILDING_SURVIVAL_MAX_BLOCKS_PLACED.get();
    }

    public static int getBuildingMaxBlocksPerAxis(Player player) {
        return player.isCreative() ? BUILDING_CREATIVE_MAX_BLOCKS_PER_AXIS.get() : BUILDING_SURVIVAL_MAX_BLOCKS_PER_AXIS.get();
    }

    public static int getBuildingMaxMirrorSize(Player player) {
        return player.isCreative() ? BUILDING_CREATIVE_MAX_MIRROR_SIZE.get() : BUILDING_SURVIVAL_MAX_MIRROR_SIZE.get();
    }

    public static int getBuildingMaxArrayCount(Player player) {
        return player.isCreative() ? BUILDING_CREATIVE_MAX_ARRAY_COUNT.get() : BUILDING_SURVIVAL_MAX_ARRAY_COUNT.get();
    }

    public static int getBuildingMaxArrayOffset(Player player) {
        return player.isCreative() ? BUILDING_CREATIVE_MAX_ARRAY_OFFSET.get() : BUILDING_SURVIVAL_MAX_ARRAY_OFFSET.get();
    }

    public static float getBuildingPreviewBlockTransparency() {
        return BUILDING_PREVIEW_BLOCK_TRANSPARENCY.get().floatValue();
    }


    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Only get values once the spec is built and loaded
        if (event.getConfig().getSpec() == COMMON_SPEC) {
            disableFlightInertia = DISABLE_FLIGHT_INERTIA.get();
            flightSpeed = FLIGHT_SPEED.get().floatValue();
            enhanceCreativeHotbar = ENHANCE_CREATIVE_HOTBAR.get();
            enhanceSurvivalHotbar = ENHANCE_SURVIVAL_HOTBAR.get();
            allowInventoryRotationInSurvival = ALLOW_INVENTORY_ROTATION_IN_SURVIVAL.get();
        } else if (event.getConfig().getSpec() == CLIENT_SPEC) {
            creativeHotbarMaxSize = CREATIVE_HOTBAR_MAX_SIZE.get();
            survivalHotbarMaxSize = SURVIVAL_HOTBAR_MAX_SIZE.get();
            hotbarMinScrollMargin = HOTBAR_MIN_SCROLL_MARGIN.get();
            hotbarMaxScrollMargin = HOTBAR_MAX_SCROLL_MARGIN.get();
            showFullInventoryWhileRotating = HOTBAR_SHOW_FULL_INVENTORY_WHILE_ROTATING.get();
            fullInventoryPreviewHoldSeconds = HOTBAR_FULL_INVENTORY_PREVIEW_HOLD_SECONDS.get();
        }
    }
}
