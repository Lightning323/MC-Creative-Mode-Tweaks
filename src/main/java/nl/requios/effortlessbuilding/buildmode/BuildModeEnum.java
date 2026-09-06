package nl.requios.effortlessbuilding.buildmode;

import nl.requios.effortlessbuilding.AllIcons;
import nl.requios.effortlessbuilding.buildmode.buildmodes.Circle;
import nl.requios.effortlessbuilding.buildmode.buildmodes.Cube;
import nl.requios.effortlessbuilding.buildmode.buildmodes.Cylinder;
import nl.requios.effortlessbuilding.buildmode.buildmodes.DiagonalLine;
import nl.requios.effortlessbuilding.buildmode.buildmodes.DiagonalWall;
import nl.requios.effortlessbuilding.buildmode.buildmodes.Disabled;
import nl.requios.effortlessbuilding.buildmode.buildmodes.Floor;
import nl.requios.effortlessbuilding.buildmode.buildmodes.Line;
import nl.requios.effortlessbuilding.buildmode.buildmodes.Plane;
import nl.requios.effortlessbuilding.buildmode.buildmodes.SlopeFloor;
import nl.requios.effortlessbuilding.buildmode.buildmodes.Sphere;
import nl.requios.effortlessbuilding.buildmode.buildmodes.Wall;

public enum BuildModeEnum {
   DISABLED("normal", new Disabled(), BuildModeCategoryEnum.BASIC, AllIcons.I_DISABLE, new ModeOptions.OptionEnum[0]),
   LINE("line", new Line(), BuildModeCategoryEnum.BASIC, AllIcons.I_LINE, new ModeOptions.OptionEnum[0]),
   PLANE("plane", new Plane(), BuildModeCategoryEnum.BASIC, AllIcons.I_FLOOR, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.FILL}),
   WALL("wall", new Wall(), BuildModeCategoryEnum.BASIC, AllIcons.I_WALL, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.FILL}),
   FLOOR("floor", new Floor(), BuildModeCategoryEnum.BASIC, AllIcons.I_FLOOR, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.FILL}),
   CUBE("cube", new Cube(), BuildModeCategoryEnum.BASIC, AllIcons.I_CUBE, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.CUBE_FILL}),
   DIAGONAL_LINE("diagonal_line", new DiagonalLine(), BuildModeCategoryEnum.DIAGONAL, AllIcons.I_DIAGONAL_LINE, new ModeOptions.OptionEnum[0]),
   DIAGONAL_WALL("diagonal_wall", new DiagonalWall(), BuildModeCategoryEnum.DIAGONAL, AllIcons.I_DIAGONAL_WALL, new ModeOptions.OptionEnum[0]),
   SLOPE_FLOOR("slope_floor", new SlopeFloor(), BuildModeCategoryEnum.DIAGONAL, AllIcons.I_SLOPED_FLOOR, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.RAISED_EDGE}),
   CIRCLE("circle", new Circle(), BuildModeCategoryEnum.CIRCULAR, AllIcons.I_CIRCLE, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.CIRCLE_START, ModeOptions.OptionEnum.FILL}),
   CYLINDER("cylinder", new Cylinder(), BuildModeCategoryEnum.CIRCULAR, AllIcons.I_CYLINDER, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.CIRCLE_START, ModeOptions.OptionEnum.FILL}),
   SPHERE("sphere", new Sphere(), BuildModeCategoryEnum.CIRCULAR, AllIcons.I_SPHERE, new ModeOptions.OptionEnum[]{ModeOptions.OptionEnum.CIRCLE_START, ModeOptions.OptionEnum.FILL});

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
      return new BuildModeEnum[]{DISABLED, LINE, PLANE, WALL, FLOOR, CUBE, DIAGONAL_LINE, DIAGONAL_WALL, SLOPE_FLOOR, CIRCLE, CYLINDER, SPHERE};
   }
}
