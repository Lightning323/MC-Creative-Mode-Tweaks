package nl.requios.effortlessbuilding.buildmode;

import org.joml.Vector4f;

public enum BuildModeCategoryEnum {
   BASIC(new Vector4f(0.0F, 0.5F, 1.0F, 0.8F)),
   DIAGONAL(new Vector4f(0.56F, 0.28F, 0.87F, 0.8F)),
   CIRCULAR(new Vector4f(0.29F, 0.76F, 0.3F, 0.8F)),
   ROOF(new Vector4f(0.83F, 0.87F, 0.23F, 0.8F));

   public final Vector4f color;

   private BuildModeCategoryEnum(Vector4f color) {
      this.color = color;
   }

   // $FF: synthetic method
   private static BuildModeCategoryEnum[] $values() {
      return new BuildModeCategoryEnum[]{BASIC, DIAGONAL, CIRCULAR, ROOF};
   }
}
