package org.lightning323.creative_mode_tweaks.client.keys;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class KeyBase extends KeyMapping {

    public final String description;

    public static final List<KeyMapping> keys = new ArrayList<>();

    public KeyBase(String description, int keyCode, String category) {
        super(description, keyCode, category);
        this.description = description;
        keys.add(this);
    }

    public void onClientTick(ClientTickEvent.Post event) {
    }

    public void onKeyPress() {
    }

    public void onKeyRelease() {
    }
}