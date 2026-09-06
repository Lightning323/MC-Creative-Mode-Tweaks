package nl.requios.effortlessbuilding.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.player.Player;

public class ServerConfig {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   public static final ServerConfig INSTANCE = new ServerConfig();
   public int survivalReach = 32;
   public int survivalMaxBlocksPlaced = 2000;
   public int survivalMaxBlocksPerAxis = 64;
   public int survivalMaxMirrorSize = 256;
   public int survivalMaxArrayCount = 64;
   public int survivalMaxArrayOffset = 64;
   public boolean survivalAllowBreaking = true;
   public boolean survivalOnlyPlacedBlocks = true;
   public float survivalMaxHardness = -1.0F;
   public boolean survivalRequireTools = false;
   public boolean survivalUseDurability = false;
   public boolean showWelcomeMessage = true;
   public boolean showBuildModeHint = true;
   public int creativeReach = 200;
   public int creativeMaxBlocksPlaced = 50000;
   public int creativeMaxBlocksPerAxis = 1000;
   public int creativeMaxMirrorSize = 256;
   public int creativeMaxArrayCount = 256;
   public int creativeMaxArrayOffset = 256;

   public int getReach(Player player) {
      return player.isCreative() ? this.creativeReach : this.survivalReach;
   }

   public int getMaxBlocksPlaced(Player player) {
      return player.isCreative() ? this.creativeMaxBlocksPlaced : this.survivalMaxBlocksPlaced;
   }

   public int getMaxBlocksPerAxis(Player player) {
      return player.isCreative() ? this.creativeMaxBlocksPerAxis : this.survivalMaxBlocksPerAxis;
   }

   public int getMaxMirrorSize(Player player) {
      return player.isCreative() ? this.creativeMaxMirrorSize : this.survivalMaxMirrorSize;
   }

   public int getMaxArrayCount(Player player) {
      return player.isCreative() ? this.creativeMaxArrayCount : this.survivalMaxArrayCount;
   }

   public int getMaxArrayOffset(Player player) {
      return player.isCreative() ? this.creativeMaxArrayOffset : this.survivalMaxArrayOffset;
   }

   public void clampAll() {
      this.survivalReach = Math.clamp((long)this.survivalReach, 1, 1000);
      this.survivalMaxBlocksPlaced = Math.clamp((long)this.survivalMaxBlocksPlaced, 1, 100000);
      this.survivalMaxBlocksPerAxis = Math.clamp((long)this.survivalMaxBlocksPerAxis, 1, 1000);
      this.survivalMaxMirrorSize = Math.clamp((long)this.survivalMaxMirrorSize, 1, 1000);
      this.survivalMaxArrayCount = Math.clamp((long)this.survivalMaxArrayCount, 1, 1000);
      this.survivalMaxArrayOffset = Math.clamp((long)this.survivalMaxArrayOffset, 1, 1000);
      this.creativeReach = Math.clamp((long)this.creativeReach, 1, 1000);
      this.creativeMaxBlocksPlaced = Math.clamp((long)this.creativeMaxBlocksPlaced, 1, 100000);
      this.creativeMaxBlocksPerAxis = Math.clamp((long)this.creativeMaxBlocksPerAxis, 1, 1000);
      this.creativeMaxMirrorSize = Math.clamp((long)this.creativeMaxMirrorSize, 1, 1000);
      this.creativeMaxArrayCount = Math.clamp((long)this.creativeMaxArrayCount, 1, 1000);
      this.creativeMaxArrayOffset = Math.clamp((long)this.creativeMaxArrayOffset, 1, 1000);
   }

   public void copyFrom(ServerConfig other) {
      this.survivalReach = other.survivalReach;
      this.survivalMaxBlocksPlaced = other.survivalMaxBlocksPlaced;
      this.survivalMaxBlocksPerAxis = other.survivalMaxBlocksPerAxis;
      this.survivalMaxMirrorSize = other.survivalMaxMirrorSize;
      this.survivalMaxArrayCount = other.survivalMaxArrayCount;
      this.survivalMaxArrayOffset = other.survivalMaxArrayOffset;
      this.survivalAllowBreaking = other.survivalAllowBreaking;
      this.survivalOnlyPlacedBlocks = other.survivalOnlyPlacedBlocks;
      this.survivalMaxHardness = other.survivalMaxHardness;
      this.survivalRequireTools = other.survivalRequireTools;
      this.survivalUseDurability = other.survivalUseDurability;
      this.showWelcomeMessage = other.showWelcomeMessage;
      this.showBuildModeHint = other.showBuildModeHint;
      this.creativeReach = other.creativeReach;
      this.creativeMaxBlocksPlaced = other.creativeMaxBlocksPlaced;
      this.creativeMaxBlocksPerAxis = other.creativeMaxBlocksPerAxis;
      this.creativeMaxMirrorSize = other.creativeMaxMirrorSize;
      this.creativeMaxArrayCount = other.creativeMaxArrayCount;
      this.creativeMaxArrayOffset = other.creativeMaxArrayOffset;
   }

   public void reset() {
      this.copyFrom(new ServerConfig());
   }

   public String toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("survivalReach", this.survivalReach);
      obj.addProperty("survivalMaxBlocksPlaced", this.survivalMaxBlocksPlaced);
      obj.addProperty("survivalMaxBlocksPerAxis", this.survivalMaxBlocksPerAxis);
      obj.addProperty("survivalMaxMirrorSize", this.survivalMaxMirrorSize);
      obj.addProperty("survivalMaxArrayCount", this.survivalMaxArrayCount);
      obj.addProperty("survivalMaxArrayOffset", this.survivalMaxArrayOffset);
      obj.addProperty("survivalAllowBreaking", this.survivalAllowBreaking);
      obj.addProperty("survivalOnlyPlacedBlocks", this.survivalOnlyPlacedBlocks);
      obj.addProperty("survivalMaxHardness", this.survivalMaxHardness);
      obj.addProperty("survivalRequireTools", this.survivalRequireTools);
      obj.addProperty("survivalUseDurability", this.survivalUseDurability);
      obj.addProperty("showWelcomeMessage", this.showWelcomeMessage);
      obj.addProperty("showBuildModeHint", this.showBuildModeHint);
      obj.addProperty("creativeReach", this.creativeReach);
      obj.addProperty("creativeMaxBlocksPlaced", this.creativeMaxBlocksPlaced);
      obj.addProperty("creativeMaxBlocksPerAxis", this.creativeMaxBlocksPerAxis);
      obj.addProperty("creativeMaxMirrorSize", this.creativeMaxMirrorSize);
      obj.addProperty("creativeMaxArrayCount", this.creativeMaxArrayCount);
      obj.addProperty("creativeMaxArrayOffset", this.creativeMaxArrayOffset);
      return GSON.toJson(obj);
   }

   public static ServerConfig fromJson(String json) {
      ServerConfig config = new ServerConfig();

      try {
         JsonObject obj = (JsonObject)GSON.fromJson(json, JsonObject.class);
         if (obj.has("survivalReach")) {
            config.survivalReach = obj.get("survivalReach").getAsInt();
         }

         if (obj.has("survivalMaxBlocksPlaced")) {
            config.survivalMaxBlocksPlaced = obj.get("survivalMaxBlocksPlaced").getAsInt();
         }

         if (obj.has("survivalMaxBlocksPerAxis")) {
            config.survivalMaxBlocksPerAxis = obj.get("survivalMaxBlocksPerAxis").getAsInt();
         }

         if (obj.has("survivalMaxMirrorSize")) {
            config.survivalMaxMirrorSize = obj.get("survivalMaxMirrorSize").getAsInt();
         }

         if (obj.has("survivalMaxArrayCount")) {
            config.survivalMaxArrayCount = obj.get("survivalMaxArrayCount").getAsInt();
         }

         if (obj.has("survivalMaxArrayOffset")) {
            config.survivalMaxArrayOffset = obj.get("survivalMaxArrayOffset").getAsInt();
         }

         if (obj.has("survivalAllowBreaking")) {
            config.survivalAllowBreaking = obj.get("survivalAllowBreaking").getAsBoolean();
         }

         if (obj.has("survivalOnlyPlacedBlocks")) {
            config.survivalOnlyPlacedBlocks = obj.get("survivalOnlyPlacedBlocks").getAsBoolean();
         }

         if (obj.has("survivalMaxHardness")) {
            config.survivalMaxHardness = obj.get("survivalMaxHardness").getAsFloat();
         }

         if (obj.has("survivalRequireTools")) {
            config.survivalRequireTools = obj.get("survivalRequireTools").getAsBoolean();
         }

         if (obj.has("survivalUseDurability")) {
            config.survivalUseDurability = obj.get("survivalUseDurability").getAsBoolean();
         }

         if (obj.has("showWelcomeMessage")) {
            config.showWelcomeMessage = obj.get("showWelcomeMessage").getAsBoolean();
         }

         if (obj.has("showBuildModeHint")) {
            config.showBuildModeHint = obj.get("showBuildModeHint").getAsBoolean();
         }

         if (obj.has("creativeReach")) {
            config.creativeReach = obj.get("creativeReach").getAsInt();
         }

         if (obj.has("creativeMaxBlocksPlaced")) {
            config.creativeMaxBlocksPlaced = obj.get("creativeMaxBlocksPlaced").getAsInt();
         }

         if (obj.has("creativeMaxBlocksPerAxis")) {
            config.creativeMaxBlocksPerAxis = obj.get("creativeMaxBlocksPerAxis").getAsInt();
         }

         if (obj.has("creativeMaxMirrorSize")) {
            config.creativeMaxMirrorSize = obj.get("creativeMaxMirrorSize").getAsInt();
         }

         if (obj.has("creativeMaxArrayCount")) {
            config.creativeMaxArrayCount = obj.get("creativeMaxArrayCount").getAsInt();
         }

         if (obj.has("creativeMaxArrayOffset")) {
            config.creativeMaxArrayOffset = obj.get("creativeMaxArrayOffset").getAsInt();
         }
      } catch (Exception var3) {
      }

      config.clampAll();
      return config;
   }
}
