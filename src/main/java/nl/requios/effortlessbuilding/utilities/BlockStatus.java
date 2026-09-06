package nl.requios.effortlessbuilding.utilities;

public enum BlockStatus {
   VALID,
   NOT_PLACED_BY_PLAYER,
   TOO_HARD,
   MISSING_TOOL,
   OUTSIDE_REACH,
   MAX_BLOCKS_EXCEEDED,
   WORLD_BORDER,
   PROTECTED_TILE_ENTITY,
   INSUFFICIENT_ITEMS,
   BREAKING_DISABLED;

   public boolean isValid() {
      return this == VALID;
   }

   // $FF: synthetic method
   private static BlockStatus[] $values() {
      return new BlockStatus[]{VALID, NOT_PLACED_BY_PLAYER, TOO_HARD, MISSING_TOOL, OUTSIDE_REACH, MAX_BLOCKS_EXCEEDED, WORLD_BORDER, PROTECTED_TILE_ENTITY, INSUFFICIENT_ITEMS, BREAKING_DISABLED};
   }
}
