package nl.requios.effortlessbuilding.item;

import java.util.List;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record RandomizerTooltipData(List<ItemStack> stacks) implements TooltipComponent {
   public RandomizerTooltipData(List<ItemStack> stacks) {
      stacks = stacks.stream().map(ItemStack::copy).toList();
      this.stacks = stacks;
   }
}
