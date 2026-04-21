package org.lightning323.creative_mode_tweaks.client.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lightning323.creative_mode_tweaks.network.packets.PacketToggleNoclip;
import org.lightning323.creative_mode_tweaks.utils.mixin.I_OptionInstance;
import org.lightning323.creative_mode_tweaks.utils.mixin.Player_I;

@OnlyIn(Dist.CLIENT)
public class ClientSettings {

    private static double DEFAULT_GAMMA = 1.0D;
    public static final double MAX_GAMMA = 8.5;

    public static void setNoClip(boolean noClipEnabled) {
        if (Minecraft.getInstance().player.isCreative()) {
            Player_I player_i = (Player_I) Minecraft.getInstance().player;
            //Send the packet to the server
            PacketDistributor.sendToServer(new PacketToggleNoclip(noClipEnabled));
            //Set the value on the client as well
            player_i.setNoClip(noClipEnabled);
        }
    }

    public static boolean isNoClip() {
        Player_I player_i = (Player_I) Minecraft.getInstance().player;
        return player_i.isNoClip();
    }

    public static void setNightVision(boolean enabled) {
        if (enabled) {
            DEFAULT_GAMMA = getGammaClamped();
            setGamma(MAX_GAMMA);
        } else setGamma(DEFAULT_GAMMA);
    }

    private static final Minecraft mc = Minecraft.getInstance();
    private static final OptionInstance<Double> gamma = mc.options.gamma();
    private static final I_OptionInstance<Double> mixInGamma = (I_OptionInstance<Double>) (Object) gamma;

    private static void setGamma(double g) {
//        gamma.set(g);
        mixInGamma.setUnchecked(g); //Set without clamping it
    }

    private static double getGamma() {
        return gamma.get();
    }

    private static double getGammaClamped() {
        double value = gamma.get();
        if (value < 0.0D) return 0.0D;
        else if (value > 1.0D) return 1.0D;
        else return value;
    }

    public static boolean isNightVision() {
        return getGamma() == MAX_GAMMA;
    }
}
