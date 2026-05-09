package org.lightning323.creative_mode_tweaks.mixin.hotbar;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public interface SlotWrapperAccessor {
    // This targets the private 'target' field inside the SlotWrapper class
    @Accessor("target")
    void setTarget(Slot slot);

    @Accessor("target")
    Slot getTarget();
}