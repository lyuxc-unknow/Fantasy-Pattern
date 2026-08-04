/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
 */

package cn.lyxc.fantasytechnology.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.inv.CraftingSimulationState;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.crafting.maxfast.OmniMaxFastMode;
import cn.lyxc.fantasytechnology.crafting.maxfast.OmniMaxFastPlanner;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Semaphore;

/// Routes crafting calculations through the aggregated planner and keeps the number of them in flight sane.
///
/// AE2 hands every simulation the same slice of the server's per-tick calculation budget, split across however many
/// are registered, so a burst of requests makes each of them crawl. Letting only a few run at a time - with one lane
/// reserved for a player waiting at a terminal - finishes them sooner without spending more time per tick.
@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class OmniCraftingCalculationMixin implements OmniMaxFastPlanner.CalculationBridge {

    @Unique
    private static final int FANTASY_TECHNOLOGY$CONCURRENT_CALCULATIONS =
            Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));
    @Unique
    private static final Semaphore FANTASY_TECHNOLOGY$CALCULATION_SLOTS =
            new Semaphore(FANTASY_TECHNOLOGY$CONCURRENT_CALCULATIONS, true);
    @Unique
    private static final Semaphore FANTASY_TECHNOLOGY$INTERACTIVE_SLOT = new Semaphore(1, true);

    @Shadow
    @Final
    private KeyCounter missing;

    @Shadow
    abstract void handlePausing() throws InterruptedException;

    @Shadow
    public abstract boolean isSimulation();

    @Unique
    private boolean fantasyTechnology$plannerEnabled;
    @Unique
    private boolean fantasyTechnology$interactiveRequest;
    @Unique
    private OmniMaxFastPlanner.Session fantasyTechnology$session;
    @Unique
    private long fantasyTechnology$aggregatedNodeCount = -1;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fantasyTechnology$detectFantasyProvider(Level level, IGrid grid,
            ICraftingSimulationRequester requester, GenericStack output, CalculationStrategy strategy,
            CallbackInfo callback) {
        // The fantasy annihilation block is what switches the planner on: no block, no behavioural change to AE2.
        fantasyTechnology$plannerEnabled = grid.getMachines(FantasyAnnihilationBlockEntity.class)
                .iterator().hasNext();
        var source = requester.getActionSource();
        fantasyTechnology$interactiveRequest = source != null && source.player().isPresent();
    }

    // ------------------------------------------------------------------------
    // OmniMaxFastPlanner.CalculationBridge
    // ------------------------------------------------------------------------

    @Override
    public void fantasyTechnology$pause() throws InterruptedException {
        handlePausing();
    }

    @Override
    public boolean fantasyTechnology$simulating() {
        return isSimulation();
    }

    @Override
    public KeyCounter fantasyTechnology$missingItems() {
        return missing;
    }

    // ------------------------------------------------------------------------
    // Scheduling
    // ------------------------------------------------------------------------

    @WrapMethod(method = "run")
    private ICraftingPlan fantasyTechnology$throttleCalculation(Operation<ICraftingPlan> original) {
        if (!fantasyTechnology$plannerEnabled) {
            return original.call();
        }

        // Acquired before run() registers the simulation with the tick handler, so a queued calculation is not yet
        // competing for the budget and the main thread never waits on one.
        boolean backgroundSlot = false;
        boolean interactiveSlot = false;
        if (fantasyTechnology$interactiveRequest) {
            backgroundSlot = FANTASY_TECHNOLOGY$CALCULATION_SLOTS.tryAcquire();
            if (!backgroundSlot) {
                FANTASY_TECHNOLOGY$INTERACTIVE_SLOT.acquireUninterruptibly();
                interactiveSlot = true;
            }
        } else {
            FANTASY_TECHNOLOGY$CALCULATION_SLOTS.acquireUninterruptibly();
            backgroundSlot = true;
        }
        try {
            return original.call();
        } finally {
            if (backgroundSlot) {
                FANTASY_TECHNOLOGY$CALCULATION_SLOTS.release();
            }
            if (interactiveSlot) {
                FANTASY_TECHNOLOGY$INTERACTIVE_SLOT.release();
            }
        }
    }

    // ------------------------------------------------------------------------
    // Aggregated planning
    // ------------------------------------------------------------------------

    @WrapOperation(method = "runCraftAttempt", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/CraftingTreeNode;request(Lappeng/crafting/inv/CraftingSimulationState;JLappeng/api/stacks/KeyCounter;)V"))
    private void fantasyTechnology$aggregateRecipeTree(CraftingTreeNode tree, CraftingSimulationState inventory,
            long requestedAmount, KeyCounter containerItems, Operation<Void> original)
            throws CraftBranchFailure, InterruptedException {
        fantasyTechnology$aggregatedNodeCount = -1;
        if (!fantasyTechnology$plannerEnabled || containerItems != null
                || FTConfig.MAX_FAST_MODE.get() != OmniMaxFastMode.SAFE) {
            original.call(tree, inventory, requestedAmount, containerItems);
            return;
        }

        var session = fantasyTechnology$session;
        if (session == null) {
            session = new OmniMaxFastPlanner.Session(FTConfig.MAX_FAST_MAX_NODES.get(),
                    FTConfig.MAX_FAST_COMPILE_BUDGET_MS.get(), this);
            fantasyTechnology$session = session;
        }

        var result = session.tryExecute(tree, inventory, requestedAmount);
        if (result.branchFailure() != null) {
            throw result.branchFailure();
        }
        if (result.applied()) {
            // AE2 multiplies this by eight for the plan's byte cost; keep the product representable.
            fantasyTechnology$aggregatedNodeCount = Math.min(result.logicalNodeCount(), Long.MAX_VALUE / 8);
            if (FTConfig.DIAGNOSTICS.get()) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy planner applied: amount={}, uniqueNodes={}, mergedOccurrences={}, barriers={}, logicalNodes={}, compileMs={}, executeMs={}",
                        requestedAmount, result.uniqueNodes(), result.mergedOccurrences(), result.barrierCount(),
                        result.logicalNodeCount(), result.compileNanos() / 1_000_000.0,
                        result.executionNanos() / 1_000_000.0);
            }
            return;
        }

        if (result.error() != null) {
            FantasyTechnology.LOGGER.warn(
                    "Fantasy planner hit an internal compatibility error and fell back to AE2", result.error());
        } else if (FTConfig.DIAGNOSTICS.get()) {
            FantasyTechnology.LOGGER.info(
                    "Fantasy planner fallback: amount={}, reason={}, uniqueNodes={}, mergedOccurrences={}, barriers={}, compileMs={}, executeMs={}",
                    requestedAmount, result.fallbackReason(), result.uniqueNodes(), result.mergedOccurrences(),
                    result.barrierCount(), result.compileNanos() / 1_000_000.0,
                    result.executionNanos() / 1_000_000.0);
        }
        original.call(tree, inventory, requestedAmount, containerItems);
    }

    /// The tree only holds the nodes the planner actually expanded, so its own count would understate the plan.
    @WrapOperation(method = "runCraftAttempt", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/CraftingTreeNode;getNodeCount()J"))
    private long fantasyTechnology$useAggregatedNodeCount(CraftingTreeNode tree, Operation<Long> original) {
        return fantasyTechnology$aggregatedNodeCount >= 0
                ? fantasyTechnology$aggregatedNodeCount
                : original.call(tree);
    }
}
