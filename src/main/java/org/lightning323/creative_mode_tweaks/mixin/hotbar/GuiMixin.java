package org.lightning323.creative_mode_tweaks.mixin.hotbar;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.utils.HotbarUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lightning323.creative_mode_tweaks.utils.HotbarUtil.*;

@Mixin({Gui.class})
public abstract class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    protected abstract @Nullable Player getCameraPlayer();

    @Shadow
    protected abstract void renderSlot(GuiGraphics var1, int var2, int var3, DeltaTracker var4, Player var5, ItemStack var6, int var7);

    //Constants that dont change
    @Unique
    final int hotbarHeight = HOTBAR_SLOT_GUI_SIZE + 2;

    @Unique
    private int getDistanceOnWheel(int a, int b, int total) {
        int diff = a - b;
        // Wraps the difference into the range [-total/2, total/2]
        return Math.floorMod(diff + total / 2, total) - total / 2;
    }

    @Inject(
            method = "renderItemHotbar",
            at = @At("HEAD"), // Or "RETURN" to run after the original code
            cancellable = true
    )
    private void onRenderItemHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Player player = this.getCameraPlayer();
        if (enableEnhancedHotbar(Minecraft.getInstance().player)) {
            ci.cancel();
            ItemStack itemstack = player.getOffhandItem();
            HumanoidArm humanoidarm = player.getMainArm().getOpposite();

            //Constants
            final int xCenter = graphics.guiWidth() / 2;
            final double availableSlots = ((double) graphics.guiWidth() / HOTBAR_SLOT_GUI_SIZE) - 4;
            final int maxHotbarSize = player.isCreative() ? Config.creativeHotbarMaxSize : Config.survivalHotbarMaxSize;
            final int hotbarSlots = (int) Math.floor(Mth.clamp(availableSlots, 9, maxHotbarSize) / 3) * 3;
            final int scrollMargin = (int) Mth.map(hotbarSlots, 9, maxHotbarSize, Config.hotbarMinScrollMargin, Config.hotbarMaxScrollMargin);
            final int hotbarWidth = (HOTBAR_SLOT_GUI_SIZE * hotbarSlots) + 2;
            final int hotbarX = xCenter - (hotbarWidth / 2);
            final int hotbarY = graphics.guiHeight() - hotbarHeight;
            final int x1 = hotbarX + hotbarWidth;

            RenderSystem.enableBlend();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, -90.0F);

            //Hotbar sprite

            ResourceLocation sprite = HotbarUtil.HOTBAR_SPRITE_9;
            if (hotbarSlots == 12) sprite = HotbarUtil.HOTBAR_SPRITE_12;
            else if (hotbarSlots == 15) sprite = HotbarUtil.HOTBAR_SPRITE_15;
            else if (hotbarSlots == 18) sprite = HotbarUtil.HOTBAR_SPRITE_18;
            graphics.blitSprite(sprite,
                    hotbarX, //X
                    hotbarY, //Y
                    hotbarWidth, //width
                    hotbarHeight //height
            );

            int selection = player.getInventory().selected;

            //We want the hotbar scroll to move with the selection, Selectron starts at 0, 9 is one slot over the gui
            int relativePos = getDistanceOnWheel(selection, hotbarScroll, 36);

            if (scrollMargin >= hotbarSlots / 2)
                hotbarScroll = selection - (hotbarSlots / 2);
            else {
                if (relativePos < scrollMargin) {
                    hotbarScroll = selection - scrollMargin;
                } else if (relativePos >= hotbarSlots - scrollMargin) {
                    hotbarScroll = selection - (hotbarSlots - 1 - scrollMargin);
                }
            }

            int selectionXAxis = getDistanceOnWheel(selection, hotbarScroll, 36);//If the scroll is 3, and the selection is 18, we want 18-6

            graphics.blitSprite(HotbarUtil.HOTBAR_SELECTION_SPRITE,
                    hotbarX - 1 + selectionXAxis * 20 + selectionXAxis / hotbarSlots * 2,
                    graphics.guiHeight() - 22 - 1,
                    24, 23);

            if (!itemstack.isEmpty()) {
                if (humanoidarm == HumanoidArm.LEFT) {
                    graphics.blitSprite(HotbarUtil.HOTBAR_OFFHAND_LEFT_SPRITE, hotbarX - 29, graphics.guiHeight() - 23, 29, 24);
                } else {
                    graphics.blitSprite(HotbarUtil.HOTBAR_OFFHAND_RIGHT_SPRITE, x1 + 91, graphics.guiHeight() - 23, 29, 24);
                }
            }

            graphics.pose().popPose();
            RenderSystem.disableBlend();

            //Display the items in the hotbar
            int l = 1;
            for (int i = 0; i < hotbarSlots; ++i) {
                int x = hotbarX + i * 20 + 3 + i / hotbarSlots * 2;
                int y = graphics.guiHeight() - 16 - 3;
                int index = Math.floorMod(
                        i + hotbarScroll,
                        player.getInventory().items.size()
                );

                ItemStack item = (ItemStack) player.getInventory().items.get(index);
                this.renderSlot(graphics, x, y, deltaTracker, player, item, l++);
            }

            if (!itemstack.isEmpty()) {
                int i2 = graphics.guiHeight() - 16 - 3;
                if (humanoidarm == HumanoidArm.LEFT) {
                    this.renderSlot(graphics, hotbarX - 26, i2, deltaTracker, player, itemstack, l++);
                } else {
                    this.renderSlot(graphics, x1 + 10, i2, deltaTracker, player, itemstack, l++);
                }
            }

            if (this.minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
                RenderSystem.enableBlend();
                float f = this.minecraft.player.getAttackStrengthScale(0.0F);
                if (f < 1.0F) {
                    int j2 = graphics.guiHeight() - 20;
                    int k2 = x1 + 6;
                    if (humanoidarm == HumanoidArm.RIGHT) {
                        k2 = hotbarX - 22;
                    }

                    int l1 = (int) (f * 19.0F);
                    graphics.blitSprite(HotbarUtil.HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE, k2, j2, 18, 18);
                    graphics.blitSprite(HotbarUtil.HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE, 18, 18, 0, 18 - l1, k2, j2 + 18 - l1, 18, l1);
                }

                RenderSystem.disableBlend();
            }
        }
    }


}
