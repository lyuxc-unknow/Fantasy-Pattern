package cn.lyxc.fantasytechnology.recipeprovider;

import cn.lyxc.fantasytechnology.MinecraftTestBootstrap;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingServerRecipeProviderTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void acceptsDefaultItemRemainders() {
        var recipe = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.CLAY),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.WATER_BUCKET)));
        assertTrue(CraftingServerRecipeProvider.usesIngredientRemainders(recipe));
    }

    @Test
    void refusesRecipeSpecificRemaindersInsteadOfInventingThem() {
        var recipe = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.CLAY),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.WATER_BUCKET))) {
            @Override
            public @NotNull NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
                return NonNullList.withSize(input.size(), ItemStack.EMPTY);
            }
        };
        assertFalse(CraftingServerRecipeProvider.usesIngredientRemainders(recipe));
    }
}
