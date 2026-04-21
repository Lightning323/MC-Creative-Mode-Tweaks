package org.lightning323.creative_mode_tweaks.client.keys;


import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lightning323.creative_mode_tweaks.client.utils.ClientSettings;
import org.lightning323.creative_mode_tweaks.client.utils.ClientUtils;

@OnlyIn(Dist.CLIENT)
public class NightVisionKey extends KeyBase {
    public NightVisionKey(String name, int keyCode, String category) {
        super(name, keyCode, category);
    }

    public void onKeyRelease() {
        ClientSettings.setNightVision(ClientSettings.isNightVision());
        ClientUtils.showToast("Night Vision", "Night Vision " + (ClientSettings.isNightVision() ? "Enabled" : "Disabled"));
    }
}