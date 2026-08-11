package cn.lyxc.fantasytechnology.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import cn.lyxc.fantasytechnology.crafting.MolecularReusableInputAdapters;
import cn.lyxc.fantasytechnology.integration.omnisequence.FantasyOmniBatchAdmission;
import com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission;
import com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingProvider;
import com.atir.molecularmanipulator.api.crafting.OmniBatchDelivery;
import com.atir.molecularmanipulator.api.crafting.OmniBatchProbe;
import com.atir.molecularmanipulator.api.crafting.OmniBatchRequest;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/// OmniSequence's public batch-provider SPI for the fantasy annihilation block.
///
/// This mixin is conditionally applied only when that SPI exists. Keeping the optional interface out of the block
/// entity itself is important: the normal mod must still load without OmniSequence on the classpath.
@Mixin(value = FantasyAnnihilationBlockEntity.class, remap = false)
public abstract class OmniBatchCraftingProviderMixin implements OmniBatchCraftingProvider {

    @Nullable
    @Override
    public OmniBatchAdmission prepareOmniBatch(OmniBatchProbe probe) {
        if (!FTConfig.BATCH_DISPATCH_ENABLED.get()
                || !(probe.pattern() instanceof FantasyCraftingPattern pattern)) {
            return null;
        }

        var blockEntity = fantasyTechnology$self();
        Level level = blockEntity.getLevel();
        if (level == null || blockEntity.getGrid() == null
                || !blockEntity.getAvailablePatterns().contains(pattern)
                || !fantasyTechnology$validateProbe(pattern, probe, level)) {
            return null;
        }

        long maxCrafts = fantasyTechnology$queueCraftLimit(pattern, probe.requestedMaxCrafts());
        if (maxCrafts < 2) {
            return null;
        }
        return new FantasyOmniBatchAdmission(maxCrafts, request -> {
            try {
                return fantasyTechnology$commitOmniBatch(pattern, maxCrafts, request);
            } catch (RuntimeException exception) {
                return OmniBatchDelivery.RejectReason.INTERNAL_ERROR;
            }
        });
    }

    @Unique
    private FantasyAnnihilationBlockEntity fantasyTechnology$self() {
        return (FantasyAnnihilationBlockEntity) (Object) this;
    }

    /// Validates the one-craft probe without reserving inventory or queue capacity. The actual request is always
    /// checked again at commit time because substitutions and provider state may change in between.
    @Unique
    private static boolean fantasyTechnology$validateProbe(FantasyCraftingPattern pattern,
            OmniBatchProbe probe, Level level) {
        var patternInputs = pattern.getInputs();
        long[] amounts = new long[patternInputs.length];
        try {
            for (var delivered : probe.oneCraftInputs()) {
                int slot = delivered.slot();
                if (slot < 0 || slot >= patternInputs.length
                        || !patternInputs[slot].isValid(delivered.key(), level)) {
                    return false;
                }
                amounts[slot] = Math.addExact(amounts[slot], delivered.amount());
            }
            for (int slot = 0; slot < patternInputs.length; slot++) {
                if (amounts[slot] != patternInputs[slot].getMultiplier()) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /// Fuel and persistent-counter capacity bound an admission; both are rechecked when the delivery commits.
    @Unique
    private long fantasyTechnology$queueCraftLimit(FantasyCraftingPattern pattern, long requestedMaxCrafts) {
        return fantasyTechnology$self().getMaxBatchCrafts(pattern, requestedMaxCrafts);
    }

    @Unique
    @Nullable
    private OmniBatchDelivery.RejectReason fantasyTechnology$commitOmniBatch(
            FantasyCraftingPattern admittedPattern,
            long admittedMaxCrafts, OmniBatchRequest request) {
        if (!(request.pattern() instanceof FantasyCraftingPattern pattern)
                || !admittedPattern.equals(pattern)
                || request.craftCount() < 2
                || request.craftCount() > admittedMaxCrafts) {
            return OmniBatchDelivery.RejectReason.PATTERN_UNAVAILABLE;
        }

        var blockEntity = fantasyTechnology$self();
        Level level = blockEntity.getLevel();
        if (level == null || blockEntity.getGrid() == null
                || !blockEntity.getAvailablePatterns().contains(pattern)) {
            return OmniBatchDelivery.RejectReason.PATTERN_UNAVAILABLE;
        }

        try {
            KeyCounter staged = new KeyCounter();
            if (!fantasyTechnology$validateInputs(pattern, request, level, staged)
                    || !fantasyTechnology$validateAndStageOutputs(pattern, request, staged)) {
                return OmniBatchDelivery.RejectReason.UNSUPPORTED_INPUT;
            }

            return blockEntity.acceptOmniBatch(staged, request.craftCount())
                    ? null
                    : OmniBatchDelivery.RejectReason.CAPACITY_CHANGED;
        } catch (ArithmeticException exception) {
            return OmniBatchDelivery.RejectReason.UNSUPPORTED_INPUT;
        } catch (RuntimeException exception) {
            return OmniBatchDelivery.RejectReason.INTERNAL_ERROR;
        }
    }

    /// The request contains the exact extracted material, including substitutions. Consumable slots therefore have
    /// to total multiplier * craftCount, while one reusable key may serve every craft and is returned worn down.
    @Unique
    private static boolean fantasyTechnology$validateInputs(FantasyCraftingPattern pattern,
            OmniBatchRequest request, Level level, KeyCounter staged) {
        var patternInputs = pattern.getInputs();
        long[] amounts = new long[patternInputs.length];
        MolecularReusableInputAdapters.Mode[] modes =
                new MolecularReusableInputAdapters.Mode[patternInputs.length];
        int[] entries = new int[patternInputs.length];

        for (var delivered : request.inputs()) {
            int slot = delivered.slot();
            if (slot < 0 || slot >= patternInputs.length) {
                return false;
            }

            var input = patternInputs[slot];
            if (!input.isValid(delivered.key(), level)) {
                return false;
            }
            var analysis = MolecularReusableInputAdapters.analyze(input, delivered.key(), level,
                    request.craftCount());
            if (!analysis.isSupported()) {
                return false;
            }

            var mode = analysis.mode();
            if (modes[slot] != null && modes[slot] != mode) {
                return false;
            }
            modes[slot] = mode;
            amounts[slot] = Math.addExact(amounts[slot], delivered.amount());
            entries[slot]++;

            if (analysis.isReusable()) {
                // A tool pool needs an explicit per-tool allocation. The v1 API does not carry one, so accept only
                // the single reusable key that AE2 extracted for this input slot.
                if (entries[slot] != 1 || analysis.safeCrafts() < request.craftCount()) {
                    return false;
                }
                AEKey remaining = MolecularReusableInputAdapters.wearDownBy(input, delivered.key(),
                        request.craftCount());
                if (remaining != null) {
                    fantasyTechnology$addChecked(staged, remaining, delivered.amount());
                }
            }
        }

        for (int slot = 0; slot < patternInputs.length; slot++) {
            if (entries[slot] == 0 || modes[slot] == null) {
                return false;
            }
            long expected = modes[slot] == MolecularReusableInputAdapters.Mode.CONSUMABLE
                    ? Math.multiplyExact(patternInputs[slot].getMultiplier(), request.craftCount())
                    : patternInputs[slot].getMultiplier();
            if (amounts[slot] != expected) {
                return false;
            }
        }
        return true;
    }

    /// OmniSequence has already scaled expectedOutputs before it creates the delivery request. Rebuild the expected
    /// counter from this pattern and verify it exactly, rather than multiplying the delivered values a second time.
    @Unique
    private static boolean fantasyTechnology$validateAndStageOutputs(FantasyCraftingPattern pattern,
            OmniBatchRequest request, KeyCounter staged) {
        KeyCounter expected = new KeyCounter();
        KeyCounter delivered = new KeyCounter();
        for (GenericStack output : pattern.getOutputs()) {
            fantasyTechnology$addChecked(expected, output.what(),
                    Math.multiplyExact(output.amount(), request.craftCount()));
        }
        for (GenericStack output : request.expectedOutputs()) {
            fantasyTechnology$addChecked(delivered, output.what(), output.amount());
        }
        if (!fantasyTechnology$countersEqual(expected, delivered)) {
            return false;
        }
        staged.addAll(expected);
        return true;
    }

    @Unique
    private static boolean fantasyTechnology$countersEqual(KeyCounter left, KeyCounter right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (var entry : left) {
            if (right.get(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private static void fantasyTechnology$addChecked(KeyCounter counter, AEKey key, long amount) {
        if (key == null || amount <= 0 || counter.get(key) < 0) {
            throw new ArithmeticException("Invalid key-counter entry");
        }
        Math.addExact(counter.get(key), amount);
        counter.add(key, amount);
    }

}
