package cn.lyxc.fantasytechnology.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import cn.lyxc.fantasytechnology.crafting.MolecularReusableInputAdapters;
import cn.lyxc.fantasytechnology.integration.ae2.IFantasyBatchCraftingProvider;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.FantasyPatternItem;
import cn.lyxc.fantasytechnology.registry.FTBlockEntities;
import cn.lyxc.fantasytechnology.registry.FTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The fantasy annihilation block ("幻梦寂灭"). An ME autocrafting device holding fantasy patterns.
///
/// Unlike a normal pattern provider, the machine consumes one AE2 matter-ball charge per accepted craft (this can be
/// disabled with {@code FTConfig.CONSUME_FUEL}). A craft completes immediately after the CPU hands over its inputs;
/// output delivery is deferred by one network tick because AE2 registers the CPU's waiting-for counter only after
/// {@code pushPattern} returns.
public class FantasyAnnihilationBlockEntity extends AENetworkedInvBlockEntity
        implements ICraftingProvider, PatternContainer, IGridTickable, IFantasyBatchCraftingProvider {

    public static final int PATTERN_SLOTS = 36;

    /** One visible fuel slot; partially used balls are represented by {@link #matterBallCharges}. */
    private static final int MATTER_BALL_SLOTS = 1;

    /** No idle network power is used; matter-ball fuel is the machine's only per-craft cost. */
    private static final double IDLE_POWER_USAGE = 0.0;

    private final AppEngInternalInventory patternInv = new AppEngInternalInventory(this, PATTERN_SLOTS, 1);
    private final AppEngInternalInventory matterBallInv = new AppEngInternalInventory(this, MATTER_BALL_SLOTS, 64);

    /// Outputs waiting for the next grid tick so the CPU has time to register its expected result.
    ///
    /// They cannot be inserted inside {@code pushPattern}: the crafting CPU starts waiting for a pattern's output only
    /// after that call returns.
    private final KeyCounter pendingOutputs = new KeyCounter();

    /** Prepaid craft charges, including credits migrated from the unfinished instant-fuel implementation. */
    private long matterBallCharges;
    private boolean consumingMatterBallSlot;

    @Nullable
    private List<IPatternDetails> patternCache;

    /// Kept for the CPU batching compatibility hook. OmniSequence may arm this for a single batched push.
    private long batchCrafts = 1;

    public FantasyAnnihilationBlockEntity(BlockPos pos, BlockState state) {
        this(FTBlockEntities.FANTASY_ANNIHILATION.get(), pos, state);
    }

    public FantasyAnnihilationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        patternInv.setFilter(new PatternSlotFilter());
        matterBallInv.setFilter(new FuelSlotFilter());
        // consumableInv accepts any item by default (no filter)

        getMainNode()
                .setIdlePowerUsage(IDLE_POWER_USAGE)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(ICraftingProvider.class, this)
                .addService(IGridTickable.class, this);
    }

    public AppEngInternalInventory getPatternInv() {
        return patternInv;
    }

    public AppEngInternalInventory getMatterBallInv() {
        return matterBallInv;
    }

    public long getMatterBallCharges() {
        ItemStack stack = matterBallInv.getStackInSlot(0);
        long storedCharges = (long) stack.getCount() * craftsPerFuel(stack);
        return Long.MAX_VALUE - matterBallCharges < storedCharges
                ? Long.MAX_VALUE
                : matterBallCharges + storedCharges;
    }

    @Override
    public InternalInventory getInternalInventory() {
        // Pattern inventory compatibility is retained for AE2's base persistence and pattern-access mechanisms.
        return patternInv;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(Direction side) {
        // Fuel slot on all sides; consumable input via the exposed API.
        return matterBallInv;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == patternInv) {
            patternCache = null;
            ICraftingProvider.requestUpdate(getMainNode());
        } else if (inv == matterBallInv && !consumingMatterBallSlot) {
            saveChanges();
        }
    }

    private boolean canConsumeMatterBalls(long crafts) {
        return crafts > 0 && (!FTConfig.CONSUME_FUEL.get() || getMatterBallCharges() >= crafts);
    }

    private void consumeMatterBalls(long crafts) {
        if (!FTConfig.CONSUME_FUEL.get()) {
            return;
        }
        long fromCharges = Math.min(matterBallCharges, crafts);
        matterBallCharges -= fromCharges;
        long remaining = crafts - fromCharges;
        if (remaining == 0) {
            return;
        }

        ItemStack stack = matterBallInv.getStackInSlot(0).copy();
        int craftsPerFuel = craftsPerFuel(stack);
        if (craftsPerFuel <= 0) {
            throw new IllegalStateException("Fuel configuration changed during an instant craft");
        }
        long fuelItems = Math.floorDiv(remaining - 1, craftsPerFuel) + 1;
        if (fuelItems > stack.getCount()) {
            throw new IllegalStateException("Fuel balance changed during an instant craft");
        }
        stack.shrink(Math.toIntExact(fuelItems));
        matterBallCharges = Math.multiplyExact(fuelItems, craftsPerFuel) - remaining;

        consumingMatterBallSlot = true;
        try {
            matterBallInv.setItemDirect(0, stack);
        } finally {
            consumingMatterBallSlot = false;
        }
    }

    // ------------------------------------------------------------------------
    // ICraftingProvider: request-based instant crafting driven by crafting CPUs
    // ------------------------------------------------------------------------

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (patternCache == null) {
            Set<IPatternDetails> patterns = new LinkedHashSet<>();
            for (int i = 0; i < patternInv.size(); i++) {
                FantasyPatternData data = FantasyPatternItem.getData(patternInv.getStackInSlot(i));
                if (data != null && data.isCraftable()) {
                    patterns.add(new FantasyCraftingPattern(AEItemKey.of(patternInv.getStackInSlot(i)), data));
                }
            }
            patternCache = List.copyOf(patterns);
        }
        return patternCache;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!(patternDetails instanceof FantasyCraftingPattern pattern)
                || batchCrafts < 1) {
            return false;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        KeyCounter stagedOutputs = new KeyCounter();
        try {
            long crafts = batchCrafts;
            for (GenericStack output : pattern.getOutputs()) {
                addChecked(stagedOutputs, output.what(), Math.multiplyExact(output.amount(), crafts));
            }

            IPatternDetails.IInput[] patternInputs = pattern.getInputs();
            for (int i = 0; i < inputHolder.length; i++) {
                if (i >= patternInputs.length) {
                    continue;
                }
                IPatternDetails.IInput input = patternInputs[i];
                for (var entry : inputHolder[i]) {
                    AEKey remaining = MolecularReusableInputAdapters.wearDownBy(input, entry.getKey(), crafts);
                    if (remaining != null) {
                        addChecked(stagedOutputs, remaining, entry.getLongValue());
                    }
                }
            }

            // Validate results and reusable-input returns before consuming a single ingredient.
            var storage = grid.getStorageService().getInventory();
            IActionSource source = IActionSource.ofMachine(this);
            for (var entry : stagedOutputs) {
                if (storage.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE, source)
                        < entry.getLongValue()) {
                    return false;
                }
            }
            if (!canAppendPendingOutputs(stagedOutputs) || !canConsumeMatterBalls(crafts)) {
                return false;
            }
        } catch (ArithmeticException exception) {
            return false;
        }

        // The CPU extracted the material before it called us. Clearing the holders accepts the job; reusable
        // remainders and its result are delivered on the next tick, after AE2 registers what the CPU is waiting for.
        for (KeyCounter counter : inputHolder) {
            counter.reset();
        }
        pendingOutputs.addAll(stagedOutputs);
        consumeMatterBalls(batchCrafts);
        saveChanges();
        wakeProcessingTick();
        return true;
    }

    private static void addChecked(KeyCounter counter, AEKey key, long amount) {
        if (key == null || amount <= 0 || counter.get(key) < 0 || amount > Long.MAX_VALUE - counter.get(key)) {
            throw new ArithmeticException("Invalid or overflowing craft output");
        }
        counter.add(key, amount);
    }

    private boolean canAppendPendingOutputs(KeyCounter additions) {
        for (var entry : additions) {
            long queued = pendingOutputs.get(entry.getKey());
            if (queued < 0 || entry.getLongValue() > Long.MAX_VALUE - queued) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------
    // IFantasyBatchCraftingProvider: several instant recipes per push
    // ------------------------------------------------------------------------

    @Override
    public long fantasyTechnology$batchLimit(IPatternDetails patternDetails) {
        if (!(patternDetails instanceof FantasyCraftingPattern pattern) || getMainNode().getGrid() == null) {
            return 0;
        }
        return getMaxBatchCrafts(pattern, Long.MAX_VALUE);
    }

    public long getMaxBatchCrafts(FantasyCraftingPattern pattern, long requestedCrafts) {
        long limit = Math.min(Math.max(0, requestedCrafts),
                FTConfig.CONSUME_FUEL.get() ? getMatterBallCharges() : Long.MAX_VALUE);
        try {
            for (GenericStack output : pattern.getOutputs()) {
                long queued = pendingOutputs.get(output.what());
                if (queued < 0 || output.amount() <= 0) {
                    return 0;
                }
                limit = Math.min(limit, (Long.MAX_VALUE - queued) / output.amount());
            }
            return limit;
        } catch (ArithmeticException exception) {
            return 0;
        }
    }

    /**
     * Commits a validated OmniSequence batch to the same next-tick output queue used by a normal push.
     *
     * OmniSequence has already extracted the complete input holder and validated the expected output counter before
     * calling this method. The block entity still checks its matter-ball balance so a stale admission cannot consume
     * materials after another request spent the available fuel.
     */
    public boolean acceptOmniBatch(KeyCounter stagedOutputs, long crafts) {
        IGrid grid = getMainNode().getGrid();
        if (crafts < 2 || stagedOutputs == null || stagedOutputs.isEmpty()
                || grid == null || !canConsumeMatterBalls(crafts) || !canAppendPendingOutputs(stagedOutputs)) {
            return false;
        }
        long previousCharges = matterBallCharges;
        ItemStack previousMatterBalls = matterBallInv.getStackInSlot(0).copy();
        KeyCounter previousOutputs = new KeyCounter();
        previousOutputs.addAll(pendingOutputs);
        boolean queued = false;
        try {
            KeyCounter checked = new KeyCounter();
            for (var entry : stagedOutputs) {
                addChecked(checked, entry.getKey(), entry.getLongValue());
            }
            queued = true;
            pendingOutputs.addAll(checked);
            consumeMatterBalls(crafts);
            saveChanges();
            wakeProcessingTick();
            return true;
        } catch (RuntimeException exception) {
            // The admission is still rejectable until it returns. Restore the queue and fuel state so OmniSequence
            // can safely reinject the inputs.
            if (queued) {
                pendingOutputs.reset();
                pendingOutputs.addAll(previousOutputs);
                matterBallCharges = previousCharges;
                consumingMatterBallSlot = true;
                try {
                    matterBallInv.setItemDirect(0, previousMatterBalls);
                } catch (RuntimeException ignored) {
                    // Preserve the original failure as the admission result.
                } finally {
                    consumingMatterBallSlot = false;
                }
            }
            return false;
        }
    }

    @Override
    public void fantasyTechnology$beginBatch(long crafts) {
        batchCrafts = Math.max(1, crafts);
    }

    @Override
    public void fantasyTechnology$endBatch() {
        batchCrafts = 1;
    }

    // ------------------------------------------------------------------------
    // IGridTickable: hands instant-craft outputs to ME storage after pushPattern returns
    // ------------------------------------------------------------------------

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, pendingOutputs.isEmpty());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        flushPendingOutputs();
        return pendingOutputs.isEmpty() ? TickRateModulation.SLEEP : TickRateModulation.URGENT;
    }

    /** Wakes both a sleeping and an alertable AE2 tick tracker; the block ticker remains the final fallback. */
    private void wakeProcessingTick() {
        getMainNode().ifPresent((grid, node) -> {
            try {
                grid.getTickManager().wakeDevice(node);
            } catch (RuntimeException exception) {
                FantasyTechnology.LOGGER.debug("Unable to wake fantasy annihilation tick tracker", exception);
            }
            try {
                grid.getTickManager().alertDevice(node);
            } catch (RuntimeException exception) {
                FantasyTechnology.LOGGER.debug("Unable to alert fantasy annihilation tick tracker", exception);
            }
        });
    }

    private void flushPendingOutputs() {
        IGrid grid = getMainNode().getGrid();
        if (grid == null || pendingOutputs.isEmpty()) {
            return;
        }

        var storage = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(this);

        // Insertion can notify the CPU and make it queue another operation, so do not iterate the mutable queue.
        KeyCounter batch = new KeyCounter();
        batch.addAll(pendingOutputs);
        pendingOutputs.reset();

        for (var entry : batch) {
            long amount = entry.getLongValue();
            long inserted = storage.insert(entry.getKey(), amount, Actionable.MODULATE, source);
            if (inserted < amount) {
                pendingOutputs.add(entry.getKey(), amount - inserted);
            }
        }
        // The queued state may already have been persisted. Mark its removal/reduction so a later reload cannot
        // restore outputs that were successfully delivered to storage.
        saveChanges();
    }

    // ------------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------------

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        matterBallInv.writeToNBT(tag, "matterBallInv", registries);
        if (matterBallCharges > 0) {
            tag.putLong("matterBallCharges", matterBallCharges);
        } else {
            tag.remove("matterBallCharges");
        }
        writeCounter(tag, "pendingOutputs", pendingOutputs, registries);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        consumingMatterBallSlot = true;
        try {
            matterBallInv.readFromNBT(tag, "matterBallInv", registries);
        } finally {
            consumingMatterBallSlot = false;
        }
        matterBallCharges = Math.max(0, tag.getLong("matterBallCharges"));
        readCounter(tag, "pendingOutputs", pendingOutputs, registries);
        // Complete any in-flight delayed craft from older saves immediately after the format change.
        KeyCounter legacyProcessing = new KeyCounter();
        readCounter(tag, "processingOutputs", legacyProcessing, registries);
        pendingOutputs.addAll(legacyProcessing);
    }

    private static void writeCounter(CompoundTag tag, String name, KeyCounter counter, HolderLookup.Provider registries) {
        if (counter.isEmpty()) {
            tag.remove(name);
            return;
        }
        ListTag list = new ListTag();
        for (var entry : counter) {
            list.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getLongValue())));
        }
        tag.put(name, list);
    }

    private static void readCounter(CompoundTag tag, String name, KeyCounter counter, HolderLookup.Provider registries) {
        counter.reset();
        ListTag list = tag.getList(name, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            GenericStack stack = GenericStack.readTag(registries, list.getCompound(i));
            if (stack != null && stack.amount() > 0) {
                counter.add(stack.what(), stack.amount());
            }
        }
    }

    @Override
    public boolean isBusy() {
        // Crafting itself is instant. The next-tick delivery queue does not throttle further pushes.
        return false;
    }

    @Override
    public void onReady() {
        super.onReady();
        if (level != null && !level.isClientSide() && !pendingOutputs.isEmpty()) {
            wakeProcessingTick();
        }
    }

    public boolean isWaitingForGrid() {
        return getMainNode().getGrid() == null;
    }

    // ------------------------------------------------------------------------
    // PatternContainer: integration with the pattern access terminal
    // ------------------------------------------------------------------------

    @Nullable
    @Override
    public IGrid getGrid() {
        return getMainNode().getGrid();
    }

    @Override
    public boolean isVisibleInTerminal() {
        return true;
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return patternInv;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        Component name = hasCustomName() ? getCustomName() : FTBlocks.FANTASY_ANNIHILATION.get().getName();
        return new PatternContainerGroup(AEItemKey.of(FTBlocks.FANTASY_ANNIHILATION.get()), name, List.of());
    }

    /// Whether the surrounding multiblock structure is complete. Reserved for a future multiblock layout.
    public boolean isStructureFormed() {
        return true;
    }

    /// Whether an item stack is a configured fuel item (matter ball by default).
    private static boolean isFuelItem(ItemStack stack) {
        return craftsPerFuel(stack) > 0;
    }

    /// Number of crafts supplied by one item in this stack, or zero when it is not configured as fuel.
    private static int craftsPerFuel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return getFuelCrafts().getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), 0);
    }

    /// Fuel values are rebuilt on every call because the server config may change without a world reload.
    private static Map<ResourceLocation, Integer> getFuelCrafts() {
        Map<ResourceLocation, Integer> fuels = new LinkedHashMap<>();
        for (String configured : FTConfig.ANNIHILATION_FUEL_ITEMS.get()) {
            FTConfig.AnnihilationFuel fuel = FTConfig.parseAnnihilationFuel(configured);
            if (fuel != null) {
                fuels.put(fuel.itemId(), fuel.crafts());
            }
        }
        if (fuels.isEmpty()) {
            FTConfig.AnnihilationFuel fallback = FTConfig.parseAnnihilationFuel(
                    FTConfig.DEFAULT_ANNIHILATION_FUEL_ITEMS.getFirst());
            if (fallback != null) {
                fuels.put(fallback.itemId(), fallback.crafts());
            }
        }
        return fuels;
    }

    private static class PatternSlotFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return stack.getItem() instanceof FantasyPatternItem;
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }
    }

    private static class FuelSlotFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return isFuelItem(stack);
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }
    }

}
