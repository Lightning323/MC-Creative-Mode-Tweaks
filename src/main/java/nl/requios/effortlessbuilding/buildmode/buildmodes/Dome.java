package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.world.phys.AABB;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.RaycastToPlane;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Hemisphere extending away from the face selected on the first click. Starts
 * exactly like {@link Circle} (same normal from the first clicked face, same
 * {@code CIRCLE_START}/{@code FILL}/{@code POINT_BUILD} options for the base)
 * but the next point is the tip along the normal: every layer scales
 * spherically from the full base to a single point at the tip.
 *
 * <p>Clicks: 2-point base needs 3 clicks (first, second for the circle, third
 * for the tip); 3-point base needs 4 clicks (first/second/third for the
 * ellipse like {@link Circle}, fourth for the tip).</p>
 */
public class Dome extends ThreeClicksBuildMode {
   /** Outward normal of the base disc, from the first clicked face (like Circle). */
   private Direction direction = Direction.UP;
   /** Captured at first click: 3-point base draws the ellipse, 2-point a circle. */
   private boolean ellipseThreePoint;
   /** Base ellipse third point in 3-point mode (stored on the 3rd click). */
   private @Nullable BlockPos thirdBasePos;
   /** Tip point stored on the final click for the placement packet. */
   private @Nullable BlockPos storedTip;

   @Override
   public void initialize() {
      super.initialize();
      this.direction = Direction.UP;
      this.ellipseThreePoint = false;
      this.thirdBasePos = null;
      this.storedTip = null;
   }

   /** The first clicked face is the dome's outward direction. */
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
         this.storedTip = null;
      }
      ++this.clicks;
      if (this.clicks == 1) {
         this.firstBlockEntry = new BlockEntry(clickedPos);
         this.secondBlockEntry = null;
         this.thirdBasePos = null;
         this.storedTip = null;
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
         // 3rd click: tip.
         BlockPos tip = this.findTip(player, this.secondBlockEntry.blockPos);
         if (tip == null) {
            this.clicks = 2;
            return false;
         }
         this.storedTip = limitToBuildRange(player, this.firstBlockEntry.blockPos, tip);
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
      // 4th click: tip.
      BlockPos tip = this.findTip(player, this.secondBlockEntry.blockPos);
      if (tip == null) {
         this.clicks = 3;
         return false;
      }
      this.storedTip = limitToBuildRange(player, this.firstBlockEntry.blockPos, tip);
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
         BlockPos tip = this.storedTip != null ? this.storedTip
               : this.findTip(player, secondPos);
         if (tip == null) {
            return Circle.circleBounds(firstPos, secondPos, this.direction);
         }
         return domeBounds(firstPos, secondPos, null,
               limitToBuildRange(player, firstPos, tip), this.direction);
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
      BlockPos tip = this.storedTip != null ? this.storedTip
            : this.findTip(player, secondPos);
      if (tip == null) {
         return Circle.ellipseBounds(firstPos, secondPos, this.thirdBasePos, this.direction);
      }
      return domeBounds(firstPos, secondPos, this.thirdBasePos,
            limitToBuildRange(player, firstPos, tip), this.direction);
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
         BlockPos tip = this.storedTip != null ? this.storedTip
               : this.findTip(player, secondPos);
         if (tip == null) {
            return;
         }
         tip = limitToBuildRange(player, firstPos, tip);
         blocks.clear();
         if (fast) {
            forEachOrientedDome(firstPos, secondPos, null, tip, this.direction, full, blocks::addPacked);
         } else {
            blocks.addAllPositions(getOrientedDomeBlocks(firstPos, secondPos, null, tip, this.direction));
         }
         blocks.firstPos = firstPos;
         blocks.lastPos = tip;
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
      BlockPos tip = this.storedTip != null ? this.storedTip
            : this.findTip(player, secondPos);
      if (tip == null) {
         return;
      }
      tip = limitToBuildRange(player, firstPos, tip);
      blocks.clear();
      if (fast) {
         forEachOrientedDome(firstPos, secondPos, this.thirdBasePos, tip, this.direction, full, blocks::addPacked);
      } else {
         blocks.addAllPositions(getOrientedDomeBlocks(firstPos, secondPos, this.thirdBasePos, tip, this.direction));
      }
      blocks.firstPos = firstPos;
      blocks.lastPos = tip;
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
      return this.ellipseThreePoint ? this.storedTip : null;
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
         // thirdPos is the base ellipse third, fourthPos the tip.
         return getOrientedDomeBlocks(firstPos, secondPos, thirdPos, fourthPos, this.direction);
      }
      if (thirdPos == null) {
         return Circle.getOrientedCircleBlocks(firstPos, secondPos, this.direction);
      }
      return getOrientedDomeBlocks(firstPos, secondPos, null, thirdPos, this.direction);
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
         forEachOrientedDome(firstPos, secondPos, thirdPos, fourthPos, this.direction, full, out);
         return;
      }
      if (thirdPos == null) {
         Circle.forEachOrientedCircle(firstPos, secondPos, this.direction, full, out);
         return;
      }
      forEachOrientedDome(firstPos, secondPos, null, thirdPos, this.direction, full, out);
   }

   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      // Base footprint on the disc plane through the anchor (like Circle).
      return RaycastToPlane.findNormalPlane(this.direction.getAxis(), player, firstPos);
   }

   protected BlockPos findThirdPos(Player player, BlockPos firstPos, BlockPos secondPos, boolean skipRaytrace) {
      // Base ellipse stretch stays coplanar (like Circle).
      return RaycastToPlane.findNormalPlane(this.direction.getAxis(), player, firstPos);
   }

   /** Tip height along the normal from the base (like Pyramid height). */
   protected @Nullable BlockPos findTip(Player player, BlockPos anchor) {
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
      return getOrientedDomeBlocks(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), null,
            new BlockPos(x3, y3, z3), this.direction);
   }

   @Override
   protected void forEachIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
      Circle.forEachOrientedCircle(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), this.direction,
            ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
   }

   @Override
   protected void forEachFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3, LongConsumer out) {
      forEachOrientedDome(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), null,
            new BlockPos(x3, y3, z3), this.direction,
            ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
   }

   // -- oriented dome shapes (all facings) -----------------------------------

   /**
    * Hemisphere on the base disc: 2-point base is a circle, 3-point base an
    * ellipse (both exactly like {@link Circle}); every layer along the normal
    * scales spherically from the full base to a single point at the forced tip
    * in the facing direction. A null {@code thirdBase} always draws the
    * circular dome.
    */
   public static List<BlockPos> getOrientedDomeBlocks(BlockPos first, BlockPos second, @Nullable BlockPos thirdBase, BlockPos tip, Direction normal) {
      List<BlockPos> list = new ArrayList<>(4096);
      forEachOrientedDome(first, second, thirdBase, tip, normal,
            ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, packed -> list.add(BlockPos.of(packed)));
      return list;
   }

   /** Bare-int twin of {@link #getOrientedDomeBlocks}: same blocks, packed longs, no allocation. */
   public static void forEachOrientedDome(BlockPos first, BlockPos second, @Nullable BlockPos thirdBase, BlockPos tip, Direction normal, boolean full, LongConsumer out) {
      Circle.Disc baseDisc = thirdBase == null
            ? Circle.Disc.circle(first, second, normal)
            : Circle.Disc.ellipse(first, second, thirdBase, normal);
      Direction.Axis axis = normal.getAxis();
      int base = RaycastToPlane.coordinate(first, axis);
      int height = Math.abs(RaycastToPlane.coordinate(tip, axis) - base);
      int step = normal.getAxisDirection().getStep();
      int forcedTip = base + step * height;
      for (int n = base; step > 0 ? n <= forcedTip : n >= forcedTip; n += step) {
         double d = Math.abs(n - base);
         double scale = height == 0 ? 1.0D
               : Math.sqrt(Math.max(0.0D, 1.0D - (d * d) / ((double) height * (double) height)));
         Circle.Disc layer = new Circle.Disc(baseDisc.normal(), baseDisc.uAxis(), baseDisc.vAxis(), n,
               baseDisc.cu(), baseDisc.cv(), baseDisc.ax(), baseDisc.ay(),
               baseDisc.ra() * scale, baseDisc.rb() * scale);
         layer.emit(full, out);
      }
   }

   /** Full-block preview boundary of the dome (base extents, base..forced tip). */
   static AABB domeBounds(BlockPos first, BlockPos second, @Nullable BlockPos thirdBase, BlockPos tip, Direction normal) {
      AABB baseBounds = thirdBase == null
            ? Circle.circleBounds(first, second, normal)
            : Circle.ellipseBounds(first, second, thirdBase, normal);
      int baseCoord = RaycastToPlane.coordinate(first, normal.getAxis());
      int forcedTipCoord = baseCoord + normal.getAxisDirection().getStep()
            * Math.abs(RaycastToPlane.coordinate(tip, normal.getAxis()) - baseCoord);
      BlockPos forcedTip = switch (normal.getAxis()) {
         case X -> new BlockPos(forcedTipCoord, tip.getY(), tip.getZ());
         case Y -> new BlockPos(tip.getX(), forcedTipCoord, tip.getZ());
         case Z -> new BlockPos(tip.getX(), tip.getY(), forcedTipCoord);
      };
      AABB tipBox = toFullBlockAABB(forcedTip.getX(), forcedTip.getY(), forcedTip.getZ(),
            forcedTip.getX(), forcedTip.getY(), forcedTip.getZ());
      return new AABB(Math.min(baseBounds.minX, tipBox.minX), Math.min(baseBounds.minY, tipBox.minY), Math.min(baseBounds.minZ, tipBox.minZ),
            Math.max(baseBounds.maxX, tipBox.maxX), Math.max(baseBounds.maxY, tipBox.maxY), Math.max(baseBounds.maxZ, tipBox.maxZ));
   }
}
