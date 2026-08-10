package cn.lyxc.fantasytechnology.client.screen;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import cn.lyxc.fantasytechnology.menu.FantasyDeviceAccessMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/// Screen of the device access block, drawn by AE2's screen framework like any of its own machine menus.
public class FantasyDeviceAccessScreen extends AEBaseScreen<FantasyDeviceAccessMenu> {

    /// Resolved against the ae2 namespace by {@code StyleManager}, hence the file lives under assets/ae2/screens/.
    public static final String STYLE = "/screens/fantasy_device_access.json";

    public FantasyDeviceAccessScreen(FantasyDeviceAccessMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected boolean shouldAddToolbar() {
        // Plain machine menu: no terminal toolbar, search bar or help sidebar.
        return false;
    }
}
