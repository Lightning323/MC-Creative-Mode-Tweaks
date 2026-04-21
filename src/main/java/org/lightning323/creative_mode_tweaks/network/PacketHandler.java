package org.lightning323.creative_mode_tweaks.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.lightning323.creative_mode_tweaks.network.packets.PacketAdjustRange;
import org.lightning323.creative_mode_tweaks.network.packets.PacketGameModeChanged;
import org.lightning323.creative_mode_tweaks.network.packets.PacketReplace;
import org.lightning323.creative_mode_tweaks.network.packets.PacketToggleNoclip;

import static org.lightning323.creative_mode_tweaks.CreativeModeTweaks.MODID;

@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD)
public class PacketHandler {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        // "1" is your protocol version
        final PayloadRegistrar registrar = event.registrar("1");

        // Register ToggleNoClip (Assumed simple payload)
        registrar.playToServer(
                PacketToggleNoclip.TYPE,
                PacketToggleNoclip.CODEC,
                PacketToggleNoclip::handle
        );

        // Register Replace
        registrar.playToServer(
                PacketReplace.TYPE,
                PacketReplace.CODEC,
                PacketReplace::handle
        );

        // Register Adjust Range
        registrar.playToServer(
                PacketAdjustRange.TYPE,
                PacketAdjustRange.CODEC,
                PacketAdjustRange::handle
        );

        registrar.playToClient(
                PacketGameModeChanged.TYPE,
                PacketGameModeChanged.CODEC,
                PacketGameModeChanged::handle
        );
    }
}