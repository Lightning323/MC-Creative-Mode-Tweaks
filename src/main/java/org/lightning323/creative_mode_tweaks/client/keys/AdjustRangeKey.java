package org.lightning323.creative_mode_tweaks.client.keys;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.client.rendering.TextRenderer;
import org.lightning323.creative_mode_tweaks.network.packets.PacketAdjustRange;

@OnlyIn(Dist.CLIENT)
public class AdjustRangeKey extends KeyBase implements LayeredDraw.Layer {

    /** Last reach sent to the server; avoids spamming a packet every frame. */
    private int lastSentReach = -1;

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
                dist = Config.REACH_MAX;
            } else {
                dist = mc.player.getEyePosition(partialTicks).distanceTo(rayTraceResult.getLocation());
            }

            // The pointed distance becomes the creative single reach; the
            // creative building reach follows via the configured offset.
            int singleReach = Config.clampReach(dist);
            int buildingReach = Config.deriveBuildingReach(singleReach);

            // Update Server (only when the value changed) and mirror locally
            // so the preview and crosshair pick it up without a round-trip.
            if (singleReach != lastSentReach) {
                lastSentReach = singleReach;
                Config.updateClientSingleReach(singleReach);
                PacketDistributor.sendToServer(new PacketAdjustRange(singleReach));
            }
            // Render UI
            TextRenderer.showMessage(guiGraphics, mc.getWindow(),
                    "Reach: " + singleReach + " blocks (build " + buildingReach + ")");
        } else {
            lastSentReach = -1;
        }
    }
}

