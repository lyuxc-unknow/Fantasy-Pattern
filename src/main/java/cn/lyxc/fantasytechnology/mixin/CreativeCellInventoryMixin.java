package cn.lyxc.fantasytechnology.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/// Replaces the amount a configured creative ME storage cell reports to the network.
///
/// AE2's own `getAvailableStacks` reports a hard-coded `2147483647` (Integer.MAX_VALUE) for every key configured in
/// the cell workbench, and that reported stock is what the terminal displays and what the crafting simulation counts
/// as available. This mixin replaces **only that literal**: the surrounding loop and the `KeyCounter.add` call stay
/// byte-for-byte AE2's, so the only behavioural delta is the number.
///
/// Everything else about the cell is deliberately untouched. In particular the cell's `insert`/`extract` already
/// accept any amount they are asked for, so "how much the network thinks it has" is the only thing this cap bounds;
/// how much the crafting CPU actually moves is left to AE2 and the external crafting-calculation mod.
///
/// The value is `1_000_000_000_000_000` (1P) as a first attempt, and is intentionally *below* the `2147483647` it replaces:
/// it is the cheapest way to prove the injection takes effect and to watch the surrounding calculations at the
/// boundary. Tuning it means editing the single literal in the handler below.
///
/// Overflow caution before raising it much further - AE2 sums this value with every other storage mount holding the
/// same key, using a plain (non-saturating) long addition (`VariantCounter.add` -> fastutil `addTo`), and the level
/// emitter sums across keys the same way. A value close to `Long.MAX_VALUE` therefore wraps negative as soon as one
/// other container on the network holds the same item, which would make the item vanish from terminals and derail
/// crafting simulation. This mixin keeps AE2's `add` semantics as-is on purpose.
@Mixin(targets = "appeng.me.cells.CreativeCellInventory")
public abstract class CreativeCellInventoryMixin {

    /// Only the returned value differs from AE2; the method body that consumes it is not replaced.
    @ModifyConstant(
            method = "getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V",
            constant = @Constant(longValue = 2147483647L))
    private long fantasyTechnology$reportedConfiguredAmount(long original) {
        return 1_000_000_000_000_000L;
    }
}
