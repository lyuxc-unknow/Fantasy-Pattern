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

import appeng.api.stacks.AEKey;
import cn.lyxc.fantasytechnology.cache.PatternValidityCache;
import cn.lyxc.fantasytechnology.cache.RemainingKeyCache;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ConcurrentHashMap;

/// Memoizes the two per-ingredient questions AE2 asks over and over while planning and executing a crafting pattern.
///
/// `getRemainingKey` runs the recipe's `getRemainingItems` against a rebuilt crafting grid and AE2 caches none of it,
/// even though the answer only depends on the key handed in. `isValid` has an internal cache, but only for keys
/// without data components - so the damaged tools and NBT-bearing ingredients that make a plan expensive in the first
/// place are exactly the ones that miss it.
///
/// Each question gets a one-entry `recent*` slot, which covers the overwhelmingly common case of a slot being asked
/// about the same key repeatedly; a bounded `*ByKey` map is only allocated once a second key shows up. Answers are
/// deterministic for a given pattern instance, and pattern instances are rebuilt whenever a provider's inventory
/// changes, so nothing here outlives its input.
///
/// Stands aside when OmniSequence-Transfinite is installed - it ships the same cache; see
/// {@link FantasyTechnologyMixinPlugin}.
@Mixin(targets = "appeng.crafting.pattern.AECraftingPattern$Input", remap = false)
public abstract class AECraftingPatternInputMixin {
    /// Substitution can offer a slot every item in a tag, so the keyed maps are capped and dropped wholesale rather
    /// than growing with whatever the network happens to hold.
    @Unique
    private static final int FANTASY_TECHNOLOGY$KEYED_CACHE_LIMIT = 32;
    @Unique
    private volatile RemainingKeyCache fantasytechnology$recentRemainder;
    @Unique
    private volatile PatternValidityCache fantasytechnology$recentValidity;
    @Unique
    private volatile ConcurrentHashMap<AEKey, RemainingKeyCache> fantasytechnology$remainderByKey;
    @Unique
    private volatile ConcurrentHashMap<AEKey, PatternValidityCache> fantasytechnology$validityByKey;

    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void fantasytechnology$reuseValidity(AEKey input, Level level,
            CallbackInfoReturnable<Boolean> callback) {
        var cache = fantasytechnology$recentValidity;
        if (cache != null && cache.level() == level
                && (cache.input() == input || cache.input().equals(input))) {
            callback.setReturnValue(cache.valid());
            return;
        }
        var caches = fantasytechnology$validityByKey;
        if (caches != null) {
            cache = caches.get(input);
            if (cache != null && cache.level() == level) {
                fantasytechnology$recentValidity = cache;
                callback.setReturnValue(cache.valid());
            }
        }
    }

    @Inject(method = "isValid", at = @At("RETURN"))
    private void fantasytechnology$cacheValidity(AEKey input, Level level,
            CallbackInfoReturnable<Boolean> callback) {
        var next = new PatternValidityCache(input, level, callback.getReturnValue());
        var previous = fantasytechnology$recentValidity;
        fantasytechnology$recentValidity = next;
        if (previous == null || previous.level() == level
                && (previous.input() == input || previous.input().equals(input))) {
            return;
        }
        var caches = fantasytechnology$validityByKey;
        if (caches == null) {
            synchronized (this) {
                caches = fantasytechnology$validityByKey;
                if (caches == null) {
                    caches = new ConcurrentHashMap<>();
                    caches.put(previous.input(), previous);
                    fantasytechnology$validityByKey = caches;
                }
            }
        }
        fantasytechnology$putBounded(caches, input, next);
    }

    @Inject(method = "getRemainingKey", at = @At("HEAD"), cancellable = true)
    private void fantasytechnology$reuseRemainingKey(AEKey template,
            CallbackInfoReturnable<AEKey> callback) {
        var cache = fantasytechnology$recentRemainder;
        if (cache != null && (cache.input() == template || cache.input().equals(template))) {
            callback.setReturnValue(cache.output());
            return;
        }
        var caches = fantasytechnology$remainderByKey;
        if (caches != null) {
            cache = caches.get(template);
            if (cache != null) {
                fantasytechnology$recentRemainder = cache;
                callback.setReturnValue(cache.output());
            }
        }
    }

    @Inject(method = "getRemainingKey", at = @At("RETURN"))
    private void fantasytechnology$cacheRemainingKey(AEKey template,
            CallbackInfoReturnable<AEKey> callback) {
        var next = new RemainingKeyCache(template, callback.getReturnValue());
        var previous = fantasytechnology$recentRemainder;
        fantasytechnology$recentRemainder = next;
        if (previous == null || previous.input() == template || previous.input().equals(template)) {
            return;
        }
        var caches = fantasytechnology$remainderByKey;
        if (caches == null) {
            synchronized (this) {
                caches = fantasytechnology$remainderByKey;
                if (caches == null) {
                    caches = new ConcurrentHashMap<>();
                    caches.put(previous.input(), previous);
                    fantasytechnology$remainderByKey = caches;
                }
            }
        }
        fantasytechnology$putBounded(caches, template, next);
    }

    @Unique
    private static <T> void fantasytechnology$putBounded(
            ConcurrentHashMap<AEKey, T> cache, AEKey key, T value) {
        if (cache.size() >= FANTASY_TECHNOLOGY$KEYED_CACHE_LIMIT && !cache.containsKey(key)) {
            cache.clear();
        }
        cache.put(key, value);
    }
}
