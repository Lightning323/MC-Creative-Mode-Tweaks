package org.lightning323.creative_mode_tweaks.mixin.hotbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.client.ClientHotbarUtil;
import org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.lightning323.creative_mode_tweaks.client.ClientModEvents.KEY_ROTATE_INV_DOWN;
import static org.lightning323.creative_mode_tweaks.client.ClientModEvents.KEY_ROTATE_INV_UP;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(
            method = "keyPressed(III)Z",
            at = @At("TAIL"),
            cancellable = true
    )
    private void keyEvent(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (Config.allowInventoryRotationInSurvival) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                if (keyCode == KEY_ROTATE_INV_UP.getKey().getValue()) {
                    ClientHotbarUtil.rotateInventoryAndSync(player, 9, false);
                    cir.setReturnValue(true); // Tell the game we handled this key
                } else if (keyCode == KEY_ROTATE_INV_DOWN.getKey().getValue()) {
                    ClientHotbarUtil.rotateInventoryAndSync(player, -9, false);
                    cir.setReturnValue(true);
                }
            }
        }
    }
}