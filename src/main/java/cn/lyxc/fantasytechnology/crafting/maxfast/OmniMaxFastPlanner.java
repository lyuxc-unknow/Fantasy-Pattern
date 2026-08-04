/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
 */

package cn.lyxc.fantasytechnology.crafting.maxfast;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import cn.lyxc.fantasytechnology.integration.ae2.OmniCraftingTreeNodeBridge;
import cn.lyxc.fantasytechnology.integration.ae2.OmniCraftingTreeProcessBridge;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

/// Replaces AE2's recursive recipe-tree walk with one linear pass over a deduplicated graph.
///
/// AE2 requests a node once per position it occupies in the tree, so a chain like Mystical Agriculture's essence
/// tiers is walked an exponential number of times. Here every distinct `(key, amount)` becomes a single graph node,
/// the requests flowing into it are summed, and the graph is executed in topological order - each recipe is
/// evaluated exactly once no matter how many parents ask for it.
///
/// The compiler only keeps a node if it can prove the aggregate is equivalent to AE2's walk. Anything else becomes a
/// *boundary*: that node is handed back to AE2's own `request` at execution time, so a single awkward recipe costs
/// its subtree rather than the whole plan. Some findings are fatal to the aggregate as a whole and fall back before
/// anything at all has been delegated - that ordering matters, because AE2's `request` records missing items and
/// disables branches on the calculation object itself, and those effects would be counted twice if the plan were
/// re-run from scratch afterwards.
public final class OmniMaxFastPlanner {

    private OmniMaxFastPlanner() {
    }

    /// The parts of the surrounding {@code CraftingCalculation} the planner has to reach: its cooperative pause
    /// checkpoint, whether this attempt is the final simulating one, and the missing-item list a node that cannot be
    /// satisfied has to be recorded in.
    ///
    /// The implementation is mixed into AE2's own class, so the names carry this mod's prefix - a plain `pause()`
    /// merged into a public AE2 class is exactly the kind of thing two addons collide on.
    public interface CalculationBridge {

        void fantasyTechnology$pause() throws InterruptedException;

        boolean fantasyTechnology$simulating();

        KeyCounter fantasyTechnology$missingItems();
    }

    /// One calculation's worth of state. AE2 may run several attempts against the same tree (the full amount first,
    /// then a binary search for a smaller one), so the compiled graph is built once and reused; a structural failure
    /// is remembered too, so a hopeless tree is not recompiled on every attempt.
    public static final class Session {

        private final int maxNodes;
        private final long compileBudgetNanos;
        private final CalculationBridge bridge;

        private CraftingTreeNode root;
        private Graph graph;
        private String structuralFailure;
        private Throwable structuralError;
        private long compileNanos;

        public Session(int maxNodes, int compileBudgetMillis, CalculationBridge bridge) {
            this.maxNodes = maxNodes;
            this.compileBudgetNanos = TimeUnit.MILLISECONDS.toNanos(compileBudgetMillis);
            this.bridge = bridge;
        }

        public Result tryExecute(CraftingTreeNode requestedRoot, CraftingSimulationState inventory,
                long requestedAmount) throws InterruptedException {
            if (requestedAmount <= 0) {
                return Result.fallback("invalid_request_amount", 0, 0, 0, 0, 0, null);
            }
            if (root != null && root != requestedRoot) {
                return Result.fallback("calculation_root_changed", 0, 0, 0, 0, 0, null);
            }
            root = requestedRoot;

            if (graph == null && structuralFailure == null) {
                compile(requestedRoot);
            }
            if (graph == null) {
                return Result.fallback(structuralFailure, 0, 0, 0, compileNanos, 0, structuralError);
            }

            // Nothing below is supposed to delegate to AE2 before it has committed to the aggregate, but the
            // snapshot makes that a guarantee rather than an argument: a fallback restores the missing-item list to
            // whatever it was, so re-running the tree cannot double-count anything.
            var missing = bridge.fantasyTechnology$missingItems();
            var missingSnapshot = new KeyCounter();
            missingSnapshot.addAll(missing);

            long startedAt = System.nanoTime();
            boolean applied = false;
            try {
                execute(graph, inventory, requestedAmount, bridge);
                applied = true;
                return Result.applied(graph.nodes().size(), graph.mergedOccurrences(), graph.barrierCount(),
                        countLogicalNodes(graph), compileNanos, System.nanoTime() - startedAt);
            } catch (CraftBranchFailure failure) {
                return Result.branchFailure(graph.nodes().size(), graph.mergedOccurrences(), graph.barrierCount(),
                        compileNanos, System.nanoTime() - startedAt, failure);
            } catch (Fallback fallback) {
                return Result.fallback(fallback.reason, graph.nodes().size(), graph.mergedOccurrences(),
                        graph.barrierCount(), compileNanos, System.nanoTime() - startedAt, null);
            } catch (RuntimeException exception) {
                return Result.fallback("internal_execution_exception", graph.nodes().size(),
                        graph.mergedOccurrences(), graph.barrierCount(), compileNanos,
                        System.nanoTime() - startedAt, exception);
            } finally {
                if (!applied) {
                    missing.clear();
                    missing.addAll(missingSnapshot);
                }
            }
        }

        private void compile(CraftingTreeNode requestedRoot) throws InterruptedException {
            long startedAt = System.nanoTime();
            var compiler = new Compiler(maxNodes, startedAt + compileBudgetNanos, bridge);
            try {
                graph = compiler.compile(requestedRoot);
            } catch (Fallback fallback) {
                structuralFailure = fallback.reason;
            } catch (RuntimeException exception) {
                structuralFailure = "internal_compile_exception";
                structuralError = exception;
            } finally {
                compileNanos = Math.max(0, System.nanoTime() - startedAt - compiler.pausedNanos);
            }
        }
    }

    public record Result(boolean applied, String fallbackReason, int uniqueNodes, long mergedOccurrences,
            int barrierCount, long logicalNodeCount, long compileNanos, long executionNanos,
            CraftBranchFailure branchFailure, Throwable error) {

        private static Result applied(int uniqueNodes, long mergedOccurrences, int barrierCount,
                long logicalNodeCount, long compileNanos, long executionNanos) {
            return new Result(true, null, uniqueNodes, mergedOccurrences, barrierCount, logicalNodeCount,
                    compileNanos, executionNanos, null, null);
        }

        private static Result branchFailure(int uniqueNodes, long mergedOccurrences, int barrierCount,
                long compileNanos, long executionNanos, CraftBranchFailure failure) {
            return new Result(false, null, uniqueNodes, mergedOccurrences, barrierCount, 0, compileNanos,
                    executionNanos, failure, null);
        }

        private static Result fallback(String reason, int uniqueNodes, long mergedOccurrences, int barrierCount,
                long compileNanos, long executionNanos, Throwable error) {
            return new Result(false, reason, uniqueNodes, mergedOccurrences, barrierCount, 0, compileNanos,
                    executionNanos, null, error);
        }
    }

    // ------------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------------

    private static void execute(Graph graph, CraftingSimulationState parent, long requestedAmount,
            CalculationBridge bridge) throws Fallback, CraftBranchFailure, InterruptedException {
        var inventory = new ChildCraftingSimulationState(parent);

        // Decided up front, before a single node has been delegated to AE2: this is the last thing that can still
        // reject the aggregate outright, and rejecting it after a delegation would leave side effects behind.
        validateTemplates(graph, inventory, bridge);

        var requests = new long[graph.nodes().size()];
        requests[graph.rootIndex()] = requestedAmount;

        for (int nodeIndex : graph.topologicalOrder()) {
            checkpoint(bridge);
            long requestMultipliers = requests[nodeIndex];
            if (requestMultipliers <= 0) {
                continue;
            }

            Node node = graph.nodes().get(nodeIndex);
            if (node.barrier) {
                if (!tryExecuteReusableContainerBoundary(node, inventory, requestMultipliers, bridge)) {
                    node.firstBridge().fantasytechnology$request(inventory, requestMultipliers, null);
                }
                continue;
            }

            inventory.addStackBytes(node.key, node.amount, requestMultipliers);

            // The compiler proved this node's only template is its own key, so AE2's template walk collapses into
            // one extract of whole multiples.
            long extractLimit = saturatedMultiply(node.amount, requestMultipliers);
            long available = inventory.extract(node.key, extractLimit, Actionable.SIMULATE);
            long extractedMultipliers = Math.min(requestMultipliers, available / node.amount);
            if (extractedMultipliers > 0) {
                long extractedAmount = node.amount * extractedMultipliers;
                long extracted = inventory.extract(node.key, extractedAmount, Actionable.MODULATE);
                if (extracted != extractedAmount) {
                    throw new IllegalStateException("Crafting simulation inventory changed during exact extraction");
                }
            }

            long remainingMultipliers = requestMultipliers - extractedMultipliers;
            if (remainingMultipliers == 0) {
                continue;
            }

            long totalRequestedItems = saturatedMultiply(node.amount, remainingMultipliers);
            if (node.emitter) {
                inventory.emitItems(node.key, totalRequestedItems);
                continue;
            }
            if (node.terminal) {
                // Exactly what AE2 does with a node it can neither craft nor emit: a simulating attempt records the
                // shortfall and carries on, a real one gives up on this branch.
                if (!bridge.fantasyTechnology$simulating()) {
                    throw new CraftBranchFailure(node.key, totalRequestedItems);
                }
                bridge.fantasyTechnology$missingItems().add(node.key, totalRequestedItems);
                continue;
            }
            if (node.outputPerPattern <= 0) {
                // Unreachable: the compiler commits a node's edges and its output count together. Kept so a future
                // change cannot turn this into a division by zero halfway through a plan.
                throw new Fallback("uncompiled_node");
            }

            long patternTimes = ceilDiv(totalRequestedItems, node.outputPerPattern);
            for (Edge edge : node.edges) {
                requests[edge.childIndex()] = saturatedAdd(requests[edge.childIndex()],
                        saturatedMultiply(edge.requestMultiplier(), patternTimes));
            }

            // AE2 inserts the whole production and extracts what it needs back out; the net effect on the
            // simulation inventory is the surplus, so only that is recorded.
            long surplus = Math.max(0, saturatedMultiply(node.outputPerPattern, patternTimes) - totalRequestedItems);
            if (surplus > 0) {
                inventory.insert(node.key, surplus, Actionable.MODULATE);
            }
            inventory.addCrafting(node.details, patternTimes);
            inventory.addBytes(patternTimes);
        }

        inventory.applyDiff(parent);
    }

    /// Confirms that every node still resolves to exactly its own key and amount.
    ///
    /// Only nodes fed by an input that can match loosely are checked - for AE2's own exact patterns the template set
    /// is the encoded key by construction, and walking the simulation inventory for them would cost more than the
    /// aggregation saves.
    private static void validateTemplates(Graph graph, CraftingSimulationState inventory, CalculationBridge bridge)
            throws Fallback, InterruptedException {
        for (Node node : graph.nodes()) {
            if (node.barrier || node.exactTemplates) {
                continue;
            }
            checkpoint(bridge);
            for (CraftingTreeNode occurrence : node.occurrences) {
                var occurrenceBridge = (OmniCraftingTreeNodeBridge) occurrence;
                for (InputTemplate template : occurrenceBridge.fantasytechnology$getValidItemTemplates(inventory)) {
                    if (!node.key.equals(template.key()) || template.amount() != node.amount) {
                        throw new Fallback("fuzzy_or_contextual_input");
                    }
                }
            }
        }
    }

    /// How many nodes AE2's own walk would have visited, which is what its byte cost is derived from.
    ///
    /// Every graph node contributes once per tree position that reaches it. A boundary contributes its whole
    /// subtree, because AE2 really did walk it - and by the time this runs, after execution, that subtree has been
    /// built and can be counted for real.
    private static long countLogicalNodes(Graph graph) {
        var occurrences = new long[graph.nodes().size()];
        occurrences[graph.rootIndex()] = 1;

        long total = 0;
        for (int nodeIndex : graph.topologicalOrder()) {
            long nodeOccurrences = occurrences[nodeIndex];
            if (nodeOccurrences <= 0) {
                continue;
            }
            Node node = graph.nodes().get(nodeIndex);
            long subtree = node.barrier
                    ? Math.max(1, node.firstBridge().fantasytechnology$getNodeCount())
                    : 1;
            total = saturatedAdd(total, saturatedMultiply(nodeOccurrences, subtree));
            for (Edge edge : node.edges) {
                occurrences[edge.childIndex()] = saturatedAdd(occurrences[edge.childIndex()],
                        saturatedMultiply(nodeOccurrences, edge.occurrences()));
            }
        }
        return total;
    }

    // ------------------------------------------------------------------------
    // Reusable-container boundaries
    // ------------------------------------------------------------------------

    /// Handles the one boundary shape worth special-casing: a recipe that borrows a tool or container instead of
    /// consuming it. AE2 marks such a process quantity-limited and therefore plans it one craft at a time, which is
    /// what makes a 1000-use crystal take a thousand recursive requests. Here the crafts are collapsed into a single
    /// scaled step - one tool borrowed, the worn remains handed back.
    ///
    /// Returns {@code false} when the shape is not the expected one, in which case the caller delegates to AE2. Every
    /// such rejection happens before any child has been requested, so falling back costs nothing but time.
    private static boolean tryExecuteReusableContainerBoundary(Node node, CraftingSimulationState parent,
            long requestedAmount, CalculationBridge bridge) throws CraftBranchFailure, InterruptedException {
        try {
            return tryExecuteReusableContainerBoundaryUnchecked(node, parent, requestedAmount, bridge);
        } catch (NoSuchElementException exception) {
            return rejectReusableBoundary(node, null, "provider_missing", exception);
        }
    }

    private static boolean tryExecuteReusableContainerBoundaryUnchecked(Node node, CraftingSimulationState parent,
            long requestedAmount, CalculationBridge bridge) throws CraftBranchFailure, InterruptedException {
        if (!"container_items".equals(node.barrierReason)) {
            return rejectReusableBoundary(node, null, "unsupported_barrier:" + node.barrierReason, null);
        }

        var nodeBridge = node.firstBridge();
        List<CraftingTreeProcess> processes = nodeBridge.fantasytechnology$getProcesses();
        if (processes == null || processes.size() != 1) {
            return rejectReusableBoundary(node, null, "pattern_candidate_count", null);
        }
        if (nodeBridge.fantasytechnology$canEmit()) {
            return rejectReusableBoundary(node, null, "emitting_boundary", null);
        }

        var process = (OmniCraftingTreeProcessBridge) processes.getFirst();
        if (!process.fantasytechnology$hasContainerItems()) {
            return rejectReusableBoundary(node, null, "container_flag_missing", null);
        }

        IPatternDetails details = process.fantasytechnology$getDetails();
        if (details == null) {
            return rejectReusableBoundary(node, null, "missing_pattern_details", null);
        }
        IPatternDetails.IInput[] inputs = details.getInputs();
        Map<CraftingTreeNode, Long> childNodes = process.fantasytechnology$getChildNodes();
        if (inputs == null || childNodes == null || inputs.length != childNodes.size()) {
            return rejectReusableBoundary(node, details, "dynamic_input_layout", null);
        }

        long outputPerPattern = 0;
        for (var output : details.getOutputs()) {
            if (output == null || output.what() == null || output.amount() <= 0) {
                return rejectReusableBoundary(node, details, "invalid_output", null);
            }
            if (!node.key.equals(output.what())) {
                return rejectReusableBoundary(node, details, "secondary_output", null);
            }
            outputPerPattern = saturatedAdd(outputPerPattern, output.amount());
        }
        if (outputPerPattern <= 0) {
            return rejectReusableBoundary(node, details, "missing_primary_output", null);
        }

        var attempt = new ChildCraftingSimulationState(parent);
        attempt.addStackBytes(node.key, node.amount, requestedAmount);
        long remainingAmount = requestedAmount;
        for (InputTemplate template : nodeBridge.fantasytechnology$getValidItemTemplates(attempt)) {
            remainingAmount -= extractTemplateMultipliers(attempt, template, remainingAmount);
            if (remainingAmount == 0) {
                attempt.applyDiff(parent);
                return true;
            }
        }

        long totalRequestedItems = saturatedMultiply(node.amount, remainingAmount);
        long patternTimes = ceilDiv(totalRequestedItems, outputPerPattern);

        // Every reason to give up has to be found before the first child request below, so the scaled production is
        // checked for overflow here rather than by comparing what came back out afterwards.
        long produced = 0;
        for (var output : details.getOutputs()) {
            long scaled = saturatedMultiply(output.amount(), patternTimes);
            produced = saturatedAdd(produced, scaled);
            if (scaled == Long.MAX_VALUE || produced == Long.MAX_VALUE) {
                return rejectReusableBoundary(node, details, "output_overflow", null);
            }
        }
        if (produced < totalRequestedItems) {
            return rejectReusableBoundary(node, details, "insufficient_output", null);
        }

        var inputPlans = new ArrayList<BoundaryInputPlan>(inputs.length);
        int inputIndex = 0;
        for (var entry : childNodes.entrySet()) {
            checkpoint(bridge);
            CraftingTreeNode child = entry.getKey();
            var childBridge = (OmniCraftingTreeNodeBridge) child;
            IPatternDetails.IInput input = inputs[inputIndex++];
            if (childBridge.fantasytechnology$getParentInput() != input) {
                return rejectReusableBoundary(node, details, "dynamic_input_identity", null);
            }
            if (node.key.equals(childBridge.fantasytechnology$getWhat())) {
                return rejectReusableBoundary(node, details, "self_referencing_input", null);
            }

            BoundaryInputClassification classification = classifyBoundaryInput(input, childBridge, attempt, bridge);
            if (classification.rejectionReason() != null) {
                return rejectReusableBoundary(node, details, classification.rejectionReason(), null);
            }

            long multiplier = input.getMultiplier();
            if (multiplier <= 0 || entry.getValue() == null || entry.getValue() != multiplier) {
                return rejectReusableBoundary(node, details, "invalid_input_multiplier", null);
            }
            // A borrowed input is needed once no matter how many crafts run; a consumed one scales with them.
            long childRequest = classification.mode() == BoundaryInputMode.REUSABLE
                    ? multiplier
                    : saturatedMultiply(multiplier, patternTimes);
            inputPlans.add(new BoundaryInputPlan(childBridge, childRequest));
        }

        var containerItems = new KeyCounter();
        for (BoundaryInputPlan inputPlan : inputPlans) {
            inputPlan.child().fantasytechnology$request(attempt, inputPlan.requestedAmount(), containerItems);
        }

        for (var stack : containerItems) {
            attempt.insert(stack.getKey(), stack.getLongValue(), Actionable.MODULATE);
            attempt.addStackBytes(stack.getKey(), stack.getLongValue(), 1);
        }
        for (var output : details.getOutputs()) {
            attempt.insert(output.what(), output.amount() * patternTimes, Actionable.MODULATE);
        }
        attempt.addCrafting(details, patternTimes);
        attempt.addBytes(patternTimes);

        long extracted = attempt.extract(node.key, totalRequestedItems, Actionable.MODULATE);
        if (extracted != totalRequestedItems) {
            // The overflow guard above rules this out; if it ever happens the plan is not trustworthy, and the
            // internal-error path discards the attempt and restores the calculation state.
            throw new IllegalStateException("Reusable boundary produced " + extracted + " of "
                    + totalRequestedItems + " expected outputs");
        }
        attempt.applyDiff(parent);
        return true;
    }

    private static boolean rejectReusableBoundary(Node node, @Nullable IPatternDetails details, String reason,
            @Nullable RuntimeException exception) {
        if (FTConfig.DIAGNOSTICS.get()) {
            String pattern = describePattern(details);
            if (exception == null) {
                FantasyTechnology.LOGGER.info(
                        "Fantasy planner boundary fallback: key={}, amount={}, barrier={}, pattern={}, reason={}",
                        node.key, node.amount, node.barrierReason, pattern, reason);
            } else {
                FantasyTechnology.LOGGER.warn(
                        "Fantasy planner boundary failed: key={}, amount={}, barrier={}, pattern={}, reason={}",
                        node.key, node.amount, node.barrierReason, pattern, reason, exception);
            }
        }
        return false;
    }

    private static String describePattern(@Nullable IPatternDetails details) {
        if (details == null) {
            return "unknown";
        }
        try {
            return String.valueOf(details.getDefinition());
        } catch (RuntimeException exception) {
            return details.getClass().getName();
        }
    }

    /// Whether an input is consumed by a craft or merely borrowed. Mixing the two within one input, or an input whose
    /// remains are a *different* key that the boundary would then have to route somewhere, is not handled here.
    private static BoundaryInputClassification classifyBoundaryInput(IPatternDetails.IInput input,
            OmniCraftingTreeNodeBridge child, CraftingSimulationState inventory, CalculationBridge bridge)
            throws InterruptedException {
        BoundaryInputMode mode = classifyRemainingKey(input, child.fantasytechnology$getWhat());
        if (mode == BoundaryInputMode.UNSAFE) {
            return BoundaryInputClassification.rejected("container_changes_key");
        }
        for (InputTemplate template : child.fantasytechnology$getValidItemTemplates(inventory)) {
            checkpoint(bridge);
            BoundaryInputMode templateMode = classifyRemainingKey(input, template.key());
            if (templateMode == BoundaryInputMode.UNSAFE) {
                return BoundaryInputClassification.rejected("container_changes_key");
            }
            if (templateMode != mode) {
                return BoundaryInputClassification.rejected("mixed_consumable_and_reusable_templates");
            }
        }
        return BoundaryInputClassification.accepted(mode);
    }

    private static BoundaryInputMode classifyRemainingKey(IPatternDetails.IInput input, AEKey key) {
        AEKey remainingKey = input.getRemainingKey(key);
        if (remainingKey == null) {
            return BoundaryInputMode.CONSUMABLE;
        }
        return remainingKey.equals(key) ? BoundaryInputMode.REUSABLE : BoundaryInputMode.UNSAFE;
    }

    private static long extractTemplateMultipliers(CraftingSimulationState inventory, InputTemplate template,
            long requestedMultipliers) {
        long extractLimit = saturatedMultiply(template.amount(), requestedMultipliers);
        long available = inventory.extract(template.key(), extractLimit, Actionable.SIMULATE);
        long extractedMultipliers = Math.min(requestedMultipliers, available / template.amount());
        if (extractedMultipliers <= 0) {
            return 0;
        }
        long extractedAmount = template.amount() * extractedMultipliers;
        long extracted = inventory.extract(template.key(), extractedAmount, Actionable.MODULATE);
        if (extracted != extractedAmount) {
            throw new IllegalStateException("Crafting simulation inventory changed during template extraction");
        }
        return extractedMultipliers;
    }

    private enum BoundaryInputMode {
        CONSUMABLE,
        REUSABLE,
        UNSAFE
    }

    private record BoundaryInputClassification(BoundaryInputMode mode, @Nullable String rejectionReason) {

        private static BoundaryInputClassification accepted(BoundaryInputMode mode) {
            return new BoundaryInputClassification(mode, null);
        }

        private static BoundaryInputClassification rejected(String reason) {
            return new BoundaryInputClassification(null, reason);
        }
    }

    private record BoundaryInputPlan(OmniCraftingTreeNodeBridge child, long requestedAmount) {
    }

    // ------------------------------------------------------------------------
    // Compilation
    // ------------------------------------------------------------------------

    private static final class Compiler {

        private final int maxNodes;
        private final CalculationBridge bridge;
        private final List<Node> nodes = new ArrayList<>();
        private final Object2IntOpenHashMap<NodeKey> nodeIndexes = new Object2IntOpenHashMap<>();
        /// Which node first expanded a given pattern instance. Identity, not equality: two nodes sharing a pattern
        /// object would each scale it by their own request unit.
        private final Reference2IntOpenHashMap<IPatternDetails> patternOwners = new Reference2IntOpenHashMap<>();
        private long deadline;
        private long mergedOccurrences;
        private long pausedNanos;

        private Compiler(int maxNodes, long deadline, CalculationBridge bridge) {
            this.maxNodes = maxNodes;
            this.deadline = deadline;
            this.bridge = bridge;
            this.nodeIndexes.defaultReturnValue(-1);
            this.patternOwners.defaultReturnValue(-1);
        }

        private Graph compile(CraftingTreeNode root) throws Fallback, InterruptedException {
            int rootIndex = intern(root);
            // A node is only ever added by intern, and intern only runs once a parent has fully validated the edge
            // that reaches it, so appending to the list while walking it visits every node exactly once.
            for (Node node : nodes) {
                checkBudget();
                try {
                    inspect(node);
                } catch (Barrier barrier) {
                    node.barrier = true;
                    node.barrierReason = barrier.reason;
                    if (requiresImmediateFallback(barrier.reason)) {
                        if (FTConfig.DIAGNOSTICS.get()) {
                            FantasyTechnology.LOGGER.info(
                                    "Fantasy planner compile-time fallback: key={}, amount={}, barrier={}, pattern={}",
                                    node.key, node.amount, node.barrierReason, describePattern(node.details));
                        }
                        throw new Fallback("unsafe_pattern_boundary:" + barrier.reason);
                    }
                }
            }

            int barrierCount = 0;
            for (Node node : nodes) {
                if (node.barrier) {
                    // A boundary reached from several places would be re-requested in full once per parent, which
                    // is exactly the blow-up this planner exists to avoid.
                    if (node.occurrences.size() != 1) {
                        throw new Fallback("shared_unsafe_boundary:" + node.barrierReason);
                    }
                    barrierCount++;
                }
            }

            return new Graph(List.copyOf(nodes), buildTopologicalOrder(), rootIndex, mergedOccurrences,
                    barrierCount);
        }

        private int intern(CraftingTreeNode occurrence) throws Fallback, InterruptedException {
            checkBudget();
            var occurrenceBridge = (OmniCraftingTreeNodeBridge) occurrence;
            AEKey key = occurrenceBridge.fantasytechnology$getWhat();
            long amount = occurrenceBridge.fantasytechnology$getAmount();
            if (key == null || amount <= 0) {
                throw new Fallback("invalid_node_template");
            }

            var nodeKey = new NodeKey(key, amount);
            int existing = nodeIndexes.getInt(nodeKey);
            if (existing >= 0) {
                nodes.get(existing).occurrences.add(occurrence);
                mergedOccurrences = saturatedAdd(mergedOccurrences, 1);
                return existing;
            }
            if (nodes.size() >= maxNodes) {
                throw new Fallback("node_limit");
            }

            int index = nodes.size();
            var node = new Node(index, key, amount, occurrenceBridge.fantasytechnology$getLevel());
            node.occurrences.add(occurrence);
            nodes.add(node);
            nodeIndexes.put(nodeKey, index);
            return index;
        }

        /// Validates one node's recipe in full and only then commits it to the graph.
        ///
        /// The two halves are strictly separated: nothing is interned, no edge is recorded and no occurrence is
        /// registered until every input has passed. A {@link Barrier} thrown halfway would otherwise leave children
        /// in the node list that no parent ever inspected, and a later parent reaching one of them would execute a
        /// node with no recipe behind it.
        private void inspect(Node node) throws Fallback, Barrier, InterruptedException {
            var nodeBridge = node.firstBridge();
            if (nodeBridge.fantasytechnology$canEmit()) {
                node.emitter = true;
                return;
            }

            nodeBridge.fantasytechnology$buildChildPatterns();
            List<CraftingTreeProcess> processes = nodeBridge.fantasytechnology$getProcesses();
            if (processes == null || processes.isEmpty()) {
                node.terminal = true;
                return;
            }
            if (processes.size() != 1) {
                throw new Barrier("multiple_pattern_candidates");
            }

            var process = (OmniCraftingTreeProcessBridge) processes.getFirst();
            if (process.fantasytechnology$hasContainerItems()) {
                throw new Barrier("container_items");
            }
            if (process.fantasytechnology$limitsQuantity()) {
                throw new Barrier("quantity_limited_pattern");
            }

            IPatternDetails details = process.fantasytechnology$getDetails();
            node.details = details;
            String patternBarrierReason = getPatternBarrierReason(details);
            if (patternBarrierReason != null) {
                throw new Barrier(patternBarrierReason);
            }

            long outputPerPattern = 0;
            for (var output : details.getOutputs()) {
                if (output == null || output.what() == null || output.amount() <= 0) {
                    throw new Barrier("invalid_pattern_output");
                }
                if (!node.key.equals(output.what())) {
                    throw new Barrier("secondary_or_fuzzy_output");
                }
                outputPerPattern = saturatedAdd(outputPerPattern, output.amount());
            }
            if (outputPerPattern <= 0) {
                throw new Barrier("missing_primary_output");
            }

            IPatternDetails.IInput[] inputs = details.getInputs();
            Map<CraftingTreeNode, Long> childNodes = process.fantasytechnology$getChildNodes();
            if (inputs.length != childNodes.size()) {
                throw new Barrier("dynamic_input_layout");
            }

            var pendingEdges = new ArrayList<PendingEdge>(inputs.length);
            int inputIndex = 0;
            for (var entry : childNodes.entrySet()) {
                checkBudget();
                var childBridge = (OmniCraftingTreeNodeBridge) entry.getKey();
                IPatternDetails.IInput input = inputs[inputIndex++];
                if (childBridge.fantasytechnology$getParentInput() != input) {
                    throw new Barrier("dynamic_input_identity");
                }

                var possibleInputs = input.getPossibleInputs();
                if (possibleInputs.length != 1) {
                    throw new Barrier("substitute_input");
                }
                var possibleInput = possibleInputs[0];
                if (possibleInput == null || possibleInput.what() == null || possibleInput.amount() <= 0) {
                    throw new Barrier("invalid_pattern_input");
                }
                if (!possibleInput.what().equals(childBridge.fantasytechnology$getWhat())
                        || possibleInput.amount() != childBridge.fantasytechnology$getAmount()) {
                    throw new Barrier("fuzzy_crafted_input");
                }
                if (!input.isValid(possibleInput.what(), node.level)) {
                    throw new Barrier("dynamic_input_validation");
                }
                if (input.getRemainingKey(possibleInput.what()) != null) {
                    throw new Barrier("container_items");
                }

                long multiplier = input.getMultiplier();
                if (multiplier <= 0 || entry.getValue() == null || entry.getValue() != multiplier) {
                    throw new Barrier("invalid_input_multiplier");
                }
                pendingEdges.add(new PendingEdge(entry.getKey(), multiplier));
            }

            int patternOwner = patternOwners.getInt(details);
            if (patternOwner >= 0 && patternOwner != node.index) {
                throw new Barrier("shared_pattern_with_different_request_units");
            }
            patternOwners.put(details, node.index);

            // From here on nothing can fail except the node budget, which discards the whole graph anyway.
            node.outputPerPattern = outputPerPattern;
            boolean exactTemplates = hasExactInputTemplates(details);
            var accumulators = new Int2ObjectLinkedOpenHashMap<EdgeAccumulator>(pendingEdges.size());
            for (PendingEdge pending : pendingEdges) {
                int childIndex = intern(pending.child());
                var accumulator = accumulators.get(childIndex);
                if (accumulator == null) {
                    accumulators.put(childIndex, new EdgeAccumulator(pending.multiplier()));
                } else {
                    accumulator.requestMultiplier = saturatedAdd(accumulator.requestMultiplier,
                            pending.multiplier());
                    accumulator.occurrences++;
                }
                if (!exactTemplates) {
                    nodes.get(childIndex).exactTemplates = false;
                }
            }
            for (var entry : accumulators.int2ObjectEntrySet()) {
                var accumulator = entry.getValue();
                node.edges.add(new Edge(entry.getIntKey(), accumulator.requestMultiplier, accumulator.occurrences));
                nodes.get(entry.getIntKey()).indegree++;
            }
        }

        private int[] buildTopologicalOrder() throws Fallback, InterruptedException {
            var indegrees = new int[nodes.size()];
            var ready = new IntArrayFIFOQueue();
            for (Node node : nodes) {
                indegrees[node.index] = node.indegree;
                if (node.indegree == 0) {
                    ready.enqueue(node.index);
                }
            }

            var order = new int[nodes.size()];
            int position = 0;
            while (!ready.isEmpty()) {
                checkBudget();
                int nodeIndex = ready.dequeueInt();
                order[position++] = nodeIndex;
                for (Edge edge : nodes.get(nodeIndex).edges) {
                    if (--indegrees[edge.childIndex()] == 0) {
                        ready.enqueue(edge.childIndex());
                    }
                }
            }
            if (position != nodes.size()) {
                throw new Fallback("recursive_or_cyclic_tree");
            }
            return order;
        }

        /// Compilation runs on AE2's crafting thread, which is parked whenever the server has spent its slice for
        /// the tick. That waiting is not the planner being slow, so it is taken back off the budget.
        private void checkBudget() throws InterruptedException, Fallback {
            long beforePause = System.nanoTime();
            checkpoint(bridge);
            long afterPause = System.nanoTime();
            long paused = Math.max(0, afterPause - beforePause);
            pausedNanos = saturatedAdd(pausedNanos, paused);
            deadline = saturatedAdd(deadline, paused);
            if (afterPause > deadline) {
                throw new Fallback("compile_time_budget");
            }
        }
    }

    /// Why a pattern cannot be aggregated at all, or {@code null} when it can.
    ///
    /// The whitelist is exact-class on purpose: a subclass may reinterpret any of the methods this planner relies on.
    @Nullable
    private static String getPatternBarrierReason(@Nullable IPatternDetails details) {
        if (details == null) {
            return "missing_pattern_details";
        }
        Class<?> type = details.getClass();
        if (type == AEProcessingPattern.class || type == FantasyCraftingPattern.class) {
            return null;
        }
        if (type == AECraftingPattern.class) {
            // Substitution turns one input into "any of these", so which item a craft consumes depends on what the
            // inventory holds at that moment - the one thing an aggregate cannot reproduce.
            return ((AECraftingPattern) details).canSubstitute() ? "substitution_enabled:items" : null;
        }
        return "unsupported_pattern_type:" + type.getName();
    }

    /// Whether this pattern's inputs accept nothing but the key they were encoded with. Patterns that match loosely
    /// (fantasy patterns with tag or component-insensitive ingredients) have their children re-checked against the
    /// live inventory before execution instead.
    private static boolean hasExactInputTemplates(IPatternDetails details) {
        Class<?> type = details.getClass();
        return type == AEProcessingPattern.class || type == AECraftingPattern.class;
    }

    /// Barriers that make the aggregate meaningless rather than just carving out a subtree.
    private static boolean requiresImmediateFallback(String barrierReason) {
        return "missing_pattern_details".equals(barrierReason)
                || barrierReason.startsWith("substitution_enabled:")
                || barrierReason.startsWith("unsupported_pattern_type:");
    }

    private static void checkpoint(CalculationBridge bridge) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        bridge.fantasyTechnology$pause();
    }

    // ------------------------------------------------------------------------
    // Arithmetic that saturates instead of wrapping
    // ------------------------------------------------------------------------

    private static long saturatedAdd(long left, long right) {
        if (left <= 0) {
            return Math.max(0, right);
        }
        if (right <= 0) {
            return left;
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long ceilDiv(long value, long divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------------
    // Graph
    // ------------------------------------------------------------------------

    private record NodeKey(AEKey key, long amount) {
    }

    private record PendingEdge(CraftingTreeNode child, long multiplier) {
    }

    private static final class Node {

        private final int index;
        private final AEKey key;
        private final long amount;
        private final Level level;
        /// Every tree node that folded into this one; the first is the representative the compiler inspected.
        private final List<CraftingTreeNode> occurrences = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();
        private int indegree;
        private boolean emitter;
        private boolean terminal;
        private boolean barrier;
        private boolean exactTemplates = true;
        private String barrierReason;
        private IPatternDetails details;
        private long outputPerPattern;

        private Node(int index, AEKey key, long amount, Level level) {
            this.index = index;
            this.key = key;
            this.amount = amount;
            this.level = level;
        }

        private OmniCraftingTreeNodeBridge firstBridge() {
            return (OmniCraftingTreeNodeBridge) occurrences.getFirst();
        }
    }

    private record Edge(int childIndex, long requestMultiplier, int occurrences) {
    }

    private static final class EdgeAccumulator {

        private long requestMultiplier;
        private int occurrences = 1;

        private EdgeAccumulator(long requestMultiplier) {
            this.requestMultiplier = requestMultiplier;
        }
    }

    private record Graph(List<Node> nodes, int[] topologicalOrder, int rootIndex, long mergedOccurrences,
            int barrierCount) {
    }

    /// Control-flow signals, thrown once per rejected node during compilation. Stack traces are suppressed because
    /// nobody reads them and filling one in per node is the single most expensive thing a rejection could do.
    private static final class Barrier extends Exception {

        private final String reason;

        private Barrier(String reason) {
            super(null, null, false, false);
            this.reason = reason;
        }
    }

    private static final class Fallback extends Exception {

        private final String reason;

        private Fallback(String reason) {
            super(null, null, false, false);
            this.reason = reason;
        }
    }
}
