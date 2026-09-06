package nl.requios.effortlessbuilding.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import nl.requios.effortlessbuilding.Constants;

public class ClientConfig {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   public static final ClientConfig INSTANCE = new ClientConfig();
   public static final float DEFAULT_PREVIEW_BLOCK_SIZE = 0.5F;
   public static final float DEFAULT_PREVIEW_BLOCK_TRANSPARENCY = 0.8F;
   public static final boolean DEFAULT_PROTECT_TILE_ENTITIES = true;
   public static final int DEFAULT_MAX_BLOCK_PREVIEWS = 1000;
   public static final float MIN_PREVIEW_BLOCK_SIZE = 0.1F;
   public static final float MAX_PREVIEW_BLOCK_SIZE = 1.0F;
   public static final float MIN_PREVIEW_BLOCK_TRANSPARENCY = 0.0F;
   public static final float MAX_PREVIEW_BLOCK_TRANSPARENCY = 1.0F;
   public static final int MIN_MAX_BLOCK_PREVIEWS = 50;
   public static final int MAX_MAX_BLOCK_PREVIEWS = 10000;
   private float previewBlockSize = 0.5F;
   private float previewBlockTransparency = 0.8F;
   private boolean protectTileEntities = true;
   private int maxBlockPreviews = 1000;

   public float getPreviewBlockSize() {
      return this.previewBlockSize;
   }

   public float getPreviewBlockTransparency() {
      return this.previewBlockTransparency;
   }

   public boolean shouldProtectTileEntities() {
      return this.protectTileEntities;
   }

   public int getMaxBlockPreviews() {
      return this.maxBlockPreviews;
   }

   public void setPreviewBlockSize(float value) {
      this.previewBlockSize = Math.clamp(value, 0.1F, 1.0F);
   }

   public void setPreviewBlockTransparency(float value) {
      this.previewBlockTransparency = Math.clamp(value, 0.0F, 1.0F);
   }

   public void setProtectTileEntities(boolean value) {
      this.protectTileEntities = value;
   }

   public void setMaxBlockPreviews(int value) {
      this.maxBlockPreviews = Math.clamp((long)value, 50, 10000);
   }

   public String toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("previewBlockSize", this.previewBlockSize);
      obj.addProperty("previewBlockTransparency", this.previewBlockTransparency);
      obj.addProperty("protectTileEntities", this.protectTileEntities);
      obj.addProperty("maxBlockPreviews", this.maxBlockPreviews);
      return GSON.toJson(obj);
   }

   public void loadFromJson(String json) {
      try {
         JsonObject obj = (JsonObject)GSON.fromJson(json, JsonObject.class);
         if (obj.has("previewBlockSize")) {
            this.setPreviewBlockSize(obj.get("previewBlockSize").getAsFloat());
         }

         if (obj.has("previewBlockTransparency")) {
            this.setPreviewBlockTransparency(obj.get("previewBlockTransparency").getAsFloat());
         }

         if (obj.has("protectTileEntities")) {
            this.setProtectTileEntities(obj.get("protectTileEntities").getAsBoolean());
         }

         if (obj.has("maxBlockPreviews")) {
            this.setMaxBlockPreviews(obj.get("maxBlockPreviews").getAsInt());
         }
      } catch (Exception var3) {
      }

   }

   private static Path configPath() {
      return Path.of("config", "creative_mode_tweaks-client.json");
   }

   public void load() {
      Path path = configPath();
      if (Files.exists(path, new LinkOption[0])) {
         try {
            String json = Files.readString(path);
            this.loadFromJson(json);
         } catch (IOException e) {
            Constants.LOG.warn("Failed to load client config: {}", e.getMessage());
         }
      }

   }

   public void save() {
      Path path = configPath();

      try {
         Files.createDirectories(path.getParent());
         Files.writeString(path, this.toJson());
      } catch (IOException e) {
         Constants.LOG.warn("Failed to save client config: {}", e.getMessage());
      }

   }
}
