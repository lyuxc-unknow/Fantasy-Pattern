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

package cn.lyxc.fantasytechnology.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Conservative reusable-input adapters shared by planning, extraction and the
 * fantasy annihilation block's batch dispatch.
 *
 * <p>Directly adapted from OmniSequence-Transfinite's
 * {@code MolecularReusableInputAdapters} (MIT License, see THIRD_PARTY_NOTICES.md).
 */
public final class MolecularReusableInputAdapters {
    // Finite tools are exhaustively validated against the real recipe for every
    // damage state. Keep one synchronous provider push below a watchdog-risky
    // amount of recipe work; larger orders continue in subsequent batches.
    public static final long MAX_DETERMINISTIC_TRANSITIONS = 2_048;

    private MolecularReusableInputAdapters() {
    }

    public enum Mode {
        CONSUMABLE,
        INVARIANT_REUSABLE,
        DETERMINISTIC_DAMAGE,
        UNSUPPORTED
    }

    /**
     * The outcome of walking one input's wear chain.
     *
     * <p>{@code safeCrafts} is how many crafts this one item was <em>proven</em> to serve, and
     * {@code finalKey} is what it has become by then. A null {@code finalKey} on a
     * {@link Mode#DETERMINISTIC_DAMAGE} result means the item is used up at that point, so
     * {@code safeCrafts} is its entire remaining capacity; a non-null one means the walk stopped
     * at {@code requestedCrafts} or at {@link #MAX_DETERMINISTIC_TRANSITIONS} while the item was
     * still usable, making {@code safeCrafts} a lower bound. Callers that size a batch may treat
     * either the same way; callers that decide <em>how many tools a job needs</em> must continue
     * the walk from {@code finalKey} instead of dividing by a truncated count.</p>
     */
    public record Analysis(Mode mode, AEKey initialKey, long safeCrafts,
            @Nullable AEKey finalKey) {
        public boolean isReusable() {
            return mode == Mode.INVARIANT_REUSABLE
                    || mode == Mode.DETERMINISTIC_DAMAGE;
        }

        public boolean isSupported() {
            return mode != Mode.UNSUPPORTED;
        }
    }

    /**
     * Analyses the actual key extracted by AE2, never just the encoded pattern
     * template. Damageable items are accepted only when every observed
     * transition is exactly Damage+1 and Unbreaking is absent.
     */
    public static Analysis analyze(IPatternDetails.IInput input, AEKey initialKey,
            Level level, long requestedCrafts) {
        if (input == null || initialKey == null || level == null || requestedCrafts <= 0) {
            return unsupported(initialKey);
        }

        try {
            if (!input.isValid(initialKey, level)) {
                return unsupported(initialKey);
            }

            AEKey firstRemainder = input.getRemainingKey(initialKey);
            if (firstRemainder == null) {
                if (isDeterministicDamageCandidate(input, initialKey)) {
                    // A tool on its final use legitimately has no remainder. It
                    // can execute one craft, but can never form a multi-craft batch.
                    return new Analysis(Mode.DETERMINISTIC_DAMAGE, initialKey, 1, null);
                }
                return new Analysis(Mode.CONSUMABLE, initialKey, Long.MAX_VALUE, null);
            }

            if (firstRemainder.equals(initialKey)) {
                // A damageable item can return the same key due to an Unbreaking
                // roll or another contextual rule. Never cache that random result
                // as an infinite catalyst. Minecraft 1.21 also reports stacks with
                // MAX_DAMAGE=0 as damageable; reusable recipe items such as the
                // Master Infusion Crystal intentionally use that representation.
                // An explicit UNBREAKABLE component is likewise a stable invariant.
                if (!(initialKey instanceof AEItemKey itemKey)
                        || hasFiniteMutableDurability(itemKey.toStack())) {
                    return unsupported(initialKey);
                }
                return new Analysis(Mode.INVARIANT_REUSABLE, initialKey,
                        Long.MAX_VALUE, initialKey);
            }

            if (!isDeterministicDamageCandidate(input, initialKey)
                    || !isExactDamageStep(initialKey, firstRemainder)) {
                return unsupported(initialKey);
            }

            long limit = Math.min(requestedCrafts, MAX_DETERMINISTIC_TRANSITIONS);
            long completed = 1;
            AEKey current = firstRemainder;
            while (completed < limit) {
                if (!input.isValid(current, level)) {
                    break;
                }
                AEKey next = input.getRemainingKey(current);
                completed++;
                if (next == null) {
                    return new Analysis(Mode.DETERMINISTIC_DAMAGE, initialKey,
                            completed, null);
                }
                if (!isExactDamageStep(current, next)) {
                    return unsupported(initialKey);
                }
                current = next;
            }

            return new Analysis(Mode.DETERMINISTIC_DAMAGE, initialKey,
                    completed, current);
        } catch (RuntimeException exception) {
            return unsupported(initialKey);
        }
    }

    public static boolean isExactDamageStep(AEKey current, AEKey next) {
        if (!(current instanceof AEItemKey currentItem)
                || !(next instanceof AEItemKey nextItem)) {
            return false;
        }

        ItemStack currentStack = currentItem.toStack();
        if (!currentStack.isDamageableItem()
                || hasUnbreaking(currentStack)
                || currentStack.getDamageValue() == Integer.MAX_VALUE) {
            return false;
        }

        ItemStack expected = currentStack.copy();
        expected.setCount(1);
        expected.setDamageValue(currentStack.getDamageValue() + 1);
        AEItemKey expectedKey = AEItemKey.of(expected);
        return expectedKey != null && expectedKey.equals(nextItem);
    }

    /**
     * Follows {@code crafts} wear steps of a reusable input and returns what comes back
     * afterwards, or {@code null} when the item is used up along the way.
     *
     * <p>Planning and execution must agree on that key to the bit: the crafting CPU adds it to
     * its waiting-for list and a job whose reusable input never returns under the expected key
     * stalls forever. Both sides therefore walk the chain through this one method rather than
     * reimplementing it. An input that hands the same key straight back is a fixed point and is
     * answered without walking the whole batch.</p>
     */
    @Nullable
    public static AEKey wearDownBy(IPatternDetails.IInput input, AEKey key, long crafts) {
        AEKey current = key;
        for (long step = 0; step < crafts; step++) {
            AEKey next = input.getRemainingKey(current);
            if (next == null) {
                return null;
            }
            if (next.equals(current)) {
                return current;
            }
            current = next;
        }
        return current;
    }

    /**
     * Whether the item's own state allows a deterministic wear chain, and whether this input is
     * one whose chain may be followed at all.
     *
     * <p>{@code Item#craftingRemainingItem} - what {@link ItemStack#hasCraftingRemainingItem()}
     * reports - is null for the ordinary damageable tools this is meant to cover, so upstream's
     * rule alone never admits them. Relaxing it for every input is not safe either: an AE2 crafting
     * pattern derives its remainder by running the recipe against a rebuilt crafting grid, so
     * walking a tool's chain costs one recipe evaluation per damage point, and AE2's calculator
     * deliberately plans those patterns one craft at a time. Doing otherwise turns jobs dispatched
     * to other mods' crafting devices into aggregated plans they never agreed to.</p>
     *
     * <p>So the chain is followed for inputs that declare {@link DeterministicWearInput} - a pure,
     * cheap wear function that reports null exactly at the item's last use - and otherwise only
     * under upstream's original rule. Either way the exact Damage+1 transition is still verified
     * against the real pattern by {@link #isExactDamageStep}.</p>
     */
    private static boolean isDeterministicDamageCandidate(IPatternDetails.IInput input, AEKey key) {
        if (!(key instanceof AEItemKey itemKey)) {
            return false;
        }
        ItemStack stack = itemKey.toStack();
        if (!hasFiniteMutableDurability(stack) || hasUnbreaking(stack)) {
            return false;
        }
        return input instanceof DeterministicWearInput || stack.hasCraftingRemainingItem();
    }

    /**
     * The most crafts a Damage+1 chain could ever cover for this key. Every accepted step raises
     * Damage by exactly one, so an item cannot outlive its remaining durability - which bounds any
     * walk that follows the chain across several {@link #MAX_DETERMINISTIC_TRANSITIONS} windows.
     * Zero when the key is not a finite-durability item.
     */
    public static long remainingDurabilityCrafts(AEKey key) {
        if (!(key instanceof AEItemKey itemKey)) {
            return 0;
        }
        ItemStack stack = itemKey.toStack();
        if (!hasFiniteMutableDurability(stack)) {
            return 0;
        }
        return Math.max(0, (long) stack.getMaxDamage() - stack.getDamageValue());
    }

    private static boolean hasFiniteMutableDurability(ItemStack stack) {
        return stack.isDamageableItem()
                && stack.getMaxDamage() > 0
                && !stack.has(DataComponents.UNBREAKABLE);
    }

    private static boolean hasUnbreaking(ItemStack stack) {
        for (var enchantment : stack.getEnchantments().keySet()) {
            if (enchantment.is(Enchantments.UNBREAKING)) {
                return true;
            }
        }
        return false;
    }

    private static Analysis unsupported(@Nullable AEKey initialKey) {
        return new Analysis(Mode.UNSUPPORTED, initialKey, 0, null);
    }
}
