package org.lightning323.creative_mode_tweaks.mixin.hotbar;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin({Minecraft.class})
public class MinecraftMixin {
    @Shadow
    public LocalPlayer player;

    @ModifyConstant(
            method = {"pickBlock"},
            constant = {@Constant(
                    intValue = 36
            )}
    )
    public int pickBlockMixin(int value, @Local Inventory inventory) {
        if (HotbarUtil.enableEnhancedHotbar(player)) {
            return inventory.selected >= 9 ? 0 : 36;
        }
        return 36;
    }
}
