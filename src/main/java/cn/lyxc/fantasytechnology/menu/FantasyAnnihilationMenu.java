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
