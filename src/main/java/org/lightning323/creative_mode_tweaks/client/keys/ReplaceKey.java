package org.lightning323.creative_mode_tweaks.client.keys;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.network.packets.PacketReplace;

@OnlyIn(Dist.CLIENT)
public class ReplaceKey extends KeyBase {
    private BlockState lockedBlockState = null;


    public ReplaceKey(String name, int keyCode, String category) {
        super(name, keyCode, category);
    }

    @Override
    public void onKeyPress() {
    }

    @Override
    public void onKeyRelease() {
        lockedBlockState = null;
    }


    @Override
    public void onClientTick(ClientTickEvent.Post event) {
        if (this.isDown()) {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.player.isCreative()) {
                return;
            }

            if (mc.player.isShiftKeyDown() ^ Config.INVERT_REPLACE_LOCK.get()) {
                HitResult target = mc.hitResult;
                if (target != null && target.getType() == HitResult.Type.BLOCK) {
                    lockedBlockState = mc.level.getBlockState(((BlockHitResult) target).getBlockPos());
                } else {
                    lockedBlockState = Blocks.AIR.defaultBlockState();
                }
            } else {
                lockedBlockState = null;
            }

            this.replaceSelectedBlock();
        }
    }

    private void replaceSelectedBlock() {
        Minecraft mc = Minecraft.getInstance();

        HitResult target = mc.hitResult;
        if (target == null || target.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = ((BlockHitResult) target).getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        if (lockedBlockState != null && lockedBlockState != state) {
            return;
        }
        ItemStack itemStack = mc.player.getInventory().getSelected();
        Block block = Block.byItem(itemStack.getItem());
        if (itemStack.isEmpty() || block == Blocks.AIR) {
            return;
        }
        BlockState newBlockState = block.getStateForPlacement(new BlockPlaceContext(mc.player, InteractionHand.MAIN_HAND, itemStack, (BlockHitResult) target));
        PacketDistributor.sendToServer(new PacketReplace(pos, newBlockState, state));
    }
}