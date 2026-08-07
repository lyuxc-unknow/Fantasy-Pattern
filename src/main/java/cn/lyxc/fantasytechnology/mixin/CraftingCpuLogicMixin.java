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

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.service.CraftingService;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.crafting.FantasyBatchExtraction;
import cn.lyxc.fantasytechnology.crafting.MolecularReusableInputAdapters;
import cn.lyxc.fantasytechnology.integration.ae2.IFantasyBatchCraftingProvider;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.LoadingModList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
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

    /// AE2-VM can plan a returned worn variant for a non-substituting AE2 pattern, while its CPU execution still
    /// reaches the normal AE2 extraction path. The compatibility fallback is therefore enabled only for AE2-VM;
    /// stock AE2 and other integrations keep their native input-validity contract.
    @Unique
    private static final boolean FANTASY_TECHNOLOGY$AE2_VM_LOADED =
            fantasyTechnology$isModLoaded("ae2vm");

    @Unique
    private static boolean fantasyTechnology$isModLoaded(String modId) {
        try {
            return LoadingModList.get().getModFileById(modId) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

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
        // AE2's extraction returns null (and reinjects everything) as soon as any input cannot
        // be satisfied by its exact (full-durability) templates. Re-extract with a worn-durability
        // fallback for reusable inputs so crystals/tools that only exist in a worn state are
        // actually dispatched instead of being counted by the plan and then never delivered.
        if (firstInputs == null && FANTASY_TECHNOLOGY$AE2_VM_LOADED) {
            firstInputs = fantasyTechnology$extractWithWearFallback(patternDetails, inventory, level,
                    expectedOutputs, expectedContainerItems);
        }
        if (firstInputs == null) {
            return null;
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
        // AE2 counts pushes, while one accepted batch represents several operations. Once a batch has spent the
        // caller's whole allowance, reject the holders AE2 continues preparing until its own push counter catches up.
        // AE2 reinjects every rejected holder before returning from executeCrafting.
        if (fantasyTechnology$usedOperations >= fantasyTechnology$operationBudget) {
            return false;
        }

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
        if (crafts > fantasyTechnology$operationBudget - fantasyTechnology$usedOperations) {
            return false;
        }
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

    /// Full re-extraction that mirrors {@code CraftingCpuHelper.extractPatternInputs}
    /// but additionally satisfies reusable (tool/catalyst) inputs with worn durability
    /// variants when the exact full-durability template is not in stock. AE2's own
    /// template scan finds worn variants via {@code findFuzzyTemplates} but filters them
    /// out with a strict {@code isValid} check; this restores them for deterministic
    /// Damage+1 wear chains. The CPU's container loop then keeps wearing the extracted
    /// tool down one durability per craft, matching how the plan counts its durability.
    /// Returns {@code null} (and reinjects) when an input cannot be satisfied at all.
    @Unique
    private KeyCounter[] fantasyTechnology$extractWithWearFallback(IPatternDetails patternDetails,
            ICraftingInventory inventory, Level level, KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems) {
        // AE2 leaves the container counter populated when its first extraction fails, even though it reinjects the
        // corresponding inputs. This is a fresh attempt, so both transient counters must be rebuilt from scratch.
        expectedOutputs.reset();
        expectedContainerItems.reset();

        var patternInputs = patternDetails.getInputs();
        KeyCounter[] inputs = new KeyCounter[patternInputs.length];
        try {
            for (int i = 0; i < patternInputs.length; i++) {
                var input = patternInputs[i];
                KeyCounter counter = inputs[i] = new KeyCounter();
                long remainingMultipliers = input.getMultiplier();
                if (remainingMultipliers <= 0) {
                    throw new IllegalStateException("Invalid pattern input multiplier");
                }

                // Mirror CraftingCpuHelper exactly: extractTemplates returns template multipliers, while the holder
                // carries template.amount() physical units. Substitutes share and decrement one remaining count.
                for (InputTemplate template : CraftingCpuHelper.getValidItemTemplates(inventory, input, level)) {
                    long extractedMultipliers = CraftingCpuHelper.extractTemplates(
                            inventory, template, remainingMultipliers);
                    if (extractedMultipliers > 0) {
                        counter.add(template.key(), Math.multiplyExact(
                                extractedMultipliers, template.amount()));
                        AEKey remainder = input.getRemainingKey(template.key());
                        if (remainder != null) {
                            expectedContainerItems.add(remainder, extractedMultipliers);
                        }
                        remainingMultipliers -= extractedMultipliers;
                        if (remainingMultipliers == 0) {
                            break;
                        }
                    }
                }

                if (remainingMultipliers > 0) {
                    remainingMultipliers = fantasyTechnology$extractWearVariants(
                            input, inventory, counter, expectedContainerItems, remainingMultipliers);
                }
                if (remainingMultipliers != 0) {
                    throw new IllegalStateException("Pattern input could not be fully extracted");
                }
            }

            for (var output : patternDetails.getOutputs()) {
                expectedOutputs.add(output.what(), output.amount());
            }
            return inputs;
        } catch (RuntimeException exception) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, inputs);
            expectedOutputs.reset();
            expectedContainerItems.reset();
            return null;
        }
    }

    @Unique
    private static long fantasyTechnology$extractWearVariants(IPatternDetails.IInput input,
            ICraftingInventory inventory, KeyCounter holder, KeyCounter expectedContainerItems,
            long remainingMultipliers) {
        var seenCandidates = new HashSet<AEKey>();
        var possibleInputs = input.getPossibleInputs();
        if (possibleInputs == null) {
            return remainingMultipliers;
        }

        for (var possible : possibleInputs) {
            if (possible == null || possible.what() == null || possible.amount() <= 0) {
                continue;
            }
            AEKey templateKey = possible.what();
            for (AEKey candidate : inventory.findFuzzyTemplates(templateKey)) {
                if (remainingMultipliers == 0) {
                    return 0;
                }
                if (candidate == null || candidate.equals(templateKey) || !seenCandidates.add(candidate)
                        || !fantasyTechnology$isSafeWearVariant(input, templateKey, candidate)) {
                    continue;
                }

                long requestedAmount = Math.multiplyExact(possible.amount(), remainingMultipliers);
                long available = inventory.extract(candidate, requestedAmount, Actionable.SIMULATE);
                long extractedMultipliers = Math.min(
                        remainingMultipliers, available / possible.amount());
                if (extractedMultipliers <= 0) {
                    continue;
                }
                long extractedAmount = Math.multiplyExact(possible.amount(), extractedMultipliers);
                long extracted = inventory.extract(candidate, extractedAmount, Actionable.MODULATE);
                if (extracted != extractedAmount) {
                    throw new IllegalStateException("Crafting inventory changed during worn-tool extraction");
                }

                holder.add(candidate, extractedAmount);
                AEKey remainder = input.getRemainingKey(candidate);
                if (remainder != null) {
                    expectedContainerItems.add(remainder, extractedMultipliers);
                }
                remainingMultipliers -= extractedMultipliers;
                if (FTConfig.DIAGNOSTICS.get()) {
                    FantasyTechnology.LOGGER.info(
                            "Fantasy wear fallback: input {} satisfied by worn {} x{} (remaining={})",
                            templateKey, candidate, extractedAmount,
                            MolecularReusableInputAdapters.remainingDurabilityCrafts(candidate));
                }
            }
        }
        return remainingMultipliers;
    }

    /// AE2-VM may plan a returned worn tool for a non-substituting AE2 pattern. Admit that exact case only: every
    /// data component except DAMAGE must match the encoded template, and the real recipe remainder must be Damage+1
    /// (or consume the final durability point). This deliberately does not relax arbitrary input validity.
    @Unique
    private static boolean fantasyTechnology$isSafeWearVariant(IPatternDetails.IInput input,
            AEKey templateKey, AEKey candidate) {
        if (!(templateKey instanceof AEItemKey templateItem)
                || !(candidate instanceof AEItemKey candidateItem)) {
            return false;
        }
        if (!templateItem.getItem().equals(candidateItem.getItem())) {
            return false;
        }
        ItemStack templateStack = templateItem.toStack();
        if (!templateStack.isDamageableItem() || templateStack.getMaxDamage() <= 0
                || templateStack.has(DataComponents.UNBREAKABLE)) {
            return false;
        }
        ItemStack candidateStack = candidateItem.toStack();
        if (!candidateStack.isDamageableItem()
                || candidateStack.getDamageValue() <= templateStack.getDamageValue()
                || candidateStack.getDamageValue() > candidateStack.getMaxDamage()
                || candidateStack.has(DataComponents.UNBREAKABLE)) {
            return false;
        }
        for (var enchantment : candidateStack.getEnchantments().keySet()) {
            if (enchantment.is(Enchantments.UNBREAKING)) {
                return false;
            }
        }

        ItemStack normalizedCandidate = candidateStack.copy();
        normalizedCandidate.setDamageValue(templateStack.getDamageValue());
        AEItemKey normalizedKey = AEItemKey.of(normalizedCandidate);
        if (!templateItem.equals(normalizedKey)) {
            return false;
        }

        AEKey remainder = input.getRemainingKey(candidate);
        return remainder == null
                ? MolecularReusableInputAdapters.remainingDurabilityCrafts(candidate) == 1
                : MolecularReusableInputAdapters.isExactDamageStep(candidate, remainder);
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
