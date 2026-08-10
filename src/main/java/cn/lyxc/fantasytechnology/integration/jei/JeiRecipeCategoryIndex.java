package cn.lyxc.fantasytechnology.integration.jei;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Answers "which category did this recipe come from, and what machines does it list?", which JEI itself will not
/// tell a universal transfer handler.
///
/// A universal handler is invoked with the recipe object alone - JEI knows the category it came from, but
/// {@code IUniversalRecipeTransferHandler#transferRecipe} does not pass it, and there is no recipe-to-category call
/// in the API. Guessing from the recipe's class does not work either: almost every vanilla-backed category shares
/// {@code RecipeHolder}, so the class says nothing about which one a given recipe belongs to.
///
/// So the mapping is built the only way that is exact - walk every category once and remember which one each recipe
/// object came out of, by identity. That is a single pass over JEI's own recipe lists, done lazily on the first
/// transfer check and reused for the rest of the session. It costs one map of every recipe in the game; JEI is
/// already holding all of those objects, so this adds a reference each, not a copy.
///
/// Everything here runs on the client, on the render thread, and is discarded whenever JEI reloads.
public final class JeiRecipeCategoryIndex {

    /// What a recipe has to be checked against: the category it belongs to, and the machines that category lists.
    public record Category(ResourceLocation uid, List<Item> catalysts) {
    }

    @Nullable
    private static volatile IJeiRuntime runtime;

    /// Recipe object -> its category. Identity, because recipe objects are the very instances JEI hands back and two
    /// equal recipes from different categories must not collapse into one.
    @Nullable
    private static Map<Object, Category> categoryByRecipe;

    private JeiRecipeCategoryIndex() {
    }

    /// Called when JEI hands over its runtime, and again on every reload. Drops the index so it is rebuilt against
    /// the new recipe set rather than answering from the old one.
    public static void setRuntime(@Nullable IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        categoryByRecipe = null;
    }

    /// The category {@code recipe} came from, or null when JEI does not know the recipe at all.
    @Nullable
    public static Category categoryOf(Object recipe) {
        if (recipe == null) {
            return null;
        }
        Map<Object, Category> index = index();
        return index == null ? null : index.get(recipe);
    }

    @Nullable
    private static Map<Object, Category> index() {
        Map<Object, Category> existing = categoryByRecipe;
        if (existing != null) {
            return existing;
        }
        IJeiRuntime jei = runtime;
        if (jei == null) {
            return null;
        }
        Map<Object, Category> built = build(jei);
        categoryByRecipe = built;
        return built;
    }

    private static Map<Object, Category> build(IJeiRuntime jei) {
        long startedAt = System.nanoTime();
        var index = new IdentityHashMap<Object, Category>();
        var recipeManager = jei.getRecipeManager();
        int categories = 0;

        for (var category : recipeManager.createRecipeCategoryLookup().includeHidden().get().toList()) {
            RecipeType<?> type = category.getRecipeType();
            Category info;
            try {
                info = new Category(type.getUid(), readCatalysts(jei, type));
            } catch (RuntimeException exception) {
                // A category whose catalysts cannot be read is one the fallback cannot gate on; leaving its recipes
                // out makes them "unknown", which the check reports as having no device rather than as allowed.
                FantasyTechnology.LOGGER.warn("Could not read the catalysts of recipe category {}", type.getUid(),
                        exception);
                continue;
            }

            categories++;
            try {
                for (Object recipe : recipeManager.createRecipeLookup(type).includeHidden().get().toList()) {
                    // First category wins. A recipe object shared by two categories is vanishingly rare, and taking
                    // the first keeps the answer stable across rebuilds.
                    index.putIfAbsent(recipe, info);
                }
            } catch (RuntimeException exception) {
                FantasyTechnology.LOGGER.warn("Could not read the recipes of category {}", type.getUid(), exception);
            }
        }

        FantasyTechnology.LOGGER.info("Indexed {} recipes across {} JEI categories for device access checks in {}ms",
                index.size(), categories, (System.nanoTime() - startedAt) / 1_000_000);
        return index;
    }

    /// The distinct items a category lists as its catalysts, in JEI's own order.
    private static List<Item> readCatalysts(IJeiRuntime jei, RecipeType<?> type) {
        Set<Item> items = new LinkedHashSet<>();
        jei.getRecipeManager().createRecipeCatalystLookup(type).includeHidden().getItemStack()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::getItem)
                .forEach(items::add);
        return List.copyOf(items);
    }
}
