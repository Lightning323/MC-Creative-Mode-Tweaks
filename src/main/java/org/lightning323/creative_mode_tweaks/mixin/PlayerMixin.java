package org.lightning323.creative_mode_tweaks.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.lightning323.creative_mode_tweaks.utils.mixin.Player_I;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements Player_I {


    /**
     * There is a server side and a client side version of each player
     * If we JUST set the variable on the server, it will fight with the existing variable on the client
     */
    //Unique is used to prevent from conflicting with other mixin varaibles
    @Unique
    private boolean noClipEnabled = true;

    protected PlayerMixin(EntityType<? extends LivingEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Shadow
    public abstract boolean isSpectator();

    @Shadow
    public abstract Abilities getAbilities();

    @Shadow
    public abstract boolean isCreative();

    @Override
    public boolean isNoClip() {
        return noClipEnabled && isCreative();
    }

    @Override
    public void setNoClip(boolean noClip) {
        this.noClipEnabled = noClip;
    }

    @Inject(
            method = "updatePlayerPose",
            at = @At(value = "TAIL")
    )
    private void modifyPose(CallbackInfo ci) {
        if (this.getAbilities().flying && isNoClip()) this.setPose(Pose.STANDING);
    }

    /**
     * Inject right after noPhysics is set, but before any further logic.
     */
    @Inject(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER,
                    ordinal = 0
            )
    )
    private void onTick_AfterNoPhysics(CallbackInfo ci) {
        // Modify noPhysics only if the player is a spectator and noClip is enabled.
        if (this.getAbilities().flying && isNoClip()) {
            this.noPhysics = true;
            this.setOnGround(false);
        }
    }


//    @Inject(method = "tick", at = @At("HEAD"))
//    private void onTick_Head(CallbackInfo ci) {
//        System.out.println("HEAD--------- NoClip: " + noClipEnabled);
//    }
//
//    @Inject(method = "tick", at = @At("TAIL"))
//    private void onTick_TAIL(CallbackInfo ci) {
//        System.out.println("TAIL--------- NoClip: " + noClipEnabled);
//    }

}
