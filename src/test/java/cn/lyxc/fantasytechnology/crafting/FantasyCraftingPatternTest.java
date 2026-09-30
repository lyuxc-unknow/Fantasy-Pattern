package cn.lyxc.fantasytechnology.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.MinecraftTestBootstrap;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import com.ae2vm.addon.crafting.DurableInputAdapters;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FantasyCraftingPatternTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void consumesBowEvenThoughItHasDurability() {
        var bow = AEItemKey.of(Items.BOW);
        var input = pattern(bow).getInputs()[0];
        assertNull(input.getRemainingKey(bow));
        assertNull(DurableInputAdapters.wearDownBy(input, bow, 1));
        assertEquals(DurableInputAdapters.Mode.CONSUMABLE,
                DurableInputAdapters.classifyEncodedCandidate(input, bow));
    }

    @Test
    void returnsDeclaredBucketRemainder() {
        var water = AEItemKey.of(Items.WATER_BUCKET);
        assertEquals(AEItemKey.of(Items.BUCKET), pattern(water).getInputs()[0].getRemainingKey(water));
    }

    @Test
    void declaredCrystalWearSurvivesAndBreaksOnLastUse() {
        var registry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        registry.unfreeze();
        Item crystal = new Item(new Item.Properties().durability(4)) {
            @Override
            public @NotNull ItemStack getCraftingRemainingItem(ItemStack input) {
                if (input.getDamageValue() + 1 >= input.getMaxDamage()) {
                    return ItemStack.EMPTY;
                }
                ItemStack result = input.copy();
                result.setDamageValue(input.getDamageValue() + 1);
                return result;
            }
        };
        Registry.register(registry, "test:reusable_crystal", crystal);
        registry.freeze();
        var fresh = AEItemKey.of(crystal);
        var input = pattern(fresh).getInputs()[0];
        var worn = (AEItemKey) input.getRemainingKey(fresh);
        assertNotNull(worn);
        assertEquals(1, worn.toStack().getDamageValue());
        assertTrue(input.isValid(worn, null));
        assertNull(DurableInputAdapters.wearDownBy(input, fresh, 4));
        ItemStack renamed = worn.toStack();
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("different component"));
        assertFalse(input.isValid(AEItemKey.of(renamed), null));
    }

    private static FantasyCraftingPattern pattern(AEItemKey input) {
        return new FantasyCraftingPattern(AEItemKey.of(Items.PAPER), new FantasyPatternData(
                List.of(PatternIngredient.of(new GenericStack(input, 1))),
                List.of(new GenericStack(AEItemKey.of(Items.DISPENSER), 1))));
    }
}
