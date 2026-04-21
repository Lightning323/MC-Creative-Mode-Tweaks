package org.lightning323.creative_mode_tweaks.utils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import static org.lightning323.creative_mode_tweaks.CreativeModeTweaks.MODID;

public class ServerSettings {

	// UUIDs are out, ResourceLocations are in for 1.21.1
	private static final ResourceLocation REACH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "reach_modifier");

	public static void clearRangeModifier(Player player) {
		AttributeInstance blockReach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
		AttributeInstance entityReach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);

		if (blockReach != null) blockReach.removeModifier(REACH_MODIFIER_ID);
		if (entityReach != null) entityReach.removeModifier(REACH_MODIFIER_ID);
	}

	public static void changeRangeModifier(Player player, double dist) {
		// Clear existing first
		clearRangeModifier(player);

		AttributeInstance blockAttr = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
		AttributeInstance entityAttr = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);

		if (blockAttr != null) {
			// Formula: Desired Distance - Base Value (usually 4.5 or 5.0)
			double val = dist - blockAttr.getBaseValue();
			AttributeModifier modifier = new AttributeModifier(REACH_MODIFIER_ID, val, AttributeModifier.Operation.ADD_VALUE);
			blockAttr.addTransientModifier(modifier);
		}

		if (entityAttr != null) {
			double val = dist - entityAttr.getBaseValue();
			AttributeModifier modifier = new AttributeModifier(REACH_MODIFIER_ID, val, AttributeModifier.Operation.ADD_VALUE);
			entityAttr.addTransientModifier(modifier);
		}
	}
}