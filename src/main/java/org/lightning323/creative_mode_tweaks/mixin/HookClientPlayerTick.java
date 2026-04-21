package org.lightning323.creative_mode_tweaks.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.lightning323.creative_mode_tweaks.client.Mixins;
import org.lightning323.creative_mode_tweaks.client.PlayerTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.spongepowered.asm.mixin.injection.At.Shift.AFTER;

@Mixin(LocalPlayer.class)
public abstract class HookClientPlayerTick extends AbstractClientPlayer {
	protected HookClientPlayerTick(ClientLevel world, GameProfile profile) {
		super(world, profile);
	}

	@Inject(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;aiStep()V", ordinal = 0, shift = AFTER))
	private void afterSuperCall(CallbackInfo info) {
		LocalPlayer player = Mixins.me(this);
		PlayerTicker.get(player).afterSuperCall(player);
	}
}