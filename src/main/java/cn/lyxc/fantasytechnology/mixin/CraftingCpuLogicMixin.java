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

package cn.lyxc.fantasytechnology.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.service.CraftingService;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.crafting.FantasyBatchExtraction;
import cn.lyxc.fantasytechnology.integration.ae2.IFantasyBatchCraftingProvider;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/// Lets a crafting CPU dispatch several repetitions of one recipe in a single push.
///
/// AE2 executes a task by extracting the materials for one recipe, handing them to a provider, and repeating - one
/// full inventory walk and one push per craft. When the provider that would receive the recipe declares itself
/// batch-capable ({@link IFantasyBatchCraftingProvider}, implemented here by the fantasy annihilation block), the
/// extraction is grown to N recipes with a single extract per material and the whole set is pushed once. The task
/// counter is adjusted by the same N, and the return value of {@code executeCrafting} reports all N crafts, so the
/// CPU's per-tick operation budget and co-processor scaling stay exactly what they were: this trades N inventory
/// walks for one, it does not hand out extra throughput.
///
/// Everything that cannot be verified falls back to AE2's original one-craft-at-a-time behaviour.
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicMixin {

    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    public abstract long getWaitingFor(AEKey template);

    @Unique
    private CraftingService fantasyTechnology$craftingService;
    @Unique
    private IEnergyService fantasyTechnology$energyService;

    /// Operations this {@code executeCrafting} call may still spend, and how many it has spent. AE2 counts one per
    /// push; a batch of N counts as N so that batching never widens the tick budget.
    @Unique
    private int fantasyTechnology$operationBudget;
    @Unique
    private long fantasyTechnology$usedOperations;
    @Unique
    private long fantasyTechnology$batchedExtraCrafts;

    @Unique
    private IPatternDetails fantasyTechnology$batchPattern;
    @Unique
    private FantasyBatchExtraction fantasyTechnology$batch;

    /// Waiting-for swaps owed by batches pushed during this {@code executeCrafting} call; see
    /// {@link FantasyBatchExtraction.RemainderCorrection}.
    @Unique
    private List<FantasyBatchExtraction.RemainderCorrection> fantasyTechnology$remainderCorrections;

    /// Safety net for a batch whose corrections never reached the return hook - an exception unwinding out of
    /// {@code executeCrafting} would otherwise strand the job waiting on a key nothing returns. The provider only
    /// flushes its results on a later tick, so applying them here is still in time.
    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void fantasyTechnology$flushStrandedCorrections(IEnergyService energyService,
            CraftingService craftingService, CallbackInfo callback) {
        fantasyTechnology$applyRemainderCorrections();
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void fantasyTechnology$beginBatchContext(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> callback) {
        fantasyTechnology$craftingService = craftingService;
        fantasyTechnology$energyService = energyService;
        fantasyTechnology$operationBudget = maxPatterns;
        fantasyTechnology$usedOperations = 0;
        fantasyTechnology$batchedExtraCrafts = 0;
        fantasyTechnology$batchPattern = null;
        fantasyTechnology$batch = null;
        fantasyTechnology$remainderCorrections = null;
    }

    /// AE2 counts one pushed pattern per push; add the repetitions that rode along inside batches so the caller
    /// subtracts the real number of crafts from its remaining operations.
    ///
    /// This is also the first moment at which every push of this call has been registered in the job's waiting-for
    /// list, so it is where a batch that wore a tool down further than AE2 assumed puts the list straight.
    @ModifyReturnValue(method = "executeCrafting", at = @At("RETURN"))
    private int fantasyTechnology$reportBatchedCrafts(int pushedPatterns) {
        fantasyTechnology$applyRemainderCorrections();
        long extra = fantasyTechnology$batchedExtraCrafts;
        fantasyTechnology$craftingService = null;
        fantasyTechnology$energyService = null;
        fantasyTechnology$batchPattern = null;
        fantasyTechnology$batch = null;
        fantasyTechnology$batchedExtraCrafts = 0;
        if (extra <= 0 || pushedPatterns <= 0) {
            return pushedPatterns;
        }
        return (int) Math.min(Integer.MAX_VALUE, pushedPatterns + extra);
    }

    /// Replaces the single-craft remainders AE2 registered for a batched reusable input with what the provider will
    /// actually hand back after the whole batch.
    ///
    /// Both halves are self-correcting: the amount that is really coming is inserted, and the amount AE2 assumed is
    /// taken back out. If some other wrapper prevented AE2 from registering its assumption in the first place, the
    /// removal simply finds nothing and the insert alone is already the right answer.
    @Unique
    private void fantasyTechnology$applyRemainderCorrections() {
        var corrections = fantasyTechnology$remainderCorrections;
        if (corrections == null || corrections.isEmpty()) {
            fantasyTechnology$remainderCorrections = null;
            return;
        }
        var currentJob = job;
        if (currentJob == null) {
            // No job means nothing is waiting for anything; the corrections have nowhere to go and no effect.
            fantasyTechnology$remainderCorrections = null;
            return;
        }
        var waitingFor = ((ExecutingCraftingJobAccessor) currentJob).fantasyTechnology$getWaitingFor();
        if (waitingFor == null) {
            // Keep them: a later tick may find the list again, and dropping them strands the job.
            return;
        }

        fantasyTechnology$remainderCorrections = null;
        for (var correction : corrections) {
            if (correction.returnedKey() != null) {
                waitingFor.insert(correction.returnedKey(), correction.amount(), Actionable.MODULATE);
            }
            long removed = waitingFor.extract(correction.assumedKey(), correction.amount(), Actionable.MODULATE);
            if (removed != correction.amount() && FTConfig.DIAGNOSTICS.get()) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy batch: waiting-for held {} of {} x{} to swap for {}; another wrapper had already "
                                + "changed the CPU's expectation",
                        removed, correction.assumedKey(), correction.amount(), correction.returnedKey());
            }
        }
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter[] fantasyTechnology$extractBatch(IPatternDetails patternDetails,
            ICraftingInventory inventory, Level level, KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems, Operation<KeyCounter[]> original) {
        fantasyTechnology$batchPattern = null;
        fantasyTechnology$batch = null;

        var firstInputs = original.call(patternDetails, inventory, level, expectedOutputs, expectedContainerItems);
        if (firstInputs == null) {
            return null;
        }
        // When OmniSequence's own crafting-CPU mixin is installed alongside this mod and an Omni-Computation Core
        // is on the same network, its (outer) extraction wrap has already grown the holder to N recipes before we
        // see it. Re-expanding that would square the craft count, so a holder that already carries more than one
        // recipe is passed through untouched.
        if (fantasyTechnology$alreadyScaled(patternDetails, firstInputs)) {
            return firstInputs;
        }

        long maxCrafts = fantasyTechnology$plannedBatchSize(patternDetails, expectedOutputs, expectedContainerItems);
        if (maxCrafts <= 1) {
            if (FTConfig.DIAGNOSTICS.get() && !expectedContainerItems.isEmpty()) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy batch: container pattern rejected by size checks (maxCrafts={}, containers={}), "
                                + "falling back to one craft at a time",
                        maxCrafts, expectedContainerItems);
            }
            return firstInputs;
        }

        var batch = FantasyBatchExtraction.expand(patternDetails, inventory, fantasyTechnology$energyService,
                level, firstInputs, expectedOutputs, expectedContainerItems, maxCrafts);
        if (batch == null) {
            if (FTConfig.DIAGNOSTICS.get() && !expectedContainerItems.isEmpty()) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy batch: container pattern expansion failed (maxCrafts={}, containers={}), "
                                + "falling back to one craft at a time",
                        maxCrafts, expectedContainerItems);
            }
            return firstInputs;
        }

        fantasyTechnology$batchPattern = patternDetails;
        fantasyTechnology$batch = batch;
        return batch.inputs();
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean fantasyTechnology$pushBatch(ICraftingProvider provider, IPatternDetails patternDetails,
            KeyCounter[] inputs, Operation<Boolean> original) {
        var batch = fantasyTechnology$batch;
        boolean batched = batch != null && fantasyTechnology$batchPattern == patternDetails
                && batch.inputs() == inputs;
        if (!batched) {
            boolean accepted = original.call(provider, patternDetails, inputs);
            if (accepted) {
                fantasyTechnology$usedOperations++;
            }
            return accepted;
        }

        long crafts = batch.craftCount();
        // The provider that gets the push has to be the one the batch was sized against. If AE2 reached a different
        // one, refuse rather than hand an N-fold recipe to something that only promised to take a single craft; AE2
        // reinjects the whole holder and retries next tick.
        if (!(provider instanceof IFantasyBatchCraftingProvider batchProvider)
                || batchProvider.fantasyTechnology$batchLimit(patternDetails) < crafts) {
            return false;
        }

        var task = fantasyTechnology$findTask(patternDetails);
        if (task == null) {
            return false;
        }
        long originalTaskValue = task.fantasyTechnology$getValue();
        if (originalTaskValue < crafts) {
            return false;
        }
        // AE2 decrements by one right after a successful push, so leave it one above the intended remainder.
        task.fantasyTechnology$setValue(originalTaskValue - crafts + 1);

        boolean accepted = false;
        try {
            batchProvider.fantasyTechnology$beginBatch(crafts);
            try {
                accepted = original.call(provider, patternDetails, inputs);
            } finally {
                batchProvider.fantasyTechnology$endBatch();
            }
        } finally {
            if (!accepted) {
                task.fantasyTechnology$setValue(originalTaskValue);
            }
        }

        if (accepted) {
            fantasyTechnology$usedOperations += crafts;
            fantasyTechnology$batchedExtraCrafts += crafts - 1;
            if (!batch.remainderCorrections().isEmpty()) {
                if (fantasyTechnology$remainderCorrections == null) {
                    fantasyTechnology$remainderCorrections = new ArrayList<>();
                }
                fantasyTechnology$remainderCorrections.addAll(batch.remainderCorrections());
            }
            fantasyTechnology$batchPattern = null;
            fantasyTechnology$batch = null;
        }
        return accepted;
    }

    /// True when {@code inputs} already carries more than one complete recipe: either a previous batch push of ours
    /// or an expansion performed by OmniSequence's own crafting-CPU mixin. Either way it must not be expanded again.
    @Unique
    private static boolean fantasyTechnology$alreadyScaled(IPatternDetails patternDetails, KeyCounter[] inputs) {
        var patternInputs = patternDetails.getInputs();
        for (int i = 0; i < patternInputs.length && i < inputs.length; i++) {
            long single = patternInputs[i].getMultiplier();
            if (single <= 0) {
                continue;
            }
            KeyCounter counter = inputs[i];
            long total = 0;
            if (counter != null) {
                for (var entry : counter) {
                    total += entry.getLongValue();
                }
            }
            if (total > single) {
                return true;
            }
        }
        return false;
    }

    /// How many repetitions of this pattern may be dispatched at once, or {@code 0} for AE2's default behaviour.
    /// Ordered cheapest check first, because this runs once per craft the CPU pushes.
    @Unique
    private long fantasyTechnology$plannedBatchSize(IPatternDetails patternDetails, KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems) {
        // Container patterns are the rare case; with diagnostics on, log the factor values so a rejected batch can
        // be diagnosed from the log alone (which check returned 0).
        boolean containers = FTConfig.DIAGNOSTICS.get() && !expectedContainerItems.isEmpty();
        if (!FTConfig.BATCH_DISPATCH_ENABLED.get()) {
            if (containers) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy batch diagnose: disabled by config");
            }
            return 0;
        }

        // Note: container items (reusable crystals, worn tools) are NOT rejected here any more. The extraction
        // plan analyses them for deterministic wear and batching proceeds when every container can serve the whole
        // batch; otherwise the plan itself falls back to a single craft.
        long remainingOperations = fantasyTechnology$operationBudget - fantasyTechnology$usedOperations;
        if (remainingOperations <= 0) {
            // No push budget left this tick. One remaining operation is enough for a batch: N crafts collapse into
            // one push and the CPU's budget is debited by N afterwards, exactly as if they had been pushed one by
            // one - this trades N inventory walks for one, it does not widen the tick budget.
            if (containers) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy batch diagnose: no operation budget left (ops={})", remainingOperations);
            }
            return 0;
        }

        var currentJob = job;
        var craftingService = fantasyTechnology$craftingService;
        if (currentJob == null || craftingService == null || fantasyTechnology$energyService == null) {
            if (containers) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy batch diagnose: no job/service context (job={}, service={}, energy={})",
                        currentJob != null, craftingService != null, fantasyTechnology$energyService != null);
            }
            return 0;
        }

        var task = fantasyTechnology$findTask(patternDetails);
        if (task == null) {
            if (containers) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy batch diagnose: task lookup failed for {}", patternDetails);
            }
            return 0;
        }
        long remainingCrafts = task.fantasyTechnology$getValue();
        if (remainingCrafts <= 1) {
            if (containers) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy batch diagnose: task nearly done (crafts={})", remainingCrafts);
            }
            return 0;
        }

        long providerLimit = fantasyTechnology$firstProviderBatchLimit(craftingService, patternDetails);
        if (providerLimit <= 1) {
            if (containers) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy batch diagnose: provider limit too small ({})", providerLimit);
            }
            return 0;
        }

        long waitingLimit = fantasyTechnology$waitingForLimit(expectedOutputs);
        if (waitingLimit <= 1) {
            if (containers) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy batch diagnose: waiting-for headroom too small ({})", waitingLimit);
            }
            return 0;
        }

        long batch = Math.min(Math.min(remainingOperations, remainingCrafts), Math.min(providerLimit, waitingLimit));
        if (containers) {
            FantasyTechnology.LOGGER.info(
                    "Fantasy batch diagnose: ops={} crafts={} provider={} waiting={} batch={}",
                    remainingOperations, remainingCrafts, providerLimit, waitingLimit, batch);
        }
        return batch;
    }

    /// The batch size offered by the provider AE2 will actually reach first - it walks providers in priority order
    /// and skips the busy ones, so sizing against anything else risks building a batch nobody accepts.
    @Unique
    private static long fantasyTechnology$firstProviderBatchLimit(CraftingService craftingService,
            IPatternDetails patternDetails) {
        try {
            var providers = craftingService.getProviders(patternDetails);
            if (providers == null) {
                return 0;
            }
            for (var provider : providers) {
                if (provider == null || provider.isBusy()) {
                    continue;
                }
                return IFantasyBatchCraftingProvider.batchLimitOf(provider, patternDetails);
            }
            return 0;
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    /// Keeps the batch small enough that scaling the expected outputs cannot overflow the job's waiting-for list.
    @Unique
    private long fantasyTechnology$waitingForLimit(KeyCounter expectedOutputs) {
        long limit = Long.MAX_VALUE;
        for (var entry : expectedOutputs) {
            var key = entry.getKey();
            long amountPerCraft = entry.getLongValue();
            if (key == null || amountPerCraft <= 0) {
                return 0;
            }
            long alreadyWaiting = getWaitingFor(key);
            if (alreadyWaiting < 0) {
                return 0;
            }
            limit = Math.min(limit, (Long.MAX_VALUE - alreadyWaiting) / amountPerCraft);
            if (limit <= 1) {
                return limit;
            }
        }
        return limit;
    }

    @Unique
    @Nullable
    private CraftingTaskProgressAccessor fantasyTechnology$findTask(IPatternDetails patternDetails) {
        var currentJob = job;
        if (currentJob == null) {
            return null;
        }
        var task = ((ExecutingCraftingJobAccessor) currentJob).fantasyTechnology$getTasks().get(patternDetails);
        return task instanceof CraftingTaskProgressAccessor accessor ? accessor : null;
    }
}
