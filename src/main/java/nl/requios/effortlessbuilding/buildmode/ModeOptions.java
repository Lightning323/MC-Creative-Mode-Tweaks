package nl.requios.effortlessbuilding.buildmode;

import nl.requios.effortlessbuilding.AllIcons;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.lightning323.creative_mode_tweaks.Config;

public class ModeOptions {
   private static ActionEnum buildSpeed;
   private static ActionEnum fill;
   private static ActionEnum cubeFill;
   private static ActionEnum raisedEdge;
   private static ActionEnum lineThickness;
   private static ActionEnum circleStart;
   private static ActionEnum pointBuild;
   private static ActionEnum meshFace;
   private static ActionEnum sides;
   private static ActionEnum planeAlign;

   public static ActionEnum getBuildSpeed() {
      return buildSpeed;
   }

   public static ActionEnum getFill() {
      return fill;
   }

   public static ActionEnum getCubeFill() {
      return cubeFill;
   }

   public static ActionEnum getRaisedEdge() {
      return raisedEdge;
   }

   public static ActionEnum getLineThickness() {
      return lineThickness;
   }

   public static ActionEnum getCircleStart() {
      return circleStart;
   }

   public static ActionEnum getPointBuild() {
      return pointBuild;
   }

   public static boolean isTwoPointBuild() {
      return pointBuild == ActionEnum.TWO_POINT_BUILD;
   }

   /**
    * Restores the default point-build option (2-point). Called on world enter
    * so selections always start from 2-point unless the player opts into
    * 3-point via the radial menu.
    */
   public static void resetPointBuildToDefault() {
      pointBuild = ActionEnum.TWO_POINT_BUILD;
   }

   public static ActionEnum getMeshFace() {
      return meshFace;
   }

   public static ActionEnum getSides() {
      return sides;
   }

   public static ActionEnum getPlaneAlign() {
      return planeAlign;
   }

   public static boolean isPlaneAlignAuto() {
      return planeAlign == ActionEnum.ALIGN_AUTO;
   }

   /**
    * Restores the default plane align option (auto). Called on world enter
    * so the plane tool always starts from auto unless the player opts into
    * a forced horizontal/vertical alignment via the radial menu.
    */
   public static void resetPlaneAlignToDefault() {
      planeAlign = ActionEnum.ALIGN_AUTO;
   }

   public static void applyForCalculation(ActionEnum fill, ActionEnum cubeFill, ActionEnum raisedEdge, ActionEnum circleStart, ActionEnum pointBuild, ActionEnum sides, ActionEnum planeAlign) {
      ModeOptions.fill = fill;
      ModeOptions.cubeFill = cubeFill;
      ModeOptions.raisedEdge = raisedEdge;
      ModeOptions.circleStart = circleStart;
      ModeOptions.pointBuild = pointBuild;
      ModeOptions.sides = sides;
      ModeOptions.planeAlign = planeAlign;
   }

   public static void performAction(Player player, ActionEnum action) {
      if (action != null) {
         switch (action.ordinal()) {
            case 0:
               PacketHandler.sendToServer(new UndoPacket());
               break;
            case 1:
               PacketHandler.sendToServer(new RedoPacket());
            case 2:
            case 3:
            case 4:
            case 6:
            case 7:
            case 8:
            case 9:
            default:
               break;
            case 5:
               BuildSettings.CLIENT.cycleReplaceMode();
               break;
            case 10:
               buildSpeed = ActionEnum.NORMAL_SPEED;
               break;
            case 11:
               buildSpeed = ActionEnum.FAST_SPEED;
               break;
            case 12:
               fill = ActionEnum.FULL;
               break;
            case 13:
               fill = ActionEnum.HOLLOW;
               break;
            case 14:
               cubeFill = ActionEnum.CUBE_FULL;
               break;
            case 15:
               cubeFill = ActionEnum.CUBE_HOLLOW;
               break;
            case 16:
               cubeFill = ActionEnum.CUBE_SKELETON;
               break;
            case 17:
               raisedEdge = ActionEnum.SHORT_EDGE;
               break;
            case 18:
               raisedEdge = ActionEnum.LONG_EDGE;
               break;
            case 19:
               lineThickness = ActionEnum.THICKNESS_1;
               break;
            case 20:
               lineThickness = ActionEnum.THICKNESS_3;
               break;
            case 21:
               lineThickness = ActionEnum.THICKNESS_5;
               break;
            case 22:
               circleStart = ActionEnum.CIRCLE_START_CORNER;
               break;
            case 23:
               circleStart = ActionEnum.CIRCLE_START_CENTER;
               break;
            case 24:
               if (Config.isAngelPlacementAllowed(player)) {
                  BuildSettings.CLIENT.toggleAngelPlacement();
               }
               break;
            case 25:
               pointBuild = ActionEnum.TWO_POINT_BUILD;
               break;
            case 26:
               pointBuild = ActionEnum.THREE_POINT_BUILD;
               break;
            case 27:
               meshFace = ActionEnum.MESH_TRIANGLE;
               break;
            case 28:
               meshFace = ActionEnum.MESH_QUAD;
               break;
            case 29:
               sides = ActionEnum.THREE_SIDED;
               break;
            case 30:
               sides = ActionEnum.FOUR_SIDED;
               break;
            case 33:
               planeAlign = ActionEnum.ALIGN_AUTO;
               break;
            case 34:
               planeAlign = ActionEnum.ALIGN_HORIZONTAL;
               break;
            case 35:
               planeAlign = ActionEnum.ALIGN_VERTICAL;
               break;
         }

         if (player.level().isClientSide && action != ActionEnum.OPEN_MODIFIER_SETTINGS && action != ActionEnum.PREVIOUS_BUILD_MODE && action != ActionEnum.DISABLE_BUILD_MODE_TOGGLE && action != ActionEnum.UNDO && action != ActionEnum.REDO) {
            if (action == ActionEnum.TOGGLE_ANGEL_PLACEMENT) {
               if (Config.isAngelPlacementAllowed(player)) {
                  player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.angel_placement", Component.translatable(BuildSettings.CLIENT.isAngelPlacementEnabled() ? "options.on" : "options.off")), true);
               } else {
                  player.displayClientMessage(Component.translatable("creative_mode_tweaks.message.angel_placement_disabled"), true);
               }
            } else {
               player.displayClientMessage(Component.translatable(action.getNameKey()), true);
            }
         }

      }
   }

   static {
      buildSpeed = ActionEnum.NORMAL_SPEED;
      fill = ActionEnum.FULL;
      cubeFill = ActionEnum.CUBE_FULL;
      raisedEdge = ActionEnum.SHORT_EDGE;
      lineThickness = ActionEnum.THICKNESS_1;
      circleStart = ActionEnum.CIRCLE_START_CORNER;
      pointBuild = ActionEnum.TWO_POINT_BUILD;
      meshFace = ActionEnum.MESH_TRIANGLE;
      sides = ActionEnum.FOUR_SIDED;
      planeAlign = ActionEnum.ALIGN_AUTO;
   }

   public static enum ActionEnum {
      UNDO("undo", AllIcons.I_UNDO),
      REDO("redo", AllIcons.I_REDO),
      OPEN_MODIFIER_SETTINGS("open_modifier_settings", AllIcons.I_MODIFIERS),
      PREVIOUS_BUILD_MODE("previous_build_mode", AllIcons.I_SINGLE),
      DISABLE_BUILD_MODE_TOGGLE("disable_build_mode_toggle", AllIcons.I_DISABLE),
      CYCLE_REPLACE_MODE("cycle_replace_mode", AllIcons.I_REPLACE),
      REPLACE_ONLY_AIR("replace_only_air", AllIcons.I_REPLACE_AIR),
      REPLACE_BLOCKS_AND_AIR("replace_blocks_and_air", AllIcons.I_REPLACE_BLOCKS_AND_AIR),
      REPLACE_ONLY_BLOCKS("replace_only_blocks", AllIcons.I_REPLACE_BLOCKS),
      REPLACE_FILTERED_BY_OFFHAND("replace_filtered_by_offhand", AllIcons.I_REPLACE_OFFHAND_FILTERED),
      NORMAL_SPEED("normal_speed", AllIcons.I_NORMAL_SPEED),
      FAST_SPEED("fast_speed", AllIcons.I_FAST_SPEED),
      FULL("full", AllIcons.I_FILLED),
      HOLLOW("hollow", AllIcons.I_HOLLOW),
      CUBE_FULL("full", AllIcons.I_CUBE_FILLED),
      CUBE_HOLLOW("hollow", AllIcons.I_CUBE_HOLLOW),
      CUBE_SKELETON("skeleton", AllIcons.I_CUBE_SKELETON),
      SHORT_EDGE("short_edge", AllIcons.I_SHORT_EDGE),
      LONG_EDGE("long_edge", AllIcons.I_LONG_EDGE),
      THICKNESS_1("thickness_1", AllIcons.I_THICKNESS_1),
      THICKNESS_3("thickness_3", AllIcons.I_THICKNESS_3),
      THICKNESS_5("thickness_5", AllIcons.I_THICKNESS_5),
      CIRCLE_START_CORNER("start_corner", AllIcons.I_CIRCLE_START_CORNER),
      CIRCLE_START_CENTER("start_center", AllIcons.I_CIRCLE_START_CENTER),
      TOGGLE_ANGEL_PLACEMENT("toggle_angel_placement", AllIcons.ANGEL_PLACEMENT_ON),
      TWO_POINT_BUILD("two_point", AllIcons.I_TWO_POINT),
      THREE_POINT_BUILD("three_point", AllIcons.I_THREE_POINT),
      MESH_TRIANGLE("mesh_triangle", AllIcons.I_MESH_TRIANGLE),
      MESH_QUAD("mesh_quad", AllIcons.I_MESH_QUAD),
      THREE_SIDED("three_sided", AllIcons.I_MESH_TRIANGLE),
      FOUR_SIDED("four_sided", AllIcons.I_MESH_QUAD),
      TOGGLE_NIGHT_VISION("toggle_night_vision", AllIcons.I_EYE_ON),
      TOGGLE_NOCLIP("toggle_noclip", AllIcons.I_PLAYER),
      ALIGN_AUTO("align_auto", AllIcons.I_ALIGN_AUTO),
      ALIGN_HORIZONTAL("align_horizontal", AllIcons.I_ALIGN_HORIZONTAL),
      ALIGN_VERTICAL("align_vertical", AllIcons.I_ALIGN_VERTICAL);

      public String name;
      public AllIcons icon;

      private ActionEnum(String name, AllIcons icon) {
         this.name = name;
         this.icon = icon;
      }

      public String getName() {
         return this.name;
      }

      public String getNameKey() {
         return "creative_mode_tweaks.action." + this.name;
      }

      public String getDescriptionKey() {
         return "creative_mode_tweaks.action." + this.name + ".description";
      }

      // $FF: synthetic method
      private static ActionEnum[] $values() {
         return new ActionEnum[]{UNDO, REDO, OPEN_MODIFIER_SETTINGS, PREVIOUS_BUILD_MODE, DISABLE_BUILD_MODE_TOGGLE, CYCLE_REPLACE_MODE, REPLACE_ONLY_AIR, REPLACE_BLOCKS_AND_AIR, REPLACE_ONLY_BLOCKS, REPLACE_FILTERED_BY_OFFHAND, NORMAL_SPEED, FAST_SPEED, FULL, HOLLOW, CUBE_FULL, CUBE_HOLLOW, CUBE_SKELETON, SHORT_EDGE, LONG_EDGE, THICKNESS_1, THICKNESS_3, THICKNESS_5, CIRCLE_START_CORNER, CIRCLE_START_CENTER, TOGGLE_ANGEL_PLACEMENT, TWO_POINT_BUILD, THREE_POINT_BUILD, MESH_TRIANGLE, MESH_QUAD, THREE_SIDED, FOUR_SIDED, TOGGLE_NIGHT_VISION, TOGGLE_NOCLIP, ALIGN_AUTO, ALIGN_HORIZONTAL, ALIGN_VERTICAL};
      }
   }

   public static enum OptionEnum {
      BUILD_SPEED("creative_mode_tweaks.action.build_speed", new ActionEnum[]{ActionEnum.NORMAL_SPEED, ActionEnum.FAST_SPEED}),
      FILL("creative_mode_tweaks.action.filling", new ActionEnum[]{ActionEnum.FULL, ActionEnum.HOLLOW}),
      CUBE_FILL("creative_mode_tweaks.action.filling", new ActionEnum[]{ActionEnum.CUBE_FULL, ActionEnum.CUBE_HOLLOW, ActionEnum.CUBE_SKELETON}),
      RAISED_EDGE("creative_mode_tweaks.action.raised_edge", new ActionEnum[]{ActionEnum.SHORT_EDGE, ActionEnum.LONG_EDGE}),
      LINE_THICKNESS("creative_mode_tweaks.action.thickness", new ActionEnum[]{ActionEnum.THICKNESS_1, ActionEnum.THICKNESS_3, ActionEnum.THICKNESS_5}),
      CIRCLE_START("creative_mode_tweaks.action.circle_start", new ActionEnum[]{ActionEnum.CIRCLE_START_CORNER, ActionEnum.CIRCLE_START_CENTER}),
      POINT_BUILD("creative_mode_tweaks.action.point_build", new ActionEnum[]{ActionEnum.TWO_POINT_BUILD, ActionEnum.THREE_POINT_BUILD}),
      MESH_FACE("creative_mode_tweaks.action.mesh_face", new ActionEnum[]{ActionEnum.MESH_TRIANGLE, ActionEnum.MESH_QUAD}),
      SIDES("creative_mode_tweaks.action.sides", new ActionEnum[]{ActionEnum.THREE_SIDED, ActionEnum.FOUR_SIDED}),
      PLANE_ALIGN("creative_mode_tweaks.action.align_mode", new ActionEnum[]{ActionEnum.ALIGN_AUTO, ActionEnum.ALIGN_HORIZONTAL, ActionEnum.ALIGN_VERTICAL});

      public String name;
      public ActionEnum[] actions;

      private OptionEnum(String name, ActionEnum... actions) {
         this.name = name;
         this.actions = actions;
      }

      // $FF: synthetic method
      private static OptionEnum[] $values() {
         return new OptionEnum[]{BUILD_SPEED, FILL, CUBE_FILL, RAISED_EDGE, LINE_THICKNESS, CIRCLE_START, POINT_BUILD, MESH_FACE, SIDES, PLANE_ALIGN};
      }
   }
}
