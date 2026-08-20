package cn.lyxc.fantasytechnology.item;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The recipe stored in a fantasy pattern: a list of ingredients and a list of results, in the shape of an AE2
/// processing pattern rather than a crafting grid. Order and position carry no meaning, so empty entries are dropped on
/// construction and the lists are only bounded from above.
///
/// Everything is expressed as {@link GenericStack}, so items and fluids are handled the same way throughout - a recipe
/// that consumes water and produces a liquid encodes exactly like one made of items.
///
/// Ingredients may be tag-based (see {@link PatternIngredient}), in which case autocrafting will accept anything in the
/// tag instead of insisting on what the pattern happened to be encoded with. Per-entry {@code ignoreData} flags drop
/// data components when matching; {@link #outputsIgnore()} holds the parallel flags for the results (kept for display
/// and re-editing, they carry no matching semantics).
///
/// Ordinary JEI/client-authenticated patterns carry their recipe directly. A trusted server-authenticated pattern also
/// carries an opaque server recipe token; the server re-resolves that token before planning and before execution.
public record FantasyPatternData(List<PatternIngredient> inputs, List<GenericStack> outputs,
        List<Boolean> outputsIgnore, Optional<Long> serverRecipeToken) {

    /// How many ingredient entries a single pattern can hold.
    public static final int MAX_INPUTS = 81;
    /// How many result entries a single pattern can hold.
    public static final int MAX_OUTPUTS = 6;

    /// Compatibility constructor for data that predates the ignore-data flags (everything then defaults to false).
    public FantasyPatternData(List<PatternIngredient> inputs, List<GenericStack> outputs) {
        this(inputs, outputs, List.of(), Optional.empty());
    }

    public FantasyPatternData(List<PatternIngredient> inputs, List<GenericStack> outputs,
            List<Boolean> outputsIgnore) {
        this(inputs, outputs, outputsIgnore, Optional.empty());
    }

    public static final Codec<FantasyPatternData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PatternIngredient.CODEC.listOf().fieldOf("inputs").forGetter(FantasyPatternData::inputs),
            GenericStack.CODEC.listOf().fieldOf("outputs").forGetter(FantasyPatternData::outputs),
            Codec.BOOL.listOf().optionalFieldOf("outputsIgnore", List.of())
                    .forGetter(FantasyPatternData::outputsIgnore),
            Codec.LONG.optionalFieldOf("server_recipe_token")
                    .forGetter(FantasyPatternData::serverRecipeToken))
            .apply(instance, FantasyPatternData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FantasyPatternData> STREAM_CODEC = StreamCodec.composite(
            PatternIngredient.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_INPUTS)),
            FantasyPatternData::inputs,
            GenericStack.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OUTPUTS)),
            FantasyPatternData::outputs,
            ByteBufCodecs.BOOL.apply(ByteBufCodecs.list(MAX_OUTPUTS)),
            FantasyPatternData::outputsIgnore,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_LONG),
            FantasyPatternData::serverRecipeToken,
            FantasyPatternData::new);

    public FantasyPatternData {
        inputs = inputs.stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        // Filter outputs and their ignore flags together so the two lists stay in lockstep.
        List<GenericStack> keptOutputs = new ArrayList<>();
        List<Boolean> keptIgnore = new ArrayList<>();
        for (int i = 0; i < outputs.size(); i++) {
            GenericStack stack = outputs.get(i);
            if (stack != null && stack.amount() > 0) {
                keptOutputs.add(stack);
                keptIgnore.add(i < outputsIgnore.size() ? outputsIgnore.get(i) : Boolean.FALSE);
            }
        }
        outputs = List.copyOf(keptOutputs);
        outputsIgnore = List.copyOf(keptIgnore);
        serverRecipeToken = serverRecipeToken == null ? Optional.empty() : serverRecipeToken;
        if (inputs.size() > MAX_INPUTS) {
            throw new IllegalArgumentException(
                    "Fantasy pattern allows at most " + MAX_INPUTS + " ingredients, got " + inputs.size());
        }
        if (outputs.size() > MAX_OUTPUTS) {
            throw new IllegalArgumentException(
                    "Fantasy pattern allows at most " + MAX_OUTPUTS + " results, got " + outputs.size());
        }
    }

    /// Aggregates the ingredients into required amounts. Entries that share a tag merge into one bulk request even if
    /// they were encoded from different keys, so six planks of three different woods become one "6x #planks".
    public Map<IngredientKey, Long> requiredIngredients() {
        Map<IngredientKey, Long> required = new LinkedHashMap<>();
        for (PatternIngredient ingredient : inputs) {
            required.merge(IngredientKey.of(ingredient), ingredient.amount(), Long::sum);
        }
        return required;
    }

    /// @return true if there is at least one ingredient and at least one result.
    public boolean isCraftable() {
        return !inputs.isEmpty() && !outputs.isEmpty();
    }

    /// The identity of one aggregated ingredient: either a tag, or an exact key - never both - plus whether data
    /// components are ignored when matching. Built through {@link #of(PatternIngredient)} so that equality behaves
    /// accordingly.
    public record IngredientKey(@Nullable ResourceLocation tag, AEKey representative, boolean ignoreData) {

        public static IngredientKey of(PatternIngredient ingredient) {
            return new IngredientKey(ingredient.tag().orElse(null), ingredient.what(), ingredient.ignoreData());
        }

        public boolean isTagged() {
            return tag != null;
        }

        public boolean matches(AEKey key) {
            if (key == null) {
                return false;
            }
            if (tag != null) {
                return PatternIngredient.isInTag(key, tag);
            }
            return ignoreData ? representative.dropSecondary().equals(key.dropSecondary())
                    : representative.equals(key);
        }

        /// Every key that satisfies this ingredient, the representative first.
        public List<AEKey> possibleKeys() {
            return new PatternIngredient(new GenericStack(representative, 1), Optional.ofNullable(tag),
                    ignoreData).possibleKeys();
        }

        /// How this ingredient reads in a tooltip: the tag id when tagged, the key's name otherwise.
        public Component displayName() {
            return tag != null ? Component.literal("#" + tag) : representative.getDisplayName();
        }

        // Records compare every component, but two entries for the same tag must be equal regardless of which key was
        // stored as the representative. The ignore-data flag only separates exact-key entries: it has no meaning for
        // tag entries (tag matching never compares components), so those still merge freely.
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof IngredientKey(ResourceLocation otherTag, AEKey otherRepresentative,
                    boolean otherIgnoreData))) {
                return false;
            }
            if (tag != null || otherTag != null) {
                // equals() on a non-null tag already rules out the other one being null.
                return tag != null && tag.equals(otherTag)
                        && representative.getType() == otherRepresentative.getType();
            }
            return representative.equals(otherRepresentative) && ignoreData == otherIgnoreData;
        }

        @Override
        public int hashCode() {
            if (tag != null) {
                return tag.hashCode();
            }
            return representative.hashCode() * 31 + (ignoreData ? 1 : 0);
        }
    }
}
