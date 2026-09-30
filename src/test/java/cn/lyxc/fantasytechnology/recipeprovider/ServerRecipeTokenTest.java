package cn.lyxc.fantasytechnology.recipeprovider;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.MinecraftTestBootstrap;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ServerRecipeTokenTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void fingerprintsInputAndOutputComponents() {
        ItemStack first = new ItemStack(Items.PAPER);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("first"));
        ItemStack second = new ItemStack(Items.PAPER);
        second.set(DataComponents.CUSTOM_NAME, Component.literal("second"));
        assertNotEquals(token(first, first), token(first, second));
        assertNotEquals(token(first, first), token(second, first));
        assertEquals(token(first, second), token(first.copy(), second.copy()));
    }

    @Test
    void compoundInsertionOrderDoesNotChangeFingerprint() {
        CompoundTag first = new CompoundTag();
        first.putInt("Aa", 1);
        first.putInt("BB", 2); // Same Java string hash: insertion order can affect the underlying map iteration.
        CompoundTag second = new CompoundTag();
        second.putInt("BB", 2);
        second.putInt("Aa", 1);
        assertEquals(token(withData(first), withData(first)), token(withData(second), withData(second)));
    }

    @Test
    void listOrderAndTagTypesRemainSignificant() {
        CompoundTag first = new CompoundTag();
        ListTag list = new ListTag();
        list.add(StringTag.valueOf("one"));
        list.add(StringTag.valueOf("two"));
        first.put("list", list);
        CompoundTag second = new CompoundTag();
        ListTag reversed = new ListTag();
        reversed.add(StringTag.valueOf("two"));
        reversed.add(StringTag.valueOf("one"));
        second.put("list", reversed);
        assertNotEquals(token(withData(first), withData(first)), token(withData(second), withData(second)));
        first.putInt("number", 1);
        second = first.copy();
        second.putLong("number", 1L);
        assertNotEquals(token(withData(first), withData(first)), token(withData(second), withData(second)));
    }

    @Test
    void fingerprintsEnchantmentLevelsUsingWorldRegistries() {
        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var enchantment = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING);
        ItemStack first = new ItemStack(Items.BOW);
        first.enchant(enchantment, 1);
        ItemStack second = new ItemStack(Items.BOW);
        second.enchant(enchantment, 2);
        var provider = ResourceLocation.parse("test:provider");
        var recipe = ResourceLocation.parse("test:enchantment");
        var inputs = List.of(PatternIngredient.of(new GenericStack(AEItemKey.of(Items.STONE), 1)));
        long firstToken = ServerRecipeToken.of(registries, provider, recipe, null, inputs,
                List.of(new GenericStack(AEItemKey.of(first), 1)), List.of(false));
        long secondToken = ServerRecipeToken.of(registries, provider, recipe, null, inputs,
                List.of(new GenericStack(AEItemKey.of(second), 1)), List.of(false));
        assertNotEquals(firstToken, secondToken);
        assertThrows(IllegalStateException.class, () -> token(new ItemStack(Items.STONE), first));
    }

    private static ItemStack withData(CompoundTag tag) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static long token(ItemStack input, ItemStack output) {
        return ServerRecipeToken.of(ResourceLocation.parse("test:provider"), ResourceLocation.parse("test:recipe"),
                null, List.of(PatternIngredient.of(new GenericStack(AEItemKey.of(input), 1))),
                List.of(new GenericStack(AEItemKey.of(output), 1)), List.of(false));
    }
}
