package cn.lyxc.fantasytechnology.blockentity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

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
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;

import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.FantasyPatternItem;
import cn.lyxc.fantasytechnology.registry.FTBlockEntities;
import cn.lyxc.fantasytechnology.registry.FTBlocks;

/**
 * The fantasy annihilation block ("幻梦寂灭"). An ME autocrafting device holding fantasy patterns:
 *
 * The patterns installed here are registered with the network's crafting service (so they show up in terminals and
 * the pattern access terminal). When a crafting CPU executes one of them, the block consumes the ingredients and
 * inserts the result into the network instantly - no intermediate process, and it never reports busy, so a single
 * block executes as many crafting operations per tick as the CPU requests.
 */
public class FantasyAnnihilationBlockEntity extends AENetworkedInvBlockEntity
        implements ICraftingProvider, PatternContainer, IGridTickable {

    public static final int PATTERN_SLOTS = 36;

    /** Idle power draw while connected to a grid (AE/t). */
    private static final double IDLE_POWER_USAGE = 1.0;

    private final AppEngInternalInventory patternInv = new AppEngInternalInventory(this, PATTERN_SLOTS, 1);

    /**
     * Results that have been crafted but not yet handed to the network.
     *
     * They cannot be inserted from inside {@code pushPattern}, because the crafting CPU only registers what it is
     * waiting for after that call returns; see the comment there. Draining happens on the next grid tick.
     */
    private final KeyCounter pendingOutputs = new KeyCounter();

    @Nullable
    private List<IPatternDetails> patternCache;

    public FantasyAnnihilationBlockEntity(BlockPos pos, BlockState state) {
        this(FTBlockEntities.FANTASY_ANNIHILATION.get(), pos, state);
    }

    public FantasyAnnihilationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        patternInv.setFilter(new PatternSlotFilter());
        getMainNode()
                .setIdlePowerUsage(IDLE_POWER_USAGE)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(ICraftingProvider.class, this)
                .addService(IGridTickable.class, this);
    }

    public AppEngInternalInventory getPatternInv() {
        return patternInv;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return patternInv;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == patternInv) {
            // Patterns changed: drop the cache and re-register them with the crafting service.
            patternCache = null;
            ICraftingProvider.requestUpdate(getMainNode());
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
        if (!(patternDetails instanceof FantasyCraftingPattern pattern)) {
            return false;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        var storage = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(this);

        // The network must have room for the full result, otherwise refuse and let the CPU retry later. Simulating
        // against pendingOutputs as well would be more precise, but the queue is drained every tick, so at worst we
        // accept one tick's worth of work more than storage can take and hold it until room appears.
        for (GenericStack output : pattern.getOutputs()) {
            if (storage.insert(output.what(), output.amount(), Actionable.SIMULATE, source) < output.amount()) {
                return false;
            }
        }

        // Annihilate the ingredients. The CPU has already extracted them from the network, so their
        // ownership is ours - emptying the counters simply makes them vanish.
        for (KeyCounter counter : inputHolder) {
            counter.reset();
        }

        // Queue the result instead of inserting it right now.
        //
        // The CPU only starts waiting for a pattern's outputs *after* pushPattern returns: executeCrafting calls us
        // first and adds the expected outputs to its waitingFor list afterwards. Inserting here would therefore offer
        // each result to a CPU that is not yet expecting it, so every insert would be matched against the previous
        // operation's expectation and the job would end up permanently one craft short.
        for (GenericStack output : pattern.getOutputs()) {
            pendingOutputs.add(output.what(), output.amount());
        }
        getMainNode().ifPresent((g, node) -> g.getTickManager().alertDevice(node));
        return true;
    }

    /**
     * Pushes queued results into the network. Runs on the tick after the craft was pushed, by which point the
     * crafting CPU is waiting for them and will claim them as completed operations.
     */
    private void flushPendingOutputs() {
        IGrid grid = getMainNode().getGrid();
        if (grid == null || pendingOutputs.isEmpty()) {
            return;
        }

        var storage = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(this);

        // Drain into a snapshot first: inserting notifies the crafting CPU, and anything that ends up queueing more
        // results while we are inside the loop must not mutate the collection we are iterating.
        KeyCounter batch = new KeyCounter();
        batch.addAll(pendingOutputs);
        pendingOutputs.reset();

        for (var entry : batch) {
            long amount = entry.getLongValue();
            long inserted = storage.insert(entry.getKey(), amount, Actionable.MODULATE, source);
            // Storage filled up in the meantime; keep the rest and retry on a later tick.
            if (inserted < amount) {
                pendingOutputs.add(entry.getKey(), amount - inserted);
            }
        }
    }

    // ------------------------------------------------------------------------
    // IGridTickable: drains the queued results
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

    // ------------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------------

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // The queue normally lives for a single tick, but it must survive an unload in between so that a craft in
        // flight is not silently destroyed.
        if (!pendingOutputs.isEmpty()) {
            ListTag list = new ListTag();
            for (var entry : pendingOutputs) {
                list.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getLongValue())));
            }
            tag.put("pendingOutputs", list);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        pendingOutputs.reset();
        ListTag list = tag.getList("pendingOutputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            GenericStack stack = GenericStack.readTag(registries, list.getCompound(i));
            if (stack != null) {
                pendingOutputs.add(stack.what(), stack.amount());
            }
        }
    }

    @Override
    public boolean isBusy() {
        // Crafting is instant, so the block always accepts new work.
        return false;
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

    // ------------------------------------------------------------------------
    // Multiblock (reserved)
    // ------------------------------------------------------------------------

    /**
     * Whether the surrounding multiblock structure is complete. Reserved for a future multiblock layout - for now the
     * structure is always considered formed, so the block works standalone exactly like before.
     */
    public boolean isStructureFormed() {
        return true;
    }

    /**
     * Only fantasy patterns may be placed into pattern slots.
     */
    private static class PatternSlotFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, net.minecraft.world.item.ItemStack stack) {
            return stack.getItem() instanceof FantasyPatternItem;
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }
    }
}
