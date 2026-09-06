package nl.requios.effortlessbuilding;

import nl.requios.effortlessbuilding.screen.ClientConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class NeoForgeConfigScreenRegistrar {
   public static void register(ModContainer modContainer) {
      modContainer.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory)(container, parent) -> new ClientConfigScreen());
   }
}
