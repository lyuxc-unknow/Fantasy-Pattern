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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/// Editor for a list of resource-location based values (JEI category ids, fuel definitions, ...).
/// Values stay local to this screen until Done is pressed.
final class ResourceLocationListScreen extends Screen {

    private static final int MAX_ENTRIES = 128;
    private static final int LIST_TOP = 32;
    private static final int LIST_BOTTOM_MARGIN = 44;

    private final Screen parent;
    private final Consumer<List<String>> onDone;
    private final Component inputLabel;
    private final String invalidKey;
    private final Function<String, @Nullable String> normalizer;
    private List<String> workingValues;
    private ResourceLocationList resourceLocationList;
    private Button addButton;
    private Component error;

    ResourceLocationListScreen(Screen parent, Component title, Component inputLabel, String invalidKey,
            List<String> values, Consumer<List<String>> onDone) {
        this(parent, title, inputLabel, invalidKey, ResourceLocationListScreen::normalizeResourceLocation,
                values, onDone);
    }

    ResourceLocationListScreen(Screen parent, Component title, Component inputLabel, String invalidKey,
            Function<String, @Nullable String> normalizer, List<String> values, Consumer<List<String>> onDone) {
        super(title);
        this.parent = parent;
        this.inputLabel = inputLabel;
        this.invalidKey = invalidKey;
        this.normalizer = normalizer;
        this.onDone = onDone;
        this.workingValues = new ArrayList<>(values);
    }

    @Override
    protected void init() {
        if (this.resourceLocationList != null) {
            this.workingValues = this.resourceLocationList.values();
        }

        this.resourceLocationList = this.addRenderableWidget(new ResourceLocationList(this.minecraft, this.width,
                this.height - LIST_TOP - LIST_BOTTOM_MARGIN, LIST_TOP, this.workingValues, this.inputLabel,
                this::updateAddButton));

        int buttonY = this.height - 28;
        this.addButton = this.addRenderableWidget(Button.builder(
                Component.translatable("fantasy_technology.config.add"), button -> {
                    this.resourceLocationList.addEntry("");
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
        for (String raw : this.resourceLocationList.values()) {
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            String normalizedValue = this.normalizer.apply(value);
            if (normalizedValue == null) {
                this.error = Component.translatable(this.invalidKey, value);
                return;
            }
            normalized.add(normalizedValue);
        }
        this.onDone.accept(List.copyOf(normalized));
        this.minecraft.setScreen(this.parent);
    }

    private static @Nullable String normalizeResourceLocation(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? null : id.toString();
    }

    private void updateAddButton() {
        if (this.addButton != null) {
            this.addButton.active = this.resourceLocationList.children().size() < MAX_ENTRIES;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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

    private static final class ResourceLocationList extends ContainerObjectSelectionList<ResourceLocationEntry> {

        private final Component inputLabel;
        private final Runnable onEntriesChanged;

        private ResourceLocationList(Minecraft minecraft, int width, int height, int y, List<String> values,
                Component inputLabel, Runnable onEntriesChanged) {
            super(minecraft, width, height, y, 24);
            this.centerListVertically = false;
            this.inputLabel = inputLabel;
            this.onEntriesChanged = onEntriesChanged;
            for (String value : values) {
                addEntry(value);
            }
        }

        private void addEntry(String value) {
            if (this.children().size() < MAX_ENTRIES) {
                this.addEntry(new ResourceLocationEntry(this, this.minecraft, value));
                this.setScrollAmount(this.getMaxScroll());
                this.onEntriesChanged.run();
            }
        }

        private void removeRow(ResourceLocationEntry entry) {
            this.removeEntry(entry);
            this.onEntriesChanged.run();
        }

        private List<String> values() {
            return this.children().stream().map(ResourceLocationEntry::value).toList();
        }

        @Override
        public int getRowWidth() {
            return Math.min(310, this.width - 24);
        }
    }

    private static final class ResourceLocationEntry extends ContainerObjectSelectionList.Entry<ResourceLocationEntry> {

        private final ResourceLocationList owner;
        private final EditBox input;
        private final Button remove;

        private ResourceLocationEntry(ResourceLocationList owner, Minecraft minecraft, String value) {
            this.owner = owner;
            this.input = new EditBox(minecraft.font, 0, 0, 278, 20, owner.inputLabel);
            this.input.setMaxLength(255);
            this.input.setValue(value);
            this.remove = Button.builder(Component.literal("x"), button -> owner.removeRow(this))
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
        public @NotNull List<? extends GuiEventListener> children() {
            return List.of(this.input, this.remove);
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return List.of(this.input, this.remove);
        }
    }
}
