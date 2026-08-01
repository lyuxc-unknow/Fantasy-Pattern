package cn.lyxc.fantasytechnology.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import cn.lyxc.fantasytechnology.network.TransferRecipePayload;

/**
 * Transfers any recipe JEI can show into the fantasy encoding terminal, items and fluids alike.
 *
 * A fantasy pattern is a processing recipe - a bag of ingredients and a bag of results, with no notion of a grid or a
 * machine - so there is nothing category-specific to honour. Registering universally therefore makes smelting,
 * stonecutting, and modded categories work for free instead of only vanilla crafting.
 *
 * Both the recipe id and the displayed stacks are sent; see {@link TransferRecipePayload} for why the server needs
 * both. Fluids can only ever come from the display, since vanilla's recipe API cannot express them.
 */
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

        Optional<ResourceLocation> recipeId = recipe instanceof RecipeHolder<?> holder
                ? Optional.of(holder.id())
                : Optional.empty();

        List<GenericStack> inputs = read(recipeSlots, RecipeIngredientRole.INPUT, FantasyPatternData.MAX_INPUTS);
        List<GenericStack> outputs = read(recipeSlots, RecipeIngredientRole.OUTPUT, FantasyPatternData.MAX_OUTPUTS);

        // A pattern needs something to consume and something to produce. Results only ever come from the display, so
        // without them there is nothing to encode no matter what the server could resolve.
        if (outputs.isEmpty() || (recipeId.isEmpty() && inputs.isEmpty())) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("gui.fantasy_technology.transfer_unsupported"));
        }

        if (doTransfer) {
            PacketDistributor.sendToServer(new TransferRecipePayload(recipeId, inputs, outputs));
        }
        return null;
    }

    /**
     * The stacks JEI is currently displaying for one role, as AE2 keys. Ingredient types other than items and fluids
     * are skipped: a fantasy pattern has no way to represent them.
     */
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

        return null;
    }
}
