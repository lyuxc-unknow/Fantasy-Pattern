/*
 * MIT License
 *
 * Copyright (c) 2026 HibikiShino and OmniSequence: Transfinite contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cn.lyxc.fantasytechnology.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.config.FTConfig;
import com.ae2vm.addon.crafting.DurableInputAdapters;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Grows a one-craft input set that AE2 already extracted into an {@code N}-craft one.
///
/// AE2 pulls the materials for a single recipe out of the CPU inventory, then pushes them at a provider. When the
/// provider can take several recipes at once ({@link cn.lyxc.fantasytechnology.integration.ae2.IFantasyBatchCraftingProvider}),
/// this pulls the remaining {@code N-1} recipes with one extract per material instead of {@code N-1} full rounds of
/// AE2's template walk. Anything that cannot be verified returns {@code null}, which leaves the original one-craft
/// extraction untouched for the caller to dispatch normally.
public final class FantasyBatchExtraction {

    /// AE2 compares available power against the requested amount with this slack; mirror it so the CPU's own check
    /// cannot reject a batch this class just sized to fit.
    private static final double POWER_EPSILON = 0.01;

    private final KeyCounter[] inputs;
    private final long craftCount;
    private final List<RemainderCorrection> remainderCorrections;

    private FantasyBatchExtraction(KeyCounter[] inputs, long craftCount,
            List<RemainderCorrection> remainderCorrections) {
        this.inputs = inputs;
        this.craftCount = craftCount;
        this.remainderCorrections = remainderCorrections;
    }

    /// The complete {@code N}-craft input holder, ready to be handed to the provider in place of AE2's original one.
    public KeyCounter[] inputs() {
        return inputs;
    }

    public long craftCount() {
        return craftCount;
    }

    /// What the CPU's waiting-for list has to be corrected by once AE2 has registered this push. Empty unless the
    /// batch carries a tool that wears down; see {@link RemainderCorrection}.
    public List<RemainderCorrection> remainderCorrections() {
        return remainderCorrections;
    }

    /// One reusable slot whose post-batch state differs from what AE2 assumed while extracting.
    ///
    /// AE2 records one craft's worth of remainders during extraction and inserts them into the job's waiting-for
    /// list immediately after the push. A tool that served {@code amount} items across {@code N} crafts comes back
    /// as damage+N, not damage+1, so that entry names a key nothing will ever return and the job waits forever.
    ///
    /// Rewriting AE2's transient counter from inside the wrapped extraction is not enough: another mod wrapping the
    /// same call site may reset or re-derive it afterwards - OmniSequence's Omni-Computation Core does exactly that
    /// - and the correction is silently lost. The waiting-for list itself is the authority, so the swap is applied
    /// there once AE2 is done filling it. A {@code null} {@link #returnedKey} means the batch used the tool up and
    /// nothing comes back at all.
    public record RemainderCorrection(AEKey assumedKey, @Nullable AEKey returnedKey, long amount) {
    }

    /// Extracts the additional {@code maxCrafts - 1} recipes, or returns {@code null} to keep AE2's single craft.
    ///
    /// On success {@code expectedOutputs} is rewritten to the scaled amounts, because the CPU adds it to its
    /// waiting-for list right after the push and would otherwise expect a single recipe's worth of results.
    @Nullable
    public static FantasyBatchExtraction expand(IPatternDetails patternDetails, ICraftingInventory inventory,
            IEnergyService energyService, Level level, KeyCounter[] firstInputs, KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems, long maxCrafts) {
        if (maxCrafts <= 1) {
            return null;
        }

        var plan = ExpansionPlan.of(patternDetails, level, firstInputs, expectedOutputs, expectedContainerItems,
                maxCrafts);
        if (plan == null) {
            return null;
        }

        try {
            long craftCount = plan.limitByInventory(inventory, maxCrafts);
            if (craftCount <= 1) {
                return null;
            }
            craftCount = plan.limitByEnergy(energyService, craftCount);
            if (craftCount <= 1) {
                return null;
            }
            return plan.extractAdditional(inventory, expectedOutputs, craftCount);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /// One material slot of the recipe as AE2 actually extracted it.
    private record PlannedInput(AEKey key, long amountPerCraft) {
    }

    private record PlannedOutput(AEKey key, long amountPerCraft) {
    }

    /// A slot holding a reusable item - a master infusion crystal, a wearing tool. One item serves the whole batch,
    /// so the slot is neither multiplied nor re-extracted; {@link #singleCraftRemainder} is what AE2 assumed comes back
    /// after a single craft, which is what has to be corrected once the batch size is known.
    private record ReusableSlot(IPatternDetails.IInput input, AEKey key, long amount, AEKey singleCraftRemainder) {
    }

    private static final class ExpansionPlan {
        private final PlannedInput[] inputs;
        /// Aggregate per key over the *consumed* slots only. Reusable slots are left out: the one
        /// item AE2 already extracted serves the whole batch, so no additional stock is needed -
        /// and none could be found, the CPU inventory no longer holds it.
        private final Object2LongOpenHashMap<AEKey> amountsPerKey;
        private final PlannedOutput[] outputs;
        /// AE2 charges power per item in the holder. Only consumed slots grow with the batch; a reusable slot still
        /// holds its single item however many crafts ride on it, so its cost is a flat addition rather than a
        /// per-craft one. Folding it into the per-craft figure would price batches out of existence on patterns
        /// whose reusable tool is the expensive part.
        private final double consumedPowerPerCraft;
        private final double reusablePower;
        private final long representableCrafts;
        /// Reusable slots by input index; null for an ordinary consumed ingredient.
        private final ReusableSlot[] reusableSlots;

        private ExpansionPlan(PlannedInput[] inputs, Object2LongOpenHashMap<AEKey> amountsPerKey,
                PlannedOutput[] outputs, double consumedPowerPerCraft, double reusablePower,
                long representableCrafts, ReusableSlot[] reusableSlots) {
            this.inputs = inputs;
            this.amountsPerKey = amountsPerKey;
            this.outputs = outputs;
            this.consumedPowerPerCraft = consumedPowerPerCraft;
            this.reusablePower = reusablePower;
            this.representableCrafts = representableCrafts;
            this.reusableSlots = reusableSlots;
        }

        @Nullable
        static ExpansionPlan of(IPatternDetails patternDetails, Level level, KeyCounter[] firstInputs,
                KeyCounter expectedOutputs, KeyCounter expectedContainerItems, long requestedMaxCrafts) {
            if (patternDetails == null || level == null || firstInputs == null || expectedOutputs == null
                    || expectedContainerItems == null || requestedMaxCrafts <= 1) {
                return null;
            }

            var patternInputs = patternDetails.getInputs();
            if (patternInputs == null || firstInputs.length != patternInputs.length) {
                return null;
            }

            var plannedInputs = new PlannedInput[firstInputs.length];
            var amountsPerKey = new Object2LongOpenHashMap<AEKey>(firstInputs.length);
            var reusableSlots = new ReusableSlot[firstInputs.length];
            var derivedSingleCraftRemainders = new KeyCounter();
            long representableCrafts = Long.MAX_VALUE;
            try {
                for (int index = 0; index < firstInputs.length; index++) {
                    var holder = firstInputs[index];
                    // Several keys in one slot means AE2 satisfied it from a mix of substitutes. Reproducing that
                    // mix is not something a single scaled extract can promise, so leave the pattern alone.
                    if (holder == null || holder.size() != 1) {
                        return null;
                    }
                    var entry = holder.getFirstEntry();
                    if (entry == null || entry.getKey() == null || entry.getLongValue() <= 0) {
                        return null;
                    }

                    AEKey key = entry.getKey();
                    long amount = entry.getLongValue();
                    plannedInputs[index] = new PlannedInput(key, amount);
                    representableCrafts = Math.min(representableCrafts, Long.MAX_VALUE / amount);

                    AEKey singleCraftRemainder = patternInputs[index].getRemainingKey(key);
                    if (singleCraftRemainder == null) {
                        amountsPerKey.put(key, Math.addExact(amountsPerKey.getLong(key), amount));
                        continue;
                    }

                    // A reusable input is not consumed per craft: AE2 extracted one item into this slot and
                    // expects the worn one back. Analyse with the actual batch size, not an unbounded request:
                    // a wearing tool has a finite chain, and walking it to the end would report "worn out" even
                    // though the requested batch fits inside the remaining durability.
                    var analysis = DurableInputAdapters.analyze(
                            patternInputs[index], key, level, requestedMaxCrafts);
                    if (!analysis.isReusable() || analysis.safeCrafts() < 2) {
                        // Random damage (Unbreaking), an unknown transition, or a tool with a single use left can
                        // never form a multi-craft batch: fall back to one craft at a time.
                        logReusableFallback(key, analysis);
                        return null;
                    }
                    reusableSlots[index] = new ReusableSlot(
                            patternInputs[index], key, amount, singleCraftRemainder);
                    derivedSingleCraftRemainders.add(singleCraftRemainder, amount);
                    // A wearing tool caps the batch at the crafts it can still serve. Being used up by the last
                    // craft of the batch is fine - nothing comes back, and nothing is waited for.
                    representableCrafts = Math.min(representableCrafts, analysis.safeCrafts());
                }

                // AE2 records one craft's worth of remainders while extracting. If the counter says anything
                // else, this is not a pristine single-recipe extraction - someone has already rescaled it - and
                // multiplying the holder on top of that would desynchronise the CPU's waiting-for list.
                if (!remaindersMatch(derivedSingleCraftRemainders, expectedContainerItems)) {
                    return null;
                }

                // The same key may occur in several slots; the aggregate is what gets extracted, so clamp by it too.
                for (var entry : amountsPerKey.object2LongEntrySet()) {
                    representableCrafts = Math.min(representableCrafts, Long.MAX_VALUE / entry.getLongValue());
                }

                var plannedOutputs = new PlannedOutput[expectedOutputs.size()];
                int outputCount = 0;
                for (var entry : expectedOutputs) {
                    if (entry.getKey() == null || entry.getLongValue() <= 0) {
                        return null;
                    }
                    plannedOutputs[outputCount++] = new PlannedOutput(entry.getKey(), entry.getLongValue());
                    representableCrafts = Math.min(representableCrafts, Long.MAX_VALUE / entry.getLongValue());
                }
                if (outputCount == 0) {
                    return null;
                }

                double consumedPowerPerCraft = 0;
                double reusablePower = 0;
                for (int index = 0; index < plannedInputs.length; index++) {
                    double slotPower = powerOf(plannedInputs[index]);
                    if (reusableSlots[index] != null) {
                        reusablePower += slotPower;
                    } else {
                        consumedPowerPerCraft += slotPower;
                    }
                }

                return new ExpansionPlan(plannedInputs, amountsPerKey, plannedOutputs,
                        consumedPowerPerCraft, reusablePower, representableCrafts, reusableSlots);
            } catch (ArithmeticException exception) {
                return null;
            }
        }

        /// Mirrors {@code CraftingCpuHelper#calculatePatternPower} for a single slot.
        private static double powerOf(PlannedInput input) {
            return (double) input.amountPerCraft() / input.key().getAmountPerOperation();
        }

        /// Caps the batch at what the network can actually pay for. AE2 charges the whole batch in one go and drops
        /// the task for this tick if the power is short, so overshooting here would cost a craft rather than gain
        /// one.
        long limitByEnergy(IEnergyService energyService, long craftCount) {
            double requestedPower = consumedPowerPerCraft * craftCount + reusablePower;
            if (!Double.isFinite(requestedPower) || requestedPower <= 0) {
                // A pattern that costs nothing cannot be sized by power, and does not need to be.
                return consumedPowerPerCraft <= 0 ? craftCount : 1;
            }

            double availablePower = energyService.extractAEPower(requestedPower, Actionable.SIMULATE,
                    PowerMultiplier.CONFIG);
            if (availablePower >= requestedPower - POWER_EPSILON) {
                return craftCount;
            }
            if (!Double.isFinite(availablePower) || availablePower <= 0 || consumedPowerPerCraft <= 0) {
                return 1;
            }

            double poweredCrafts = Math.floor(
                    (availablePower + POWER_EPSILON - reusablePower) / consumedPowerPerCraft);
            if (poweredCrafts < 2) {
                return 1;
            }
            return poweredCrafts >= craftCount ? craftCount : (long) poweredCrafts;
        }

        /// How many crafts the CPU inventory can still cover, counting the one AE2 already extracted.
        long limitByInventory(ICraftingInventory inventory, long maxCrafts) {
            long craftCount = Math.min(maxCrafts, representableCrafts);
            if (craftCount <= 1) {
                return 1;
            }

            for (var entry : amountsPerKey.object2LongEntrySet()) {
                long available = inventory.extract(entry.getKey(), Long.MAX_VALUE, Actionable.SIMULATE);
                long additionalCrafts = available / entry.getLongValue();
                if (additionalCrafts < craftCount - 1) {
                    craftCount = additionalCrafts + 1;
                    if (craftCount <= 1) {
                        return 1;
                    }
                }
            }
            return craftCount;
        }

        @Nullable
        FantasyBatchExtraction extractAdditional(ICraftingInventory inventory, KeyCounter expectedOutputs,
                long craftCount) {
            long additionalCrafts = craftCount - 1;
            var extracted = new KeyCounter[inputs.length];
            var combined = new KeyCounter[inputs.length];

            try {
                for (int index = 0; index < inputs.length; index++) {
                    var input = inputs[index];
                    var holder = combined[index] = new KeyCounter();

                    if (reusableSlots[index] != null) {
                        // Reusable container: one item serves the whole batch. The single item AE2 already
                        // extracted stays in the holder untouched and nothing extra is pulled from the inventory.
                        holder.add(input.key(), input.amountPerCraft());
                        continue;
                    }

                    long extraAmount = Math.multiplyExact(input.amountPerCraft(), additionalCrafts);
                    holder.add(input.key(), Math.multiplyExact(input.amountPerCraft(), craftCount));

                    var taken = extracted[index] = new KeyCounter();
                    long moved = inventory.extract(input.key(), extraAmount, Actionable.MODULATE);
                    if (moved > 0) {
                        taken.add(input.key(), moved);
                    }
                    if (moved != extraAmount) {
                        // limitByInventory promised this would fit. Put back exactly what this loop took and let
                        // AE2 dispatch the single craft it is still holding.
                        CraftingCpuHelper.reinjectPatternInputs(inventory, extracted);
                        return null;
                    }
                }

                expectedOutputs.reset();
                for (var output : outputs) {
                    expectedOutputs.add(output.key(), Math.multiplyExact(output.amountPerCraft(), craftCount));
                }

                // `expectedContainerItems` is deliberately left exactly as AE2 wrote it - another mod wrapping this
                // same extraction may reset or re-derive it, so a rewrite here cannot be relied on. Where the batch
                // makes AE2's single-craft assumption wrong, the job's waiting-for list is corrected instead, after
                // AE2 has finished filling it from that counter.
                var corrections = new ArrayList<RemainderCorrection>(1);
                for (var slot : reusableSlots) {
                    if (slot == null) {
                        continue;
                    }
                    AEKey worn = DurableInputAdapters.wearDownBy(
                            slot.input(), slot.key(), craftCount);
                    // An item that comes back unchanged after the whole batch needs no correction at all: what AE2
                    // recorded for one craft is already right for all of them.
                    if (slot.singleCraftRemainder().equals(worn)) {
                        continue;
                    }
                    corrections.add(new RemainderCorrection(
                            slot.singleCraftRemainder(), worn, slot.amount()));
                }
                return new FantasyBatchExtraction(combined, craftCount, List.copyOf(corrections));
            } catch (RuntimeException exception) {
                CraftingCpuHelper.reinjectPatternInputs(inventory, extracted);
                return null;
            }
        }

        /// Whether AE2's recorded remainders are exactly the ones this pattern's holder implies for a single craft.
        /// Neither counter is modified: they belong to the caller, and one of them is AE2's live bookkeeping.
        private static boolean remaindersMatch(KeyCounter derived, KeyCounter recorded) {
            int recordedEntries = 0;
            for (var entry : recorded) {
                if (entry.getLongValue() != 0) {
                    recordedEntries++;
                }
            }
            int derivedEntries = 0;
            for (var entry : derived) {
                if (entry.getLongValue() == 0) {
                    continue;
                }
                derivedEntries++;
                if (entry.getKey() == null || recorded.get(entry.getKey()) != entry.getLongValue()) {
                    return false;
                }
            }
            return derivedEntries == recordedEntries;
        }

        private static void logReusableFallback(AEKey key,
                DurableInputAdapters.Analysis analysis) {
            if (FTConfig.DIAGNOSTICS.get()) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy batch: reusable input {} is not batchable (mode={}, safeCrafts={}); "
                                + "falling back to one craft at a time",
                        key, analysis.mode(), analysis.safeCrafts());
            }
        }
    }
}
