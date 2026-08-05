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

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.crafting.maxfast.OmniMaxFastMode;
import cn.lyxc.fantasytechnology.crafting.maxfast.OmniMaxFastPlanner;
import cn.lyxc.fantasytechnology.integration.ae2.OmniCraftingTreeNodeBridge;
import cn.lyxc.fantasytechnology.integration.ae2.OmniCraftingTreeProcessBridge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.concurrent.Semaphore;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class OmniCraftingCalculationMixin {
    @Unique
    private static final int FANTASY_TECHNOLOGY$MAX_BACKGROUND_CALCULATIONS =
            Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));
    @Unique
    private static final Semaphore FANTASY_TECHNOLOGY$CALCULATION_SLOTS =
            new Semaphore(FANTASY_TECHNOLOGY$MAX_BACKGROUND_CALCULATIONS, true);
    @Unique
    private static final Semaphore FANTASY_TECHNOLOGY$INTERACTIVE_SLOT = new Semaphore(1, true);

    @Shadow
    abstract void handlePausing() throws InterruptedException;

    @Shadow
    public abstract KeyCounter getMissingItems();

    @Shadow
    public abstract boolean isSimulation();

    @Unique
    private boolean fantasytechnology$providerOnGrid;
    @Unique
    private boolean fantasytechnology$interactiveRequest;
    @Unique
    private OmniMaxFastPlanner.Session fantasytechnology$maxFastSession;
    @Unique
    private long fantasytechnology$maxFastNodeCount = -1;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fantasytechnology$detectFantasyProvider(Level level, IGrid grid,
            ICraftingSimulationRequester requester, GenericStack output,
            CalculationStrategy strategy, CallbackInfo callback) {
        // A cheap pre-filter only: without an annihilation block on the grid no fantasy pattern can be reached, so
        // AE2 is left completely alone. Having one is *not* a licence to aggregate every job on that grid - the
        // planner still refuses any tree that turns out not to use a fantasy pattern.
        fantasytechnology$providerOnGrid = grid.getMachines(FantasyAnnihilationBlockEntity.class)
                .iterator().hasNext();
        var source = requester.getActionSource();
        fantasytechnology$interactiveRequest = source != null && source.player().isPresent();
    }

    @WrapMethod(method = "run")
    private ICraftingPlan fantasytechnology$trackOmniCalculation(
            Operation<ICraftingPlan> original) {
        if (!fantasytechnology$providerOnGrid) {
            return original.call();
        }

        boolean backgroundSlot = false;
        boolean interactiveSlot = false;
        if (fantasytechnology$interactiveRequest) {
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


    @WrapOperation(method = "runCraftAttempt", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/CraftingTreeNode;request(Lappeng/crafting/inv/CraftingSimulationState;JLappeng/api/stacks/KeyCounter;)V"))
    private void fantasytechnology$aggregateSafeRecipeTree(CraftingTreeNode tree,
            CraftingSimulationState inventory, long requestedAmount, KeyCounter containerItems,
            Operation<Void> original) throws CraftBranchFailure, InterruptedException {
        fantasytechnology$maxFastNodeCount = -1;
        if (!fantasytechnology$providerOnGrid
                || containerItems != null || FTConfig.MAX_FAST_MODE.get() != OmniMaxFastMode.SAFE) {
            original.call(tree, inventory, requestedAmount, containerItems);
            return;
        }

        var session = fantasytechnology$maxFastSession;
        if (session == null) {
            session = new OmniMaxFastPlanner.Session(
                    FTConfig.MAX_FAST_MAX_NODES.get(),
                    FTConfig.MAX_FAST_COMPILE_BUDGET_MS.get(),
                    this::handlePausing);
            fantasytechnology$maxFastSession = session;
        }

        KeyCounter missingItems = getMissingItems();
        var missingSnapshot = new KeyCounter();
        missingSnapshot.addAll(missingItems);
        var possibleSnapshot = fantasytechnology$snapshotPossibleStates(tree);

        OmniMaxFastPlanner.Result result;
        try {
            result = session.tryExecute(
                    tree, inventory, requestedAmount, isSimulation(), missingItems);
        } catch (InterruptedException | RuntimeException | Error failure) {
            fantasytechnology$restoreAttemptState(
                    tree, missingItems, missingSnapshot, possibleSnapshot);
            throw failure;
        }
        if (!result.applied()) {
            fantasytechnology$restoreAttemptState(
                    tree, missingItems, missingSnapshot, possibleSnapshot);
        }
        if (result.branchFailure() != null) {
            throw result.branchFailure();
        }
        if (result.applied()) {
            if (!result.nativeNodeCount()) {
                fantasytechnology$maxFastNodeCount = Math.min(
                        result.logicalNodeCount(), Long.MAX_VALUE / 8);
            }
            if (FTConfig.DIAGNOSTICS.get()) {
                FantasyTechnology.LOGGER.info(
                        "Omni MAX_FAST applied: amount={}, uniqueNodes={}, mergedOccurrences={}, barriers={}, logicalNodes={}, compileMs={}, executeMs={}",
                        requestedAmount, result.uniqueNodes(), result.mergedOccurrences(),
                        result.barrierCount(), result.logicalNodeCount(),
                        result.compileNanos() / 1_000_000.0,
                        result.executionNanos() / 1_000_000.0);
            }
            return;
        }

        if (result.error() != null) {
            FantasyTechnology.LOGGER.warn(
                    "Omni MAX_FAST encountered an internal compatibility error and fell back to AE2",
                    result.error());
        } else if (FTConfig.DIAGNOSTICS.get()) {
            FantasyTechnology.LOGGER.info(
                    "Omni MAX_FAST fallback: amount={}, reason={}, uniqueNodes={}, mergedOccurrences={}, barriers={}, compileMs={}, executeMs={}",
                    requestedAmount, result.fallbackReason(), result.uniqueNodes(),
                    result.mergedOccurrences(), result.barrierCount(),
                    result.compileNanos() / 1_000_000.0,
                    result.executionNanos() / 1_000_000.0);
        }
        original.call(tree, inventory, requestedAmount, containerItems);
    }

    @Unique
    private static IdentityHashMap<CraftingTreeProcess, Boolean>
            fantasytechnology$snapshotPossibleStates(CraftingTreeNode root) {
        var result = new IdentityHashMap<CraftingTreeProcess, Boolean>();
        fantasytechnology$visitBuiltProcesses(root, process -> result.put(
                process,
                ((OmniCraftingTreeProcessBridge) process).fantasytechnology$isPossible()));
        return result;
    }

    @Unique
    private static void fantasytechnology$restoreAttemptState(
            CraftingTreeNode root, KeyCounter missingItems, KeyCounter missingSnapshot,
            IdentityHashMap<CraftingTreeProcess, Boolean> possibleSnapshot) {
        missingItems.clear();
        missingItems.addAll(missingSnapshot);
        fantasytechnology$visitBuiltProcesses(root, process -> {
            Boolean previous = possibleSnapshot.get(process);
            ((OmniCraftingTreeProcessBridge) process).fantasytechnology$setPossible(
                    previous == null || previous);
        });
    }

    @Unique
    private static void fantasytechnology$visitBuiltProcesses(
            CraftingTreeNode root,
            java.util.function.Consumer<CraftingTreeProcess> visitor) {
        var pending = new ArrayDeque<CraftingTreeNode>();
        var visited = new IdentityHashMap<CraftingTreeNode, Boolean>();
        pending.addLast(root);
        while (!pending.isEmpty()) {
            CraftingTreeNode node = pending.removeFirst();
            if (visited.put(node, Boolean.TRUE) != null) {
                continue;
            }
            var bridge = (OmniCraftingTreeNodeBridge) node;
            var processes = bridge.fantasytechnology$getProcesses();
            if (processes == null) {
                continue;
            }
            for (CraftingTreeProcess process : processes) {
                visitor.accept(process);
                var processBridge = (OmniCraftingTreeProcessBridge) process;
                var children = processBridge.fantasytechnology$getChildNodes();
                if (children != null) {
                    for (CraftingTreeNode child : children.keySet()) {
                        pending.addLast(child);
                    }
                }
            }
        }
    }

    @WrapOperation(method = "runCraftAttempt", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/CraftingTreeNode;getNodeCount()J"))
    private long fantasytechnology$useAggregatedNodeCount(CraftingTreeNode tree,
            Operation<Long> original) {
        return fantasytechnology$maxFastNodeCount >= 0
                ? fantasytechnology$maxFastNodeCount
                : original.call(tree);
    }
}
