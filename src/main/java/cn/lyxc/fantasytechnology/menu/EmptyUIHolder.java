package cn.lyxc.fantasytechnology.menu;

import net.minecraft.world.entity.player.Player;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

/**
 * Stand-in UI holder for the rare case where a menu is constructed for a block entity that is no longer there. It
 * builds an empty UI and immediately reports itself invalid, so the menu closes on the next tick.
 */
final class EmptyUIHolder implements IContainerUIHolder {

    static final EmptyUIHolder INSTANCE = new EmptyUIHolder();

    private EmptyUIHolder() {
    }

    @Override
    public ModularUI createUI(Player player) {
        return ModularUI.of(UI.empty(), player);
    }

    @Override
    public boolean isStillValid(Player player) {
        return false;
    }
}
