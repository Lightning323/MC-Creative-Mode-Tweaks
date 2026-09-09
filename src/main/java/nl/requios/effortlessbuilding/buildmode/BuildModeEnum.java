package nl.requios.effortlessbuilding.buildmode;

import nl.requios.effortlessbuilding.AllIcons;
import nl.requios.effortlessbuilding.buildmode.buildmodes.*;

public enum BuildModeEnum {
   DISABLED("normal", new Single(), BuildModeCategoryEnum.DEFAULT, AllIcons.I_SINGLE, new ModeOptions.OptionEnum[0]),
   LINE("line", new Line(), BuildModeCategoryEnum.BASIC, AllIcons.I_LINE, new ModeOptions.OptionEnum[0]),
   PLANE("plane", new Plane(), BuildModeCategoryEnum.BASIC, AllIcons.I_FLOOR, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.FILL}),
   WALL("wall", new Wall(), BuildModeCategoryEnum.BASIC, AllIcons.I_WALL, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.FILL}),
   FLOOR("floor", new Floor(), BuildModeCategoryEnum.BASIC, AllIcons.I_FLOOR, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.FILL}),
   CUBE("cube", new Cube(), BuildModeCategoryEnum.BASIC, AllIcons.I_CUBE, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.CUBE_FILL, ModeOptions.OptionEnum.POINT_BUILD}),

   DIAGONAL_LINE("diagonal_line", new DiagonalLine(), BuildModeCategoryEnum.DIAGONAL, AllIcons.I_DIAGONAL_LINE, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.POINT_BUILD}),
   DIAGONAL_WALL("diagonal_wall", new DiagonalWall(), BuildModeCategoryEnum.DIAGONAL, AllIcons.I_DIAGONAL_WALL, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.POINT_BUILD}),
   SLOPE_FLOOR("slope_floor", new SlopeFloor(), BuildModeCategoryEnum.DIAGONAL, AllIcons.I_SLOPED_FLOOR, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.RAISED_EDGE, ModeOptions.OptionEnum.POINT_BUILD}),
   CIRCLE("circle", new Circle(), BuildModeCategoryEnum.CIRCULAR, AllIcons.I_CIRCLE, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.CIRCLE_START, ModeOptions.OptionEnum.FILL}),
   CYLINDER("cylinder", new Cylinder(), BuildModeCategoryEnum.CIRCULAR, AllIcons.I_CYLINDER, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.CIRCLE_START, ModeOptions.OptionEnum.FILL}),
   SPHERE("sphere", new Sphere(), BuildModeCategoryEnum.CIRCULAR, AllIcons.I_SPHERE, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.CIRCLE_START, ModeOptions.OptionEnum.POINT_BUILD, ModeOptions.OptionEnum.FILL}),
   DOME("dome", new Dome(), BuildModeCategoryEnum.CIRCULAR, AllIcons.I_DOME, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.CIRCLE_START, ModeOptions.OptionEnum.FILL}),

   MESH("mesh", new Mesh(), BuildModeCategoryEnum.COMPLEX, AllIcons.I_MESH, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.MESH_FACE}),
   PYRAMID("pyramid", new Pyramid(), BuildModeCategoryEnum.COMPLEX, AllIcons.I_PYRAMID, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.CIRCLE_START, ModeOptions.OptionEnum.SIDES, ModeOptions.OptionEnum.FILL});

   private final String name;
   public final IBuildMode instance;
   public final BuildModeCategoryEnum category;
   public final AllIcons icon;
   public final ModeOptions.OptionEnum[] options;

   private BuildModeEnum(String name, IBuildMode instance, BuildModeCategoryEnum category, AllIcons icon, ModeOptions.OptionEnum... options) {
      this.name = name;
      this.instance = instance;
      this.category = category;
      this.icon = icon;
      this.options = options;
   }

   public String getNameKey() {
      return "creative_mode_tweaks.mode." + this.name;
   }

   public String getDescriptionKey() {
      return "creative_mode_tweaks.modedescription." + this.name;
   }

   // $FF: synthetic method
   private static BuildModeEnum[] $values() {
      return new BuildModeEnum[]{DISABLED, LINE, PLANE, WALL, FLOOR, CUBE, MESH, DIAGONAL_LINE, DIAGONAL_WALL, SLOPE_FLOOR, CIRCLE, CYLINDER, SPHERE, DOME, PYRAMID};
   }
}
