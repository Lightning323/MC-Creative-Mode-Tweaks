package nl.requios.effortlessbuilding.modifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.requios.effortlessbuilding.Constants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public class ModifierServerStorage {
   private static final Map<UUID, ModifierSystem> playerModifiers = new HashMap();

   public static ModifierSystem getModifiers(UUID playerId) {
      return (ModifierSystem)playerModifiers.computeIfAbsent(playerId, (k) -> new ModifierSystem());
   }

   public static void setModifiers(UUID playerId, List<IModifier> modifiers) {
      ModifierSystem system = (ModifierSystem)playerModifiers.computeIfAbsent(playerId, (k) -> new ModifierSystem());
      system.clearModifiers();

      for(IModifier m : modifiers) {
         system.addModifier(m);
      }

   }

   private static Path playerFile(MinecraftServer server, UUID playerId) {
      return server.getWorldPath(LevelResource.ROOT).resolve("creative_mode_tweaks").resolve("modifiers").resolve(String.valueOf(playerId) + ".json");
   }

   public static void loadPlayer(MinecraftServer server, UUID playerId) {
      Path file = playerFile(server, playerId);
      if (!Files.exists(file, new LinkOption[0])) {
         playerModifiers.computeIfAbsent(playerId, (k) -> new ModifierSystem());
      } else {
         try {
            String json = Files.readString(file);
            List<IModifier> modifiers = ModifierSerializer.deserialize(json);
            setModifiers(playerId, modifiers);
         } catch (Exception e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to load modifiers for {}: {}", playerId, e.getMessage());
            playerModifiers.computeIfAbsent(playerId, (k) -> new ModifierSystem());
         }

      }
   }

   public static void savePlayer(MinecraftServer server, UUID playerId) {
      ModifierSystem system = (ModifierSystem)playerModifiers.get(playerId);
      if (system != null) {
         String json = ModifierSerializer.serialize(system.getModifiers());
         Path file = playerFile(server, playerId);

         try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json);
         } catch (IOException e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to save modifiers for {}: {}", playerId, e.getMessage());
         }

      }
   }

   public static void removePlayer(UUID playerId) {
      playerModifiers.remove(playerId);
   }

   public static void clearAll() {
      playerModifiers.clear();
   }

   public static String serializePlayer(UUID playerId) {
      ModifierSystem system = (ModifierSystem)playerModifiers.get(playerId);
      return system == null ? "[]" : ModifierSerializer.serialize(system.getModifiers());
   }
}
