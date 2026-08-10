package cn.lyxc.fantasytechnology.deviceaccess;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The device-access rules currently in force, and the lookup the encoding terminal gates on.
///
/// Written by {@link DeviceRequirementLoader} on the server and by {@link DeviceRequirementSync} on the client;
/// both sides hold the same list. On an integrated server the two are literally the same field, which is harmless
/// because they would only ever be set to equal content.
public final class DeviceRequirements {

    private static volatile List<DeviceRequirement> rules = List.of();

    /// Rules that name a recipe id, which take precedence over everything else.
    private static volatile Map<ResourceLocation, DeviceRequirement> byRecipe = Map.of();

    private DeviceRequirements() {
    }

    public static void setRules(List<DeviceRequirement> newRules) {
        var recipes = new HashMap<ResourceLocation, DeviceRequirement>();
        for (DeviceRequirement rule : newRules) {
            for (ResourceLocation recipe : rule.recipes()) {
                recipes.putIfAbsent(recipe, rule);
            }
        }
        byRecipe = Map.copyOf(recipes);
        rules = List.copyOf(newRules);
    }

    public static List<DeviceRequirement> rules() {
        return rules;
    }

    /// The rule covering this recipe, or null when no pack defines one.
    ///
    /// Most specific wins, so a pack can set a blanket requirement for a category and still carve out individual
    /// recipes inside it:
    ///
    /// 1. a rule naming the recipe id;
    /// 2. a rule naming the category *and* the item being produced;
    /// 3. a rule naming the category alone.
    @Nullable
    public static DeviceRequirement find(@Nullable ResourceLocation recipeId,
            @Nullable ResourceLocation categoryId, Set<ResourceLocation> outputs) {
        if (recipeId != null) {
            DeviceRequirement byId = byRecipe.get(recipeId);
            if (byId != null) {
                return byId;
            }
        }
        if (categoryId == null) {
            return null;
        }

        DeviceRequirement wholeCategory = null;
        for (DeviceRequirement rule : rules) {
            for (DeviceRequirement.CategoryMatch category : rule.categories()) {
                if (!category.matches(categoryId, outputs)) {
                    continue;
                }
                if (category.isScoped()) {
                    return rule;
                }
                if (wholeCategory == null) {
                    wholeCategory = rule;
                }
            }
        }
        return wholeCategory;
    }
}
