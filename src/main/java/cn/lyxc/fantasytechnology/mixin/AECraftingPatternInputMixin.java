/*
 * Portions of this file are adapted from OmniSequence-Transfinite
 * (https://github.com/AyaYumi/OmniSequence-Transfinite),
 * Copyright (c) 2025 AyaYumi, licensed under the MIT License.
 * See THIRD_PARTY_NOTICES.md in the project root for the full license text.
 */

package cn.lyxc.fantasytechnology.mixin;

import appeng.api.stacks.AEKey;
import cn.lyxc.fantasytechnology.cache.PatternValidityCache;
import cn.lyxc.fantasytechnology.cache.RemainingKeyCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.ConcurrentHashMap;

/// Memoizes the two per-ingredient questions AE2 asks over and over while planning and executing a crafting pattern.
///
/// `getRemainingKey` runs the recipe's `getRemainingItems` against a rebuilt crafting grid and AE2 caches none of it,
/// even though the answer only depends on the key handed in. `isValid` has an internal cache, but only for keys
/// without data components - so the damaged tools and NBT-bearing ingredients that make a plan expensive in the first
/// place are exactly the ones that miss it.
///
/// One inline slot covers the overwhelmingly common case of a slot being asked about the same key repeatedly; a
/// bounded map is only allocated once a second key shows up. Answers are deterministic for a given pattern instance,
/// and pattern instances are rebuilt whenever a provider's inventory changes, so nothing here outlives its input.
@Mixin(targets = "appeng.crafting.pattern.AECraftingPattern$Input", remap = false)
public abstract class AECraftingPatternInputMixin {

    @Unique
    private static final int FANTASY_TECHNOLOGY$CACHE_LIMIT = 32;

    @Unique
    private volatile PatternValidityCache fantasytechnology$lastValidity;
    @Unique
    private volatile ConcurrentHashMap<AEKey, PatternValidityCache> fantasytechnology$validityCache;
    @Unique
    private volatile RemainingKeyCache fantasytechnology$lastRemainingKey;
    @Unique
    private volatile ConcurrentHashMap<AEKey, RemainingKeyCache> fantasytechnology$remainingKeyCache;

    @WrapMethod(method = "isValid")
    private boolean fantasytechnology$cacheValidity(AEKey input, Level level, Operation<Boolean> original) {
        var last = fantasytechnology$lastValidity;
        if (last != null && last.level() == level && aE2_TheDream$sameKey(last.input(), input)) {
            return last.valid();
        }

        var cache = fantasytechnology$validityCache;
        if (cache != null) {
            var hit = cache.get(input);
            if (hit != null && hit.level() == level) {
                fantasytechnology$lastValidity = hit;
                return hit.valid();
            }
        }

        var entry = new PatternValidityCache(input, level, original.call(input, level));
        fantasytechnology$lastValidity = entry;
        if (last != null) {
            // A second distinct key means the inline slot would thrash from here on; start keeping both.
            if (cache == null) {
                synchronized (this) {
                    cache = fantasytechnology$validityCache;
                    if (cache == null) {
                        cache = new ConcurrentHashMap<>();
                        cache.put(last.input(), last);
                        fantasytechnology$validityCache = cache;
                    }
                }
            }
            aE2_TheDream$putBounded(cache, input, entry);
        }
        return entry.valid();
    }

    @WrapMethod(method = "getRemainingKey")
    private AEKey fantasytechnology$cacheRemainingKey(AEKey template, Operation<AEKey> original) {
        var last = fantasytechnology$lastRemainingKey;
        if (last != null && aE2_TheDream$sameKey(last.input(), template)) {
            return last.output();
        }

        var cache = fantasytechnology$remainingKeyCache;
        if (cache != null) {
            var hit = cache.get(template);
            if (hit != null) {
                fantasytechnology$lastRemainingKey = hit;
                return hit.output();
            }
        }

        var entry = new RemainingKeyCache(template, original.call(template));
        fantasytechnology$lastRemainingKey = entry;
        if (last != null) {
            if (cache == null) {
                synchronized (this) {
                    cache = fantasytechnology$remainingKeyCache;
                    if (cache == null) {
                        cache = new ConcurrentHashMap<>();
                        cache.put(last.input(), last);
                        fantasytechnology$remainingKeyCache = cache;
                    }
                }
            }
            aE2_TheDream$putBounded(cache, template, entry);
        }
        return entry.output();
    }

    @Unique
    private static boolean aE2_TheDream$sameKey(AEKey cached, AEKey requested) {
        return cached == requested || cached.equals(requested);
    }

    /// Substitution can offer a slot every item in a tag, so the map is capped and dropped wholesale rather than
    /// growing with whatever the network happens to hold.
    @Unique
    private static <T> void aE2_TheDream$putBounded(ConcurrentHashMap<AEKey, T> cache, AEKey key, T value) {
        if (cache.size() >= FANTASY_TECHNOLOGY$CACHE_LIMIT && !cache.containsKey(key)) {
            cache.clear();
        }
        cache.put(key, value);
    }
}
