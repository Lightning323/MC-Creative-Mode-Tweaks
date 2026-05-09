//package org.lightning323.creative_mode_tweaks.mixin.hotbar;
//
//import net.minecraft.world.inventory.Slot;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.gen.Invoker;
//
//@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
//public interface SlotWrapperInvoker {
//
//    @Invoker("<init>")
//    static Object invokeInit(
//            Slot slot,
//            int slotId,
//            int x,
//            int y
//    ) {
//        throw new AssertionError();
//    }
//}