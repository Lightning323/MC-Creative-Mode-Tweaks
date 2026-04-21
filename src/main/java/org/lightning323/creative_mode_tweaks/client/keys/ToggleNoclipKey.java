package org.lightning323.creative_mode_tweaks.client.keys;

import net.minecraft.client.Minecraft;
import org.lightning323.creative_mode_tweaks.client.utils.ClientSettings;
import org.lightning323.creative_mode_tweaks.client.utils.ClientUtils;

public class ToggleNoclipKey extends KeyBase {


    public ToggleNoclipKey(String description, int keyCode, String category) {
        super(description, keyCode, category);
    }

    @Override
    public void onKeyPress() {
        /**
         * If we are in creative mode, change noclip
         */
        if (Minecraft.getInstance().player.isCreative()) {
            ClientSettings.setNoClip(!ClientSettings.isNoClip());
            ClientUtils.displayMessge("No-Clip " + (ClientSettings.isNoClip() ? "Enabled" : "Disabled"));
        }
    }
}
