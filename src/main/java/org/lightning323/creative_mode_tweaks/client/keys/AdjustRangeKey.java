package org.lightning323.creative_mode_tweaks.client.keys;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.client.rendering.TextRenderer;
import org.lightning323.creative_mode_tweaks.network.packets.PacketAdjustRange;

@OnlyIn(Dist.CLIENT)
public class AdjustRangeKey extends KeyBase implements LayeredDraw.Layer {

    public AdjustRangeKey(String name, int keyCode, String category) {
        super(name, keyCode, category);
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (isDown()) {
            Minecraft mc = Minecraft.getInstance();

            // Safety checks: ensure player exists and is in creative
            if (mc.player == null || !mc.player.isCreative()) {
                return;
            }

            // 1.21 use deltaTracker.getGameTimeDeltaTicks() instead of partialTicks
            float partialTicks = deltaTracker.getGameTimeDeltaTicks();

            // Update distance via RayTrace
            HitResult rayTraceResult = mc.getCameraEntity().pick(255.0, partialTicks, false);
            double dist;

            if (rayTraceResult == null || rayTraceResult.getType() == HitResult.Type.MISS) {
                dist = Config.REACH_MAX_RANGE.get();
            } else {
                dist = mc.player.getEyePosition(partialTicks).distanceTo(rayTraceResult.getLocation());
                dist = Mth.clamp(dist, Config.REACH_MIN_RANGE.get(), Config.REACH_MAX_RANGE.get());
            }

            // Update Server
            PacketDistributor.sendToServer(new PacketAdjustRange(dist));
            // Render UI
            String distStr = String.valueOf((int) dist);
            TextRenderer.showMessage(guiGraphics, mc.getWindow(), "Reach: " + distStr + " blocks");
        }
    }
}


