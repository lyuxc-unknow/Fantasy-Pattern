/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
 */

package cn.lyxc.fantasytechnology.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.service.CraftingService;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
    }

    /// AE2 counts one pushed pattern per push; add the repetitions that rode along inside batches so the caller
    /// subtracts the real number of crafts from its remaining operations.
    @ModifyReturnValue(method = "executeCrafting", at = @At("RETURN"))
    private int fantasyTechnology$reportBatchedCrafts(int pushedPatterns) {
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

        long maxCrafts = fantasyTechnology$plannedBatchSize(patternDetails, expectedOutputs, expectedContainerItems);
        if (maxCrafts <= 1) {
            return firstInputs;
        }

        var batch = FantasyBatchExtraction.expand(patternDetails, inventory, fantasyTechnology$energyService,
                firstInputs, expectedOutputs, expectedContainerItems, maxCrafts);
        if (batch == null) {
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
        if (IFantasyBatchCraftingProvider.batchLimitOf(provider, patternDetails) < crafts) {
            return false;
        }

        var batchProvider = (IFantasyBatchCraftingProvider) provider;
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
            fantasyTechnology$batchPattern = null;
            fantasyTechnology$batch = null;
        }
        return accepted;
    }

    /// How many repetitions of this pattern may be dispatched at once, or {@code 0} for AE2's default behaviour.
    /// Ordered cheapest check first, because this runs once per craft the CPU pushes.
    @Unique
    private long fantasyTechnology$plannedBatchSize(IPatternDetails patternDetails, KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems) {
        if (!FTConfig.BATCH_DISPATCH_ENABLED.get() || !expectedContainerItems.isEmpty()) {
            return 0;
        }

        long remainingOperations = fantasyTechnology$operationBudget - fantasyTechnology$usedOperations;
        if (remainingOperations <= 1) {
            return 0;
        }

        var currentJob = job;
        var craftingService = fantasyTechnology$craftingService;
        if (currentJob == null || craftingService == null || fantasyTechnology$energyService == null) {
            return 0;
        }

        var task = fantasyTechnology$findTask(patternDetails);
        if (task == null) {
            return 0;
        }
        long remainingCrafts = task.fantasyTechnology$getValue();
        if (remainingCrafts <= 1) {
            return 0;
        }

        long providerLimit = fantasyTechnology$firstProviderBatchLimit(craftingService, patternDetails);
        if (providerLimit <= 1) {
            return 0;
        }

        long waitingLimit = fantasyTechnology$waitingForLimit(expectedOutputs);
        if (waitingLimit <= 1) {
            return 0;
        }

        return Math.min(Math.min(remainingOperations, remainingCrafts), Math.min(providerLimit, waitingLimit));
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
