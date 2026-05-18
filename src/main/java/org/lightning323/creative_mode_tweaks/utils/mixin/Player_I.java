package org.lightning323.creative_mode_tweaks.utils.mixin;

import net.minecraft.world.entity.player.Player;
import org.lightning323.creative_mode_tweaks.Config;

public interface Player_I {
    public boolean isNoClip();

    public void setNoClip(boolean noClip);

    public void setEnableEnhancedHotbar();

    public boolean getEnableEnhancedHotbar();
}
