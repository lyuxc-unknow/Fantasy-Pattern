package cn.lyxc.fantasytechnology.network;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/// Client -> server: fills the fantasy encoding terminal from a recipe the player transferred out of a recipe viewer.
///
/// Two things travel, because neither alone is sufficient:
/// <ul>
/// <li>The recipe id, whenever the viewer had a real registered recipe. The server re-reads it from its own recipe
/// manager, which is the only place the ingredients still carry their tags - the client's copy has them flattened into
/// plain item lists by {@code Ingredient.CONTENTS_STREAM_CODEC} during sync.</li>
/// <li>The ingredients and results as displayed, which is the only way fluids can be recovered at all: vanilla's
/// {@code Recipe#getIngredients} and {@code getResultItem} are item-only, so a recipe's liquid parts exist nowhere but
/// in what the viewer draws.</li>
/// </ul>
///
/// The server therefore takes tagged item ingredients from the recipe and adds the displayed fluids on top; those two
/// sets cannot overlap, since a fluid can never appear in {@code getIngredients}. Results always come from the display,
/// which sees byproducts and fluid outputs that {@code getResultItem} does not.
public record TransferRecipePayload(Optional<ResourceLocation> recipeId, List<GenericStack> inputs,
        List<GenericStack> outputs) implements CustomPacketPayload {

    public static final Type<TransferRecipePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID, "transfer_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransferRecipePayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC.cast()),
                    TransferRecipePayload::recipeId,
                    GenericStack.STREAM_CODEC.apply(ByteBufCodecs.list(FantasyPatternData.MAX_INPUTS)),
                    TransferRecipePayload::inputs,
                    GenericStack.STREAM_CODEC.apply(ByteBufCodecs.list(FantasyPatternData.MAX_OUTPUTS)),
                    TransferRecipePayload::outputs,
                    TransferRecipePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.containerMenu instanceof FantasyEncodingTermMenu menu)) {
                return;
            }

            Recipe<?> recipe = recipeId
                    .flatMap(id -> player.level().getRecipeManager().byKey(id))
                    .map(RecipeHolder::value)
                    .orElse(null);

            List<PatternIngredient> ingredients = resolveIngredients(recipe);
            if (ingredients.isEmpty()) {
                // No registered recipe, or one that does not report its ingredients (special crafting recipes return
                // an empty list): take the whole thing from what the viewer displayed, tags and all lost.
                ingredients = displayedIngredients(inputs, FantasyPatternData.MAX_INPUTS, false);
            } else {
                ingredients.addAll(displayedIngredients(inputs,
                        FantasyPatternData.MAX_INPUTS - ingredients.size(), true));
            }

            List<GenericStack> results = outputs.stream()
                    .filter(stack -> stack != null && stack.amount() > 0)
                    .limit(FantasyPatternData.MAX_OUTPUTS)
                    .toList();

            if (!ingredients.isEmpty() && !results.isEmpty()) {
                menu.setEncodedRecipe(ingredients, results);
            }
        });
    }


    /// The recipe's ingredients as the server sees them, tags intact. Items only - see the class comment.
    private static List<PatternIngredient> resolveIngredients(Recipe<?> recipe) {
        List<PatternIngredient> ingredients = new ArrayList<>();
        if (recipe == null) {
            return ingredients;
        }
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty() || ingredients.size() >= FantasyPatternData.MAX_INPUTS) {
                continue;
            }
            PatternIngredient converted = convert(ingredient);
            if (converted != null) {
                ingredients.add(converted);
            }
        }
        return ingredients;
    }

    /// Ingredients taken straight from what the viewer displayed, with no tags.
    ///
    /// @param fluidsOnly when true, only fluid entries are kept - used to top up a set of ingredients that the server
    ///                   already resolved from the recipe, which by definition contains no fluids.
    private static List<PatternIngredient> displayedIngredients(List<GenericStack> stacks, int limit,
            boolean fluidsOnly) {
        List<PatternIngredient> ingredients = new ArrayList<>();
        for (GenericStack stack : stacks) {
            if (ingredients.size() >= limit) {
                break;
            }
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            if (fluidsOnly && stack.what() instanceof AEItemKey) {
                continue;
            }
            ingredients.add(PatternIngredient.of(stack));
        }
        return ingredients;
    }

    /// Turns one recipe ingredient into a pattern ingredient, keeping the tag when the ingredient is exactly one tag.
    /// Anything more complex (a hand-written item list, or a NeoForge custom ingredient) falls back to its first
    /// matching item, since a fantasy pattern can only express "this key" or "anything in this tag".
    private static PatternIngredient convert(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return null;
        }
        AEItemKey representative = AEItemKey.of(items[0]);
        if (representative == null) {
            return null;
        }
        GenericStack stack = new GenericStack(representative, 1);

        ResourceLocation tag = tagOf(ingredient);
        return tag == null ? PatternIngredient.of(stack) : PatternIngredient.of(stack, tag);
    }

    private static ResourceLocation tagOf(Ingredient ingredient) {
        if (ingredient.isCustom()) {
            return null;
        }
        Ingredient.Value[] values = ingredient.getValues();
        if (values.length == 1 && values[0] instanceof Ingredient.TagValue tagValue) {
            return tagValue.tag().location();
        }
        return null;
    }
}
