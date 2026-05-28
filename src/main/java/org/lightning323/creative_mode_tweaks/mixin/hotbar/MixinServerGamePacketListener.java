package org.lightning323.creative_mode_tweaks.mixin.hotbar;

import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mojang.text2speech.Narrator.LOGGER;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListener {

    @Shadow
    public ServerPlayer player; // Access the player context safely on the server

    @Inject(
            method = "handleSetCarriedItem(Lnet/minecraft/network/protocol/game/ServerboundSetCarriedItemPacket;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void handleCustomCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;

        PacketUtils.ensureRunningOnSameThread(packet, self, self.player.serverLevel());
        //We change getSelectionSize to getHotbarSize
        if (packet.getSlot() >= 0 && packet.getSlot() < HotbarUtil.getHotbarSize(self.player)) {
            if (this.player.getInventory().selected != packet.getSlot() && this.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                this.player.stopUsingItem();
            }

            this.player.getInventory().selected = packet.getSlot();
            this.player.resetLastActionTime();
        } else {
            LOGGER.warn("{} tried to set an invalid carried item, {}", this.player.getName().getString(), packet.getSlot());
        }
        ci.cancel();
    }
}