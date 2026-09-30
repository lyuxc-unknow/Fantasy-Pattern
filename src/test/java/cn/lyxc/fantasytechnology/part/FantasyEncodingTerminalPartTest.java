package cn.lyxc.fantasytechnology.part;

import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.MinecraftTestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class FantasyEncodingTerminalPartTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void savesSelectionAlongsidePreviewAndSharesItBetweenViewers() {
        var part = terminal();
        IFantasyEncodingTerminalHost firstViewer = part;
        IFantasyEncodingTerminalHost secondViewer = part;
        part.getEncodedInputs().setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 3));
        firstViewer.setServerRecipeToken(Optional.of(42L));
        assertEquals(Optional.of(42L), secondViewer.getServerRecipeToken());
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var nbt = new CompoundTag();
        part.writeToNBT(nbt, registries);
        var restored = terminal();
        restored.readFromNBT(nbt, registries);
        assertEquals(firstViewer.getServerRecipeToken(), restored.getServerRecipeToken());
        assertEquals(part.getEncodedInputs().getStack(0), restored.getEncodedInputs().getStack(0));
        restored.clearContent();
        restored.writeToNBT(nbt, registries);
        assertFalse(nbt.contains("serverRecipeToken"));
    }

    @Test
    void onlyOneViewerLoadsEachInsertedPattern() {
        var part = terminal();
        assertFalse(part.consumeEncodedPatternChange());
        part.getPatternInv().setItemDirect(FantasyEncodingTerminalPart.ENCODED_PATTERN_SLOT,
                new ItemStack(Items.PAPER));
        assertTrue(part.consumeEncodedPatternChange());
        assertFalse(part.consumeEncodedPatternChange());
        part.setServerRecipeToken(Optional.of(9L));
        assertFalse(part.consumeEncodedPatternChange());
        assertEquals(Optional.of(9L), part.getServerRecipeToken());
    }

    @Test
    void preservesPendingAndAlreadyObservedInsertionsAcrossSaves() {
        var part = terminal();
        part.getPatternInv().setItemDirect(FantasyEncodingTerminalPart.ENCODED_PATTERN_SLOT,
                new ItemStack(Items.PAPER));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var nbt = new CompoundTag();
        part.writeToNBT(nbt, registries);
        var restored = terminal();
        restored.readFromNBT(nbt, registries);
        assertTrue(restored.consumeEncodedPatternChange());
        restored.setServerRecipeToken(Optional.of(123L));
        restored.writeToNBT(nbt, registries);
        var reopened = terminal();
        reopened.readFromNBT(nbt, registries);
        assertFalse(reopened.consumeEncodedPatternChange());
        assertEquals(Optional.of(123L), reopened.getServerRecipeToken());
    }

    private static FantasyEncodingTerminalPart terminal() {
        return new FantasyEncodingTerminalPart(new IPartItem<FantasyEncodingTerminalPart>() {
            @Override
            public Class<FantasyEncodingTerminalPart> getPartClass() { return FantasyEncodingTerminalPart.class; }
            @Override
            public FantasyEncodingTerminalPart createPart() { return terminal(); }
            @Override
            public @NotNull Item asItem() { return Items.PAPER; }
        }) {
            @Override
            public void saveChanges() { /* No world is attached in this persistence test. */ }
        };
    }
}
