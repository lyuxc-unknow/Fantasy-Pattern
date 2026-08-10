package cn.lyxc.fantasytechnology.menu;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigInventory;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.blockentity.FantasyDeviceAccessBlockEntity;
import cn.lyxc.fantasytechnology.deviceaccess.DeviceAccessCheck;
import cn.lyxc.fantasytechnology.item.FantasyBlankPatternItem;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.FantasyPatternItem;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import cn.lyxc.fantasytechnology.network.DeviceCatalystsPayload;
import cn.lyxc.fantasytechnology.part.FantasyEncodingTerminalPart;
import cn.lyxc.fantasytechnology.part.IFantasyEncodingTerminalHost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.List;
import java.util.Map;

/// Menu of the fantasy encoding terminal, shaped like AE2's processing pattern mode.
///
/// Built on AE2's {@link MEStorageMenu}, so it carries the full ME network item list on top of the encoding area.
///
/// The ingredient and result slots alike are preview-only: they are filled by transferring a recipe and changed
/// through the terminal's own buttons, never by hand. For the ingredients that is a hard requirement, since an
/// ingredient may carry a tag that a slot has no way to represent and editing it by hand would silently drop that
/// tag.
///
/// Slot positions come from the screen style at
/// {@code assets/ae2/screens/terminals/fantasy_encoding_terminal.json}, matched up by slot semantic.
public class FantasyEncodingTermMenu extends MEStorageMenu {

    /// Built unregistered so we can put it into our own {@code DeferredRegister} rather than depending on AE2's
    /// internal registration queue running after ours.
    public static final MenuType<FantasyEncodingTermMenu> TYPE = MenuTypeBuilder
            .create(FantasyEncodingTermMenu::new, IFantasyEncodingTerminalHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID,
                    "fantasy_encoding_terminal"));

    private static final String ACTION_ENCODE = "encode";
    private static final String ACTION_CLEAR = "clear";
    private static final String ACTION_DOUBLE = "double";
    private static final String ACTION_TOGGLE_IGNORE = "toggleIgnore";

    /// The ignore-data flags, packed into a bitset that AE2's menu synchronisation carries to the client.
    ///
    /// They live on the part, and a client's copy of a part is never told about a change the server made on its own -
    /// a recipe transferred in from JEI, a clear, an encoded pattern decoded back into the grid. Mirroring them here
    /// every tick keeps the screen's right-click tooltip honest instead of letting it drift. Ids 100 and 101 are
    /// taken by {@link MEStorageMenu}.
    @GuiSync(110)
    public long inputIgnoreLow;
    @GuiSync(111)
    public long inputIgnoreHigh;
    @GuiSync(112)
    public int outputIgnoreBits;

    /// Ceiling for {@link #doubleAmounts()}. A click that would push any single ingredient or result past this is
    /// cancelled wholesale, so all entries always stay on the same multiple of the original amounts. The ceiling
    /// sits at the int range, matching the amount range the terminal's slots display and interact with.
    private static final long MAX_AMOUNT = Integer.MAX_VALUE;

    private final IFantasyEncodingTerminalHost terminalHost;
    private final ConfigInventory encodedInputs;
    private final ConfigInventory encodedOutputs;
    private final FakeSlot[] inputSlots = new FakeSlot[FantasyEncodingTerminalPart.INPUT_SLOTS];
    private final FakeSlot[] outputSlots = new FakeSlot[FantasyEncodingTerminalPart.OUTPUT_SLOTS];
    private final AppEngSlot blankPatternSlot;
    private final AppEngSlot encodedPatternSlot;

    /// Last seen contents of the encoded pattern slot, so a newly inserted pattern is decoded exactly once.
    private ItemStack lastEncodedPattern = ItemStack.EMPTY;

    /// Change counter and contents of the last catalyst summary sent to the client; see {@link #syncDeviceCatalysts}.
    private long lastCatalystVersion = Long.MIN_VALUE;
    private Map<Item, Integer> lastSentCatalysts = Map.of();

    /// Ticks between the sweeps that catch changes no inventory edit announces - a device block broken, the network
    /// rewired. One second is far below what a player can react to and costs one walk of the device blocks.
    private static final int CATALYST_SWEEP_TICKS = 20;
    private int catalystSweepCooldown;

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
            outputSlots[i] = new PreviewSlot(outputInv, i);
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
        registerClientAction(ACTION_DOUBLE, this::doubleAmounts);
        registerClientAction(ACTION_TOGGLE_IGNORE, Integer.class, this::toggleIgnore);
    }

    public IFantasyEncodingTerminalHost getTerminalHost() {
        return terminalHost;
    }

    /// The ingredient slots, in grid order; the screen scrolls a 3x3 window over them.
    public FakeSlot[] getInputSlots() {
        return inputSlots;
    }

    /// The result slots, shown as a fixed 3x3 block.
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
            packIgnoreFlags();
            syncDeviceCatalysts();
        }
        super.broadcastChanges();
    }

    /// Pushes what the network's device access blocks hold to the client, which is where the recipe transfer button
    /// decides whether a recipe may be encoded at all.
    ///
    /// Rebuilt when some device access block has changed contents - walking every one of them and their 36 slots on
    /// every tick of every open terminal would be a poor trade for data that changes when a player moves an item.
    /// The counter is global, so an unrelated network's change costs one redundant rebuild and nothing else.
    ///
    /// It cannot be the only trigger, though: breaking a device block, or the terminal's own network being split or
    /// rewired, changes the answer without any inventory changing. A slow sweep catches those. Either way the packet
    /// only goes out when the summary really differs from the last one sent.
    private void syncDeviceCatalysts() {
        boolean sweep = --catalystSweepCooldown <= 0;
        if (sweep) {
            catalystSweepCooldown = CATALYST_SWEEP_TICKS;
        }
        long version = FantasyDeviceAccessBlockEntity.changeCounter();
        if ((!sweep && version == lastCatalystVersion) || !(getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        lastCatalystVersion = version;

        var catalysts = FantasyDeviceAccessBlockEntity.collectCatalysts(networkGrid());
        if (catalysts.equals(lastSentCatalysts)) {
            return;
        }
        lastSentCatalysts = Map.copyOf(catalysts);
        PacketDistributor.sendToPlayer(serverPlayer, new DeviceCatalystsPayload(lastSentCatalysts));
    }

    /// The grid this terminal is attached to, or null when it is not connected to one.
    @Nullable
    private IGrid networkGrid() {
        if (!isActionHost()) {
            return null;
        }
        var node = getActionHost().getActionableNode();
        return node == null ? null : node.getGrid();
    }

    /// Whether the network may encode this recipe, decided against what its device access blocks actually hold.
    ///
    /// Runs the same three-step rule the client's transfer button used - config, then datapack rule, then the
    /// four-catalyst default - so the two sides can only disagree about the contents, never about the rule.
    public boolean allowsDeviceAccess(@Nullable ResourceLocation recipeId, @Nullable ResourceLocation categoryId,
            Set<ResourceLocation> outputs, Collection<Item> catalysts) {
        if (DeviceAccessCheck.unrestricted()) {
            return true;
        }
        var available = FantasyDeviceAccessBlockEntity.collectCatalysts(networkGrid());
        return DeviceAccessCheck.check(recipeId, categoryId, outputs, catalysts, available::getInt).allowed();
    }

    /// Copies the part's ignore-data flags into the synchronised bitset. Server side only; the client reads the
    /// result through {@link #isInputIgnored(int)} and {@link #isOutputIgnored(int)}.
    private void packIgnoreFlags() {
        long low = 0;
        long high = 0;
        for (int i = 0; i < inputSlots.length; i++) {
            if (terminalHost.getInputIgnore(i)) {
                if (i < Long.SIZE) {
                    low |= 1L << i;
                } else {
                    high |= 1L << (i - Long.SIZE);
                }
            }
        }
        int outputs = 0;
        for (int i = 0; i < outputSlots.length; i++) {
            if (terminalHost.getOutputIgnore(i)) {
                outputs |= 1 << i;
            }
        }
        inputIgnoreLow = low;
        inputIgnoreHigh = high;
        outputIgnoreBits = outputs;
    }

    /// Whether ingredient slot {@code slot} ignores data components. Answers the same on both sides, unlike asking
    /// the part directly.
    public boolean isInputIgnored(int slot) {
        if (slot < 0 || slot >= inputSlots.length) {
            return false;
        }
        return slot < Long.SIZE
                ? (inputIgnoreLow >>> slot & 1L) != 0
                : (inputIgnoreHigh >>> (slot - Long.SIZE) & 1L) != 0;
    }

    /// Whether result slot {@code slot} ignores data components. Answers the same on both sides.
    public boolean isOutputIgnored(int slot) {
        return slot >= 0 && slot < outputSlots.length && (outputIgnoreBits >>> slot & 1) != 0;
    }

    /// AE2 routes clicks on ghost slots through here rather than through the vanilla click handling, including the
    /// paths that call {@code Slot#set} directly. Ingredient slots are preview-only, so drop anything aimed at them.
    @Override
    public void doAction(ServerPlayer player, InventoryAction action, int slotId, long id) {
        if (isPreviewSlot(slotId)) {
            return;
        }
        super.doAction(player, action, slotId, id);
    }

    /// The other half of the ghost-slot protocol: dropping an item onto a ghost slot sends SET_FILTER, which lands here
    /// instead of in {@link #doAction}. Ingredient slots ignore it.
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

    /// Shift-clicking a fantasy pattern in the player inventory should fill the pattern slots first, not the ME network
    /// storage. Blank fantasy patterns go into the blank pattern slot, encoded recombination patterns into the encoded
    /// pattern slot; whatever does not fit falls back to the network like any other item.
    ///
    /// Note: {@link Slot#safeInsert} modifies and returns the passed stack, so a copy is inserted and the original
    /// {@code input} is kept intact for the placed-amount calculation and the fallback to the network.
    @Override
    protected int transferStackToMenu(ItemStack input) {
        AppEngSlot target = null;
        if (input.getItem() instanceof FantasyBlankPatternItem) {
            target = blankPatternSlot;
        } else if (FantasyPatternItem.isEncoded(input)) {
            target = encodedPatternSlot;
        }
        if (target != null) {
            ItemStack remainder = target.safeInsert(input.copy());
            int placed = input.getCount() - remainder.getCount();
            if (placed > 0) {
                return placed;
            }
        }
        return super.transferStackToMenu(input);
    }

    // ------------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------------

    /// Encodes the current ingredients and results onto a blank fantasy pattern. Safe to call from either side.
    public void encode() {
        if (isClientSide()) {
            sendClientAction(ACTION_ENCODE);
            return;
        }

        List<PatternIngredient> inputs = collectInputs();
        List<GenericStack> outputs = new ArrayList<>();
        List<Boolean> outputsIgnore = new ArrayList<>();
        for (int i = 0; i < outputSlots.length; i++) {
            GenericStack stack = encodedOutputs.getStack(i);
            if (stack != null && stack.amount() > 0) {
                outputs.add(stack);
                outputsIgnore.add(terminalHost.getOutputIgnore(i));
            }
        }
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return;
        }

        ItemStack encoded = FantasyPatternItem.encode(inputs, outputs, outputsIgnore);

        ItemStack existing = encodedPatternSlot.getItem().copy();
        if (existing.isEmpty()) {
            // Re-encoding in place is only possible with a blank fantasy pattern to consume.
            ItemStack blank = blankPatternSlot.getItem().copy();
            if (blank.isEmpty() || !(blank.getItem() instanceof FantasyBlankPatternItem)) {
                return;
            }
            blank.shrink(1);
            blankPatternSlot.set(blank);
            encodedPatternSlot.set(encoded);
        } else if (existing.getCount() == 1 && FantasyPatternItem.isEncoded(existing)) {
            // Overwrite the recombination pattern that is already sitting in the slot, no blank needed.
            encodedPatternSlot.set(encoded);
        } else {
            return;
        }

        lastEncodedPattern = encodedPatternSlot.getItem().copy();
    }

    /// Toggles the ignore-data flag of one encoding entry. Safe to call from either side.
    ///
    /// {@code entry} is an index into the ingredient slots followed by the result slots - not a menu slot index, and
    /// not a screen position. The client only forwards the request: the flag lives on the part, and the answer comes
    /// back through the synchronised bitset on the next broadcast, so the two sides cannot drift apart.
    public void toggleIgnore(int entry) {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_IGNORE, entry);
            return;
        }
        if (entry >= 0 && entry < inputSlots.length) {
            terminalHost.setInputIgnore(entry, !terminalHost.getInputIgnore(entry));
        } else if (entry >= inputSlots.length && entry < inputSlots.length + outputSlots.length) {
            int output = entry - inputSlots.length;
            terminalHost.setOutputIgnore(output, !terminalHost.getOutputIgnore(output));
        }
    }

    /// Clears the ingredients and results. Safe to call from either side.
    public void clear() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }
        for (int i = 0; i < inputSlots.length; i++) {
            encodedInputs.setStack(i, null);
            terminalHost.setInputTag(i, null);
            terminalHost.setInputIgnore(i, false);
        }
        for (int i = 0; i < outputSlots.length; i++) {
            encodedOutputs.setStack(i, null);
            terminalHost.setOutputIgnore(i, false);
        }
    }

    /// Doubles the amount of every ingredient and result being encoded, keeping their tags. Safe to call from
    /// either side.
    public void doubleAmounts() {
        if (isClientSide()) {
            sendClientAction(ACTION_DOUBLE);
            return;
        }
        // All-or-nothing: if doubling any single entry would exceed the ceiling, the whole doubling is cancelled
        // so entries never end up with inconsistent multiples.
        if (wouldOverflow()) {
            return;
        }
        for (int i = 0; i < inputSlots.length; i++) {
            GenericStack stack = encodedInputs.getStack(i);
            if (stack != null && stack.amount() > 0) {
                encodedInputs.setStack(i, new GenericStack(stack.what(), stack.amount() * 2));
            }
        }
        for (int i = 0; i < outputSlots.length; i++) {
            GenericStack stack = encodedOutputs.getStack(i);
            if (stack != null && stack.amount() > 0) {
                encodedOutputs.setStack(i, new GenericStack(stack.what(), stack.amount() * 2));
            }
        }
    }

    /// Whether doubling would push any single ingredient or result past {@link #MAX_AMOUNT}.
    private boolean wouldOverflow() {
        for (int i = 0; i < inputSlots.length; i++) {
            GenericStack stack = encodedInputs.getStack(i);
            if (stack != null && stack.amount() > MAX_AMOUNT / 2) {
                return true;
            }
        }
        for (int i = 0; i < outputSlots.length; i++) {
            GenericStack stack = encodedOutputs.getStack(i);
            if (stack != null && stack.amount() > MAX_AMOUNT / 2) {
                return true;
            }
        }
        return false;
    }

    public void setEncodedRecipe(List<PatternIngredient> inputs, List<GenericStack> outputs) {
        setEncodedRecipe(inputs, outputs, List.of());
    }

    /// Replaces the ingredients and results wholesale, used by the recipe viewer integration. Server side only.
    ///
    /// The ingredients carry the tag they came from, which is what makes a bookshelf pattern ask for any plank rather
    /// than the particular one the recipe viewer happened to display.
    public void setEncodedRecipe(List<PatternIngredient> inputs, List<GenericStack> outputs,
            List<Boolean> outputsIgnore) {
        for (int i = 0; i < inputSlots.length; i++) {
            PatternIngredient ingredient = i < inputs.size() ? inputs.get(i) : null;
            if (ingredient == null || ingredient.isEmpty()) {
                encodedInputs.setStack(i, null);
                terminalHost.setInputTag(i, null);
                terminalHost.setInputIgnore(i, false);
            } else {
                encodedInputs.setStack(i, ingredient.stack());
                terminalHost.setInputTag(i, ingredient.tag().orElse(null));
                terminalHost.setInputIgnore(i, ingredient.ignoreData());
            }
        }
        for (int i = 0; i < outputSlots.length; i++) {
            GenericStack stack = i < outputs.size() ? outputs.get(i) : null;
            encodedOutputs.setStack(i, stack != null && stack.amount() > 0 ? stack : null);
            terminalHost.setOutputIgnore(i, i < outputsIgnore.size() && outputsIgnore.get(i));
        }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    /// Loads an encoded pattern back into the ingredient and result slots, tags and ignore-data flags included.
    private void loadPattern(ItemStack stack) {
        FantasyPatternData data = FantasyPatternItem.getData(stack);
        if (data == null) {
            return;
        }
        setEncodedRecipe(data.inputs(), data.outputs(), data.outputsIgnore());
    }

    /// The ingredients as encoded, each carrying the tag its slot was filled from (if it is still valid) and the
    /// slot's ignore-data flag.
    private List<PatternIngredient> collectInputs() {
        List<PatternIngredient> ingredients = new ArrayList<>();
        for (int i = 0; i < inputSlots.length; i++) {
            GenericStack stack = encodedInputs.getStack(i);
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            ResourceLocation tag = terminalHost.getInputTag(i);
            PatternIngredient ingredient = tag == null ? PatternIngredient.of(stack)
                    : PatternIngredient.of(stack, tag);
            ingredients.add(ingredient.withIgnoreData(terminalHost.getInputIgnore(i)));
        }
        return ingredients;
    }

    // ------------------------------------------------------------------------
    // Slots
    // ------------------------------------------------------------------------

    /// Its contents are filled by transferring a recipe and changed through the terminal's buttons, never by hand.
    /// For the ingredients that is a hard requirement: they carry a tag the slot itself cannot represent, so letting
    /// the player edit them would silently drop it.
    ///
    /// Two methods are deliberately NOT overridden, because both are on the path the server uses to push slot contents
    /// to the client and blocking them leaves the slots empty on screen until the UI is reopened:
    /// <ul>
    /// <li>{@code set}, which {@code AbstractContainerMenu#setItem} calls when a slot update arrives, and</li>
    /// <li>{@code canSetFilterTo}, which {@code FakeSlot#set} checks before applying that update.</li>
    /// </ul>
    /// The player-facing routes are blocked in the menu instead - see {@link #doAction} and {@link #setFilter}.
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

    /// Takes blank fantasy patterns, which encoding consumes.
    private static class BlankPatternSlot extends AppEngSlot {
        BlankPatternSlot(InternalInventory inv) {
            super(inv, FantasyEncodingTerminalPart.BLANK_PATTERN_SLOT);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof FantasyBlankPatternItem && super.mayPlace(stack);
        }
    }

    /// Holds the encoded recombination pattern. Encoded patterns may also be placed here by hand, which loads their
    /// contents back into the terminal so the recipe can be edited and written back.
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
