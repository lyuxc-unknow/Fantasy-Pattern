package cn.lyxc.fantasytechnology.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.FantasyPatternItem;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.recipeprovider.ServerRecipe;
import cn.lyxc.fantasytechnology.recipeprovider.ServerRecipeProviders;
import cn.lyxc.fantasytechnology.registry.FTComponents;
import com.ae2vm.addon.crafting.DeterministicWearInput;
import com.ae2vm.addon.crafting.DurableInputAdapters;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// {@link IPatternDetails} implementation backed by a fantasy pattern ("幻梦样板"). Exposes the stored processing recipe
/// (up to 81 ingredients, up to 6 results, items and fluids alike) to AE2's autocrafting system so that crafting CPUs
/// can plan and execute it. Execution happens inside the fantasy annihilation block only - the pattern cannot be
/// executed by pattern providers or molecular assemblers.
///
/// Reusable ingredients - tools, infusion crystals, buckets - are declared to AE2 as container items: one goes into
/// each craft and a worn one comes back out, which {@link Input#getRemainingKey} spells out. That single fact is
/// what keeps a plan sane, because AE2's calculator marks any process holding container items as limited-quantity:
/// it then plans the crafts one at a time and feeds each returned item into the next craft. A 1000-use crystal
/// therefore shows up once in a plan that runs 85 crafts instead of 85 times, and a second one is only requested at
/// the point where the first would break. No durability is counted here by hand - stating what one use leaves
/// behind is enough for the calculator to derive how many tools the job needs.
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

    /// Whether a craft wears this ingredient down instead of consuming it: damageable items, items with a
    /// crafting-remaining form (buckets, reusable infusion crystals), and diggers that carry no durability at all.
    public static boolean isReusable(AEKey key) {
        return DurableInputAdapters.isReusableIngredient(key);
    }

    /// What one use of a reusable ingredient leaves behind:
    ///
    /// - its crafting-remaining form when it has one, so a water bucket comes back as an empty bucket;
    /// - the same item with one more point of damage when it is damageable;
    /// - the item unchanged when it is a tool that carries no durability at all;
    /// - null for everything else, including the moment one more point of damage would break it - which is how AE2
    ///   learns that this particular one is used up and that the plan needs a replacement from here on.
    ///
    /// The decision is made per key rather than once per ingredient, because a tagged ingredient is only classified
    /// by its representative: a tag holding both a water bucket and a plain bucket would otherwise hand the plain
    /// one straight back, and an ingredient that is never consumed is an ingredient duplicated for free.
    @Nullable
    static AEKey wearDown(AEKey template) {
        return DurableInputAdapters.wearDown(template);
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
        return new Input(possible.toArray(GenericStack[]::new), multiplier, key,
                isReusable(key.representative()));
    }

    /// Decodes a fantasy pattern item key into pattern details, or null if it is not an encoded fantasy pattern.
    @Nullable
    public static FantasyCraftingPattern decode(AEItemKey what, Level level) {
        if (what == null || !(what.getItem() instanceof FantasyPatternItem)) {
            return null;
        }
        FantasyPatternData data = what.get(FTComponents.FANTASY_PATTERN_DATA.get());
        if (data == null || !data.isCraftable()) {
            return null;
        }

        boolean serverAuthenticated = data.serverRecipeToken().isPresent();
        // The two authorization modes are intentionally disjoint. A server-authenticated pattern is only valid while
        // trusted parsing is enabled, and a JEI/client-authenticated pattern is only valid in the ordinary mode.
        // Checking on the logical server is authoritative; the client may still decode the stored display data for a
        // tooltip while it waits for the server's menu/config state.
        if (!level.isClientSide() && serverAuthenticated != FTConfig.TRUST_SERVER_RECIPE_PARSING.get()) {
            return null;
        }

        if (serverAuthenticated && !level.isClientSide()) {
            ServerRecipe recipe = ServerRecipeProviders.findByToken(level, data.serverRecipeToken().orElseThrow())
                    .orElse(null);
            if (recipe == null || recipe.token() != data.serverRecipeToken().orElseThrow()) {
                return null;
            }
            // Inputs and outputs are deliberately replaced by the current server recipe. The component's copied
            // stacks are display data only; planning and item requests must use the server's current definition.
            data = new FantasyPatternData(recipe.inputs(), recipe.outputs(), recipe.outputsIgnore(),
                    data.serverRecipeToken());
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
    /// single entry for an exact ingredient, everything in the tag for a tagged one.
    ///
    /// {@link #getRemainingKey} is {@link #wearDown}, a pure function of the key that reports null the moment the
    /// item is used up, so the input carries {@link DeterministicWearInput}.
    private record Input(GenericStack[] possible, long multiplier, FantasyPatternData.IngredientKey key,
            boolean reusable) implements IInput, DeterministicWearInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return possible;
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        /// AE2 filters both the network and the crafting simulation through this, so it decides what a craft may
        /// actually pick up.
        ///
        /// A reusable ingredient has to accept its own worn states on top of whatever the encoded ingredient says.
        /// The pattern was encoded from a pristine crystal, but from the second craft onwards the only one around
        /// is the crystal this pattern handed back a moment ago - refusing it would make the calculator ask for a
        /// fresh crystal every single craft, which is the whole problem this avoids. Tagged ingredients already
        /// behave this way, since tag membership is a property of the item and ignores wear.
        @Override
        public boolean isValid(AEKey input, Level level) {
            if (key.matches(input)) {
                return true;
            }
            return reusable && input instanceof AEItemKey item
                    && key.representative() instanceof AEItemKey representative
                    && item.getItem() == representative.getItem();
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            return reusable ? wearDown(template) : null;
        }
    }
}
