package org.lightning323.creative_mode_tweaks.mixin.hotbar;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lightning323.creative_mode_tweaks.utils.HotbarUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({Gui.class})
public abstract class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    protected abstract @Nullable Player getCameraPlayer();

    @Shadow
    protected abstract void renderSlot(GuiGraphics var1, int var2, int var3, DeltaTracker var4, Player var5, ItemStack var6, int var7);

    int oneHotbarLength = 182;
    int halfHotbarLength = 91;
    int hotbarHeight = 22;

    boolean displayArm = false;

    private void drawHotbar(GuiGraphics graphics, int x, int y, int horizontalHotbars) {
        int x0 = (graphics.guiWidth() / 2) - horizontalHotbars * (HotbarUtil.HOTBAR_UNIT_LENGTH/2);

        int hotbarY = graphics.guiHeight() - (HotbarUtil.HOTBAR_UNIT_HEIGHT * y);
        int hotbarX = x0 + (x * HotbarUtil.HOTBAR_UNIT_LENGTH);

            graphics.blitSprite(HotbarUtil.HOTBAR_SPRITE,
                    hotbarX, //X
                    hotbarY, //Y
                    HotbarUtil.HOTBAR_UNIT_LENGTH, //width
                    HotbarUtil.HOTBAR_UNIT_HEIGHT //height
            );
    }

    @Overwrite
    private void renderItemHotbar(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Player player = this.getCameraPlayer();
        if (player != null) {
            ItemStack itemstack = player.getOffhandItem();
            HumanoidArm humanoidarm = player.getMainArm().getOpposite();
            int xCenter = graphics.guiWidth() / 2;
            int hotbarCount = HotbarUtil.getBarCount(graphics.guiWidth());


            int x0 = xCenter - hotbarCount * 91;
            int x1 = x0 + hotbarCount * 182;
            RenderSystem.enableBlend();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, -90.0F);

            //Draw the hotbars themselves
            drawHotbar(graphics,0,1,2);
            drawHotbar(graphics,1,1,2);
            drawHotbar(graphics,0,2,2);
            drawHotbar(graphics,1,2,2);

            graphics.blitSprite(HotbarUtil.HOTBAR_SELECTION_SPRITE, x0 - 1 + player.getInventory().selected * 20 + player.getInventory().selected / 9 * 2, graphics.guiHeight() - 22 - 1, 24, 23);

            if (!itemstack.isEmpty()) {
                if (humanoidarm == HumanoidArm.LEFT) {
                    graphics.blitSprite(HotbarUtil.HOTBAR_OFFHAND_LEFT_SPRITE, x0 - 29, graphics.guiHeight() - 23, 29, 24);
                } else {
                    graphics.blitSprite(HotbarUtil.HOTBAR_OFFHAND_RIGHT_SPRITE, x1 + 91, graphics.guiHeight() - 23, 29, 24);
                }
            }

            graphics.pose().popPose();
            RenderSystem.disableBlend();
            int l = 1;

            for (int i = 0; i < hotbarCount * 9; ++i) {
                int x = x0 + i * 20 + 3 + i / 9 * 2;
                int y = graphics.guiHeight() - 16 - 3;
                this.renderSlot(graphics, x, y, deltaTracker, player, (ItemStack) player.getInventory().items.get(i), l++);
            }

            if (!itemstack.isEmpty()) {
                int i2 = graphics.guiHeight() - 16 - 3;
                if (humanoidarm == HumanoidArm.LEFT) {
                    this.renderSlot(graphics, x0 - 26, i2, deltaTracker, player, itemstack, l++);
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
                        k2 = x0 - 22;
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
