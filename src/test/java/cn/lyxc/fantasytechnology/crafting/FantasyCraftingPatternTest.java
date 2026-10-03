package cn.lyxc.fantasytechnology.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
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
    void onlyPatternsWithoutCraftingRemaindersMayBeBatchDispatched() {
        // A plain consumable leaves nothing behind, so a batch of N crafts needs no remainder accounting.
        assertFalse(pattern(AEItemKey.of(Items.COBBLESTONE)).hasCraftingRemainders());
        // Durability alone is not a remainder: the dispenser consumes its bow.
        assertFalse(pattern(AEItemKey.of(Items.BOW)).hasCraftingRemainders());
        // A declared remainder is what counted batch dispatch cannot account for yet, so it stays single-craft.
        assertTrue(pattern(AEItemKey.of(Items.WATER_BUCKET)).hasCraftingRemainders());
    }

    @Test
    void rechecksTheKeysTheCpuActuallyExtracted() {
        // A pattern only knows the alternatives it was decoded with, while the CPU extracts from live storage: a tag
        // can gain a member afterwards, and a data-insensitive ingredient accepts variants whose remainder differs.
        var cobblestone = pattern(AEItemKey.of(Items.COBBLESTONE));
        assertFalse(cobblestone.hasCraftingRemainders());
        assertFalse(cobblestone.extractedInputsHaveRemainders(
                new KeyCounter[]{counted(AEItemKey.of(Items.COBBLESTONE), 8)}));
        assertTrue(cobblestone.extractedInputsHaveRemainders(
                new KeyCounter[]{counted(AEItemKey.of(Items.WATER_BUCKET), 8)}));
    }

    private static KeyCounter counted(AEKey key, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(key, amount);
        return counter;
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
