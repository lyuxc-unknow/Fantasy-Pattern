package cn.lyxc.fantasytechnology.blockentity;

import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import cn.lyxc.fantasytechnology.config.FTConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/// Catalyst slots whose stack size follows {@link FTConfig#catalystSlotLimit()} instead of the item's own maximum.
///
/// AE2's default insert and extract paths clamp to {@link ItemStack#getMaxStackSize()}, which would keep a slot at 64
/// even after the provider limit is raised.
final class CatalystInventory extends AppEngInternalInventory {

    /// Real stack size when it does not fit in the vanilla item codec. Absent on stacks of 99 or fewer.
    private static final String REAL_COUNT = "ft_count";

    CatalystInventory(InternalInventoryHost host, int slots) {
        super(host, slots, FTConfig.DEFAULT_CATALYST_SLOT_LIMIT);
    }

    @Override
    public int getSlotLimit(int slot) {
        return FTConfig.catalystSlotLimit();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= size()) {
            throw new IllegalArgumentException("slot out of range");
        }
        if (stack.isEmpty() || !isItemValid(slot, stack)) {
            return stack;
        }

        ItemStack inSlot = getStackInSlot(slot);
        int room = getSlotLimit(slot) - inSlot.getCount();
        if (room <= 0) {
            return stack;
        }
        if (!inSlot.isEmpty() && !ItemStack.isSameItemSameComponents(inSlot, stack)) {
            return stack;
        }

        int moved = Math.min(stack.getCount(), room);
        if (!simulate) {
            ItemStack result = inSlot.isEmpty() ? stack.copy() : inSlot.copy();
            result.setCount(inSlot.getCount() + moved);
            setItemDirect(slot, result);
        }
        if (moved >= stack.getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(moved);
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= size()) {
            throw new IllegalArgumentException("slot out of range");
        }
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack inSlot = getStackInSlot(slot);
        if (inSlot.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // One take matches a normal stack of this item, usually 64. Larger amounts stay in the slot.
        int taken = Math.min(amount, Math.min(inSlot.getCount(), Math.max(1, inSlot.getMaxStackSize())));
        ItemStack extracted = inSlot.copy();
        extracted.setCount(taken);
        if (!simulate) {
            if (taken >= inSlot.getCount()) {
                setItemDirect(slot, ItemStack.EMPTY);
            } else {
                ItemStack remaining = inSlot.copy();
                remaining.shrink(taken);
                setItemDirect(slot, remaining);
            }
        }
        return extracted;
    }

    @Override
    public void writeToNBT(CompoundTag data, String name, HolderLookup.Provider registries) {
        if (isEmpty()) {
            data.remove(name);
            return;
        }
        ListTag list = new ListTag();
        for (int slot = 0; slot < size(); slot++) {
            ItemStack stack = getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int count = stack.getCount();
            CompoundTag tag = new CompoundTag();
            tag.putInt("Slot", slot);
            // save() returns a copy of the tag. The compound passed in is left unchanged, so the result has to be kept.
            ItemStack savable = count > FantasyAnnihilationBlockEntity.VANILLA_ITEM_STACK_SAVE_LIMIT
                    ? stack.copyWithCount(1)
                    : stack;
            if (!(savable.save(registries, tag) instanceof CompoundTag saved)) {
                continue;
            }
            if (count > FantasyAnnihilationBlockEntity.VANILLA_ITEM_STACK_SAVE_LIMIT) {
                saved.putInt(REAL_COUNT, count);
            }
            list.add(saved);
        }
        data.put(name, list);
    }

    @Override
    public void readFromNBT(CompoundTag data, String name, HolderLookup.Provider registries) {
        super.readFromNBT(data, name, registries);
        if (!data.contains(name, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = data.getList(name, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.contains(REAL_COUNT, Tag.TAG_INT)) {
                continue;
            }
            int slot = tag.getInt("Slot");
            int count = tag.getInt(REAL_COUNT);
            if (slot < 0 || slot >= size() || count <= 0) {
                continue;
            }
            ItemStack stack = getStackInSlot(slot);
            if (!stack.isEmpty()) {
                stack.setCount(count);
            }
        }
    }
}
