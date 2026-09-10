package org.lightning323.creative_mode_tweaks;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import nl.requios.effortlessbuilding.EffortlessBuilding;
import org.lightning323.creative_mode_tweaks.network.packets.ClientboundSyncConfigPayload;
import org.lightning323.creative_mode_tweaks.network.packets.PacketGameModeChanged;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CreativeModeTweaks.MODID)
public class CreativeModeTweaks {
    public static final String MODID = "creative_mode_tweaks";
    public static final Logger LOG = LogUtils.getLogger();

    public CreativeModeTweaks(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
        EffortlessBuilding.initialize(modEventBus);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, CreativeModeTweaks::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangeGameModeEvent.class, CreativeModeTweaks::onGameModeChange);
        // Silences per-block batch sounds from build-mode placement (the
        // action's single sound still plays client-side on click).
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, nl.requios.effortlessbuilding.network.PacketHandler::onBatchSound);
        modEventBus.addListener(CreativeModeTweaks::onRegisterTests);
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Single-block reach is resolved live via PlayerMixin from
            // Config.getSingleReach (same stateless pattern as build-mode
            // reach), so there is no attribute modifier to re-apply here.
            //Update the client-side config
            PacketDistributor.sendToPlayer(player, new ClientboundSyncConfigPayload());
        }
    }

    public static void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        PacketDistributor.sendToPlayer(player, new PacketGameModeChanged(event.getNewGameMode().getId()));
    }

    public static void onRegisterTests(RegisterGameTestsEvent event) {
        event.register(ModTests.class);
    }

    public static ResourceLocation resource(String s) {
        return ResourceLocation.fromNamespaceAndPath(MODID, s);
    }
}
