package nl.requios.effortlessbuilding.config;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import nl.requios.effortlessbuilding.Constants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public class ServerConfigStorage {
   private static Path configFile(MinecraftServer server) {
      return server.getWorldPath(LevelResource.ROOT).resolve("creative_mode_tweaks").resolve("server_config.json");
   }

   public static void load(MinecraftServer server) {
      Path file = configFile(server);
      if (!Files.exists(file, new LinkOption[0])) {
         ServerConfig.INSTANCE.reset();
      } else {
         try {
            String json = Files.readString(file);
            ServerConfig loaded = ServerConfig.fromJson(json);
            ServerConfig.INSTANCE.copyFrom(loaded);
         } catch (Exception e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to load server config: {}", e.getMessage());
            ServerConfig.INSTANCE.reset();
         }

      }
   }

   public static void save(MinecraftServer server) {
      Path file = configFile(server);

      try {
         Files.createDirectories(file.getParent());
         Files.writeString(file, ServerConfig.INSTANCE.toJson());
      } catch (Exception e) {
         Constants.LOG.error("[EffortlessBuilding] Failed to save server config: {}", e.getMessage());
      }

   }

   public static void clear() {
      ServerConfig.INSTANCE.reset();
   }
}
