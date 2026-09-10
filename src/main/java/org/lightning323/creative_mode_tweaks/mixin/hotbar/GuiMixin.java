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
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.client.ClientHotbarUtil;
import org.lightning323.creative_mode_tweaks.client.ClientModEvents;
import org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil;
import org.lightning323.creative_mode_tweaks.utils.mixin.Player_I;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil.*;

@Mixin({Gui.class})
public abstract class GuiMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    protected abstract @Nullable Player getCameraPlayer();

    @Shadow
    protected abstract void renderSlot(GuiGraphics var1, int var2, int var3, DeltaTracker var4, Player var5, ItemStack var6, int var7);

    @Shadow
    public int leftHeight;

    @Shadow
    public int rightHeight;

    //Constants that dont change
    @Unique
    final int hotbarHeight = HOTBAR_SLOT_GUI_SIZE + 2;
    @Unique
    private int maxHotbarSize;
    @Unique
    private double availableSlots;
    @Unique
    private int xCenter;
    @Unique
    private int scrollMargin;
    @Unique
    private int selection;
    @Unique
    private int hotbarWidth;
    @Unique
    private int hotbarX;
    @Unique
    private int hotbarY;
    @Unique
    private int x1;

    /**
     * Extra vertical space taken by the 4x9 full-inventory preview compared to a
     * single hotbar row. The preview stacks 4 rows of {@code HOTBAR_SLOT_GUI_SIZE}
     * (20px) where vanilla only uses one, so survival HUD anchored just above the
     * hotbar must be raised by the 3 extra rows to keep vanilla spacing.
     */
    @Unique
    private static final int creative_mode_tweaks$PREVIEW_HUD_LIFT = 3 * HOTBAR_SLOT_GUI_SIZE;

    @Unique
    private int creative_mode_tweaks$getPreviewHudLift() {
        return ClientModEvents.isInventoryPreviewHeld() ? creative_mode_tweaks$PREVIEW_HUD_LIFT : 0;
    }

    /**
     * Raises the vanilla survival-HUD anchors while the 4x9 preview is visible.
     * {@code leftHeight}/{@code rightHeight} drive hearts, armor, hunger, air,
     * vehicle hearts, the selected-item name and the action-bar overlay, so bumping
     * the freshly reset (39) values here preserves their vanilla gaps above the
     * now taller preview. The XP/jump bars use fixed Y positions and are shifted
     * separately via pose translation below.
     */
    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/client/gui/GuiLayerManager;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"
            )
    )
    private void creative_mode_tweaks$liftSurvivalHudAnchors(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        int lift = creative_mode_tweaks$getPreviewHudLift();
        if (lift != 0) {
            this.leftHeight += lift;
            this.rightHeight += lift;
        }
    }

    /**
     * The XP bar, XP level number and jump meter use hardcoded Y offsets from the
     * screen bottom, so the anchor bump above does not move them. Translate the
     * pose for just these layers while the preview is open to keep the same
     * vanilla gap above the taller grid. Always push/pop to stay balanced.
     */
    @Inject(
            method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V",
            at = @At("HEAD")
    )
    private void creative_mode_tweaks$shiftExperienceBarHead(GuiGraphics graphics, int x, CallbackInfo ci) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, (float) -creative_mode_tweaks$getPreviewHudLift(), 0.0F);
    }

    @Inject(
            method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V",
            at = @At("RETURN")
    )
    private void creative_mode_tweaks$shiftExperienceBarReturn(GuiGraphics graphics, int x, CallbackInfo ci) {
        graphics.pose().popPose();
    }

    @Inject(
            method = "renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD")
    )
    private void creative_mode_tweaks$shiftExperienceLevelHead(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, (float) -creative_mode_tweaks$getPreviewHudLift(), 0.0F);
    }

    @Inject(
            method = "renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN")
    )
    private void creative_mode_tweaks$shiftExperienceLevelReturn(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        graphics.pose().popPose();
    }

    @Inject(
            method = "renderJumpMeter(Lnet/minecraft/world/entity/PlayerRideableJumping;Lnet/minecraft/client/gui/GuiGraphics;I)V",
            at = @At("HEAD")
    )
    private void creative_mode_tweaks$shiftJumpMeterHead(PlayerRideableJumping rideable, GuiGraphics graphics, int x, CallbackInfo ci) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, (float) -creative_mode_tweaks$getPreviewHudLift(), 0.0F);
    }

    @Inject(
            method = "renderJumpMeter(Lnet/minecraft/world/entity/PlayerRideableJumping;Lnet/minecraft/client/gui/GuiGraphics;I)V",
            at = @At("RETURN")
    )
    private void creative_mode_tweaks$shiftJumpMeterReturn(PlayerRideableJumping rideable, GuiGraphics graphics, int x, CallbackInfo ci) {
        graphics.pose().popPose();
    }


    @Inject(
            method = "renderItemHotbar",
            at = @At("HEAD"), // Or "RETURN" to run after the original code
            cancellable = true
    )
    private void onRenderItemHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Player player = this.getCameraPlayer();
        if (player == null) return;
        if (ClientModEvents.isInventoryPreviewHeld()) {
            ci.cancel();
            creative_mode_tweaks$renderFullInventoryPreview(graphics, deltaTracker, player);
            return;
        }
        if (enableEnhancedHotbar(player)) {
            ci.cancel();
            ItemStack itemstack = player.getOffhandItem();
            HumanoidArm humanoidarm = player.getMainArm().getOpposite();

            //Constants
            xCenter = graphics.guiWidth() / 2;
            availableSlots = ((double) graphics.guiWidth() / HOTBAR_SLOT_GUI_SIZE) - 4;
            maxHotbarSize = player.isCreative() ? Config.creativeHotbarMaxSize : Config.survivalHotbarMaxSize;
            hotbarSlots = (int) Math.floor(Mth.clamp(availableSlots, 9, maxHotbarSize) / 3) * 3;
            scrollMargin = hotbarSlots == 9 ? Config.hotbarMinScrollMargin :
                    (int) Mth.map(hotbarSlots, 9, maxHotbarSize, Config.hotbarMinScrollMargin, Config.hotbarMaxScrollMargin);
            selection = player.getInventory().selected;
            hotbarWidth = (HOTBAR_SLOT_GUI_SIZE * hotbarSlots) + 2;
            hotbarX = xCenter - (hotbarWidth / 2);
            hotbarY = graphics.guiHeight() - hotbarHeight;
            x1 = hotbarX + hotbarWidth;

            RenderSystem.enableBlend();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, -90.0F);

            //Hotbar sprite

            ResourceLocation sprite = ClientHotbarUtil.HOTBAR_SPRITE_9;
            if (hotbarSlots == 12) sprite = ClientHotbarUtil.HOTBAR_SPRITE_12;
            else if (hotbarSlots == 15) sprite = ClientHotbarUtil.HOTBAR_SPRITE_15;
            else if (hotbarSlots == 18) sprite = ClientHotbarUtil.HOTBAR_SPRITE_18;
            graphics.blitSprite(sprite,
                    hotbarX, //X
                    hotbarY, //Y
                    hotbarWidth, //width
                    hotbarHeight //height
            );


            //We want the hotbar scroll to move with the selection, Selectron starts at 0, 9 is one slot over the gui
            int relativePos = getDistanceOnInvWheel(selection, hotbarScroll);

            if (scrollMargin >= hotbarSlots / 2)
                hotbarScroll = selection - (hotbarSlots / 2);
            else {
                if (relativePos < scrollMargin) {
                    hotbarScroll = selection - scrollMargin;
                } else if (relativePos >= hotbarSlots - scrollMargin) {
                    hotbarScroll = selection - (hotbarSlots - 1 - scrollMargin);
                }
            }

            int selectionXAxis = getDistanceOnInvWheel(selection, hotbarScroll);//If the scroll is 3, and the selection is 18, we want 18-6

            graphics.blitSprite(ClientHotbarUtil.HOTBAR_SELECTION_SPRITE,
                    hotbarX - 1 + selectionXAxis * 20 + selectionXAxis / hotbarSlots * 2,
                    graphics.guiHeight() - 22 - 1,
                    24, 23);

            if (!itemstack.isEmpty()) {
                if (humanoidarm == HumanoidArm.LEFT) {
                    graphics.blitSprite(ClientHotbarUtil.HOTBAR_OFFHAND_LEFT_SPRITE, hotbarX - 29, graphics.guiHeight() - 23, 29, 24);
                } else {
                    graphics.blitSprite(ClientHotbarUtil.HOTBAR_OFFHAND_RIGHT_SPRITE, x1 + 91, graphics.guiHeight() - 23, 29, 24);
                }
            }

            graphics.pose().popPose();
            RenderSystem.disableBlend();

            //Display the targetItems in the hotbar
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
                    graphics.blitSprite(ClientHotbarUtil.HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE, k2, j2, 18, 18);
                    graphics.blitSprite(ClientHotbarUtil.HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE, 18, 18, 0, 18 - l1, k2, j2 + 18 - l1, 18, l1);
                }

                RenderSystem.disableBlend();
            }
        }
    }

    /**
     * Lifts the selected-item name ("Grass Block", etc.) above the 4x9 preview while it is visible.
     * Vanilla draws it at {@code guiHeight - max(yShift, 59)}, which lands inside row 2 of the
     * preview (spanning {@code guiHeight - 82} to the bottom). Only the height offset is raised;
     * all other vanilla behavior (fade, font, spectator check) is untouched.
     */
    @ModifyVariable(
            method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private int creative_mode_tweaks$liftSelectedItemName(int yShift) {
        if (ClientModEvents.isInventoryPreviewHeld()) {
            int lift = 82 + 12;
            if (this.minecraft.gameMode != null && !this.minecraft.gameMode.canHurtPlayer()) {
                lift += 14;
            }
            return Math.max(yShift, lift);
        }
        return yShift;
    }

    /**
     * Renders the full 36-slot inventory as a 4x9 grid stacked above the hotbar position.
     * Bottom row (row 0) holds slots 0-8 so it matches the vanilla inventory-screen layout,
     * and the selection cursor is drawn on the selected slot wherever it sits in the grid.
     * Shown only while a Shift Inventory Rows key is held; single-row rendering resumes on release.
     */
    @Unique
    private void creative_mode_tweaks$renderFullInventoryPreview(GuiGraphics graphics, DeltaTracker deltaTracker, Player player) {
        final int previewCols = 9;
        final int previewRows = 4;
        final int previewWidth = (HOTBAR_SLOT_GUI_SIZE * previewCols) + 2;
        int previewXCenter = graphics.guiWidth() / 2;
        int previewX = previewXCenter - (previewWidth / 2);
        int previewX1 = previewX + previewWidth;
        int bottomY = graphics.guiHeight() - hotbarHeight;

        ItemStack offhandStack = player.getOffhandItem();
        HumanoidArm oppositeArm = player.getMainArm().getOpposite();
        int invSize = player.getInventory().items.size();
        int selected = player.getInventory().selected;
        int selCol = Math.floorMod(selected, previewCols);
        int selRow = Math.floorMod(selected / previewCols, previewRows);

        RenderSystem.enableBlend();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, -90.0F);

        for (int row = 0; row < previewRows; row++) {
            int y = bottomY - (row * HOTBAR_SLOT_GUI_SIZE);
            graphics.blitSprite(ClientHotbarUtil.HOTBAR_SPRITE_9,
                    previewX,
                    y,
                    previewWidth,
                    hotbarHeight
            );
        }

        graphics.blitSprite(ClientHotbarUtil.HOTBAR_SELECTION_SPRITE,
                previewX - 1 + (selCol * 20),
                graphics.guiHeight() - 22 - 1 - (selRow * 20),
                24, 23);

        if (!offhandStack.isEmpty()) {
            if (oppositeArm == HumanoidArm.LEFT) {
                graphics.blitSprite(ClientHotbarUtil.HOTBAR_OFFHAND_LEFT_SPRITE, previewX - 29, graphics.guiHeight() - 23, 29, 24);
            } else {
                graphics.blitSprite(ClientHotbarUtil.HOTBAR_OFFHAND_RIGHT_SPRITE, previewX1, graphics.guiHeight() - 23, 29, 24);
            }
        }

        graphics.pose().popPose();
        RenderSystem.disableBlend();

        int seed = 1;
        for (int row = 0; row < previewRows; row++) {
            for (int col = 0; col < previewCols; col++) {
                int index = (row * previewCols) + col;
                if (index < 0 || index >= invSize) continue;
                int x = previewX + (col * 20) + 3;
                int y = graphics.guiHeight() - 16 - 3 - (row * 20);
                ItemStack item = player.getInventory().items.get(index);
                this.renderSlot(graphics, x, y, deltaTracker, player, item, seed++);
            }
        }

        if (!offhandStack.isEmpty()) {
            int y = graphics.guiHeight() - 16 - 3;
            if (oppositeArm == HumanoidArm.LEFT) {
                this.renderSlot(graphics, previewX - 26, y, deltaTracker, player, offhandStack, seed++);
            } else {
                this.renderSlot(graphics, previewX1 + 3, y, deltaTracker, player, offhandStack, seed++);
            }
        }

        if (this.minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR
                && this.minecraft.player != null) {
            RenderSystem.enableBlend();
            float f = this.minecraft.player.getAttackStrengthScale(0.0F);
            if (f < 1.0F) {
                int j2 = graphics.guiHeight() - 20;
                int k2 = previewX1 + 6;
                if (oppositeArm == HumanoidArm.RIGHT) {
                    k2 = previewX - 22;
                }

                int l1 = (int) (f * 19.0F);
                graphics.blitSprite(ClientHotbarUtil.HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE, k2, j2, 18, 18);
                graphics.blitSprite(ClientHotbarUtil.HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE, 18, 18, 0, 18 - l1, k2, j2 + 18 - l1, 18, l1);
            }

            RenderSystem.disableBlend();
        }
    }

}
