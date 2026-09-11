package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.world.phys.AABB;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.RaycastToPlane;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import nl.requios.effortlessbuilding.utilities.FloodFill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Extruded disc in any orientation. Starts exactly like {@link Circle} (same
 * normal from the first clicked face, same {@code CIRCLE_START}/{@code FILL}/
 * {@code POINT_BUILD} options for the base) but the next point is the
 * extrusion point along the normal.
 *
 * <p>Clicks: 2-point base needs 3 clicks (first, second for the circle, third
 * for the extrusion); 3-point base needs 4 clicks (first/second/third for the
 * ellipse like {@link Circle}, fourth for the extrusion).</p>
 */
public class Cylinder extends ThreeClicksBuildMode {
   /** Outward normal of the base disc, from the first clicked face (like Circle/Dome). */
   private Direction direction = Direction.UP;
   /** Captured at first click: 3-point base draws the ellipse, 2-point a circle. */
   private boolean ellipseThreePoint;
   /** Base ellipse third point in 3-point mode (stored on the 3rd click). */
   private @Nullable BlockPos thirdBasePos;
   /** Extrusion point stored on the final click for the placement packet. */
   private @Nullable BlockPos storedExtrusion;

   @Override
   public void initialize() {
      super.initialize();
      this.direction = Direction.UP;
      this.ellipseThreePoint = false;
      this.thirdBasePos = null;
      this.storedExtrusion = null;
   }

   /** The first clicked face is the base disc's outward direction (like Circle). */
   @Override
   public void setFirstClickFace(Direction face) {
      this.direction = face;
   }

   @Override
   protected boolean supportsTwoPointBuild() {
      // Always plane-based; the 2pt/3pt base option is captured separately
      // and must not trigger the raw-click two-point machinery (like Circle).
      return false;
   }

   @Override
   public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
      if (this.clicks == 0) {
         this.ellipseThreePoint = ModeOptions.getPointBuild() == ModeOptions.ActionEnum.THREE_POINT_BUILD;
         this.thirdBasePos = null;
         this.storedExtrusion = null;
      }
      ++this.clicks;
      if (this.clicks == 1) {
         this.firstBlockEntry = new BlockEntry(clickedPos);
         this.secondBlockEntry = null;
         this.thirdBasePos = null;
         this.storedExtrusion = null;
         return false;
      }
      if (!this.ellipseThreePoint) {
         if (this.clicks == 2) {
            BlockPos secondPos = this.findSecondPos(player, this.firstBlockEntry.blockPos, true);
            if (secondPos == null) {
               this.clicks = 1;
               return false;
            }
            this.secondBlockEntry = new BlockEntry(secondPos);
            return false;
         }
         // 3rd click: extrusion.
         BlockPos extrusion = this.findExtrusion(player, this.secondBlockEntry.blockPos);
         if (extrusion == null) {
            this.clicks = 2;
            return false;
         }
         this.storedExtrusion = limitToBuildRange(player, this.firstBlockEntry.blockPos, extrusion);
         return true;
      }
      if (this.clicks == 2) {
         BlockPos secondPos = this.findSecondPos(player, this.firstBlockEntry.blockPos, true);
         if (secondPos == null) {
            this.clicks = 1;
            return false;
         }
         this.secondBlockEntry = new BlockEntry(secondPos);
         return false;
      }
      if (this.clicks == 3) {
         BlockPos thirdBase = this.findThirdPos(player, this.firstBlockEntry.blockPos, this.secondBlockEntry.blockPos, true);
         if (thirdBase == null) {
            this.clicks = 2;
            return false;
         }
         this.thirdBasePos = limitToBuildRange(player, this.firstBlockEntry.blockPos, thirdBase);
         return false;
      }
      // 4th click: extrusion.
      BlockPos extrusion = this.findExtrusion(player, this.secondBlockEntry.blockPos);
      if (extrusion == null) {
         this.clicks = 3;
         return false;
      }
      this.storedExtrusion = limitToBuildRange(player, this.firstBlockEntry.blockPos, extrusion);
      return true;
   }

   @Override
   public AABB getClientBoundary(Player player) {
      if (this.clicks == 0 || this.firstBlockEntry == null || this.firstBlockEntry.blockPos == null) {
         return null;
      }
      BlockPos firstPos = this.firstBlockEntry.blockPos;
      if (!this.ellipseThreePoint) {
         if (this.clicks == 1) {
            BlockPos secondPos = this.findSecondPos(player, firstPos, true);
            if (secondPos == null) {
               return null;
            }
            return Circle.circleBounds(firstPos, limitToBuildRange(player, firstPos, secondPos), this.direction);
         }
         if (this.secondBlockEntry == null || this.secondBlockEntry.blockPos == null) {
            return null;
         }
         BlockPos secondPos = this.secondBlockEntry.blockPos;
         BlockPos extrusion = this.storedExtrusion != null ? this.storedExtrusion
               : this.findExtrusion(player, secondPos);
         if (extrusion == null) {
            return Circle.circleBounds(firstPos, secondPos, this.direction);
         }
         return cylinderBounds(firstPos, secondPos, null,
               limitToBuildRange(player, firstPos, extrusion), this.direction);
      }
      if (this.clicks == 1) {
         BlockPos secondPos = this.findSecondPos(player, firstPos, true);
         if (secondPos == null) {
            return null;
         }
         return Circle.circleBounds(firstPos, limitToBuildRange(player, firstPos, secondPos), this.direction);
      }
      if (this.secondBlockEntry == null || this.secondBlockEntry.blockPos == null) {
         return null;
      }
      BlockPos secondPos = this.secondBlockEntry.blockPos;
      if (this.clicks == 2) {
         BlockPos thirdBase = this.findThirdPos(player, firstPos, secondPos, true);
         if (thirdBase == null) {
            return Circle.circleBounds(firstPos, secondPos, this.direction);
         }
         return Circle.ellipseBounds(firstPos, secondPos,
               limitToBuildRange(player, firstPos, thirdBase), this.direction);
      }
      if (this.thirdBasePos == null) {
         return null;
      }
      BlockPos extrusion = this.storedExtrusion != null ? this.storedExtrusion
            : this.findExtrusion(player, secondPos);
      if (extrusion == null) {
         return Circle.ellipseBounds(firstPos, secondPos, this.thirdBasePos, this.direction);
      }
      return cylinderBounds(firstPos, secondPos, this.thirdBasePos,
            limitToBuildRange(player, firstPos, extrusion), this.direction);
   }

   @Override
   public void getPlacementBlocks(BlockSet blocks, Player player, boolean fast) {
      if (this.clicks == 0 || this.firstBlockEntry == null || this.firstBlockEntry.blockPos == null) {
         return;
      }
      BlockPos firstPos = this.firstBlockEntry.blockPos;
      boolean full = ModeOptions.getFill() == ModeOptions.ActionEnum.FULL;
      if (!this.ellipseThreePoint) {
         if (this.clicks == 1) {
            BlockPos secondPos = this.findSecondPos(player, firstPos, true);
            if (secondPos == null) {
               return;
            }
            secondPos = limitToBuildRange(player, firstPos, secondPos);
            blocks.clear();
            if (fast) {
               Circle.forEachOrientedCircle(firstPos, secondPos, this.direction, full, blocks::addPacked);
            } else {
               blocks.addAllPositions(Circle.getOrientedCircleBlocks(firstPos, secondPos, this.direction));
            }
            blocks.firstPos = firstPos;
            blocks.lastPos = secondPos;
            return;
         }
         if (this.secondBlockEntry == null || this.secondBlockEntry.blockPos == null) {
            return;
         }
         BlockPos secondPos = this.secondBlockEntry.blockPos;
         BlockPos extrusion = this.storedExtrusion != null ? this.storedExtrusion
               : this.findExtrusion(player, secondPos);
         if (extrusion == null) {
            return;
         }
         extrusion = limitToBuildRange(player, firstPos, extrusion);
         blocks.clear();
         if (fast) {
            forEachOrientedCylinder(firstPos, secondPos, null, extrusion, this.direction, full, blocks::addPacked);
         } else {
            blocks.addAllPositions(getOrientedCylinderBlocks(firstPos, secondPos, null, extrusion, this.direction));
         }
         blocks.firstPos = firstPos;
         blocks.lastPos = extrusion;
         return;
      }
      if (this.clicks == 1) {
         BlockPos secondPos = this.findSecondPos(player, firstPos, true);
         if (secondPos == null) {
            return;
         }
         secondPos = limitToBuildRange(player, firstPos, secondPos);
         blocks.clear();
         if (fast) {
            Circle.forEachOrientedCircle(firstPos, secondPos, this.direction, full, blocks::addPacked);
         } else {
            blocks.addAllPositions(Circle.getOrientedCircleBlocks(firstPos, secondPos, this.direction));
         }
         blocks.firstPos = firstPos;
         blocks.lastPos = secondPos;
         return;
      }
      if (this.secondBlockEntry == null || this.secondBlockEntry.blockPos == null) {
         return;
      }
      BlockPos secondPos = this.secondBlockEntry.blockPos;
      if (this.clicks == 2) {
         BlockPos thirdBase = this.findThirdPos(player, firstPos, secondPos, true);
         if (thirdBase == null) {
            return;
         }
         thirdBase = limitToBuildRange(player, firstPos, thirdBase);
         blocks.clear();
         if (fast) {
            Circle.forEachOrientedEllipse(firstPos, secondPos, thirdBase, this.direction, full, blocks::addPacked);
         } else {
            blocks.addAllPositions(Circle.getOrientedEllipseBlocks(firstPos, secondPos, thirdBase, this.direction));
         }
         blocks.firstPos = firstPos;
         blocks.lastPos = thirdBase;
         return;
      }
      if (this.thirdBasePos == null) {
         return;
      }
      BlockPos extrusion = this.storedExtrusion != null ? this.storedExtrusion
            : this.findExtrusion(player, secondPos);
      if (extrusion == null) {
         return;
      }
      extrusion = limitToBuildRange(player, firstPos, extrusion);
      blocks.clear();
      if (fast) {
         forEachOrientedCylinder(firstPos, secondPos, this.thirdBasePos, extrusion, this.direction, full, blocks::addPacked);
      } else {
         blocks.addAllPositions(getOrientedCylinderBlocks(firstPos, secondPos, this.thirdBasePos, extrusion, this.direction));
      }
      blocks.firstPos = firstPos;
      blocks.lastPos = extrusion;
   }

   @Override
   public @Nullable BlockPos getIntermediatePos() {
      return this.secondBlockEntry != null ? this.secondBlockEntry.blockPos : null;
   }

   @Override
   public @Nullable BlockPos getThirdSelectionPos() {
      return this.ellipseThreePoint ? this.thirdBasePos : null;
   }

   @Override
   public @Nullable BlockPos getFourthSelectionPos() {
      return this.ellipseThreePoint ? this.storedExtrusion : null;
   }

   /**
    * Flood seed at the center of the base circle, on the base plane — not the
    * middle of the extrusion, which would usually be mid-air inside the tube.
    * The base ellipse (3-point) shares the circle's center, so the third
    * point is irrelevant here.
    */
   @Override
   public BlockPos getFloodFillOrigin(BlockSet shapeBlocks, @Nullable BlockPos firstPos, @Nullable BlockPos secondPos,
                                      @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
      if (firstPos != null && secondPos != null) {
         return Circle.Disc.circle(firstPos, secondPos, this.direction).centerBlock();
      }
      return FloodFill.centerOf(shapeBlocks);
   }

   @Override
   public ModeOptions.ActionEnum getPointBuildAction() {
      return this.ellipseThreePoint ? ModeOptions.ActionEnum.THREE_POINT_BUILD : ModeOptions.ActionEnum.TWO_POINT_BUILD;
   }

   @Override
   public @Nullable String getShapeInfo(Player player) {
      if (this.clicks == 0 || this.firstBlockEntry == null || this.firstBlockEntry.blockPos == null) {
         return null;
      }
      BlockPos firstPos = this.firstBlockEntry.blockPos;
      BlockPos liveSecond = this.findSecondPos(player, firstPos, true);
      BlockPos storedSecond = this.secondBlockEntry != null ? this.secondBlockEntry.blockPos : null;
      BlockPos secondPos = this.clicks >= 2 && storedSecond != null ? storedSecond : liveSecond;
      if (secondPos == null) {
         return null;
      }
      secondPos = limitToBuildRange(player, firstPos, secondPos);
      if (!this.ellipseThreePoint || this.clicks == 1) {
         return Circle.circleInfo(firstPos, secondPos, this.direction);
      }
      BlockPos thirdBase = this.clicks >= 3 && this.thirdBasePos != null ? this.thirdBasePos
            : this.findThirdPos(player, firstPos, secondPos, true);
      if (thirdBase == null) {
         return Circle.circleInfo(firstPos, secondPos, this.direction);
      }
      return Circle.ellipseInfo(firstPos, secondPos, limitToBuildRange(player, firstPos, thirdBase), this.direction);
   }

   @Override
   public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
      boolean threePoint = ModeOptions.getPointBuild() == ModeOptions.ActionEnum.THREE_POINT_BUILD;
      if (threePoint) {
         if (thirdPos == null || fourthPos == null) {
            return List.of();
         }
         return getPlacementBlocks(player, firstPos, secondPos, thirdPos, fourthPos);
      }
      if (thirdPos == null) {
         return List.of();
      }
      return getPlacementBlocks(player, firstPos, secondPos, thirdPos, fourthPos);
   }

   @Override
   public List<BlockPos> getPlacementBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
      boolean threePoint = ModeOptions.getPointBuild() == ModeOptions.ActionEnum.THREE_POINT_BUILD;
      if (threePoint) {
         if (fourthPos == null) {
            if (thirdPos == null) {
               return Circle.getOrientedCircleBlocks(firstPos, secondPos, this.direction);
            }
            return Circle.getOrientedEllipseBlocks(firstPos, secondPos, thirdPos, this.direction);
         }
         // thirdPos is the base ellipse third, fourthPos the extrusion.
         return getOrientedCylinderBlocks(firstPos, secondPos, thirdPos, fourthPos, this.direction);
      }
      if (thirdPos == null) {
         return Circle.getOrientedCircleBlocks(firstPos, secondPos, this.direction);
      }
      return getOrientedCylinderBlocks(firstPos, secondPos, null, thirdPos, this.direction);
   }

   @Override
   public void forEachCommonBlock(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos, LongConsumer out) {
      boolean threePoint = ModeOptions.getPointBuild() == ModeOptions.ActionEnum.THREE_POINT_BUILD;
      boolean full = ModeOptions.getFill() == ModeOptions.ActionEnum.FULL;
      if (threePoint) {
         if (fourthPos == null) {
            if (thirdPos == null) {
               Circle.forEachOrientedCircle(firstPos, secondPos, this.direction, full, out);
            } else {
               Circle.forEachOrientedEllipse(firstPos, secondPos, thirdPos, this.direction, full, out);
            }
            return;
         }
         forEachOrientedCylinder(firstPos, secondPos, thirdPos, fourthPos, this.direction, full, out);
         return;
      }
      if (thirdPos == null) {
         Circle.forEachOrientedCircle(firstPos, secondPos, this.direction, full, out);
         return;
      }
      forEachOrientedCylinder(firstPos, secondPos, null, thirdPos, this.direction, full, out);
   }

   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      // Base footprint on the disc plane through the anchor (like Circle).
      return RaycastToPlane.findNormalPlane(this.direction.getAxis(), player, firstPos);
   }

   protected BlockPos findThirdPos(Player player, BlockPos firstPos, BlockPos secondPos, boolean skipRaytrace) {
      // Base ellipse stretch stays coplanar (like Circle).
      return RaycastToPlane.findNormalPlane(this.direction.getAxis(), player, firstPos);
   }

   /** Extrusion tip along the normal from the base (like Dome/Pyramid height). */
   protected @Nullable BlockPos findExtrusion(Player player, BlockPos anchor) {
      return RaycastToPlane.findPerpendicularHeight(this.direction.getAxis(), player, anchor);
   }

   @Override
   protected List<BlockPos> getIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return Circle.getOrientedCircleBlocks(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), this.direction);
   }

   @Override
   protected List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      // Legacy 3-int path kept for the base pipeline; full oriented placement
      // goes through the explicit-points overloads above.
      return getOrientedCylinderBlocks(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), null,
            new BlockPos(x3, y3, z3), this.direction);
   }

   @Override
   protected void forEachIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
      Circle.forEachOrientedCircle(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), this.direction,
            ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
   }

   @Override
   protected void forEachFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3, LongConsumer out) {
      forEachOrientedCylinder(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), null,
            new BlockPos(x3, y3, z3), this.direction,
            ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
   }

   // -- oriented cylinder shapes (all facings) -------------------------------

   /**
    * Extruded base disc: 2-point base is a circle, 3-point base an ellipse
    * (both exactly like {@link Circle}); every base block is extruded along
    * the normal from the base plane through {@code first} to {@code tip}.
    * A null {@code thirdBase} always draws the circle.
    */
   public static List<BlockPos> getOrientedCylinderBlocks(BlockPos first, BlockPos second, @Nullable BlockPos thirdBase, BlockPos tip, Direction normal) {
      List<BlockPos> list = new ArrayList<>(4096);
      forEachOrientedCylinder(first, second, thirdBase, tip, normal,
            ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, packed -> list.add(BlockPos.of(packed)));
      return list;
   }

   /** Bare-int twin of {@link #getOrientedCylinderBlocks}: same blocks, packed longs, no allocation. */
   public static void forEachOrientedCylinder(BlockPos first, BlockPos second, @Nullable BlockPos thirdBase, BlockPos tip, Direction normal, boolean full, LongConsumer out) {
      LongArrayList disc = new LongArrayList();
      if (thirdBase == null) {
         Circle.forEachOrientedCircle(first, second, normal, full, disc::add);
      } else {
         Circle.forEachOrientedEllipse(first, second, thirdBase, normal, full, disc::add);
      }
      Direction.Axis axis = normal.getAxis();
      int base = RaycastToPlane.coordinate(first, axis);
      int tipCoord = RaycastToPlane.coordinate(tip, axis);
      int lowest = Math.min(base, tipCoord);
      int highest = Math.max(base, tipCoord);
      for (int n = lowest; n <= highest; ++n) {
         for (int i = 0, count = disc.size(); i < count; ++i) {
            long packed = disc.getLong(i);
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            out.accept(switch (axis) {
               case X -> BlockPos.asLong(n, y, z);
               case Y -> BlockPos.asLong(x, n, z);
               case Z -> BlockPos.asLong(x, y, n);
            });
         }
      }
   }

   /** Full-block preview boundary of the extruded base. */
   static AABB cylinderBounds(BlockPos first, BlockPos second, @Nullable BlockPos thirdBase, BlockPos tip, Direction normal) {
      AABB base = thirdBase == null
            ? Circle.circleBounds(first, second, normal)
            : Circle.ellipseBounds(first, second, thirdBase, normal);
      AABB tipBox = toFullBlockAABB(tip.getX(), tip.getY(), tip.getZ(), tip.getX(), tip.getY(), tip.getZ());
      return new AABB(Math.min(base.minX, tipBox.minX), Math.min(base.minY, tipBox.minY), Math.min(base.minZ, tipBox.minZ),
            Math.max(base.maxX, tipBox.maxX), Math.max(base.maxY, tipBox.maxY), Math.max(base.maxZ, tipBox.maxZ));
   }
}
