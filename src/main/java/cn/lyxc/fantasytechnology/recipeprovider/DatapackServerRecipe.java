package cn.lyxc.fantasytechnology.recipeprovider;

import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/// Decoded form of one file under {@code data/<namespace>/recipe_provider/<path>.json}.
public record DatapackServerRecipe(ResourceLocation category, List<RecipeStackDefinition> inputs,
        List<RecipeStackDefinition> outputs, List<ResourceLocation> catalysts) {

    public static final ResourceLocation PROVIDER_ID = ResourceLocation.fromNamespaceAndPath(
            FantasyTechnology.MODID, "datapack");
    public static final ResourceLocation DEFAULT_CATEGORY = ResourceLocation.fromNamespaceAndPath(
            FantasyTechnology.MODID, "datapack");

    public static final Codec<DatapackServerRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("category", DEFAULT_CATEGORY)
                    .forGetter(DatapackServerRecipe::category),
            RecipeStackDefinition.CODEC.listOf().fieldOf("inputs").forGetter(DatapackServerRecipe::inputs),
            RecipeStackDefinition.CODEC.listOf().fieldOf("outputs").forGetter(DatapackServerRecipe::outputs),
            ResourceLocation.CODEC.listOf().optionalFieldOf("catalysts", List.of())
                    .forGetter(DatapackServerRecipe::catalysts))
            .apply(instance, DatapackServerRecipe::new));

    public ServerRecipe resolve(ResourceLocation recipeId) {
        List<PatternIngredient> resolvedInputs = inputs.stream()
                .map(RecipeStackDefinition::resolveIngredient)
                .toList();
        List<GenericStack> resolvedOutputs = outputs.stream().map(output -> {
            if (output.tag().isPresent()) {
                throw new IllegalArgumentException("Output stacks cannot use tags");
            }
            return output.resolveStack();
        }).toList();
        List<Boolean> outputsIgnore = outputs.stream().map(RecipeStackDefinition::ignoreData).toList();
        List<Item> resolvedCatalysts = new ArrayList<>();
        for (ResourceLocation catalyst : catalysts) {
            BuiltInRegistries.ITEM.getOptional(catalyst).ifPresentOrElse(resolvedCatalysts::add,
                    () -> {
                        throw new IllegalArgumentException("Unknown catalyst item " + catalyst);
                    });
        }
        return new ServerRecipe(PROVIDER_ID, recipeId, category, resolvedInputs, resolvedOutputs,
                outputsIgnore, resolvedCatalysts);
    }
}
