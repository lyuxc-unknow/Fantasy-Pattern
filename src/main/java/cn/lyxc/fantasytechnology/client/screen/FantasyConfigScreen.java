package cn.lyxc.fantasytechnology.client.screen;

import cn.lyxc.fantasytechnology.config.DeviceAccessMode;
import cn.lyxc.fantasytechnology.config.FTConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/// Native configuration screen exposed through NeoForge's mod list.
///
/// All values belong to the server config. A remote server remains authoritative, so its synced values are visible
/// but not editable; the screen writes only while an integrated server is running.
public final class FantasyConfigScreen extends OptionsSubScreen {

    private static final int CONTROL_WIDTH = 150;
    private final Draft draft;
    private boolean editable;

    private Button batchDispatchButton;
    private Button trustServerParsingButton;
    private Button deviceAccessButton;
    private Button blockedCategoriesButton;
    private Button fuelItemsButton;
    private Button consumeFuelButton;
    private StringWidget statusWidget;

    public FantasyConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options,
                Component.translatable("fantasy_technology.config.title"));
        this.layout.setFooterHeight(48);
        this.draft = Draft.read();
    }

    @Override
    protected void init() {
        this.editable = FTConfig.SPEC.isLoaded() && this.minecraft.hasSingleplayerServer();
        super.init();
        setDefaultStatus();
    }

    @Override
    protected void addOptions() {
        this.batchDispatchButton = Button.builder(toggleName(draft.batchDispatch), button -> {
            draft.batchDispatch = !draft.batchDispatch;
            button.setMessage(toggleName(draft.batchDispatch));
        }).width(CONTROL_WIDTH).build();
        addRow("fantasy_technology.configuration.batch_dispatch_enabled", this.batchDispatchButton);

        this.trustServerParsingButton = Button.builder(toggleName(draft.trustServerParsing), button -> {
            draft.trustServerParsing = !draft.trustServerParsing;
            button.setMessage(toggleName(draft.trustServerParsing));
        }).width(CONTROL_WIDTH).build();
        addRow("fantasy_technology.configuration.trust_server_recipe_parsing", this.trustServerParsingButton);

        this.deviceAccessButton = Button.builder(deviceAccessName(draft.deviceAccessMode), button -> {
            draft.deviceAccessMode = next(draft.deviceAccessMode, DeviceAccessMode.values());
            button.setMessage(deviceAccessName(draft.deviceAccessMode));
        }).width(CONTROL_WIDTH).build();
        addRow("fantasy_technology.configuration.device_access_mode", this.deviceAccessButton);

        this.blockedCategoriesButton = Button.builder(blockedCategoriesName(), button -> this.minecraft.setScreen(
                        new ResourceLocationListScreen(this,
                                Component.translatable("fantasy_technology.config.blocked_categories.title"),
                                Component.translatable("fantasy_technology.config.category_id"),
                                "fantasy_technology.config.invalid_category",
                                draft.blockedCategoryIds,
                                values -> {
                                    draft.blockedCategoryIds = new ArrayList<>(values);
                                    updateBlockedCategoriesButton();
                                })))
                .width(310)
                .tooltip(Tooltip.create(Component.translatable("fantasy_technology.config.rejoin_required")))
                .build();
        this.blockedCategoriesButton.active = this.editable;
        this.list.addSmall(this.blockedCategoriesButton, null);

        this.fuelItemsButton = Button.builder(fuelItemsName(), button -> this.minecraft.setScreen(
                        new ResourceLocationListScreen(this,
                                Component.translatable("fantasy_technology.config.fuel_items.title"),
                                Component.translatable("fantasy_technology.config.fuel_entry"),
                                "fantasy_technology.config.invalid_fuel_entry",
                                FTConfig::normalizeAnnihilationFuel,
                                draft.fuelItems,
                                values -> {
                                    draft.fuelItems = new ArrayList<>(values);
                                    updateFuelItemsButton();
                                })))
                .width(310)
                .build();
        this.fuelItemsButton.active = this.editable;
        this.list.addSmall(this.fuelItemsButton, null);

        this.consumeFuelButton = Button.builder(toggleName(draft.consumeFuel), button -> {
            draft.consumeFuel = !draft.consumeFuel;
            button.setMessage(toggleName(draft.consumeFuel));
        }).width(CONTROL_WIDTH).build();
        addRow("fantasy_technology.configuration.consume_fuel", this.consumeFuelButton);
    }

    @Override
    protected void addFooter() {
        var footer = LinearLayout.vertical().spacing(4);
        this.statusWidget = footer.addChild(new StringWidget(310, 9, CommonComponents.EMPTY, this.font).alignCenter());

        var buttons = footer.addChild(LinearLayout.horizontal().spacing(8));
        Button resetButton = buttons.addChild(Button.builder(
                Component.translatable("fantasy_technology.config.reset"), button -> resetDraft()).width(96).build());
        buttons.addChild(Button.builder(CommonComponents.GUI_CANCEL,
                button -> this.minecraft.setScreen(this.lastScreen)).width(96).build());
        Button doneButton = buttons.addChild(Button.builder(CommonComponents.GUI_DONE, button -> save()).width(96).build());

        resetButton.active = this.editable;
        doneButton.active = this.editable;
        this.layout.addToFooter(footer);
    }

    private void addRow(String translationKey, AbstractWidget control) {
        control.active = this.editable;
        var label = new StringWidget(CONTROL_WIDTH, 20, Component.translatable(translationKey), this.font)
                .alignLeft();
        this.list.addSmall(label, control);
    }

    private void save() {
        FTConfig.BATCH_DISPATCH_ENABLED.set(draft.batchDispatch);
        FTConfig.TRUST_SERVER_RECIPE_PARSING.set(draft.trustServerParsing);
        FTConfig.DEVICE_ACCESS_MODE.set(draft.deviceAccessMode);
        FTConfig.BLOCKED_JEI_CATEGORY_IDS.set(List.copyOf(draft.blockedCategoryIds));
        FTConfig.ANNIHILATION_FUEL_ITEMS.set(List.copyOf(draft.fuelItems));
        FTConfig.CONSUME_FUEL.set(draft.consumeFuel);
        FTConfig.SPEC.save();
        this.minecraft.setScreen(this.lastScreen);
    }

    private void resetDraft() {
        draft.reset();
        batchDispatchButton.setMessage(toggleName(draft.batchDispatch));
        trustServerParsingButton.setMessage(toggleName(draft.trustServerParsing));
        deviceAccessButton.setMessage(deviceAccessName(draft.deviceAccessMode));
        updateBlockedCategoriesButton();
        updateFuelItemsButton();
        consumeFuelButton.setMessage(toggleName(draft.consumeFuel));
        setDefaultStatus();
    }

    private void updateBlockedCategoriesButton() {
        if (this.blockedCategoriesButton != null) {
            this.blockedCategoriesButton.setMessage(blockedCategoriesName());
        }
    }

    private void updateFuelItemsButton() {
        if (this.fuelItemsButton != null) {
            this.fuelItemsButton.setMessage(fuelItemsName());
        }
    }

    private Component blockedCategoriesName() {
        return Component.translatable("fantasy_technology.config.blocked_categories",
                draft.blockedCategoryIds.size());
    }

    private Component fuelItemsName() {
        return Component.translatable("fantasy_technology.config.fuel_items",
                draft.fuelItems.size());
    }

    private void setDefaultStatus() {
        if (this.editable) {
            setStatus(Component.translatable("fantasy_technology.config.rejoin_required"), 0xFFA0A0A0);
        } else {
            setStatus(Component.translatable("fantasy_technology.config.local_world_only"), 0xFFFFAA00);
        }
    }

    private void setStatus(Component message, int color) {
        this.statusWidget.setMessage(message);
        this.statusWidget.setColor(color);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    private static Component toggleName(boolean enabled) {
        return Component.translatable(enabled
                ? "fantasy_technology.config.enabled"
                : "fantasy_technology.config.disabled");
    }

    private static Component deviceAccessName(DeviceAccessMode mode) {
        return Component.translatable("fantasy_technology.config.device_access_mode."
                + mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static <T> T next(T current, T[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private static final class Draft {
        private boolean batchDispatch;
        private boolean trustServerParsing;
        private DeviceAccessMode deviceAccessMode;
        private List<String> blockedCategoryIds;
        private List<String> fuelItems;
        private boolean consumeFuel;

        private static Draft read() {
            var draft = new Draft();
            if (FTConfig.SPEC.isLoaded()) {
                draft.batchDispatch = FTConfig.BATCH_DISPATCH_ENABLED.get();
                draft.trustServerParsing = FTConfig.TRUST_SERVER_RECIPE_PARSING.get();
                draft.deviceAccessMode = FTConfig.DEVICE_ACCESS_MODE.get();
                draft.blockedCategoryIds = new ArrayList<>(FTConfig.BLOCKED_JEI_CATEGORY_IDS.get());
                draft.fuelItems = new ArrayList<>(FTConfig.ANNIHILATION_FUEL_ITEMS.get());
                draft.consumeFuel = FTConfig.CONSUME_FUEL.get();
            } else {
                draft.reset();
            }
            return draft;
        }

        private void reset() {
            this.batchDispatch = FTConfig.BATCH_DISPATCH_ENABLED.getDefault();
            this.trustServerParsing = FTConfig.TRUST_SERVER_RECIPE_PARSING.getDefault();
            this.deviceAccessMode = FTConfig.DEVICE_ACCESS_MODE.getDefault();
            this.blockedCategoryIds = new ArrayList<>(FTConfig.DEFAULT_BLOCKED_JEI_CATEGORY_IDS);
            this.fuelItems = new ArrayList<>(FTConfig.DEFAULT_ANNIHILATION_FUEL_ITEMS);
            this.consumeFuel = FTConfig.CONSUME_FUEL.getDefault();
        }
    }
}
