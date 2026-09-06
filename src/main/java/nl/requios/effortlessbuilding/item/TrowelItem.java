package nl.requios.effortlessbuilding.item;

import net.minecraft.world.item.Item;

/**
 * A placement tool whose block palette is the player's hotbar.
 * Placement is handled by {@code TrowelSystem}, so this item intentionally has no use action or UI.
 */
public final class TrowelItem extends Item {
    public TrowelItem(Properties properties) {
        super(properties);
    }
}
