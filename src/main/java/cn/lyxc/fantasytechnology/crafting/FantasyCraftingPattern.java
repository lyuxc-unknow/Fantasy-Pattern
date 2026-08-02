package cn.lyxc.fantasytechnology.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.FantasyPatternItem;
import cn.lyxc.fantasytechnology.registry.FTComponents;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// {@link IPatternDetails} implementation backed by a fantasy pattern ("幻梦样板"). Exposes the stored processing recipe
/// (up to 81 ingredients, up to 6 results, items and fluids alike) to AE2's autocrafting system so that crafting CPUs
/// can plan and execute it. Execution happens inside the fantasy annihilation block only - the pattern cannot be
/// executed by pattern providers or molecular assemblers.
public class FantasyCraftingPattern implements IPatternDetails {

    private final AEItemKey definition;
    private final FantasyPatternData data;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;

    public FantasyCraftingPattern(AEItemKey definition, FantasyPatternData data) {
        this.definition = definition;
        this.data = data;

        List<IInput> inputList = new ArrayList<>();
        for (var entry : data.requiredIngredients().entrySet()) {
            IInput input = createInput(entry.getKey(), entry.getValue());
            if (input != null) {
                inputList.add(input);
            }
        }
        this.inputs = inputList.toArray(IInput[]::new);
        this.outputs = List.copyOf(data.outputs());
    }

    /// Turns one aggregated ingredient into an AE2 input.
    ///
    /// A tagged ingredient offers everything in the tag as a possible input, so the crafting planner picks whichever
    /// one the network actually has instead of insisting on what the pattern was encoded from.
    @Nullable
    private static IInput createInput(FantasyPatternData.IngredientKey key, long multiplier) {
        List<GenericStack> possible = new ArrayList<>();
        for (AEKey what : key.possibleKeys()) {
            possible.add(new GenericStack(what, 1));
        }
        if (possible.isEmpty()) {
            return null;
        }
        return new Input(possible.toArray(GenericStack[]::new), multiplier, key);
    }

    /// Decodes a fantasy pattern item key into pattern details, or null if it is not an encoded fantasy pattern.
    @Nullable
    public static FantasyCraftingPattern decode(AEItemKey what, Level level) {
        if (!(what.getItem() instanceof FantasyPatternItem)) {
            return null;
        }
        FantasyPatternData data = what.get(FTComponents.FANTASY_PATTERN_DATA.get());
        if (data == null || !data.isCraftable()) {
            return null;
        }
        return new FantasyCraftingPattern(what, data);
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    /// Fantasy patterns must never be executed by pushing their inputs into external inventories: they can only be
    /// annihilated inside the fantasy annihilation block. This prevents ingredient loss if a pattern ends up in a
    /// regular pattern provider.
    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return false;
    }

    public FantasyPatternData getData() {
        return data;
    }

    // Crafting jobs are serialized by definition and must compare equal after world reloads.
    @Override
    public boolean equals(Object obj) {
        return obj instanceof FantasyCraftingPattern other && definition.equals(other.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    /// One ingredient of the recipe: {@code multiplier} of it per craft, satisfied by any of {@code possible} - a
    /// single entry for an exact ingredient, everything in the tag for a tagged one. No container/remaining items.
    private record Input(GenericStack[] possible, long multiplier, FantasyPatternData.IngredientKey key)
            implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return possible;
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return key.matches(input);
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}
