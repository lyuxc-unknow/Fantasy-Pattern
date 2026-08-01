package cn.lyxc.fantasytechnology.client.screen;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.ActionItems;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.client.Point;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.items.misc.WrappedGenericStack;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.FakeSlot;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * The ME network list's scrollbar asks for every mouse wheel event on the screen, which would leave the
     * ingredient list below with no way to scroll at all. Hand the wheel to the ingredient list while the cursor is
     * over it, and let everything else fall through to AE2's default handling.
     */
    @Override
    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        if (deltaY != 0 && isOverIngredients(x, y)) {
            inputScrollbar.onMouseWheel(new Point((int) x - leftPos, (int) y - topPos), deltaY);
            return true;
        }
        return super.mouseScrolled(x, y, deltaX, deltaY);
    }

    /**
     * Whether the cursor is over the ingredient area - the visible slot window or the scrollbar next to it.
     *
     * The hit test has to happen here: {@link Scrollbar#onMouseWheel} ignores the point it is handed and scrolls
     * unconditionally, because AE2 normally does the hit testing one level up in the widget container, which this
     * override bypasses.
     */
    private boolean isOverIngredients(double x, double y) {
        Rect2i scrollbar = inputScrollbar.getBounds();
        if (scrollbar.contains((int) x - leftPos, (int) y - topPos)) {
            return true;
        }
        for (FakeSlot slot : menu.getInputSlots()) {
            if (slot.isActive() && isHovering(slot.x, slot.y, 16, 16, x, y)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fluid entries in the ingredient and result ghost slots are stored as {@link WrappedGenericStack}, whose vanilla
     * tooltip is just the wrapper item's name - no fluid, no amount. Replace it with the fluid's own tooltip plus a
     * formatted amount line, matching what the ME network view shows for fluids. Item entries pass through unchanged.
     */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        if (stack.getItem() instanceof WrappedGenericStack wrapped) {
            AEKey what = wrapped.unwrapWhat(stack);
            if (what instanceof AEFluidKey) {
                List<Component> tooltip = new ArrayList<>(AEKeyRendering.getTooltip(what));
                tooltip.add(Tooltips.getAmountTooltip(ButtonToolTips.StoredAmount, what,
                        wrapped.unwrapAmount(stack)));
                return tooltip;
            }
        }
        return super.getTooltipFromContainerItem(stack);
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
