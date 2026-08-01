package cn.lyxc.fantasytechnology.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.config.ActionItems;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.FakeSlot;

import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;

/**
 * Screen of the fantasy encoding terminal.
 *
 * Everything visual - the ME network item list, the panel background, and where every slot sits - comes from AE2:
 * {@link MEStorageScreen} plus the style document loaded from
 * {@code assets/ae2/screens/terminals/fantasy_encoding_terminal.json}.
 *
 * The one thing that cannot be expressed in the style document is the scrolling ingredient list: the menu registers
 * all 81 ingredient slots, but only a 3x3 window of them is visible at a time, so every frame this screen re-applies
 * the base positions and then shifts and enables just the rows the scrollbar is currently over. That is the same
 * approach AE2 uses for its own processing patterns.
 */
public class FantasyEncodingTermScreen extends MEStorageScreen<FantasyEncodingTermMenu> {

    /** Path passed to AE2's StyleManager; resolved against the ae2 namespace, hence the file's location. */
    public static final String STYLE = "/screens/terminals/fantasy_encoding_terminal.json";

    /** Ingredient columns, and how many rows of them fit in the panel. */
    private static final int INPUT_COLUMNS = 3;
    private static final int VISIBLE_ROWS = 3;
    private static final int SLOT_HEIGHT = 18;

    private final Scrollbar inputScrollbar;

    public FantasyEncodingTermScreen(FantasyEncodingTermMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);

        widgets.add("encodePattern", new ActionButton(ActionItems.ENCODE, menu::encode));

        // Small borderless button squeezed between the ingredients and the results, exactly as AE2 does it.
        ActionButton clearButton = new ActionButton(ActionItems.S_CLOSE, menu::clear);
        clearButton.setHalfSize(true);
        clearButton.setDisableBackground(true);
        widgets.add("clearPattern", clearButton);

        inputScrollbar = widgets.addScrollBar("inputScrollbar", Scrollbar.SMALL);
        inputScrollbar.setRange(0, menu.getInputSlots().length / INPUT_COLUMNS - VISIBLE_ROWS, VISIBLE_ROWS);
        inputScrollbar.setCaptureMouseWheel(false);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        // repositionSlots resets every ingredient slot to its base position from the style document; the offset below
        // is then applied on top, so this has to run before the shift and every frame.
        repositionSlots(SlotSemantics.PROCESSING_INPUTS);

        int scroll = inputScrollbar.getCurrentScroll();
        FakeSlot[] inputSlots = menu.getInputSlots();
        for (int i = 0; i < inputSlots.length; i++) {
            FakeSlot slot = inputSlots[i];
            int row = i / INPUT_COLUMNS - scroll;
            slot.setActive(row >= 0 && row < VISIBLE_ROWS);
            slot.y -= scroll * SLOT_HEIGHT;
        }
    }
}
