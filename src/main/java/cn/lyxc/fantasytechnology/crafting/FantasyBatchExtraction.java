/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
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
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

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

    private FantasyBatchExtraction(KeyCounter[] inputs, long craftCount) {
        this.inputs = inputs;
        this.craftCount = craftCount;
    }

    /// The complete {@code N}-craft input holder, ready to be handed to the provider in place of AE2's original one.
    public KeyCounter[] inputs() {
        return inputs;
    }

    public long craftCount() {
        return craftCount;
    }

    /// Extracts the additional {@code maxCrafts - 1} recipes, or returns {@code null} to keep AE2's single craft.
    ///
    /// On success {@code expectedOutputs} is rewritten to the scaled amounts, because the CPU adds it to its
    /// waiting-for list right after the push and would otherwise expect a single recipe's worth of results.
    @Nullable
    public static FantasyBatchExtraction expand(IPatternDetails patternDetails, ICraftingInventory inventory,
            IEnergyService energyService, KeyCounter[] firstInputs, KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems, long maxCrafts) {
        if (maxCrafts <= 1) {
            return null;
        }

        var plan = ExpansionPlan.of(patternDetails, firstInputs, expectedOutputs, expectedContainerItems);
        if (plan == null) {
            return null;
        }

        try {
            long craftCount = plan.limitByInventory(inventory, maxCrafts);
            if (craftCount <= 1) {
                return null;
            }
            craftCount = limitByEnergy(energyService, plan.powerPerCraft, craftCount);
            if (craftCount <= 1) {
                return null;
            }
            return plan.extractAdditional(inventory, expectedOutputs, craftCount);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /// Caps the batch at what the network can actually pay for. AE2 charges the whole batch in one go and drops the
    /// task for this tick if the power is short, so overshooting here would cost a craft rather than gain one.
    private static long limitByEnergy(IEnergyService energyService, double powerPerCraft, long craftCount) {
        if (!Double.isFinite(powerPerCraft) || powerPerCraft <= 0) {
            // A pattern that costs nothing per craft cannot be sized by power, and does not need to be.
            return craftCount;
        }

        double requestedPower = powerPerCraft * craftCount;
        if (!Double.isFinite(requestedPower)) {
            requestedPower = Double.MAX_VALUE;
        }
        double availablePower = energyService.extractAEPower(requestedPower, Actionable.SIMULATE,
                PowerMultiplier.CONFIG);
        if (availablePower >= requestedPower - POWER_EPSILON) {
            return craftCount;
        }
        if (!Double.isFinite(availablePower) || availablePower <= 0) {
            return 1;
        }

        double poweredCrafts = Math.floor((availablePower + POWER_EPSILON) / powerPerCraft);
        if (poweredCrafts < 2) {
            return 1;
        }
        return poweredCrafts >= craftCount ? craftCount : (long) poweredCrafts;
    }

    /// One material slot of the recipe as AE2 actually extracted it.
    private record PlannedInput(AEKey key, long amountPerCraft) {
    }

    private record PlannedOutput(AEKey key, long amountPerCraft) {
    }

    private static final class ExpansionPlan {
        private final PlannedInput[] inputs;
        private final Object2LongOpenHashMap<AEKey> amountsPerKey;
        private final PlannedOutput[] outputs;
        private final double powerPerCraft;
        private final long representableCrafts;

        private ExpansionPlan(PlannedInput[] inputs, Object2LongOpenHashMap<AEKey> amountsPerKey,
                PlannedOutput[] outputs, double powerPerCraft, long representableCrafts) {
            this.inputs = inputs;
            this.amountsPerKey = amountsPerKey;
            this.outputs = outputs;
            this.powerPerCraft = powerPerCraft;
            this.representableCrafts = representableCrafts;
        }

        @Nullable
        static ExpansionPlan of(IPatternDetails patternDetails, KeyCounter[] firstInputs,
                KeyCounter expectedOutputs, KeyCounter expectedContainerItems) {
            // Container items rule out batching outright: AE2 counts the returned tool once per craft, and the worn
            // one has to come back before the next craft can borrow it.
            if (patternDetails == null || firstInputs == null || expectedOutputs == null
                    || expectedContainerItems == null || !expectedContainerItems.isEmpty()) {
                return null;
            }

            var patternInputs = patternDetails.getInputs();
            if (patternInputs == null || firstInputs.length != patternInputs.length) {
                return null;
            }

            var plannedInputs = new PlannedInput[firstInputs.length];
            var amountsPerKey = new Object2LongOpenHashMap<AEKey>(firstInputs.length);
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

                    long amount = entry.getLongValue();
                    plannedInputs[index] = new PlannedInput(entry.getKey(), amount);
                    amountsPerKey.put(entry.getKey(),
                            Math.addExact(amountsPerKey.getLong(entry.getKey()), amount));
                    representableCrafts = Math.min(representableCrafts, Long.MAX_VALUE / amount);
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

                return new ExpansionPlan(plannedInputs, amountsPerKey, plannedOutputs,
                        CraftingCpuHelper.calculatePatternPower(firstInputs), representableCrafts);
            } catch (ArithmeticException exception) {
                return null;
            }
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
                    long extraAmount = Math.multiplyExact(input.amountPerCraft(), additionalCrafts);

                    var holder = combined[index] = new KeyCounter();
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
                return new FantasyBatchExtraction(combined, craftCount);
            } catch (RuntimeException exception) {
                CraftingCpuHelper.reinjectPatternInputs(inventory, extracted);
                return null;
            }
        }
    }
}
