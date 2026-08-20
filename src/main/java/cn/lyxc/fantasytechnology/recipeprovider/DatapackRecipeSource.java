package cn.lyxc.fantasytechnology.recipeprovider;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// Immutable snapshot of the trusted recipes decoded from the active datapacks.
///
/// Resolved eagerly at reload rather than read back per lookup: a trusted pattern is re-resolved on every provider
/// query and again on every craft dispatch, so re-opening and re-parsing the backing JSON each time would put a
/// synchronous file read on the crafting hot path.
final class DatapackRecipeSource {

    static final DatapackRecipeSource EMPTY = new DatapackRecipeSource(Map.of());

    private static final Codec<Optional<DatapackServerRecipe>> CODEC =
            ConditionalOps.createConditionalCodec(DatapackServerRecipe.CODEC);

    private final Map<ResourceLocation, ServerRecipe> recipes;

    private DatapackRecipeSource(Map<ResourceLocation, ServerRecipe> recipes) {
        this.recipes = recipes;
    }

    /// Decodes every reload entry once. Entries whose conditions exclude them, or that fail to decode or resolve, are
    /// logged and dropped - one broken pack file must not take the whole browser down with it.
    static DatapackRecipeSource decode(RegistryAccess registryAccess, ICondition.IContext conditionContext,
            Map<ResourceLocation, JsonElement> files) {
        var registryOps = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
        var ops = new ConditionalOps<>(registryOps, conditionContext);
        Map<ResourceLocation, ServerRecipe> resolved = new LinkedHashMap<>();
        for (var entry : files.entrySet()) {
            ResourceLocation recipeId = entry.getKey();
            CODEC.parse(ops, entry.getValue())
                    .resultOrPartial(error -> log(recipeId, error))
                    .flatMap(optional -> optional)
                    .ifPresent(definition -> {
                        try {
                            resolved.put(recipeId, definition.resolve(recipeId));
                        } catch (RuntimeException exception) {
                            log(recipeId, exception.getMessage());
                        }
                    });
        }
        return resolved.isEmpty() ? EMPTY : new DatapackRecipeSource(Map.copyOf(resolved));
    }

    Optional<ServerRecipe> find(ResourceLocation recipeId) {
        return Optional.ofNullable(recipes.get(recipeId));
    }

    Collection<ServerRecipe> all() {
        return recipes.values();
    }

    int size() {
        return recipes.size();
    }

    private static void log(ResourceLocation recipeId, String error) {
        FantasyTechnology.LOGGER.error("Skipping server recipe provider entry {}: {}", recipeId, error);
    }
}
