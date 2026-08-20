package cn.lyxc.fantasytechnology.recipeprovider;

import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// A recipe whose complete contents were produced on the logical server.
///
/// The provider id is part of the key because a datapack entry and a registered Minecraft recipe may legitimately
/// share the same resource location. Clients only ever send this pair back; inputs and outputs are resolved again
/// from this object on the server before the terminal is filled.
///
/// Deliberately a class rather than a record: {@link #token()} is read once per catalogue row and twice per pattern
/// decode, and a pattern decode happens on every craft dispatch, so the digest is computed once here instead of on
/// every call.
public final class ServerRecipe {

    private final ResourceLocation providerId;
    private final ResourceLocation recipeId;
    @Nullable
    private final ResourceLocation categoryId;
    private final List<PatternIngredient> inputs;
    private final List<GenericStack> outputs;
    private final List<Boolean> outputsIgnore;
    private final List<Item> catalysts;
    private final long token;

    public ServerRecipe(ResourceLocation providerId, ResourceLocation recipeId,
            @Nullable ResourceLocation categoryId, List<PatternIngredient> inputs, List<GenericStack> outputs,
            List<Boolean> outputsIgnore, List<Item> catalysts) {
        this.providerId = providerId;
        this.recipeId = recipeId;
        this.categoryId = categoryId;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.outputsIgnore = List.copyOf(outputsIgnore);
        this.catalysts = List.copyOf(catalysts);
        if (this.inputs.isEmpty() || this.outputs.isEmpty()) {
            throw new IllegalArgumentException("A server recipe needs at least one input and one output");
        }
        if (this.inputs.size() > FantasyPatternData.MAX_INPUTS) {
            throw new IllegalArgumentException("A server recipe allows at most "
                    + FantasyPatternData.MAX_INPUTS + " inputs");
        }
        if (this.outputs.size() > FantasyPatternData.MAX_OUTPUTS) {
            throw new IllegalArgumentException("A server recipe allows at most "
                    + FantasyPatternData.MAX_OUTPUTS + " outputs");
        }
        if (this.inputs.stream().anyMatch(input -> input.isEmpty() || input.amount() > Integer.MAX_VALUE)
                || this.outputs.stream().anyMatch(output -> output == null || output.amount() <= 0
                        || output.amount() > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("Server recipe stack amounts must be between 1 and "
                    + Integer.MAX_VALUE);
        }
        this.token = ServerRecipeToken.of(this.providerId, this.recipeId, this.categoryId, this.inputs,
                this.outputs, this.outputsIgnore);
    }

    public ResourceLocation providerId() {
        return providerId;
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    @Nullable
    public ResourceLocation categoryId() {
        return categoryId;
    }

    public List<PatternIngredient> inputs() {
        return inputs;
    }

    public List<GenericStack> outputs() {
        return outputs;
    }

    public List<Boolean> outputsIgnore() {
        return outputsIgnore;
    }

    public List<Item> catalysts() {
        return catalysts;
    }

    public GenericStack displayStack() {
        return outputs.getFirst();
    }

    /// Fingerprint of this recipe's complete contents; see {@link ServerRecipeToken}.
    public long token() {
        return token;
    }

    @Override
    public String toString() {
        return "ServerRecipe[" + providerId + " " + recipeId + "]";
    }
}
