/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
 */

package cn.lyxc.fantasytechnology.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

/// Opt-in capability for crafting providers that can execute a pattern several times from a single
/// {@link ICraftingProvider#pushPattern} call.
///
/// The crafting CPU hands such a provider one input holder carrying {@code N} complete recipes and tells it, out of
/// band, how many recipes that is. Implementing this is a promise about three things at once:
///
/// - the whole input holder is consumed or the push is refused - a partial acceptance would strand materials the CPU
///   has already written off;
/// - the provider produces {@code N} times the pattern's outputs, because the CPU registers exactly that much in its
///   waiting-for list;
/// - the provider is never "busy" mid-batch, since AE2 only consults {@link ICraftingProvider#isBusy} before the push.
///
/// Providers that queue leftovers instead of refusing (AE2's own pattern provider, for instance) must not implement
/// this interface.
public interface IFantasyBatchCraftingProvider {

    /// How many repetitions of {@code patternDetails} this provider will accept in one push. {@code 0} or {@code 1}
    /// means the pattern is dispatched one craft at a time, exactly as AE2 does by default.
    long fantasyTechnology$batchLimit(IPatternDetails patternDetails);

    /// Arms the next - and only the next - {@code pushPattern} call for {@code crafts} repetitions. Always paired with
    /// {@link #fantasyTechnology$endBatch()} in a finally block, so an exception inside the push cannot leave the
    /// provider scaling a later, unrelated recipe.
    void fantasyTechnology$beginBatch(long crafts);

    void fantasyTechnology$endBatch();

    /// The batch size {@code provider} would accept, or {@code 0} when it cannot batch this pattern at all.
    static long batchLimitOf(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (patternDetails == null || !(provider instanceof IFantasyBatchCraftingProvider batchProvider)) {
            return 0;
        }
        return Math.max(0, batchProvider.fantasyTechnology$batchLimit(patternDetails));
    }
}
