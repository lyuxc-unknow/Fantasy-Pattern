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

package cn.lyxc.fantasytechnology.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

/// Opt-in capability for crafting providers that can execute a pattern several times from a single
/// {@link ICraftingProvider#pushPattern} call.
///
/// It extends {@link ICraftingProvider} rather than standing alongside it: batching is a property of how a provider
/// receives a push, so only something that receives pushes can have it. That also keeps the crafting CPU's narrowing
/// honest - once a provider is known to be batch-capable it is still, statically, the same crafting provider AE2
/// handed over.
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
public interface IFantasyBatchCraftingProvider extends ICraftingProvider {

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
