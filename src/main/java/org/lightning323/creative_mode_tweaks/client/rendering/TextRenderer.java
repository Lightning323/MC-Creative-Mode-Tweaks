package org.lightning323.creative_mode_tweaks.client.rendering;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class TextRenderer {
	public static void showMessage(GuiGraphics guiGraphics, Window scaledResolution, String msg) {
		showMessage(guiGraphics, scaledResolution, msg, 0xFFFFFF);
	}

	private static void showMessage(GuiGraphics guiGraphics, Window scaledResolution, String msg, int color) {
		Minecraft mc = Minecraft.getInstance();
		int x = (scaledResolution.getGuiScaledWidth() - mc.font.width(msg)) / 2;
		int y = scaledResolution.getGuiScaledHeight() - 70;
		guiGraphics.drawString(mc.font, msg, x, y, color);
	}
}
