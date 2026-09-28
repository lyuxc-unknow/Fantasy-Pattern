package cn.lyxc.fantasytechnology.menu;

import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.item.FantasyPatternItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/// Menu of the fantasy annihilation block, in the style of AE2's own machine menus (see {@code SkyChestMenu}).
///
/// Built unregistered so the type can live in this mod's {@code DeferredRegister} rather than AE2's internal
/// registration queue; the block entity is the menu host and is resolved from the open position on the client.
public class FantasyAnnihilationMenu extends AEBaseMenu {

    public static final MenuType<FantasyAnnihilationMenu> TYPE = MenuTypeBuilder
            .create(FantasyAnnihilationMenu::new, FantasyAnnihilationBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID, "fantasy_annihilation"));

    @GuiSync(0)
    public boolean waitingForGrid;
    @GuiSync(1)
    public long matterBallCharges;

    private final FantasyAnnihilationBlockEntity annihilation;

    public FantasyAnnihilationMenu(int id, Inventory playerInventory, FantasyAnnihilationBlockEntity annihilation) {
        super(TYPE, id, playerInventory, annihilation);
        this.annihilation = annihilation;

        var patternInv = annihilation.getPatternInv();
        for (int i = 0; i < patternInv.size(); i++) {
            addSlot(new PatternDisplaySlot(patternInv, i), SlotSemantics.ENCODED_PATTERN);
        }
        var catalystInv = annihilation.getMatterBallInv();
        for (int i = 0; i < catalystInv.size(); i++) {
            addSlot(new CatalystSlot(catalystInv, i), SlotSemantics.STORAGE);
        }

        createPlayerInventorySlots(playerInventory);
    }

    @Override
    public void broadcastChanges() {
        waitingForGrid = annihilation.isWaitingForGrid();
        matterBallCharges = annihilation.getMatterBallCharges();
        super.broadcastChanges();
    }

    public boolean isCatalystSlot(Slot slot) {
        return slot instanceof CatalystSlot;
    }

    /// A click puts at most one stack of that item on the cursor or in a hotbar slot. Shift-click still fills the
    /// inventory, and a right-click whose half is larger than one stack sends the remainder there too.
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof CatalystSlot slot
                && handleCatalystClick(slot, button, clickType, player)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /// @return true when the click must not fall through to the vanilla slot transfer
    private boolean handleCatalystClick(CatalystSlot slot, int button, ClickType clickType, Player player) {
        ItemStack inSlot = slot.getItem();
        if (clickType == ClickType.PICKUP && button == 1 && getCarried().isEmpty() && slot.mayPickup(player)) {
            int half = (inSlot.getCount() + 1) / 2;
            if (half > handfulSize(inSlot)) {
                takeHalf(player, slot, half);
                return true;
            }
        }
        if (clickType == ClickType.PICKUP && !getCarried().isEmpty() && !inSlot.isEmpty()
                && inSlot.getCount() > handfulSize(inSlot)
                && !ItemStack.isSameItemSameComponents(inSlot, getCarried())) {
            return true;
        }
        if (clickType == ClickType.SWAP && isHotbarButton(button)) {
            return handleCatalystSwap(player, slot, button);
        }
        return false;
    }

    /// Right-click takes half of the slot. One stack of that item goes to the cursor, and the rest of the half goes
    /// into the player inventory as ordinary stacks.
    private void takeHalf(Player player, CatalystSlot slot, int half) {
        int toCursor = Math.min(half, handfulSize(slot.getItem()));
        setCarried(slot.extractUpTo(toCursor));
        int rest = half - getCarried().getCount();
        if (rest > 0) {
            ItemStack extra = slot.extractUpTo(rest);
            player.getInventory().add(extra);
            if (!extra.isEmpty()) {
                ItemStack returned = slot.safeInsert(extra);
                if (!returned.isEmpty()) {
                    player.drop(returned, false);
                }
            }
        }
        slot.setChanged();
    }

    /// @return true when vanilla must not move the whole catalyst stack into one hotbar slot
    private boolean handleCatalystSwap(Player player, CatalystSlot slot, int button) {
        if (button == Inventory.SLOT_OFFHAND && isPlayerInventorySlotLocked(button)) {
            return true;
        }
        ItemStack inSlot = slot.getItem();
        int handful = handfulSize(inSlot);
        if (inSlot.isEmpty() || inSlot.getCount() <= handful) {
            return false;
        }
        ItemStack hotbar = player.getInventory().getItem(button);
        if (!slot.mayPickup(player)) {
            return true;
        }
        if (hotbar.isEmpty()) {
            player.getInventory().setItem(button, slot.extractUpTo(handful));
            slot.setChanged();
            return true;
        }
        if (ItemStack.isSameItemSameComponents(inSlot, hotbar)) {
            int room = hotbar.getMaxStackSize() - hotbar.getCount();
            if (room > 0) {
                ItemStack moved = slot.extractUpTo(Math.min(room, handful));
                hotbar.grow(moved.getCount());
                player.getInventory().setItem(button, hotbar);
                slot.setChanged();
            }
            return true;
        }
        return true;
    }

    private static boolean isHotbarButton(int button) {
        return (button >= 0 && button < 9) || button == Inventory.SLOT_OFFHAND;
    }

    /// How many of this item fit in one ordinary stack.
    private static int handfulSize(ItemStack stack) {
        return Math.max(1, stack.getMaxStackSize());
    }

    /// Holds more than the item's vanilla stack size. The limit comes from the server config.
    ///
    /// The displayed stack is forced to a count of one so vanilla's count text, which is right-aligned and spills out
    /// of the slot once the number reaches four digits, is not drawn. The screen paints a fitted count instead.
    private static final class CatalystSlot extends AppEngSlot {

        CatalystSlot(InternalInventory inv, int index) {
            super(inv, index);
        }

        @Override
        public int getMaxStackSize() {
            return FTConfig.catalystSlotLimit();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return getMaxStackSize();
        }

        /// Menu clicks ask for the whole stack or half of it. The cursor and a thrown entity take one stack of this
        /// item; a larger move goes through {@link #extractUpTo}.
        @Override
        public ItemStack remove(int amount) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack inSlot = getItem();
            int limit = inSlot.isEmpty() ? 0 : handfulSize(inSlot);
            return extractUpTo(Math.min(amount, limit));
        }

        /// Removes up to {@code amount} items, ignoring the vanilla stack size. The caller places the result somewhere
        /// that can hold it.
        ItemStack extractUpTo(int amount) {
            ItemStack inSlot = getItem();
            if (amount <= 0 || inSlot.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, inSlot.getCount());
            ItemStack extracted = inSlot.copyWithCount(taken);
            set(taken >= inSlot.getCount() ? ItemStack.EMPTY : inSlot.copyWithCount(inSlot.getCount() - taken));
            return extracted;
        }

        @Override
        public ItemStack getDisplayStack() {
            ItemStack stack = super.getDisplayStack();
            if (stack.isEmpty() || stack.getCount() <= 1) {
                return stack;
            }
            return stack.copyWithCount(1);
        }
    }

    /// Shows the first output of an encoded fantasy pattern instead of the pattern item itself, like AE2's own
    /// pattern provider slots do for their encoded patterns.
    private static class PatternDisplaySlot extends AppEngSlot {

        PatternDisplaySlot(InternalInventory inv, int index) {
            super(inv, index);
        }

        @Override
        public ItemStack getDisplayStack() {
            ItemStack stack = super.getDisplayStack();
            // Client only, as in AE2's own pattern slot: the substitute is purely cosmetic, and the cache behind
            // getOutput is an unsynchronised WeakHashMap that the server thread has no business touching.
            if (isRemote() && stack.getItem() instanceof FantasyPatternItem) {
                ItemStack output = FantasyPatternItem.getOutput(stack);
                if (!output.isEmpty()) {
                    return output;
                }
            }
            return stack;
        }
    }

}
