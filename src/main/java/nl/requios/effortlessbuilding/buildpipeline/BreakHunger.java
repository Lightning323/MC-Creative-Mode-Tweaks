package nl.requios.effortlessbuilding.buildpipeline;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hunger exhaustion for survival build-mode breaking.
 *
 * <p>Vanilla charges no hunger for breaking blocks
 * ({@code ServerPlayerGameMode#destroyBlock} adds no exhaustion, and batch
 * breaking bypasses it anyway via direct {@code setBlock}), so without this a
 * thousand-block break would be free. This charges per broken block:</p>
 *
 * <pre>exhaustion = hardness × simulatedTicks × {@link #EXHAUSTION_PER_HARDNESS_TICK}
 *         × hardnessMult × (correctTool ? toolMult : 1)</pre>
 *
 * <ul>
 *   <li>{@code hardness} — {@link BlockState#getDestroySpeed}, so obsidian
 *       costs more than dirt per block.</li>
 *   <li>{@code simulatedTicks} — how many ticks vanilla would need to mine
 *       this block with the <b>held</b> item (not a guess): {@code
 *       ceil(hardness × (correct ? 30 : 100) / digSpeed)}. Dig speed replicates
 *       vanilla's {@code Player#getDigSpeed} exactly (tool speed, Efficiency
 *       attribute, Haste / Mining Fatigue, break-speed attribute, swimming and
 *       airborne penalties) but fires no events, so a 2000-block batch doesn't
 *       spam 2000 {@code BreakSpeed} events. A faster tool means fewer ticks
 *       means less hunger — e.g. stone by hand is 150 ticks, with a diamond
 *       pick 6.</li>
 *   <li>{@code hardnessMult} ({@code
 *       Config#BUILDING_SURVIVAL_BREAK_HUNGER_HARDNESS_MULT}) — user strength
 *       knob for the whole formula. 0 disables hunger from breaking.</li>
 *   <li>{@code toolMult} ({@code Config#BUILDING_SURVIVAL_BREAK_HUNGER_TOOL_MULT})
 *       — extra knob applied only when the held item is the correct tool for
 *       the block. Lower it to reduce (0 to eliminate) hunger while using
 *       proper tools.</li>
 * </ul>
 *
 * <p>Scale reference: 4.0 exhaustion = 1 hunger/saturation point (vanilla:
 * attacking 0.1, jumping 0.05, sprint-jumping 0.2). At multiplier 1.0,
 * hand-mining 3 stone costs ~0.14, with a diamond pick ~0.005.</p>
 */
public final class BreakHunger {
    /**
     * Internal base: exhaustion per unit of (hardness × simulated mine tick).
     * Fixed physics; players tune strength via the two config multipliers.
     */
    public static final double EXHAUSTION_PER_HARDNESS_TICK = 0.0002;

    private BreakHunger() {
    }

    /**
     * One batch's accumulator. Caches per-state dig speed / correctness (both
     * depend only on held item + effects + state, all constant mid-batch), so
     * a 2000-block break pays the attribute lookups once per block type.
     * Server thread only, single batch — not thread-safe, not reusable across
     * players.
     */
    public static final class Batch {
        private final Player player;
        private final ItemStack held;
        private final double hardnessMult;
        private final double toolMult;
        private final Map<BlockState, Float> digSpeedCache = new HashMap<>();
        private final Map<BlockState, Boolean> correctCache = new HashMap<>();
        private double total;

        public Batch(Player player, double hardnessMult, double toolMult) {
            this.player = player;
            this.held = player.getMainHandItem();
            this.hardnessMult = hardnessMult;
            this.toolMult = toolMult;
        }

        /**
         * Adds one broken block's cost. Call with the pre-break state, before
         * {@code setBlock}. Unbreakable (hardness &lt; 0) and instant
         * (hardness 0) blocks add nothing.
         */
        public void add(Level level, BlockPos pos, BlockState oldState) {
            float hardness = oldState.getDestroySpeed(level, pos);
            if (hardness <= 0.0F) {
                return;
            }
            boolean correct = this.correctCache.computeIfAbsent(oldState,
                    state -> !state.requiresCorrectToolForDrops() || this.held.isCorrectToolForDrops(state));
            // Vanilla progress rule (BlockState#getDestroyProgress), event-free:
            // progress per tick = digSpeed / hardness / (correct ? 30 : 100).
            float digSpeed = this.digSpeedCache.computeIfAbsent(oldState, state -> digSpeed(this.player, this.held, state));
            if (digSpeed <= 0.0F) {
                return;
            }
            int ticks = Math.max(1, (int) Math.ceil((double) hardness * (correct ? 30 : 100) / digSpeed));
            double cost = (double) hardness * ticks * EXHAUSTION_PER_HARDNESS_TICK * this.hardnessMult;
            if (correct) {
                cost *= this.toolMult;
            }
            this.total += cost;
        }

        /** Total exhaustion for {@link Player#causeFoodExhaustion}. */
        public float total() {
            return (float) this.total;
        }
    }

    /**
     * Vanilla {@code Player#getDigSpeed} replicated exactly (tool destroy
     * speed, Efficiency attribute, Haste / Mining Fatigue, break-speed and
     * submerged-mining attributes, airborne penalty), minus the trailing
     * {@code BreakSpeed} event so batch simulation stays side-effect free.
     * Uses the passed held stack — the item actually swung.
     */
    private static float digSpeed(Player player, ItemStack held, BlockState state) {
        float speed = held.getDestroySpeed(state);
        if (speed > 1.0F) {
            speed += (float) player.getAttributeValue(Attributes.MINING_EFFICIENCY);
        }
        if (MobEffectUtil.hasDigSpeed(player)) {
            speed *= 1.0F + (float) (MobEffectUtil.getDigSpeedAmplification(player) + 1) * 0.2F;
        }
        if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            speed *= switch (player.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
        }
        speed *= (float) player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (player.isEyeInFluid(FluidTags.WATER)) {
            speed *= (float) player.getAttribute(Attributes.SUBMERGED_MINING_SPEED).getValue();
        }
        if (!player.onGround()) {
            speed /= 5.0F;
        }
        return speed;
    }
}
