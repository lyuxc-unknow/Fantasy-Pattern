package cn.lyxc.fantasytechnology.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;

import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.registry.FTMenus;
import cn.lyxc.fantasytechnology.ui.FantasyAnnihilationUI;

/**
 * Menu of the fantasy annihilation block.
 *
 * The slot layout lives in {@link FantasyAnnihilationUI}; LDLib registers a container slot for every {@code ItemSlot}
 * element of the modular UI and derives shift-click behaviour from their slot styles.
 */
public class FantasyAnnihilationMenu extends ModularUIContainerMenu {

    /** Server-side constructor, used by the block entity's MenuProvider. */
    public FantasyAnnihilationMenu(int containerId, Inventory playerInventory,
            FantasyAnnihilationBlockEntity annihilation) {
        super(menuType(), containerId, playerInventory, annihilation);
    }

    /** Client-side constructor, used by the menu type factory with the block position from the buffer. */
    public FantasyAnnihilationMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        super(menuType(), containerId, playerInventory, holderAt(playerInventory, buffer.readBlockPos()));
    }

    private static IContainerUIHolder holderAt(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof FantasyAnnihilationBlockEntity annihilation) {
            return annihilation;
        }
        // The block entity is gone (or not synced yet): show nothing rather than crashing; stillValid closes the menu.
        return EmptyUIHolder.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private static MenuType<ModularUIContainerMenu> menuType() {
        return (MenuType<ModularUIContainerMenu>) (MenuType<?>) FTMenus.FANTASY_ANNIHILATION.get();
    }
}
