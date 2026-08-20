package cn.lyxc.fantasytechnology.recipeprovider;

import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatapackRecipeProviderTest {

    private static final String RESOURCE_ROOT = "/data/test/recipe_provider/";

    @Test
    void readsItemAndFluidStacksFromDatapackJson() {
        DatapackServerRecipe recipe = parse("items_and_fluids.json");

        assertEquals(ResourceLocation.parse("test:washer"), recipe.category());
        assertEquals(2, recipe.inputs().size());
        assertEquals(2, recipe.outputs().size());
        assertEquals(ResourceLocation.withDefaultNamespace("item"), recipe.inputs().getFirst().type());
        assertEquals(ResourceLocation.withDefaultNamespace("fluid"), recipe.inputs().get(1).type());
        assertEquals(1000, recipe.inputs().get(1).amount());
        assertEquals(Optional.of(ResourceLocation.parse("c:ingots/iron")), recipe.inputs().getFirst().tag());
        assertTrue(recipe.inputs().getFirst().ignoreData());
        assertEquals(ResourceLocation.withDefaultNamespace("fluid"), recipe.outputs().get(1).type());
        assertTrue(recipe.outputs().get(1).ignoreData());
        assertEquals(1, recipe.catalysts().size());
    }

    @Test
    void readsOptionalChemicalStacksFromDatapackJson() {
        DatapackServerRecipe recipe = parse("chemical.json");

        assertEquals(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical"),
                recipe.inputs().getFirst().type());
        assertEquals(ResourceLocation.parse("mekanism:oxygen"), recipe.inputs().getFirst().id());
        assertEquals(1000, recipe.inputs().getFirst().amount());
        assertEquals(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical"),
                recipe.outputs().getFirst().type());
    }

    @Test
    void appliesDefaultsForCategoryAndAmounts() {
        DatapackServerRecipe recipe = parse("defaults.json");

        assertEquals(DatapackServerRecipe.DEFAULT_CATEGORY, recipe.category());
        assertEquals(1, recipe.inputs().getFirst().amount());
        assertEquals(1, recipe.outputs().getFirst().amount());
        assertTrue(recipe.catalysts().isEmpty());
    }

    @Test
    void rejectsMalformedStackDefinitions() {
        assertFalse(parseResult("invalid_output_tag.json").result().isPresent());
        assertFalse(parseResult("invalid_amount.json").result().isPresent());
        assertFalse(parseResult("invalid_too_large_amount.json").result().isPresent());
        assertFalse(parseResult("invalid_missing_id.json").result().isPresent());
    }

    @Test
    void rejectsMissingOrOversizedRecipeSides() {
        assertFalse(parseResult("invalid_empty_inputs.json").result().isPresent());
        assertFalse(parseResult("invalid_empty_outputs.json").result().isPresent());
        assertFalse(parseResult(withRepeatedStacks(FantasyPatternData.MAX_INPUTS + 1, 1)).result().isPresent());
        assertFalse(parseResult(withRepeatedStacks(1, FantasyPatternData.MAX_OUTPUTS + 1)).result().isPresent());
    }

    @Test
    void acceptsMaximumRecipeSidesAndAmount() {
        JsonObject json = withRepeatedStacks(FantasyPatternData.MAX_INPUTS, FantasyPatternData.MAX_OUTPUTS);
        json.getAsJsonArray("inputs").get(0).getAsJsonObject().addProperty("amount", Integer.MAX_VALUE);

        DatapackServerRecipe recipe = parseResult(json).result().orElseThrow();
        assertEquals(FantasyPatternData.MAX_INPUTS, recipe.inputs().size());
        assertEquals(FantasyPatternData.MAX_OUTPUTS, recipe.outputs().size());
        assertEquals(Integer.MAX_VALUE, recipe.inputs().getFirst().amount());
    }

    private static DatapackServerRecipe parse(String resource) {
        return parseResult(resource).result().orElseThrow(() -> new AssertionError("failed to parse " + resource));
    }

    private static DataResult<DatapackServerRecipe> parseResult(String resource) {
        JsonElement json;
        try (Reader reader = new InputStreamReader(
                DatapackRecipeProviderTest.class.getResourceAsStream(RESOURCE_ROOT + resource),
                StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader);
        } catch (Exception exception) {
            throw new AssertionError("could not read " + resource, exception);
        }
        return parseResult(json);
    }

    private static DataResult<DatapackServerRecipe> parseResult(JsonElement json) {
        return DatapackServerRecipe.CODEC.parse(JsonOps.INSTANCE, json);
    }

    private static JsonObject withRepeatedStacks(int inputCount, int outputCount) {
        JsonObject recipe = new JsonObject();
        recipe.add("inputs", repeatedStacks(inputCount));
        recipe.add("outputs", repeatedStacks(outputCount));
        return recipe;
    }

    private static JsonArray repeatedStacks(int count) {
        JsonArray stacks = new JsonArray();
        for (int i = 0; i < count; i++) {
            JsonObject stack = new JsonObject();
            stack.addProperty("id", "minecraft:stone");
            stacks.add(stack);
        }
        return stacks;
    }
}
