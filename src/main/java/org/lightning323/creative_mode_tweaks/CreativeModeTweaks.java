package org.lightning323.creative_mode_tweaks;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import nl.requios.effortlessbuilding.EffortlessBuilding;
import org.lightning323.creative_mode_tweaks.network.packets.ClientboundSyncConfigPayload;
import org.lightning323.creative_mode_tweaks.network.packets.PacketGameModeChanged;
import org.lightning323.creative_mode_tweaks.utils.ServerSettings;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CreativeModeTweaks.MODID)
public class CreativeModeTweaks {
    public static final String MODID = "creative_mode_tweaks";
    public static final Logger LOG = LogUtils.getLogger();

    public CreativeModeTweaks(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
        EffortlessBuilding.initialize(modEventBus);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, CreativeModeTweaks::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangeGameModeEvent.class, CreativeModeTweaks::onGameModeChange);
        modEventBus.addListener(CreativeModeTweaks::onRegisterTests);
    }

    public static void serverGameModeChanged(ServerPlayer player, GameType gameType) {
        LOG.debug("Server game mode set to {}", gameType);
        if (gameType == GameType.CREATIVE) {
            double dist = Config.CREATIVE_REACH.get();
            ServerSettings.changeRangeModifier(player, dist);
        } else if (gameType == GameType.ADVENTURE || gameType == GameType.SURVIVAL) {
            ServerSettings.clearRangeModifier(player);
        }
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            serverGameModeChanged(player, player.gameMode.getGameModeForPlayer());
            //Update the client-side config
            PacketDistributor.sendToPlayer(player, new ClientboundSyncConfigPayload());
        }
    }

    public static void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        serverGameModeChanged(player, event.getNewGameMode());
        PacketDistributor.sendToPlayer(player, new PacketGameModeChanged(event.getNewGameMode().getId()));
    }

    public static void onRegisterTests(RegisterGameTestsEvent event) {
        event.register(ModTests.class);
    }

    public static ResourceLocation resource(String s) {
        return ResourceLocation.fromNamespaceAndPath(MODID, s);
    }
}
