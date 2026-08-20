package cn.lyxc.fantasytechnology.recipeprovider;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import com.ae2vm.addon.crafting.DurableInputAdapters;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/// Default trusted provider. It intentionally exposes only ordinary, fixed-output crafting-table recipes.
public final class CraftingServerRecipeProvider implements ServerRecipeProvider {

    public static final CraftingServerRecipeProvider INSTANCE = new CraftingServerRecipeProvider();
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID,
            "crafting");
    public static final ResourceLocation CATEGORY = ResourceLocation.withDefaultNamespace("crafting");

    private CraftingServerRecipeProvider() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Collection<ServerRecipe> recipes(ServerPlayer player) {
        List<ServerRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : player.level().getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)) {
            ServerRecipe resolved = resolve(player.level(), holder);
            if (resolved != null) {
                recipes.add(resolved);
            }
        }
        return recipes;
    }

    @Override
    public boolean visit(ServerPlayer player, Predicate<ServerRecipe> visitor) {
        return visit(player.level(), visitor);
    }

    @Override
    public Optional<ServerRecipe> find(ServerPlayer player, ResourceLocation recipeId) {
        return find(player.level(), recipeId);
    }

    @Override
    public Collection<ServerRecipe> recipes(Level level) {
        List<ServerRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)) {
            ServerRecipe resolved = resolve(level, holder);
            if (resolved != null) {
                recipes.add(resolved);
            }
        }
        return recipes;
    }

    @Override
    public boolean visit(Level level, Predicate<ServerRecipe> visitor) {
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)) {
            ServerRecipe resolved = resolve(level, holder);
            if (resolved != null && !visitor.test(resolved)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Optional<ServerRecipe> find(Level level, ResourceLocation recipeId) {
        return level.getRecipeManager().byKey(recipeId)
                .filter(holder -> holder.value().getType() == RecipeType.CRAFTING)
                .map(holder -> resolve(level, holder))
                .filter(Objects::nonNull);
    }

    private static @Nullable ServerRecipe resolve(Level level, RecipeHolder<?> holder) {
        if (!(holder.value() instanceof CraftingRecipe recipe) || recipe.isSpecial() || recipe.isIncomplete()) {
            return null;
        }
        ItemStack output = recipe.getResultItem(level.registryAccess());
        AEItemKey outputKey = AEItemKey.of(output);
        if (outputKey == null || output.isEmpty()) {
            return null;
        }

        List<PatternIngredient> ingredients = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            PatternIngredient resolved = resolveIngredient(ingredient);
            if (resolved == null) {
                return null;
            }
            ingredients.add(resolved);
        }
        ingredients = merge(ingredients);
        if (ingredients.isEmpty() || ingredients.size() > FantasyPatternData.MAX_INPUTS) {
            return null;
        }

        return new ServerRecipe(ID, holder.id(), CATEGORY, ingredients,
                List.of(new GenericStack(outputKey, output.getCount())), List.of(false),
                List.of(Items.CRAFTING_TABLE));
    }

    private static @Nullable PatternIngredient resolveIngredient(Ingredient ingredient) {
        ItemStack[] items;
        try {
            items = ingredient.getItems();
        } catch (RuntimeException e) {
            return null;
        }
        if (items.length == 0) {
            return null;
        }
        AEItemKey representative = AEItemKey.of(items[0]);
        if (representative == null) {
            return null;
        }
        ResourceLocation tag = null;
        if (!ingredient.isCustom()) {
            Ingredient.Value[] values = ingredient.getValues();
            if (values.length == 1 && values[0] instanceof Ingredient.TagValue(TagKey<Item> tagKey)) {
                tag = tagKey.location();
            }
        }
        boolean ignoreData = tag == null && DurableInputAdapters.isWearableTool(representative);
        return new PatternIngredient(new GenericStack(representative, 1), Optional.ofNullable(tag), ignoreData);
    }

    private static List<PatternIngredient> merge(List<PatternIngredient> ingredients) {
        Map<FantasyPatternData.IngredientKey, PatternIngredient> merged = new LinkedHashMap<>();
        for (PatternIngredient ingredient : ingredients) {
            merged.merge(FantasyPatternData.IngredientKey.of(ingredient), ingredient,
                    (kept, extra) -> kept.withAmount(kept.amount() + extra.amount()));
        }
        return List.copyOf(merged.values());
    }
}
