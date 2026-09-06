package nl.requios.effortlessbuilding.modifier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

public class ModifierSerializer {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();

   public static String serialize(List<IModifier> modifiers) {
      JsonArray array = new JsonArray();

      for(IModifier modifier : modifiers) {
         JsonObject obj = new JsonObject();
         obj.addProperty("enabled", modifier.isEnabled());
         obj.addProperty("dimension", modifier.getDimension());
         serializeModifier(modifier, obj);
         array.add(obj);
      }

      return GSON.toJson(array);
   }

   private static void serializeModifier(IModifier modifier, JsonObject obj) {
      if (modifier instanceof MirrorModifier mirror) {
         obj.addProperty("type", "mirror");
         obj.addProperty("mirrorX", mirror.mirrorX);
         obj.addProperty("mirrorY", mirror.mirrorY);
         obj.addProperty("mirrorZ", mirror.mirrorZ);
         obj.addProperty("originX", mirror.originX);
         obj.addProperty("originY", mirror.originY);
         obj.addProperty("originZ", mirror.originZ);
         obj.addProperty("size", mirror.size);
      } else if (modifier instanceof ArrayModifier array) {
         obj.addProperty("type", "array");
         obj.addProperty("count", array.count);
         obj.addProperty("offsetX", array.offsetX);
         obj.addProperty("offsetY", array.offsetY);
         obj.addProperty("offsetZ", array.offsetZ);
      } else if (modifier instanceof RadialMirrorModifier radial) {
         obj.addProperty("type", "radial_mirror");
         obj.addProperty("slices", radial.slices);
         obj.addProperty("mirrorSlices", radial.mirrorSlices);
         obj.addProperty("originX", radial.originX);
         obj.addProperty("originY", radial.originY);
         obj.addProperty("originZ", radial.originZ);
         obj.addProperty("size", radial.size);
      }

   }

   public static List<IModifier> deserialize(String json) {
      List<IModifier> result = new ArrayList();

      try {
         for(JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject obj = element.getAsJsonObject();
            IModifier modifier = deserializeModifier(obj);
            if (modifier != null) {
               modifier.setEnabled(getBoolean(obj, "enabled", true));
               modifier.setDimension(getString(obj, "dimension", ""));
               result.add(modifier);
            }
         }
      } catch (Exception var7) {
      }

      return result;
   }

   private static IModifier deserializeModifier(JsonObject obj) {
      if (!obj.has("type")) {
         return null;
      } else {
         Object var10000;
         switch (obj.get("type").getAsString()) {
            case "mirror":
               MirrorModifier mirror = new MirrorModifier();
               mirror.mirrorX = getBoolean(obj, "mirrorX", mirror.mirrorX);
               mirror.mirrorY = getBoolean(obj, "mirrorY", mirror.mirrorY);
               mirror.mirrorZ = getBoolean(obj, "mirrorZ", mirror.mirrorZ);
               mirror.originX = getDouble(obj, "originX", mirror.originX);
               mirror.originY = getDouble(obj, "originY", mirror.originY);
               mirror.originZ = getDouble(obj, "originZ", mirror.originZ);
               mirror.size = getInt(obj, "size", mirror.size);
               if (!obj.has("size") && obj.has("radius")) {
                  mirror.size = getInt(obj, "radius", mirror.size) * 2;
               }

               var10000 = mirror;
               break;
            case "array":
               ArrayModifier array = new ArrayModifier();
               array.count = getInt(obj, "count", array.count);
               array.offsetX = getInt(obj, "offsetX", array.offsetX);
               array.offsetY = getInt(obj, "offsetY", array.offsetY);
               array.offsetZ = getInt(obj, "offsetZ", array.offsetZ);
               var10000 = array;
               break;
            case "radial_mirror":
               RadialMirrorModifier radial = new RadialMirrorModifier();
               radial.slices = getInt(obj, "slices", radial.slices);
               radial.mirrorSlices = getBoolean(obj, "mirrorSlices", radial.mirrorSlices);
               radial.originX = getDouble(obj, "originX", radial.originX);
               radial.originY = getDouble(obj, "originY", radial.originY);
               radial.originZ = getDouble(obj, "originZ", radial.originZ);
               radial.size = getInt(obj, "size", radial.size);
               if (!obj.has("size") && obj.has("radius")) {
                  radial.size = getInt(obj, "radius", radial.size) * 2;
               }

               var10000 = radial;
               break;
            default:
               var10000 = null;
         }

         return (IModifier)var10000;
      }
   }

   private static int getInt(JsonObject obj, String key, int fallback) {
      return obj.has(key) ? obj.get(key).getAsInt() : fallback;
   }

   private static double getDouble(JsonObject obj, String key, double fallback) {
      return obj.has(key) ? obj.get(key).getAsDouble() : fallback;
   }

   private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
      return obj.has(key) ? obj.get(key).getAsBoolean() : fallback;
   }

   private static String getString(JsonObject obj, String key, String fallback) {
      return obj.has(key) ? obj.get(key).getAsString() : fallback;
   }
}
