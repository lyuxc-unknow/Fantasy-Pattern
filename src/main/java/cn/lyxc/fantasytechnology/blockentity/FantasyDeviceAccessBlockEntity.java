package cn.lyxc.fantasytechnology.blockentity;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
import cn.lyxc.fantasytechnology.registry.FTBlockEntities;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.atomic.AtomicLong;

/// The device access block: a plain 36-slot store that declares which machines a network owns.
///
/// It holds nothing back and produces nothing - items go in and out by hand or by pipe like any chest. What it is for
/// is the encoding terminal: a recipe may only be transferred in from a recipe viewer when the machines that viewer
/// lists as the recipe's catalysts are sitting in one of these on the same network. So the contents are a statement of
/// capability rather than a supply of materials, and they deliberately stay out of ME storage - an item the network
/// could spend on autocrafting would be a poor thing to also count as proof that you own the machine.
///
/// A slot holds at most {@link #SLOT_LIMIT}, which is what makes the terminal's "four of them" threshold a matter of
/// filling slots rather than of one deep stack.
public class FantasyDeviceAccessBlockEntity extends AENetworkedInvBlockEntity {

    public static final int SLOTS = 36;

    /// Per-slot ceiling. The terminal requires four catalysts in total, so a single slot can carry a recipe on its
    /// own - and anything beyond that has to be spread across slots, which is the point.
    public static final int SLOT_LIMIT = 64;

    /// Idle power draw while connected to a grid (AE/t).
    private static final double IDLE_POWER_USAGE = 1.0;

    /// Bumped whenever any of these blocks changes contents, anywhere. Open encoding terminals watch it so they only
    /// rebuild and resend their catalyst summary when something actually moved, instead of every tick. A single
    /// counter across all networks costs an occasional redundant rebuild and saves tracking membership per grid.
    private static final AtomicLong CHANGE_COUNTER = new AtomicLong();

    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, SLOTS, SLOT_LIMIT);

    public FantasyDeviceAccessBlockEntity(BlockPos pos, BlockState state) {
        this(FTBlockEntities.FANTASY_DEVICE_ACCESS.get(), pos, state);
    }

    public FantasyDeviceAccessBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        getMainNode()
                .setIdlePowerUsage(IDLE_POWER_USAGE)
                .setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return inv;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        CHANGE_COUNTER.incrementAndGet();
        saveChanges();
    }

    /// A value that changes whenever any device access block's contents change; see {@link #CHANGE_COUNTER}.
    public static long changeCounter() {
        return CHANGE_COUNTER.get();
    }

    /// Everything the device access blocks on {@code grid} are holding, summed per item.
    ///
    /// Catalysts are counted by item alone, ignoring data components: a recipe viewer lists the machine as a plain
    /// block, and a player who has named theirs or left durability on it still owns the machine.
    ///
    /// Blocks whose node is inactive - no channel, no power - contribute nothing, so an unpowered device is not a
    /// device you can encode against.
    public static Object2IntMap<Item> collectCatalysts(IGrid grid) {
        var counts = new Object2IntOpenHashMap<Item>();
        if (grid == null) {
            return counts;
        }
        for (var device : grid.getMachines(FantasyDeviceAccessBlockEntity.class)) {
            if (!device.getMainNode().isActive()) {
                continue;
            }
            InternalInventory inventory = device.getInternalInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                var stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    counts.mergeInt(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
        }
        return counts;
    }
}
