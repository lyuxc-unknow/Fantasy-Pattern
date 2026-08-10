package cn.lyxc.fantasytechnology.integration.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.deviceaccess.DeviceAccessCheck;
import cn.lyxc.fantasytechnology.integration.mekanism.MekanismCompat;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import cn.lyxc.fantasytechnology.network.TransferRecipePayload;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// Transfers any recipe JEI can show into the fantasy encoding terminal, items and fluids alike.
/// A fantasy pattern is a processing recipe - a bag of ingredients and a bag of results, with no notion of a grid or a
/// machine - so there is nothing category-specific to honour. Registering universally therefore makes smelting,
/// stonecutting, and modded categories work for free instead of only vanilla crafting.
/// Both the recipe id and the displayed stacks are sent; see {@link TransferRecipePayload} for why the server needs
/// both. Fluids can only ever come from the display, since vanilla's recipe API cannot express them.
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class FantasyEncodingTransferHandler implements IUniversalRecipeTransferHandler<FantasyEncodingTermMenu> {

    private final IRecipeTransferHandlerHelper helper;

    public FantasyEncodingTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends FantasyEncodingTermMenu> getContainerClass() {
        return FantasyEncodingTermMenu.class;
    }

    @Override
    public Optional<MenuType<FantasyEncodingTermMenu>> getMenuType() {
        return Optional.of(FantasyEncodingTermMenu.TYPE);
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(FantasyEncodingTermMenu container, Object recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {

        var category = JeiRecipeCategoryIndex.categoryOf(recipe);
        ResourceLocation categoryUid = category == null ? null : category.uid();
        if (isBlocked(categoryUid)) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("gui.fantasy_technology.transfer_unsupported"));
        }

        Optional<ResourceLocation> recipeId = recipe instanceof RecipeHolder<?> holder
                ? Optional.of(holder.id())
                : Optional.empty();

        Optional<ResourceLocation> categoryId = Optional.ofNullable(categoryUid);
        List<Item> catalysts = category == null ? List.of() : category.catalysts();

        List<GenericStack> inputs = read(recipeSlots, RecipeIngredientRole.INPUT, FantasyPatternData.MAX_INPUTS);
        List<GenericStack> outputs = read(recipeSlots, RecipeIngredientRole.OUTPUT, FantasyPatternData.MAX_OUTPUTS);

        // A pattern needs something to consume and something to produce. Results only ever come from the display, so
        // without them there is nothing to encode no matter what the server could resolve.
        if (outputs.isEmpty() || (recipeId.isEmpty() && inputs.isEmpty())) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("gui.fantasy_technology.transfer_unsupported"));
        }

        // Unless the config waives it, encoding is gated on the network owning the machine. Checked once the results
        // are known, because a category rule may be scoped to the item a recipe produces.
        var verdict = DeviceAccessCheck.checkOnClient(recipeId.orElse(null), categoryId.orElse(null),
                DeviceAccessCheck.outputItemIds(outputs), catalysts);
        if (!verdict.allowed()) {
            return helper.createUserErrorWithTooltip(verdict.reason());
        }

        if (doTransfer) {
            PacketDistributor.sendToServer(
                    new TransferRecipePayload(recipeId, categoryId, inputs, outputs, catalysts));
        }
        return null;
    }

    /// Categories whose "recipes" are not actual recipes must never be transferred into a pattern. The default list
    /// covers JEI's tag-information pages and AE2's P2P attunement browser, and a server may replace or extend it.
    ///
    /// This is a session snapshot rather than a live view of the config. It is refreshed by the client login event,
    /// so editing the server config cannot change an open world's transfer behaviour halfway through the session.
    private static volatile Set<ResourceLocation> blockedCategoryIds = parseBlockedCategoryIds(
            FTConfig.DEFAULT_BLOCKED_JEI_CATEGORY_IDS);

    public static void captureBlockedCategoryIds() {
        blockedCategoryIds = parseBlockedCategoryIds(FTConfig.BLOCKED_JEI_CATEGORY_IDS.get());
    }

    private static Set<ResourceLocation> parseBlockedCategoryIds(List<? extends String> configuredIds) {
        Set<ResourceLocation> parsed = new LinkedHashSet<>();
        for (String configuredId : configuredIds) {
            ResourceLocation id = ResourceLocation.tryParse(configuredId);
            if (id != null) {
                parsed.add(id);
            } else {
                // The config spec rejects these when it can, but keep the snapshot robust against values supplied by
                // another config implementation or an older malformed file.
                FantasyTechnology.LOGGER.warn("Ignoring invalid blocked JEI category id: {}", configuredId);
            }
        }
        return Set.copyOf(parsed);
    }

    /// Whether this recipe belongs to a category that must not be encoded.
    private static boolean isBlocked(@Nullable ResourceLocation categoryId) {
        return categoryId != null && blockedCategoryIds.contains(categoryId);
    }

    /// The stacks JEI is currently displaying for one role, as AE2 keys. Ingredient types other than items, fluids and
    /// MEK chemicals are skipped: a fantasy pattern has no way to represent them.
    private static List<GenericStack> read(IRecipeSlotsView slots, RecipeIngredientRole role, int limit) {
        List<GenericStack> stacks = new ArrayList<>();
        for (IRecipeSlotView slotView : slots.getSlotViews(role)) {
            if (stacks.size() >= limit) {
                break;
            }
            slotView.getDisplayedIngredient().map(FantasyEncodingTransferHandler::toGenericStack)
                    .filter(stack -> stack != null && stack.amount() > 0)
                    .ifPresent(stacks::add);
        }
        return stacks;
    }

    @Nullable
    private static GenericStack toGenericStack(ITypedIngredient<?> ingredient) {
        Optional<ItemStack> item = ingredient.getItemStack();
        if (item.isPresent() && !item.get().isEmpty()) {
            AEItemKey key = AEItemKey.of(item.get());
            return key == null ? null : new GenericStack(key, item.get().getCount());
        }

        Optional<FluidStack> fluid = ingredient.getIngredient(NeoForgeTypes.FLUID_STACK);
        if (fluid.isPresent() && !fluid.get().isEmpty()) {
            AEFluidKey key = AEFluidKey.of(fluid.get());
            return key == null ? null : new GenericStack(key, fluid.get().getAmount());
        }

        // MEK chemicals are a JEI ingredient type of their own; the compat facade does the recognising, so
        // mekanism's classes are never named here. It answers null for anything that is not a chemical.
        return MekanismCompat.toGenericStack(ingredient.getIngredient());
    }
}
