package cn.lyxc.fantasytechnology.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

/// Editor for the category-id blocklist. Values stay local to this screen until Done is pressed.
final class BlockedJeiCategoriesScreen extends Screen {

    private static final int MAX_ENTRIES = 128;
    private static final int LIST_TOP = 32;
    private static final int LIST_BOTTOM_MARGIN = 44;

    private final Screen parent;
    private final Consumer<List<String>> onDone;
    private List<String> workingValues;
    private CategoryList categoryList;
    private Button addButton;
    private Component error;

    BlockedJeiCategoriesScreen(Screen parent, List<String> values, Consumer<List<String>> onDone) {
        super(Component.translatable("fantasy_technology.config.blocked_categories.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.workingValues = new ArrayList<>(values);
    }

    @Override
    protected void init() {
        if (this.categoryList != null) {
            this.workingValues = this.categoryList.values();
        }

        this.categoryList = this.addRenderableWidget(new CategoryList(this.minecraft, this.width,
                this.height - LIST_TOP - LIST_BOTTOM_MARGIN, LIST_TOP, this.workingValues,
                this::updateAddButton));

        int buttonY = this.height - 28;
        this.addButton = this.addRenderableWidget(Button.builder(
                Component.translatable("fantasy_technology.config.add"), button -> {
                    this.categoryList.addCategory("");
                    this.error = null;
                }).bounds(this.width / 2 - 155, buttonY, 96, 20).build());
        updateAddButton();

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> commit())
                .bounds(this.width / 2 - 48, buttonY, 96, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL,
                        button -> this.minecraft.setScreen(this.parent))
                .bounds(this.width / 2 + 59, buttonY, 96, 20).build());
    }

    private void commit() {
        var normalized = new LinkedHashSet<String>();
        for (String raw : this.categoryList.values()) {
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) {
                this.error = Component.translatable("fantasy_technology.config.invalid_category", value);
                return;
            }
            normalized.add(id.toString());
        }
        this.onDone.accept(List.copyOf(normalized));
        this.minecraft.setScreen(this.parent);
    }

    private void updateAddButton() {
        if (this.addButton != null) {
            this.addButton.active = this.categoryList.children().size() < MAX_ENTRIES;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        if (this.error != null) {
            guiGraphics.drawCenteredString(this.font, this.error, this.width / 2, this.height - 40, 0xFFFF5555);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private static final class CategoryList extends ContainerObjectSelectionList<CategoryEntry> {

        private final Runnable onEntriesChanged;

        private CategoryList(Minecraft minecraft, int width, int height, int y, List<String> values,
                Runnable onEntriesChanged) {
            super(minecraft, width, height, y, 24);
            this.centerListVertically = false;
            this.onEntriesChanged = onEntriesChanged;
            for (String value : values) {
                addCategory(value);
            }
        }

        private void addCategory(String value) {
            if (this.children().size() < MAX_ENTRIES) {
                this.addEntry(new CategoryEntry(this, this.minecraft, value));
                this.setScrollAmount(this.getMaxScroll());
                this.onEntriesChanged.run();
            }
        }

        private void removeCategory(CategoryEntry entry) {
            this.removeEntry(entry);
            this.onEntriesChanged.run();
        }

        private List<String> values() {
            return this.children().stream().map(CategoryEntry::value).toList();
        }

        @Override
        public int getRowWidth() {
            return Math.min(310, this.width - 24);
        }
    }

    private static final class CategoryEntry extends ContainerObjectSelectionList.Entry<CategoryEntry> {

        private final CategoryList owner;
        private final EditBox input;
        private final Button remove;

        private CategoryEntry(CategoryList owner, Minecraft minecraft, String value) {
            this.owner = owner;
            this.input = new EditBox(minecraft.font, 0, 0, 278, 20,
                    Component.translatable("fantasy_technology.config.category_id"));
            this.input.setMaxLength(255);
            this.input.setValue(value);
            this.remove = Button.builder(Component.literal("x"), button -> owner.removeCategory(this))
                    .bounds(0, 0, 24, 20)
                    .tooltip(Tooltip.create(Component.translatable("fantasy_technology.config.remove")))
                    .build();
        }

        private String value() {
            return this.input.getValue();
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                int mouseX, int mouseY, boolean hovering, float partialTick) {
            int removeWidth = 24;
            int gap = 6;
            this.input.setPosition(left, top);
            this.input.setWidth(Math.max(40, width - removeWidth - gap));
            this.remove.setPosition(left + width - removeWidth, top);
            this.input.render(guiGraphics, mouseX, mouseY, partialTick);
            this.remove.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.input, this.remove);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.input, this.remove);
        }
    }
}
