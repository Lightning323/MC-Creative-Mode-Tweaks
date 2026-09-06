package nl.requios.effortlessbuilding.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.requios.effortlessbuilding.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class WelcomeMessageStorage {
   private static final Gson GSON = new Gson();
   private static final Set<UUID> shownPlayers = new HashSet();

   private WelcomeMessageStorage() {
   }

   public static void load(MinecraftServer server) {
      shownPlayers.clear();
      Path file = messageFile(server);
      if (Files.exists(file, new LinkOption[0])) {
         try {
            JsonArray entries = (JsonArray)GSON.fromJson(Files.readString(file), JsonArray.class);
            if (entries == null) {
               return;
            }

            for(JsonElement entry : entries) {
               shownPlayers.add(UUID.fromString(entry.getAsString()));
            }
         } catch (Exception e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to load welcome messages: {}", e.getMessage());
            shownPlayers.clear();
         }

      }
   }

   public static void showIfNeeded(ServerPlayer player) {
      if (ServerConfig.INSTANCE.showWelcomeMessage && shownPlayers.add(player.getUUID())) {
         save(player.server);
         player.sendSystemMessage(Component.translatable("creative_mode_tweaks.message.welcome_message", new Object[]{Component.keybind("key.creative_mode_tweaks.open_radial_menu").withStyle(ChatFormatting.BLUE)}));
      }
   }

   public static void clear() {
      shownPlayers.clear();
   }

   private static Path messageFile(MinecraftServer server) {
      return server.getWorldPath(LevelResource.ROOT).resolve("creative_mode_tweaks").resolve("welcome_messages.json");
   }

   private static void save(MinecraftServer server) {
      Path file = messageFile(server);

      try {
         Files.createDirectories(file.getParent());
         JsonArray entries = new JsonArray();

         for(UUID playerId : shownPlayers) {
            entries.add(playerId.toString());
         }

         Files.writeString(file, GSON.toJson(entries));
      } catch (Exception e) {
         Constants.LOG.error("[EffortlessBuilding] Failed to save welcome messages: {}", e.getMessage());
      }

   }
}
