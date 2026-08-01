package cn.lyxc.fantasytechnology.ui;

import dev.vfyjxf.taffy.style.FlexDirection;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;

import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.item.FantasyPatternItem;

/**
 * Builds the LDLib modular UI of the fantasy annihilation block: a row of pattern slots above the player inventory.
 *
 * This runs on both sides - the server builds the same tree to register the container slots, the client additionally
 * renders it - so it must not touch client-only code.
 */
public final class FantasyAnnihilationUI {

    private FantasyAnnihilationUI() {
    }

    public static ModularUI create(FantasyAnnihilationBlockEntity annihilation, Player player) {
        IItemHandler patternInv = annihilation.getPatternInv().toItemHandler();

        UIElement patternRow = new UIElement()
                .layout(layout -> layout.flexDirection(FlexDirection.ROW).gapAll(0));
        for (int i = 0; i < FantasyAnnihilationBlockEntity.PATTERN_SLOTS; i++) {
            patternRow.addChild(new ItemSlot()
                    .bind(new PatternOnlySlot(patternInv, i))
                    .slotStyle(style -> style.quickMovePriority(1)));
        }

        UIElement root = new UIElement()
                .addClass("panel_bg")
                .layout(layout -> layout.flexDirection(FlexDirection.COLUMN).widthAuto().heightAuto())
                .addChildren(
                        UIElements.label(annihilation.getDisplayName()),
                        patternRow,
                        new InventorySlots());

        return ModularUI.of(UI.of(root, StylesheetManager.ORE_MERGED), player);
    }

    /** A pattern slot: fantasy patterns only, one per slot. */
    private static class PatternOnlySlot extends SlotItemHandler {

        PatternOnlySlot(IItemHandler itemHandler, int index) {
            super(itemHandler, index, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof FantasyPatternItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
