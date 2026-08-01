package cn.lyxc.fantasytechnology.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigInventory;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.FantasyPatternItem;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import cn.lyxc.fantasytechnology.part.FantasyEncodingTerminalPart;
import cn.lyxc.fantasytechnology.part.IFantasyEncodingTerminalHost;

/**
 * Menu of the fantasy encoding terminal, shaped like AE2's processing pattern mode.
 *
 * Built on AE2's {@link MEStorageMenu}, so it carries the full ME network item list on top of the encoding area.
 *
 * The ingredient slots are preview-only: they are filled by transferring a recipe and cannot be edited by hand,
 * because an ingredient may carry a tag that a slot has no way to represent. The result slots are ordinary ghost
 * slots and can be edited freely.
 *
 * Slot positions come from the screen style at
 * {@code assets/ae2/screens/terminals/fantasy_encoding_terminal.json}, matched up by slot semantic.
 */
public class FantasyEncodingTermMenu extends MEStorageMenu {

    /**
     * Built unregistered so we can put it into our own {@code DeferredRegister} rather than depending on AE2's
     * internal registration queue running after ours.
     */
    public static final MenuType<FantasyEncodingTermMenu> TYPE = MenuTypeBuilder
            .create(FantasyEncodingTermMenu::new, IFantasyEncodingTerminalHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID,
                    "fantasy_encoding_terminal"));

    private static final String ACTION_ENCODE = "encode";
    private static final String ACTION_CLEAR = "clear";

    private final IFantasyEncodingTerminalHost terminalHost;
    private final ConfigInventory encodedInputs;
    private final ConfigInventory encodedOutputs;
    private final FakeSlot[] inputSlots = new FakeSlot[FantasyEncodingTerminalPart.INPUT_SLOTS];
    private final FakeSlot[] outputSlots = new FakeSlot[FantasyEncodingTerminalPart.OUTPUT_SLOTS];
    private final AppEngSlot blankPatternSlot;
    private final AppEngSlot encodedPatternSlot;

    /** Last seen contents of the encoded pattern slot, so a newly inserted pattern is decoded exactly once. */
    private ItemStack lastEncodedPattern = ItemStack.EMPTY;

    public FantasyEncodingTermMenu(int id, Inventory playerInventory, IFantasyEncodingTerminalHost host) {
        // false: add the player inventory ourselves, after the encoding slots, like AE2's own pattern terminal does.
        super(TYPE, id, playerInventory, host, false);

        this.terminalHost = host;
        this.encodedInputs = host.getEncodedInputs();
        this.encodedOutputs = host.getEncodedOutputs();

        InternalInventory inputInv = encodedInputs.createMenuWrapper();
        for (int i = 0; i < inputSlots.length; i++) {
            inputSlots[i] = new PreviewSlot(inputInv, i);
            addSlot(inputSlots[i], SlotSemantics.PROCESSING_INPUTS);
        }

        InternalInventory outputInv = encodedOutputs.createMenuWrapper();
        for (int i = 0; i < outputSlots.length; i++) {
            outputSlots[i] = new FakeSlot(outputInv, i);
            addSlot(outputSlots[i], SlotSemantics.PROCESSING_OUTPUTS);
        }

        InternalInventory patternInv = host.getPatternInv();
        blankPatternSlot = new BlankPatternSlot(patternInv);
        addSlot(blankPatternSlot, SlotSemantics.BLANK_PATTERN);
        encodedPatternSlot = new EncodedPatternSlot(patternInv);
        addSlot(encodedPatternSlot, SlotSemantics.ENCODED_PATTERN);

        createPlayerInventorySlots(playerInventory);

        registerClientAction(ACTION_ENCODE, this::encode);
        registerClientAction(ACTION_CLEAR, this::clear);
    }

    public IFantasyEncodingTerminalHost getTerminalHost() {
        return terminalHost;
    }

    /** The ingredient slots, in grid order; the screen scrolls a 3x3 window over them. */
    public FakeSlot[] getInputSlots() {
        return inputSlots;
    }

    /** The result slots, shown as a fixed 3x3 block. */
    public FakeSlot[] getOutputSlots() {
        return outputSlots;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            // Putting an encoded pattern into the output slot loads it back in for editing.
            ItemStack current = encodedPatternSlot.getItem();
            if (!ItemStack.matches(current, lastEncodedPattern)) {
                lastEncodedPattern = current.copy();
                loadPattern(current);
            }
        }
        super.broadcastChanges();
    }

    /**
     * AE2 routes clicks on ghost slots through here rather than through the vanilla click handling, including the
     * paths that call {@code Slot#set} directly. Ingredient slots are preview-only, so drop anything aimed at them.
     */
    @Override
    public void doAction(ServerPlayer player, InventoryAction action, int slotId, long id) {
        if (isPreviewSlot(slotId)) {
            return;
        }
        super.doAction(player, action, slotId, id);
    }

    /**
     * The other half of the ghost-slot protocol: dropping an item onto a ghost slot sends SET_FILTER, which lands here
     * instead of in {@link #doAction}. Ingredient slots ignore it.
     */
    @Override
    public void setFilter(int slotIndex, ItemStack stack) {
        if (isPreviewSlot(slotIndex)) {
            return;
        }
        super.setFilter(slotIndex, stack);
    }

    private boolean isPreviewSlot(int slotId) {
        return slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof PreviewSlot;
    }

    // ------------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------------

    /** Encodes the current ingredients and results onto a blank fantasy pattern. Safe to call from either side. */
    public void encode() {
        if (isClientSide()) {
            sendClientAction(ACTION_ENCODE);
            return;
        }

        List<PatternIngredient> inputs = collectInputs();
        List<GenericStack> outputs = collectOutputs();
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return;
        }

        ItemStack encoded = FantasyPatternItem.encode(inputs, outputs);

        ItemStack existing = encodedPatternSlot.getItem().copy();
        if (existing.isEmpty()) {
            // Re-encoding in place is only possible with a blank pattern to consume.
            ItemStack blank = blankPatternSlot.getItem().copy();
            if (blank.isEmpty() || !(blank.getItem() instanceof FantasyPatternItem)) {
                return;
            }
            blank.shrink(1);
            blankPatternSlot.set(blank);
            encodedPatternSlot.set(encoded);
        } else if (existing.getCount() == 1 && existing.getItem() instanceof FantasyPatternItem) {
            // Overwrite the pattern that is already sitting in the slot, no blank needed.
            encodedPatternSlot.set(encoded);
        } else {
            return;
        }

        lastEncodedPattern = encodedPatternSlot.getItem().copy();
    }

    /** Clears the ingredients and results. Safe to call from either side. */
    public void clear() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }
        for (int i = 0; i < inputSlots.length; i++) {
            encodedInputs.setStack(i, null);
            terminalHost.setInputTag(i, null);
        }
        for (int i = 0; i < outputSlots.length; i++) {
            encodedOutputs.setStack(i, null);
        }
    }

    /**
     * Replaces the ingredients and results wholesale, used by the recipe viewer integration. Server side only.
     *
     * The ingredients carry the tag they came from, which is what makes a bookshelf pattern ask for any plank rather
     * than the particular one the recipe viewer happened to display.
     */
    public void setEncodedRecipe(List<PatternIngredient> inputs, List<GenericStack> outputs) {
        for (int i = 0; i < inputSlots.length; i++) {
            PatternIngredient ingredient = i < inputs.size() ? inputs.get(i) : null;
            if (ingredient == null || ingredient.isEmpty()) {
                encodedInputs.setStack(i, null);
                terminalHost.setInputTag(i, null);
            } else {
                encodedInputs.setStack(i, ingredient.stack());
                terminalHost.setInputTag(i, ingredient.tag().orElse(null));
            }
        }
        for (int i = 0; i < outputSlots.length; i++) {
            GenericStack stack = i < outputs.size() ? outputs.get(i) : null;
            encodedOutputs.setStack(i, stack != null && stack.amount() > 0 ? stack : null);
        }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    /** Loads an encoded pattern back into the ingredient and result slots, tags included. */
    private void loadPattern(ItemStack stack) {
        FantasyPatternData data = FantasyPatternItem.getData(stack);
        if (data == null) {
            return;
        }
        setEncodedRecipe(data.inputs(), data.outputs());
    }

    /** The ingredients as encoded, each carrying the tag its slot was filled from (if it is still valid). */
    private List<PatternIngredient> collectInputs() {
        List<PatternIngredient> ingredients = new ArrayList<>();
        for (int i = 0; i < inputSlots.length; i++) {
            GenericStack stack = encodedInputs.getStack(i);
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            ResourceLocation tag = terminalHost.getInputTag(i);
            ingredients.add(tag == null ? PatternIngredient.of(stack) : PatternIngredient.of(stack, tag));
        }
        return ingredients;
    }

    private List<GenericStack> collectOutputs() {
        List<GenericStack> stacks = new ArrayList<>();
        for (int i = 0; i < outputSlots.length; i++) {
            GenericStack stack = encodedOutputs.getStack(i);
            if (stack != null && stack.amount() > 0) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    // ------------------------------------------------------------------------
    // Slots
    // ------------------------------------------------------------------------

    /**
     * An ingredient slot: a preview only.
     *
     * The ingredients define what a fantasy pattern will request, and they carry a tag that the slot itself cannot
     * represent, so letting the player edit them by hand would silently drop that tag. They are therefore filled only
     * by transferring a recipe.
     *
     * Two methods are deliberately NOT overridden, because both are on the path the server uses to push slot contents
     * to the client and blocking them leaves the slots empty on screen until the UI is reopened:
     * <ul>
     * <li>{@code set}, which {@code AbstractContainerMenu#setItem} calls when a slot update arrives, and</li>
     * <li>{@code canSetFilterTo}, which {@code FakeSlot#set} checks before applying that update.</li>
     * </ul>
     * The player-facing routes are blocked in the menu instead - see {@link #doAction} and {@link #setFilter}.
     */
    private static class PreviewSlot extends FakeSlot {

        PreviewSlot(InternalInventory inv, int index) {
            super(inv, index);
            setNotDraggable();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        // Client-side helper that would ask the server to set this slot; nothing to ask for.
        @Override
        public void setFilterTo(ItemStack stack) {
        }

        @Override
        public void increase(ItemStack stack) {
        }

        @Override
        public void decrease(ItemStack stack) {
        }
    }

    /** Takes unencoded fantasy patterns, which encoding consumes. */
    private static class BlankPatternSlot extends AppEngSlot {
        BlankPatternSlot(InternalInventory inv) {
            super(inv, FantasyEncodingTerminalPart.BLANK_PATTERN_SLOT);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof FantasyPatternItem && !FantasyPatternItem.isEncoded(stack)
                    && super.mayPlace(stack);
        }
    }

    /**
     * Holds the encoded pattern. Encoded patterns may also be placed here by hand, which loads their contents back
     * into the terminal so the recipe can be edited and written back.
     */
    private static class EncodedPatternSlot extends AppEngSlot {

        EncodedPatternSlot(InternalInventory inv) {
            super(inv, FantasyEncodingTerminalPart.ENCODED_PATTERN_SLOT);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return FantasyPatternItem.isEncoded(stack) && super.mayPlace(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
