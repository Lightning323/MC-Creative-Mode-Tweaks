package org.lightning323.creative_mode_tweaks.client;


import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.lightning323.creative_mode_tweaks.Config;

import java.lang.ref.WeakReference;

public final class PlayerTicker {
    private static final Minecraft MINECRAFT = Minecraft.getInstance();
    private static final Options OPTIONS = MINECRAFT.options;

    private static final ToggleKeyMapping KEY_SPRINT = (ToggleKeyMapping) OPTIONS.keySprint;
    private static final KeyMapping KEY_SNEAK = OPTIONS.keyShift;
    private static final KeyMapping KEY_FORWARD = OPTIONS.keyUp;
    private static final KeyMapping KEY_JUMP = OPTIONS.keyJump;

    private static PlayerTicker ticker = new PlayerTicker(null);

    public static PlayerTicker get(LocalPlayer player) {
        if (ticker.ref.get() != player) {
            ticker = new PlayerTicker(player);
        }

        return ticker;
    }

    private final WeakReference<LocalPlayer> ref;

    private PlayerTicker(LocalPlayer player) {
        this.ref = new WeakReference<>(player);
    }

    public static final double INERTIA_DAMPING = 0.1f;

    public void afterSuperCall(LocalPlayer player) {
        if (player.getAbilities().flying && (player.isSpectator() || player.isCreative())) {
            player.getAbilities().setFlyingSpeed(Config.flightSpeed);

            if (Config.disableFlightInertia) {
                final Input input = player.input;
                if (input.forwardImpulse == 0F && input.leftImpulse == 0F) {
                    player.setDeltaMovement(player.getDeltaMovement().multiply(INERTIA_DAMPING, 1.0, INERTIA_DAMPING));
                }
                if (!input.jumping && !input.shiftKeyDown) {
                    player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, INERTIA_DAMPING, 1.0));
                }
            }
        }
    }

}