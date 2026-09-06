package nl.requios.effortlessbuilding;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import nl.requios.effortlessbuilding.config.BuildModeHintStorage;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.config.ServerConfigStorage;
import nl.requios.effortlessbuilding.config.WelcomeMessageStorage;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;
import nl.requios.effortlessbuilding.menu.ModMenus;
import nl.requios.effortlessbuilding.modifier.ModifierServerStorage;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.BuildModeHintC2SPacket;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.network.SyncModifiersS2CPacket;
import nl.requios.effortlessbuilding.network.SyncServerConfigS2CPacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.UpdateModifiersC2SPacket;
import nl.requios.effortlessbuilding.network.UpdateServerConfigC2SPacket;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import nl.requios.effortlessbuilding.utilities.UndoManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the Effortless Building feature set as part of Creative Mode Tweaks.
 */
public final class EffortlessBuilding {
    public static final String MODID = "creative_mode_tweaks";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredItem<Item> RANDOMIZER_TOOL;
    private static final DeferredRegister<MenuType<?>> MENUS;

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToClient(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    private EffortlessBuilding() {
    }

    public static void initialize(IEventBus eventBus, ModContainer modContainer) {
        ITEMS.register(eventBus);
        MENUS.register(eventBus);
        if (FMLEnvironment.dist.isClient()) {
            NeoForgeConfigScreenRegistrar.register(modContainer);
        }

        eventBus.addListener((RegisterPayloadHandlersEvent event) -> {
            PayloadRegistrar registrar = event.registrar(MODID);
            registrar.playToServer(PlaceBuildModePacket.TYPE, PlaceBuildModePacket.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> PacketHandler.handlePlaceBuildMode(payload, (ServerPlayer) context.player())));
            registrar.playToServer(BreakBuildModePacket.TYPE, BreakBuildModePacket.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> PacketHandler.handleBreakBuildMode(payload, (ServerPlayer) context.player())));
            registrar.playToServer(UndoPacket.TYPE, UndoPacket.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> PacketHandler.handleUndo((ServerPlayer) context.player())));
            registrar.playToServer(RedoPacket.TYPE, RedoPacket.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> PacketHandler.handleRedo((ServerPlayer) context.player())));
            registrar.playToServer(UpdateModifiersC2SPacket.TYPE, UpdateModifiersC2SPacket.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> PacketHandler.handleUpdateModifiers(payload, (ServerPlayer) context.player())));
            registrar.playToClient(SyncModifiersS2CPacket.TYPE, SyncModifiersS2CPacket.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> PacketHandler.handleSyncModifiers(payload)));
            registrar.playToServer(UpdateServerConfigC2SPacket.TYPE, UpdateServerConfigC2SPacket.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> PacketHandler.handleUpdateServerConfig(payload, (ServerPlayer) context.player())));
            registrar.playToClient(SyncServerConfigS2CPacket.TYPE, SyncServerConfigS2CPacket.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> PacketHandler.handleSyncServerConfig(payload)));
            registrar.playToServer(BuildModeHintC2SPacket.TYPE, BuildModeHintC2SPacket.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> PacketHandler.handleBuildModeHint((ServerPlayer) context.player())));
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            Player patt0$temp = event.getEntity();
            if (patt0$temp instanceof ServerPlayer serverPlayer) {
                ModifierServerStorage.loadPlayer(serverPlayer.server, serverPlayer.getUUID());
                PacketHandler.sendToClient(serverPlayer, new SyncModifiersS2CPacket(ModifierServerStorage.serializePlayer(serverPlayer.getUUID())));
                PacketHandler.sendToClient(serverPlayer, new SyncServerConfigS2CPacket(ServerConfig.INSTANCE.toJson()));
                WelcomeMessageStorage.showIfNeeded(serverPlayer);
            }

        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            Player patt0$temp = event.getEntity();
            if (patt0$temp instanceof ServerPlayer serverPlayer) {
                ModifierServerStorage.savePlayer(serverPlayer.server, serverPlayer.getUUID());
                ModifierServerStorage.removePlayer(serverPlayer.getUUID());
            }

            UndoManager.clearPlayer(event.getEntity().getUUID());
            PlacedBlockTracker.clearPlayer(event.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            ModifierServerStorage.clearAll();
            ServerConfigStorage.clear();
            WelcomeMessageStorage.clear();
            BuildModeHintStorage.clear();
        });
        NeoForge.EVENT_BUS.addListener(( ServerStartingEvent event) -> {
            ServerConfigStorage.load(event.getServer());
            WelcomeMessageStorage.load(event.getServer());
            BuildModeHintStorage.load(event.getServer());
        });
    }

    static {
        RANDOMIZER_TOOL = ITEMS.register("randomizer_tool", () -> new RandomizerToolItem((new Item.Properties()).stacksTo(1)));
        MENUS = DeferredRegister.create(Registries.MENU, MODID);
        MENUS.register("randomizer", () -> ModMenus.RANDOMIZER);
    }
}
