package cn.lyxc.fantasytechnology.integration.dataenergistics;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import cn.lyxc.fantasytechnology.integration.ae2.FantasyBatchDispatchContext;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingRoutingMode;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalLong;

/// Data Energistics' counted crafting dispatch, seen from the fantasy annihilation block.
///
/// The Trinity Data Core CPU ("三位一体数位化核心") does not use AE2's `CraftingCpuLogic`, so neither OmniSequence's
/// batch dispatch nor this mod's {@code OmniBatchDispatchMixin} bridge ever runs for it. Instead it asks a provider
/// how many crafts it can take at once, offers that many in one physical dispatch, and accounts the admitted count.
/// Without this bridge the block is an ordinary provider to that CPU, which offers exactly one craft per push, so a
/// deep recursive recipe such as sixteen-times compressed cobblestone makes no progress at all.
///
/// Two contracts of that API shape the implementation below.
///
/// - **Routing mode.** Only {@link CountedCraftingRoutingMode#AGGREGATE} is offered more than one craft. The CPU
///   slices `TARGETED` capacity per known target, and pins `ORDERED` and `UNKNOWN` to a single craft, so a provider
///   that aggregates internally - as this one does, because the annihilation block is its own single target - has to
///   report `AGGREGATE` to receive a batch at all.
/// - **Input ownership.** The CPU extracts the inputs of the entire admitted batch itself (one craft up front, the
///   remaining `count - 1` crafts right before committing) and hands the provider only the one-craft prototype it
///   extracted. The provider therefore must not move items; it has to craft exactly `count()` repetitions and leave
///   the CPU's accounting alone. {@link BatchAdmission} does that by opening this mod's batch dispatch context around
///   {@code pushPattern}, which is the same bridge the OmniSequence path uses.
///
/// A pattern is only batched when neither its ingredients nor the keys the CPU actually extracted declare a crafting
/// remainder: a batch would have to hand back one remainder per craft, which this bridge does not model. Everything
/// else - no grid, no fuel, a pattern the block no longer offers - degrades to an empty capacity, which keeps the CPU
/// on its single-craft fallback instead of marking the provider unavailable. Bound inputs can make that fallback use
/// this adapter rather than the CPU's native admission, so a one-craft request must remain admissible here as well.
public final class TrinityCountedDispatch {

    private TrinityCountedDispatch() {
    }

    /// The capacity of one pattern, or an empty list when that pattern has to use the CPU's single-craft fallback.
    /// {@code extracted} is the CPU's own one-craft extraction, which is what decides the remainder question.
    public static ObjectList<CountedCraftingCapacity> captureCapacity(FantasyAnnihilationBlockEntity host,
            IPatternDetails patternDetails, KeyCounter[] extracted, long requestedCrafts) {
        FantasyCraftingPattern pattern = batchable(patternDetails, extracted);
        if (pattern == null) {
            return new ObjectArrayList<>();
        }

        long limit = host.getMaxBatchCrafts(pattern, requestedCrafts);
        if (limit <= 0) {
            // A known zero would make the CPU treat the provider as permanently unavailable and stop dispatching to
            // it. An empty capacity list instead keeps the single-craft fallback, where pushPattern still decides
            // per craft - the same behaviour this block had before the integration existed.
            return new ObjectArrayList<>();
        }

        ObjectArrayList<CountedCraftingCapacity> capacities = new ObjectArrayList<>(1);
        capacities.add(new CountedCraftingCapacity(CountedCraftingTarget.provider(),
                CountedCraftingRoutingMode.AGGREGATE, OptionalLong.of(limit), OptionalLong.of(limit)));
        return capacities;
    }

    /// The admission for one physical dispatch. Single-craft requests retain native {@code pushPattern} validation;
    /// only a request for multiple crafts has to satisfy the batch capability and capacity checks.
    @Nullable
    public static CountedCraftingAdmission prepareBatch(FantasyAnnihilationBlockEntity host,
            IPatternDetails patternDetails, KeyCounter[] extracted, long requestedCount) {
        if (!(patternDetails instanceof FantasyCraftingPattern pattern) || requestedCount <= 0) {
            return null;
        }
        if (requestedCount == 1) {
            // Trinity cannot use its native admission for non-native bound inputs, even after an empty capacity
            // capture requested single-craft fallback. Keep that fallback working with remainders or batching off;
            // commit still lets pushPattern validate the recipe mode, grid, fuel and pending outputs.
            return new BatchAdmission(host, pattern, 1);
        }
        if (batchable(pattern, extracted) == null) {
            return null;
        }

        // Re-read the limit instead of reusing the captured one: fuel charges and the pending-output queue are live
        // state, and another CPU may have dispatched to this same block between capture and preparation.
        long count = host.getMaxBatchCrafts(pattern, requestedCount);
        return count <= 0 ? null : new BatchAdmission(host, pattern, count);
    }

    /// Accept the aggregate provider target and its single-craft fallback route, but no external machine targets.
    @Nullable
    public static CountedCraftingAdmission prepareBatchForTarget(FantasyAnnihilationBlockEntity host,
            IPatternDetails patternDetails, KeyCounter[] extracted, long requestedCount, CountedCraftingTarget target) {
        // An empty capacity capture produces an UNKNOWN snapshot of route "provider". Trinity converts that to a
        // non-provider-scoped public target, which the interface's default method rejects before prepareBatch runs.
        // It still names this block, but is only a single-craft fallback, never a source of batch capacity.
        if (!target.providerScoped()
                && (requestedCount != 1 || target.machineIdentity().isPresent()
                        || !target.stableIdentity().equals(CountedCraftingTarget.provider().stableIdentity()))) {
            return null;
        }
        return prepareBatch(host, patternDetails, extracted, requestedCount);
    }

    /// The patterns this integration is allowed to batch, or null when the pattern must not be batched.
    @Nullable
    private static FantasyCraftingPattern batchable(IPatternDetails patternDetails, KeyCounter[] extracted) {
        if (!FTConfig.BATCH_DISPATCH_ENABLED.get() || !(patternDetails instanceof FantasyCraftingPattern pattern)) {
            return null;
        }
        // Patterns that hand items back stay on the single-craft path; see FantasyCraftingPattern#hasCraftingRemainders.
        // The extracted keys are checked as well, because a tag can gain a member after the pattern was decoded and a
        // data-insensitive ingredient can be satisfied by a variant that declares a remainder the encoded key does not.
        // Whether the pattern is currently offered, has a grid and has fuel is decided by getMaxBatchCrafts.
        if (pattern.hasCraftingRemainders() || pattern.extractedInputsHaveRemainders(extracted)) {
            return null;
        }
        return pattern;
    }

    /// One admitted batch. {@code count()} is what the CPU accounts for, and {@code commit} performs that many crafts.
    private record BatchAdmission(FantasyAnnihilationBlockEntity host, FantasyCraftingPattern pattern, long count)
            implements CountedCraftingAdmission {

        @Override
        public long count() {
            return count;
        }

        @Override
        public boolean commit(KeyCounter @NotNull [] prototype) {
            if (count < 2) {
                // FantasyBatchDispatchContext only accepts a batch of two or more; a single craft needs no bridge.
                return host.pushPattern(pattern, prototype);
            }
            try (var batch = FantasyBatchDispatchContext.open(count, null)) {
                return host.pushPattern(pattern, prototype);
            }
        }
    }
}
