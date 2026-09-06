package nl.requios.effortlessbuilding.modifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.IBuildSystem;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.world.entity.player.Player;

public class ModifierSystem implements IBuildSystem {
   public static final ModifierSystem CLIENT = new ModifierSystem();
   private final List<IModifier> modifiers = new ArrayList();

   public List<IModifier> getModifiers() {
      return Collections.unmodifiableList(this.modifiers);
   }

   public void addModifier(IModifier modifier) {
      this.modifiers.add(modifier);
   }

   public void clearModifiers() {
      this.modifiers.clear();
   }

   public void removeModifier(int index) {
      if (index >= 0 && index < this.modifiers.size()) {
         this.modifiers.remove(index);
      }

   }

   public void moveModifier(int index, int direction) {
      int target = index + direction;
      if (index >= 0 && index < this.modifiers.size() && target >= 0 && target < this.modifiers.size()) {
         Collections.swap(this.modifiers, index, target);
      }
   }

   public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
      for(IModifier modifier : this.modifiers) {
         if (modifier.isEnabled() && modifier.matchesDimension(player)) {
            modifier.processBlocks(blocks, player, action);
         }
      }

   }
}
