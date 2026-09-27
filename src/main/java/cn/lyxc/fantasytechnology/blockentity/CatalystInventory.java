package cn.lyxc.fantasytechnology.blockentity;

import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import cn.lyxc.fantasytechnology.config.FTConfig;
import net.minecraft.world.item.ItemStack;

/// Catalyst slots whose stack size follows {@link FTConfig#catalystSlotLimit()} instead of the item's own maximum.
///
/// AE2's default insert and extract paths clamp to {@link ItemStack#getMaxStackSize()}, which would keep a slot at 64
/// even after the provider limit is raised.
final class CatalystInventory extends AppEngInternalInventory {

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
        int taken = Math.min(amount, inSlot.getCount());
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
}
