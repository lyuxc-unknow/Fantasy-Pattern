package cn.lyxc.fantasytechnology.network;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.deviceaccess.DeviceAccessCheck;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import com.ae2vm.addon.crafting.DurableInputAdapters;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


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
/// The server therefore takes tagged item ingredients from the recipe, and pairs each with a displayed slot for its
/// amount (the viewer is the authoritative source of counts; a tag never implies an amount of one). Anything the
/// recipe does not report - fluids, chemicals, and item inputs it filters out - is appended from the display
/// untouched, and ingredients of the same kind are collapsed into one entry with the summed amount, so a bookshelf
/// fills in as {@code 6x planks + 3x book} rather than nine separate slots. Results always come from the display,
/// which sees byproducts and fluid outputs that {@code getResultItem} does not.
@MethodsReturnNonnullByDefault
public record TransferRecipePayload(Optional<ResourceLocation> recipeId, Optional<ResourceLocation> categoryId,
        List<GenericStack> inputs, List<GenericStack> outputs, List<Item> catalysts) implements CustomPacketPayload {

    public static final Type<TransferRecipePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID, "transfer_recipe"));

    /// Upper bound on the catalyst list, which only exists to be re-checked against the network. A recipe category
    /// with more machines than this is not a thing; the cap is here so a hostile packet cannot make the server walk
    /// an unbounded list.
    private static final int MAX_CATALYSTS = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, TransferRecipePayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC.cast()),
                    TransferRecipePayload::recipeId,
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC.cast()),
                    TransferRecipePayload::categoryId,
                    GenericStack.STREAM_CODEC.apply(ByteBufCodecs.list(FantasyPatternData.MAX_INPUTS)),
                    TransferRecipePayload::inputs,
                    GenericStack.STREAM_CODEC.apply(ByteBufCodecs.list(FantasyPatternData.MAX_OUTPUTS)),
                    TransferRecipePayload::outputs,
                    ByteBufCodecs.registry(Registries.ITEM)
                            .apply(ByteBufCodecs.list(MAX_CATALYSTS)),
                    TransferRecipePayload::catalysts,
                    TransferRecipePayload::new);

    /// Recipe types whose ingredients could not be read; see {@link #buildIngredients}. Static and never cleared, so
    /// one failure costs every recipe of that type its tag substitution for the rest of the session - the trade is
    /// deliberate, since the fault lies with the implementation rather than with the individual recipe, and retrying
    /// would re-trigger the other mod's error logging on every transfer.
    private static final Set<RecipeType<?>> UNRESOLVABLE_RECIPE_TYPES = ConcurrentHashMap.newKeySet();

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

            RecipeHolder<?> recipeHolder = recipeId
                    .flatMap(id -> player.level().getRecipeManager().byKey(id))
                    .orElse(null);
            // A packet that claims a registered recipe must resolve to that recipe on the server. Falling back to
            // the submitted display here would let a modified client attach arbitrary data to an invented id.
            if (recipeId.isPresent() && recipeHolder == null) {
                return;
            }

            Recipe<?> recipe = recipeHolder == null ? null : recipeHolder.value();
            ResourceLocation checkedCategoryId = categoryId.orElse(null);
            Set<ResourceLocation> checkedOutputs = DeviceAccessCheck.outputItemIds(outputs);
            if (recipe != null) {
                ResourceLocation recipeTypeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
                if (checkedCategoryId != null && recipeTypeId != null
                        && !checkedCategoryId.equals(recipeTypeId)) {
                    return;
                }
                checkedCategoryId = recipeTypeId;

                ItemStack serverResult = recipe.getResultItem(player.level().registryAccess());
                if (!serverResult.isEmpty()) {
                    checkedOutputs = Set.of(BuiltInRegistries.ITEM.getKey(serverResult.getItem()));
                }
            }

            if (DeviceAccessCheck.isBlockedCategory(checkedCategoryId)) {
                return;
            }

            // The client already refused to send this without the devices being present, but the network is the
            // server's to know: run the same check again against what the device access blocks really hold. This
            // closes the window where a block emptied between the check and the packet, and stops the gate from
            // being a client-side courtesy.
            if (!menu.allowsDeviceAccess(recipeId.orElse(null), checkedCategoryId, checkedOutputs, catalysts)) {
                return;
            }

            // Merging happens before the cap, so a recipe with more slots than a pattern can hold still contributes
            // its full amount to the entry it collapses into. Durable tools (axes, hoes, ...) then have their
            // ignore-data flag set automatically, so "repair/upgrade an axe" recipes accept any damage state instead
            // of the exact one the recipe viewer happened to display.
            List<PatternIngredient> ingredients = mergeIngredients(buildIngredients(recipe, inputs)).stream()
                    .map(TransferRecipePayload::ignoreDamageForTools)
                    .limit(FantasyPatternData.MAX_INPUTS)
                    .toList();

            List<GenericStack> results = outputs.stream()
                    .filter(stack -> stack != null && stack.amount() > 0)
                    .limit(FantasyPatternData.MAX_OUTPUTS)
                    .toList();

            if (!ingredients.isEmpty() && !results.isEmpty()) {
                menu.setEncodedRecipe(ingredients, results);
            }
        });
    }

    /// Collapses ingredients of the same kind - the same tag, or the same exact key - into a single entry whose
    /// amount is the sum, so a bookshelf transfer shows one {@code 6x #minecraft:planks} and one {@code 3x book}
    /// instead of nine separate slots. The first ingredient of each kind keeps its representative and tag; order
    /// follows first appearance. Runs before the list is capped, so nothing is lost off the end that would have
    /// merged into an entry that is kept.
    private static List<PatternIngredient> mergeIngredients(List<PatternIngredient> ingredients) {
        Map<FantasyPatternData.IngredientKey, PatternIngredient> merged = new LinkedHashMap<>();
        for (PatternIngredient ingredient : ingredients) {
            merged.merge(FantasyPatternData.IngredientKey.of(ingredient), ingredient,
                    (kept, extra) -> kept.withAmount(kept.amount() + extra.amount()));
        }
        return new ArrayList<>(merged.values());
    }

    /// Sets the ignore-data flag on exact item ingredients that are tools, so durable tool inputs transferred from a
    /// recipe - axes, hoes, and modded "transfer damage" upgrade recipes such as Mystical Agriculture's essence gear -
    /// match any damage state instead of the exact one JEI displayed. Covers both ordinary tools (any damage) and
    /// "infinite durability" tools that report no max damage but are still tool items. Tag ingredients, non-tool
    /// items, fluids and chemicals are left alone.
    private static PatternIngredient ignoreDamageForTools(PatternIngredient ingredient) {
        if (ingredient.tag().isEmpty() && ingredient.what() instanceof AEItemKey itemKey) {
            if (DurableInputAdapters.isWearableTool(itemKey)) {
                return ingredient.withIgnoreData(true);
            }
        }
        return ingredient;
    }

    /// Merges the recipe's tagged ingredients with the viewer's displayed stacks into the final ingredient list.
    ///
    /// The two sources answer different questions: the recipe knows which items may substitute for one another (the
    /// tag, or the member list of an untagged ingredient), while the viewer knows how many of each the recipe really
    /// consumes - {@code Ingredient} carries no amount. Each resolved ingredient therefore takes the amount of the
    /// displayed slot it is paired with, and that slot is consumed so a second ingredient of the same kind cannot
    /// take it twice. Pairing tries the slot at the ingredient's own index first, since a viewer normally lays its
    /// slots out in the recipe's ingredient order, and only then searches the remaining slots; index-first matters
    /// because a plain search lets a tagged ingredient swallow the exact item a later ingredient needs.
    ///
    /// An ingredient that matches no slot at all is dropped and the leftover slot is appended in its place, so the
    /// entry count is preserved and only that one ingredient's tag is lost - the viewer is the only trustworthy
    /// source of amounts and members, so where the two disagree the viewer wins. An input goes missing entirely
    /// only if the viewer shows fewer item slots than the recipe has ingredients, which is logged. Slots the recipe
    /// never reports - fluids, chemicals, or item inputs it filters out - are appended untouched.
    ///
    /// Reading the ingredients is not free: third-party implementations may mutate their ingredient stacks while
    /// building the list (Modern Industrialization sets counts in place), which other mods' runtime checks can
    /// reject. When that happens the recipe type is remembered in {@link #UNRESOLVABLE_RECIPE_TYPES}, the whole
    /// recipe side is skipped and the displayed stacks are used as-is - amounts and members stay correct, only tag
    /// substitution is lost.
    private static List<PatternIngredient> buildIngredients(@Nullable Recipe<?> recipe, List<GenericStack> displayed) {
        List<PatternIngredient> resolved = List.of();
        if (recipe != null && !UNRESOLVABLE_RECIPE_TYPES.contains(recipe.getType())) {
            try {
                resolved = resolveIngredients(recipe);
            } catch (RuntimeException e) {
                // Some machines implement getIngredients() with side effects - Modern Industrialization mutates the
                // count of its ingredient ItemStacks in place, which AllTheLeaks' ingredient deduplication locks
                // against and turns into an exception here (after logging its own warning). The tags are a
                // nice-to-have; the displayed stacks carry the true amounts, so fall back to them and remember the
                // recipe type so later transfers skip the resolution instead of re-triggering that warning.
                UNRESOLVABLE_RECIPE_TYPES.add(recipe.getType());
                FantasyTechnology.LOGGER.warn("Could not resolve ingredients from recipe type {} for transfer; "
                        + "falling back to the displayed stacks (this recipe type will not be resolved again)",
                        recipe.getType(), e);
                return displayedIngredients(displayed);
            }
        }
        if (resolved.isEmpty()) {
            // No registered recipe, one whose type already failed to resolve, or one that does not report its
            // ingredients (special crafting recipes return an empty list): take the whole thing from what the
            // viewer displayed, tags and all lost.
            return displayedIngredients(displayed);
        }

        // The displayed item stacks, in slot order, each consumed at most once by the ingredient that matches it.
        List<GenericStack> itemStacks = new ArrayList<>();
        for (GenericStack stack : displayed) {
            if (stack != null && stack.amount() > 0 && stack.what() instanceof AEItemKey) {
                itemStacks.add(stack);
            }
        }
        boolean[] consumed = new boolean[itemStacks.size()];

        List<PatternIngredient> ingredients = new ArrayList<>(resolved.size());
        int unmatched = 0;
        for (int i = 0; i < resolved.size(); i++) {
            PatternIngredient ingredient = resolved.get(i);
            int slot = findSlot(ingredient, i, itemStacks, consumed);
            if (slot < 0) {
                // The viewer shows nothing this ingredient matches - an untagged list displayed through a member
                // other than its representative, for instance. Its count and member are unreliable, so drop it and
                // let the leftover slot stand for itself below.
                unmatched++;
                continue;
            }
            consumed[slot] = true;
            ingredients.add(ingredient.withAmount(itemStacks.get(slot).amount()));
        }

        // Append what the recipe never covered: unconsumed item slots first, then everything that is not an item
        // (fluids, chemicals) - those never enter the pairing above and always come from the display.
        int leftover = 0;
        for (int i = 0; i < itemStacks.size(); i++) {
            if (!consumed[i]) {
                ingredients.add(PatternIngredient.of(itemStacks.get(i)));
                leftover++;
            }
        }
        for (GenericStack stack : displayed) {
            if (stack == null || stack.amount() <= 0 || stack.what() instanceof AEItemKey) {
                continue;
            }
            ingredients.add(PatternIngredient.of(stack));
        }

        if (unmatched > leftover) {
            // Fewer leftover slots than dropped ingredients, so some recipe input has no representation at all and
            // the encoded pattern will consume less than the recipe does. Worth knowing about; the player can still
            // fix it by hand in the terminal before encoding.
            FantasyTechnology.LOGGER.warn(
                    "Recipe type {} reports {} ingredient(s) the recipe viewer does not display; the transferred "
                            + "pattern is missing {} input(s)",
                    recipe.getType(), unmatched, unmatched - leftover);
        }
        return ingredients;
    }

    /// The displayed slot this ingredient should take its amount from, or -1 when the viewer shows nothing it could
    /// consume. The slot at the ingredient's own index is tried first - viewers normally lay their slots out in the
    /// recipe's ingredient order, and honouring that stops a tagged ingredient from swallowing the exact item a
    /// later ingredient needs - then the remaining slots are searched, exact key before tag.
    private static int findSlot(PatternIngredient ingredient, int position, List<GenericStack> itemStacks,
            boolean[] consumed) {
        if (position < itemStacks.size() && !consumed[position]
                && ingredient.matches(itemStacks.get(position).what())) {
            return position;
        }
        for (int i = 0; i < itemStacks.size(); i++) {
            if (!consumed[i] && ingredient.what().equals(itemStacks.get(i).what())) {
                return i;
            }
        }
        for (int i = 0; i < itemStacks.size(); i++) {
            if (!consumed[i] && ingredient.matches(itemStacks.get(i).what())) {
                return i;
            }
        }
        return -1;
    }


    /// The recipe's ingredients as the server sees them, tags intact. Items only - see the class comment.
    ///
    /// Two third-party surfaces are involved and they are guarded differently: a list that cannot be built at all
    /// propagates to {@link #buildIngredients}, which then gives up on the whole recipe type, while a single
    /// ingredient that cannot be read only costs itself. One bad entry must not blacklist a recipe type - and
    /// neither must a bug of our own in {@link #convert}.
    private static List<PatternIngredient> resolveIngredients(Recipe<?> recipe) {
        List<PatternIngredient> ingredients = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            PatternIngredient converted;
            try {
                converted = convert(ingredient);
            } catch (RuntimeException e) {
                FantasyTechnology.LOGGER.warn("Skipping an ingredient of recipe type {} that could not be read",
                        recipe.getType(), e);
                continue;
            }
            if (converted != null) {
                ingredients.add(converted);
            }
        }
        return ingredients;
    }

    /// Ingredients taken straight from what the viewer displayed, with no tags. Used only when the recipe reports no
    /// ingredients at all. Not capped here: the caller merges first and caps afterwards.
    private static List<PatternIngredient> displayedIngredients(List<GenericStack> stacks) {
        List<PatternIngredient> ingredients = new ArrayList<>();
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            ingredients.add(PatternIngredient.of(stack));
        }
        return ingredients;
    }

    /// Turns one recipe ingredient into a pattern ingredient, keeping the tag when the ingredient is exactly one tag.
    /// Anything more complex (a hand-written item list, or a NeoForge custom ingredient) falls back to its first
    /// matching item, since a fantasy pattern can only express "this key" or "anything in this tag".
    ///
    /// The amount is a placeholder: {@code Ingredient} has no notion of one, and {@link #buildIngredients} replaces
    /// it with the amount of the displayed slot this ingredient ends up paired with.
    private static @Nullable PatternIngredient convert(Ingredient ingredient) {
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

    private static @Nullable ResourceLocation tagOf(Ingredient ingredient) {
        if (ingredient.isCustom()) {
            return null;
        }
        Ingredient.Value[] values = ingredient.getValues();
        if (values.length == 1 && values[0] instanceof Ingredient.TagValue(
                TagKey<Item> tag
        )) {
            return tag.location();
        }
        return null;
    }
}
