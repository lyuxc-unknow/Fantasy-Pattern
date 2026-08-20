package cn.lyxc.fantasytechnology.client.screen;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.ActionItems;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.items.misc.WrappedGenericStack;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.FakeSlot;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import cn.lyxc.fantasytechnology.network.RequestServerRecipesPayload;
import cn.lyxc.fantasytechnology.network.SelectServerRecipePayload;
import cn.lyxc.fantasytechnology.part.FantasyEncodingTerminalPart;
import cn.lyxc.fantasytechnology.recipeprovider.ServerRecipeSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/// Screen of the fantasy encoding terminal.
///
/// Everything visual - the ME network item list, the panel background, and where every slot sits - comes from AE2:
/// {@link MEStorageScreen} plus the style document loaded from
/// {@code assets/ae2/screens/terminals/fantasy_encoding_terminal.json}.
///
/// The one thing that cannot be expressed in the style document is the scrolling ingredient list: the menu registers
/// all 81 ingredient slots, but only a 3x3 window of them is visible at a time, so every frame this screen re-applies
/// the base positions and then shifts and enables just the rows the scrollbar is currently over. That is the same
/// approach AE2 uses for its own processing patterns.
@MethodsReturnNonnullByDefault
public class FantasyEncodingTermScreen extends MEStorageScreen<FantasyEncodingTermMenu> {

    /// Path passed to AE2's StyleManager; resolved against the ae2 namespace, hence the file's location.
    public static final String STYLE = "/screens/terminals/fantasy_encoding_terminal.json";

    /// Ingredient columns, and how many rows of them fit in the panel.
    private static final int INPUT_COLUMNS = 3;
    private static final int VISIBLE_ROWS = 3;
    private static final int SLOT_HEIGHT = 18;
    private static final int PROVIDER_MAX_ROWS = 6;
    private static final int PROVIDER_ROW_HEIGHT = 24;
    /// How long typing has to pause before the query goes to the server, in milliseconds.
    private static final long PROVIDER_QUERY_DEBOUNCE_MS = 200;

    /// Icon of the "double the amounts" button: one 16x16 frame, drawn at the button's full size.
    private static final ResourceLocation DOUBLE_AMOUNTS_TEXTURE = ResourceLocation
            .fromNamespaceAndPath(FantasyTechnology.MODID, "textures/gui/double_amounts.png");

    private final Scrollbar inputScrollbar;
    private final IconButton recipeProviderButton;

    private boolean recipeProviderOpen;
    private EditBox recipeSearch;
    private Button providerPreviousButton;
    private Button providerNextButton;
    private Button providerRefreshButton;
    private Button providerCloseButton;
    private List<RecipeRowButton> providerRows = List.of();
    private int providerPage;
    private int providerSeenRevision = Integer.MIN_VALUE;
    /// Wall-clock time at which a typed query should turn into a request, or {@link Long#MIN_VALUE} when none is
    /// pending. Typing sends one request per pause rather than one per character.
    private long providerQueryDueAt = Long.MIN_VALUE;
    private int providerPanelX;
    private int providerPanelY;
    private int providerPanelWidth;
    private int providerPanelHeight;

    public FantasyEncodingTermScreen(FantasyEncodingTermMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);

        widgets.add("encodePattern", new ActionButton(ActionItems.ENCODE, menu::encode));

        // Small borderless button squeezed between the ingredients and the results, exactly as AE2 does it.
        ActionButton clearButton = new ActionButton(ActionItems.S_CLOSE, menu::clear);
        clearButton.setHalfSize(true);
        clearButton.setDisableBackground(true);
        widgets.add("clearPattern", clearButton);

        // Doubles the amounts of the encoded ingredients and results. Sits on the left of the results block, below
        // the clear button.
        //
        // The whole look comes from this mod's own texture, so renderWidget is replaced outright rather than going
        // through an Icon from AE2's sprite sheet. That also discards everything else IconButton draws there - the
        // button background, the hover highlight and the half-size scaling - which is why setHalfSize and
        // setDisableBackground are not called: they only feed the rendering being replaced. The button therefore
        // occupies its full 16x16 hit box, unlike the half-size clear button above it.
        IconButton doubleButton = new IconButton(btn -> menu.doubleAmounts()) {
            @Override
            protected Icon getIcon() {
                // Never consulted, since renderWidget below does not use it; only satisfies the abstract method.
                return Icon.ARROW_UP;
            }

            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                guiGraphics.blit(DOUBLE_AMOUNTS_TEXTURE, getX(), getY(), 0, 0, 16, 16, 16, 16);
            }

            @Override
            public List<Component> getTooltipMessage() {
                return List.of(Component.translatable("gui.fantasy_technology.double_amounts"));
            }
        };
        widgets.add("doubleAmounts", doubleButton);

        recipeProviderButton = new IconButton(button -> openRecipeProvider()) {
            @Override
            protected Icon getIcon() {
                return Icon.TAB_CRAFTING;
            }

            @Override
            public List<Component> getTooltipMessage() {
                return List.of(Component.translatable("gui.fantasy_technology.server_recipe_provider"));
            }
        };
        widgets.add("recipeProvider", recipeProviderButton);

        inputScrollbar = widgets.addScrollBar("inputScrollbar", Scrollbar.SMALL);
        inputScrollbar.setRange(0, menu.getInputSlots().length / INPUT_COLUMNS - VISIBLE_ROWS, VISIBLE_ROWS);
        inputScrollbar.setCaptureMouseWheel(false);
    }

    /// The ME network list's scrollbar asks for every mouse wheel event on the screen, which would leave the
    /// ingredient list below with no way to scroll at all. Hand the wheel to the ingredient list while the cursor is
    /// over it, and let everything else fall through to AE2's default handling.
    @Override
    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        if (recipeProviderOpen) {
            if (deltaY > 0) {
                changeProviderPage(-1);
            } else if (deltaY < 0) {
                changeProviderPage(1);
            }
            return true;
        }
        if (deltaY != 0 && isOverIngredients(x, y)) {
            inputScrollbar.onMouseWheel(new Point((int) x - leftPos, (int) y - topPos), deltaY);
            return true;
        }
        return super.mouseScrolled(x, y, deltaX, deltaY);
    }

    /// Whether the cursor is over the ingredient area - the visible slot window or the scrollbar next to it.
    ///
    /// The hit test has to happen here: {@link Scrollbar#onMouseWheel} ignores the point it is handed and scrolls
    /// unconditionally, because AE2 normally does the hit testing one level up in the widget container, which this
    /// override bypasses.
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

    /// Right-clicking an ingredient or result slot toggles whether that entry ignores data components (NBT) when
    /// matched. The index handed to the menu counts the ingredient slots first and the results after them; it is not
    /// a menu slot index.
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (recipeProviderOpen) {
            // The provider widgets are rendered by the top-level overlay instead of being registered with the
            // underlying AE2 screen. Keep focus ownership explicit; EditBox.mouseClicked() may report a handled
            // click even when the pointer is outside its bounds.
            if (recipeSearch != null && recipeSearch.isMouseOver(x, y)) {
                focusRecipeSearch();
                recipeSearch.mouseClicked(x, y, button);
                return true;
            }
            clearRecipeSearchFocus();
            if (providerPreviousButton.mouseClicked(x, y, button)
                    || providerNextButton.mouseClicked(x, y, button)
                    || providerRefreshButton.mouseClicked(x, y, button)
                    || providerCloseButton.mouseClicked(x, y, button)) {
                return true;
            }
            for (RecipeRowButton row : providerRows) {
                if (row.mouseClicked(x, y, button)) {
                    return true;
                }
            }
            return true;
        }
        if (button == 1) {
            for (int i = 0; i < menu.getInputSlots().length; i++) {
                FakeSlot slot = menu.getInputSlots()[i];
                if (slot.isActive() && isHovering(slot.x, slot.y, 16, 16, x, y)) {
                    menu.toggleIgnore(i);
                    return true;
                }
            }
            for (int i = 0; i < menu.getOutputSlots().length; i++) {
                FakeSlot slot = menu.getOutputSlots()[i];
                if (slot.isActive() && isHovering(slot.x, slot.y, 16, 16, x, y)) {
                    menu.toggleIgnore(FantasyEncodingTerminalPart.INPUT_SLOTS + i);
                    return true;
                }
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (recipeProviderOpen && recipeSearch != null) {
            recipeSearch.mouseReleased(x, y, button);
            providerPreviousButton.mouseReleased(x, y, button);
            providerNextButton.mouseReleased(x, y, button);
            providerRefreshButton.mouseReleased(x, y, button);
            providerCloseButton.mouseReleased(x, y, button);
            providerRows.forEach(row -> row.mouseReleased(x, y, button));
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (recipeProviderOpen && recipeSearch != null) {
            recipeSearch.mouseDragged(x, y, button, dragX, dragY);
            return true;
        }
        return super.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (recipeProviderOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeRecipeProvider();
                return true;
            }
            return recipeSearch != null && recipeSearch.isFocused()
                    && recipeSearch.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (recipeProviderOpen) {
            return recipeSearch != null && recipeSearch.isFocused()
                    && recipeSearch.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    /// Fluid and chemical entries in the ingredient and result ghost slots are stored as {@link WrappedGenericStack},
    /// whose vanilla tooltip is just the wrapper item's name - no fluid, no amount. Replace it with the key's own
    /// tooltip plus a formatted amount line, matching what the ME network view shows. Item entries pass through
    /// unchanged. The hovered ingredient/result slot's ignore-data state is appended to the tooltip so the toggle is
    /// visible without trial and error.
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip;
        if (stack.getItem() instanceof WrappedGenericStack wrapped) {
            AEKey what = wrapped.unwrapWhat(stack);
            if (what != null) {
                tooltip = new ArrayList<>(AEKeyRendering.getTooltip(what));
                tooltip.add(Tooltips.getAmountTooltip(ButtonToolTips.StoredAmount, what,
                        wrapped.unwrapAmount(stack)));
            } else {
                tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
            }
        } else {
            tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        }
        appendIgnoreStatus(tooltip);
        return tooltip;
    }

    /// Appends the ignore-data state of the hovered ingredient or result slot, so right-click toggling is visible
    /// in the tooltip. Read from the menu's synchronised bitset rather than from the part, whose client-side copy
    /// never hears about a change the server made on its own.
    private void appendIgnoreStatus(List<Component> tooltip) {
        Slot slot = hoveredSlot;
        if (slot == null) {
            return;
        }
        for (int i = 0; i < menu.getInputSlots().length; i++) {
            if (menu.getInputSlots()[i] == slot) {
                tooltip.add(ignoreStatusLine(menu.isInputIgnored(i)));
                return;
            }
        }
        for (int i = 0; i < menu.getOutputSlots().length; i++) {
            if (menu.getOutputSlots()[i] == slot) {
                tooltip.add(ignoreStatusLine(menu.isOutputIgnored(i)));
                return;
            }
        }
    }

    private static Component ignoreStatusLine(boolean ignore) {
        return Component.translatable(ignore ? "gui.fantasy_technology.ignore_data_yes"
                : "gui.fantasy_technology.ignore_data_no");
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        recipeProviderButton.setVisibility(menu.trustServerRecipeParsing);
        if (recipeProviderOpen && !menu.trustServerRecipeParsing) {
            closeRecipeProvider();
        }

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

    public boolean isRecipeProviderOpen() {
        return recipeProviderOpen;
    }

    private void openRecipeProvider() {
        if (!menu.trustServerRecipeParsing || recipeProviderOpen) {
            return;
        }
        recipeProviderOpen = true;
        recipeSearch = new EditBox(font, 0, 0, 180, 20,
                Component.translatable("gui.fantasy_technology.server_recipe_provider.search"));
        recipeSearch.setMaxLength(RequestServerRecipesPayload.MAX_QUERY_LENGTH);
        recipeSearch.setHint(Component.translatable("gui.fantasy_technology.server_recipe_provider.search"));
        recipeSearch.setResponder(value -> {
            providerPage = 0;
            // Debounced: the catalogue lives on the server, so every keystroke would otherwise be a round trip.
            providerQueryDueAt = Util.getMillis() + PROVIDER_QUERY_DEBOUNCE_MS;
        });
        providerPreviousButton = Button.builder(Component.literal("<"), button -> changeProviderPage(-1)).build();
        providerNextButton = Button.builder(Component.literal(">"), button -> changeProviderPage(1)).build();
        providerRefreshButton = Button.builder(
                Component.translatable("gui.fantasy_technology.server_recipe_provider.refresh"),
                button -> requestServerRecipes()).build();
        providerCloseButton = Button.builder(CommonComponents.GUI_BACK, button -> closeRecipeProvider()).build();
        layoutRecipeProvider();
        focusRecipeSearch();
        requestServerRecipes();
    }

    private void focusRecipeSearch() {
        if (recipeSearch != null) {
            recipeSearch.setFocused(true);
            setFocused(recipeSearch);
        }
    }

    private void clearRecipeSearchFocus() {
        if (recipeSearch != null) {
            recipeSearch.setFocused(false);
        }
        if (getFocused() == recipeSearch) {
            setFocused(null);
        }
    }

    private void closeRecipeProvider() {
        recipeProviderOpen = false;
        providerRows = List.of();
        providerQueryDueAt = Long.MIN_VALUE;
        clearRecipeSearchFocus();
    }

    /// Asks the server for the page the panel currently wants. The server decides what matches and what the player is
    /// allowed to encode, so the client never filters or sorts the catalogue itself.
    private void requestServerRecipes() {
        if (!recipeProviderOpen || recipeSearch == null) {
            return;
        }
        providerQueryDueAt = Long.MIN_VALUE;
        menu.beginServerRecipeCatalogRequest();
        PacketDistributor.sendToServer(new RequestServerRecipesPayload(
                recipeSearch.getValue(), providerPage, providerRowsPerPage()));
    }

    private void layoutRecipeProvider() {
        providerPanelWidth = Math.min(360, width - 32);
        providerPanelHeight = Math.min(262, height - 24);
        providerPanelX = (width - providerPanelWidth) / 2;
        providerPanelY = (height - providerPanelHeight) / 2;

        int contentX = providerPanelX + 12;
        int contentWidth = providerPanelWidth - 24;
        recipeSearch.setPosition(contentX, providerPanelY + 27);
        recipeSearch.setWidth(contentWidth);

        int footerY = providerPanelY + providerPanelHeight - 28;
        providerPreviousButton.setRectangle(24, 20, contentX, footerY);
        providerNextButton.setRectangle(24, 20, contentX + 28, footerY);
        providerRefreshButton.setRectangle(92, 20, providerPanelX + providerPanelWidth - 202, footerY);
        providerCloseButton.setRectangle(102, 20, providerPanelX + providerPanelWidth - 106, footerY);
        rebuildProviderRows();
    }

    /// Lays out the rows for the page the server last sent. The list arrives already filtered, sorted and judged for
    /// device access, so there is nothing left to decide here but where the rows go.
    private void rebuildProviderRows() {
        if (!recipeProviderOpen) {
            return;
        }
        List<ServerRecipeSummary> page = menu.getServerRecipeCatalog();
        int visible = Math.min(page.size(), providerRowsPerPage());
        List<RecipeRowButton> rows = new ArrayList<>(visible);
        int rowX = providerPanelX + 12;
        int rowWidth = providerPanelWidth - 24;
        int rowY = providerPanelY + 64;
        for (int i = 0; i < visible; i++) {
            rows.add(new RecipeRowButton(rowX, rowY + i * PROVIDER_ROW_HEIGHT,
                    rowWidth, PROVIDER_ROW_HEIGHT - 2, page.get(i)));
        }
        providerRows = List.copyOf(rows);
        if (providerPreviousButton != null) {
            providerPreviousButton.active = providerPage > 0;
            providerNextButton.active = providerPage + 1 < menu.getServerRecipeCatalogTotalPages();
        }
    }

    private int providerRowsPerPage() {
        return Math.max(2, Math.min(PROVIDER_MAX_ROWS, (providerPanelHeight - 98) / PROVIDER_ROW_HEIGHT));
    }

    private void changeProviderPage(int delta) {
        int next = Math.max(0, Math.min(providerPage + delta, menu.getServerRecipeCatalogTotalPages() - 1));
        if (next != providerPage) {
            providerPage = next;
            requestServerRecipes();
        }
    }

    private void selectServerRecipe(ServerRecipeSummary summary) {
        if (!summary.available()) {
            return;
        }
        PacketDistributor.sendToServer(new SelectServerRecipePayload(summary.providerId(), summary.recipeId()));
        closeRecipeProvider();
    }

    /// Drawn from ScreenEvent.Render.Post at LOWEST priority, after JEI's overlays and tooltips.
    public void renderRecipeProviderOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!recipeProviderOpen) {
            return;
        }
        if (providerPanelWidth != Math.min(360, width - 32)
                || providerPanelHeight != Math.min(262, height - 24)) {
            // The panel resized, so the page it can hold changed with it; ask the server for the new size.
            layoutRecipeProvider();
            requestServerRecipes();
        }
        if (providerQueryDueAt != Long.MIN_VALUE && Util.getMillis() >= providerQueryDueAt) {
            requestServerRecipes();
        }
        if (providerSeenRevision != menu.getServerRecipeCatalogRevision()) {
            providerSeenRevision = menu.getServerRecipeCatalogRevision();
            providerPage = menu.getServerRecipeCatalogPage();
            rebuildProviderRows();
        }

        clearTooltipForNextRenderPass();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 1_000);
        try {
            guiGraphics.fill(0, 0, width, height, 0xB0000000);
            guiGraphics.fill(providerPanelX - 1, providerPanelY - 1,
                    providerPanelX + providerPanelWidth + 1, providerPanelY + providerPanelHeight + 1, 0xFF8B8D91);
            guiGraphics.fill(providerPanelX, providerPanelY,
                    providerPanelX + providerPanelWidth, providerPanelY + providerPanelHeight, 0xFF202124);
            guiGraphics.drawCenteredString(font,
                    Component.translatable("gui.fantasy_technology.server_recipe_provider"),
                    width / 2, providerPanelY + 9, 0xFFFFFFFF);

            recipeSearch.render(guiGraphics, mouseX, mouseY, partialTick);
            providerRows.forEach(row -> row.render(guiGraphics, mouseX, mouseY, partialTick));
            providerPreviousButton.render(guiGraphics, mouseX, mouseY, partialTick);
            providerNextButton.render(guiGraphics, mouseX, mouseY, partialTick);
            providerRefreshButton.render(guiGraphics, mouseX, mouseY, partialTick);
            providerCloseButton.render(guiGraphics, mouseX, mouseY, partialTick);

            Component status;
            if (!menu.isServerRecipeCatalogReceived()) {
                status = Component.translatable("gui.fantasy_technology.server_recipe_provider.loading");
            } else if (menu.getServerRecipeCatalogTotalMatches() == 0) {
                status = Component.translatable("gui.fantasy_technology.server_recipe_provider.empty");
            } else {
                status = Component.translatable("gui.fantasy_technology.server_recipe_provider.page",
                        providerPage + 1, menu.getServerRecipeCatalogTotalPages(),
                        menu.getServerRecipeCatalogTotalMatches());
            }
            guiGraphics.drawCenteredString(font, status, width / 2, providerPanelY + 52, 0xFFA0A0A0);

            // Widget tooltips are normally deferred until Screen.renderWithTooltip returns, which has already
            // happened by this event. Render the hovered recipe tooltip now so it stays above JEI as well.
            for (RecipeRowButton row : providerRows) {
                if (row.isHovered() && row.getTooltip() != null) {
                    guiGraphics.renderTooltip(font, row.getTooltip().toCharSequence(minecraft), mouseX, mouseY);
                    break;
                }
            }
        } finally {
            guiGraphics.pose().popPose();
            clearTooltipForNextRenderPass();
        }
    }

    private final class RecipeRowButton extends Button {

        private final ServerRecipeSummary summary;

        private RecipeRowButton(int x, int y, int width, int height, ServerRecipeSummary summary) {
            super(x, y, width, height, CommonComponents.EMPTY,
                    button -> selectServerRecipe(summary), DEFAULT_NARRATION);
            this.summary = summary;
            this.active = summary.available();

            var tooltip = Component.empty()
                    .append(summary.displayStack().what().getDisplayName())
                    .append("\n")
                    .append(Component.literal(summary.recipeId().toString()).withStyle(ChatFormatting.GRAY))
                    .append("\n")
                    .append(Component.literal(summary.providerId().toString()).withStyle(ChatFormatting.DARK_GRAY));
            summary.unavailableReason().ifPresent(reason -> tooltip.append("\n").append(
                    reason.copy().withStyle(ChatFormatting.RED)));
            setTooltip(Tooltip.create(tooltip));
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            ItemStack display = GenericStack.wrapInItemStack(summary.displayStack());
            guiGraphics.renderItem(display, getX() + 4, getY() + 2);

            String name = summary.displayStack().what().getDisplayName().getString();
            String amount = summary.displayStack().what().formatAmount(summary.displayStack().amount(),
                    AmountFormat.FULL);
            int amountWidth = font.width(amount);
            int nameWidth = Math.max(20, getWidth() - 35 - amountWidth);
            String clippedName = font.plainSubstrByWidth(name, nameWidth);
            int color = active ? 0xFFFFFFFF : 0xFF8A8A8A;
            guiGraphics.drawString(font, clippedName, getX() + 25, getY() + 7, color, false);
            guiGraphics.drawString(font, amount, getRight() - amountWidth - 6, getY() + 7,
                    active ? 0xFFC7C9CC : 0xFF777777, false);
        }
    }
}
