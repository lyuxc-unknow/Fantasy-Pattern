package cn.lyxc.fantasytechnology.integration.jei;

import cn.lyxc.fantasytechnology.MinecraftTestBootstrap;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class FantasyEncodingTransferHandlerTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void rejectsMixedSupportedAndUnsupportedInputsAndOutputs() {
        var slots = slots(List.of(slot(new ItemStack(Items.IRON_INGOT), false), slot(new Object(), false)));
        assertNull(FantasyEncodingTransferHandler.read(slots, RecipeIngredientRole.INPUT, 82));
        assertNull(FantasyEncodingTransferHandler.read(slots, RecipeIngredientRole.OUTPUT, 7));
    }

    @Test
    void ignoresTrulyEmptySlots() {
        var slots = slots(List.of(slot(null, true), slot(new ItemStack(Items.IRON_INGOT, 3), false)));
        var read = FantasyEncodingTransferHandler.read(slots, RecipeIngredientRole.INPUT, 82);
        assertNotNull(read);
        assertEquals(1, read.size());
        assertEquals(3, read.getFirst().amount());
    }

    @Test
    void rejectsNonemptySlotsWithoutADisplayedAlternative() {
        assertNull(FantasyEncodingTransferHandler.read(slots(List.of(slot(null, false))),
                RecipeIngredientRole.INPUT, 82));
    }

    private static IRecipeSlotsView slots(List<IRecipeSlotView> entries) {
        return (IRecipeSlotsView) Proxy.newProxyInstance(FantasyEncodingTransferHandlerTest.class.getClassLoader(),
                new Class<?>[] { IRecipeSlotsView.class }, (proxy, method, args) -> entries);
    }

    private static IRecipeSlotView slot(Object value, boolean empty) {
        Object typed = value == null ? null : Proxy.newProxyInstance(
                FantasyEncodingTransferHandlerTest.class.getClassLoader(), new Class<?>[] { ITypedIngredient.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getItemStack" -> value instanceof ItemStack stack ? Optional.of(stack) : Optional.empty();
                    case "getIngredient" -> args == null || args.length == 0 ? value : Optional.empty();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (IRecipeSlotView) Proxy.newProxyInstance(FantasyEncodingTransferHandlerTest.class.getClassLoader(),
                new Class<?>[] { IRecipeSlotView.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "isEmpty" -> empty;
                    case "getDisplayedIngredient" -> Optional.ofNullable(typed);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
