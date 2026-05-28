package org.lightning323.creative_mode_tweaks.mixin.hotbar;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;
import org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListener {

    @Shadow
    public ServerPlayer player; // Access the player context safely on the server

    @ModifyExpressionValue(
            method = "handleSetCarriedItem(Lnet/minecraft/network/protocol/game/ServerboundSetCarriedItemPacket;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;getSelectionSize()I"
            )
    )
    private int modifyHotbarSizeValue(int originalSelectionSize) {
        return HotbarUtil.getHotbarSize(this.player);
    }
}