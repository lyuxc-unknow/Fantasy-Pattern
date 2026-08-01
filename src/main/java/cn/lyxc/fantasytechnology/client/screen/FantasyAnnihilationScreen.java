package cn.lyxc.fantasytechnology.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import cn.lyxc.fantasytechnology.menu.FantasyAnnihilationMenu;

/**
 * Screen of the fantasy annihilation block, drawn by AE2's screen framework like any of its own machine menus: the
 * panel and slot positions come from the style document, the slots themselves are ordinary {@code AppEngSlot}s.
 */
public class FantasyAnnihilationScreen extends AEBaseScreen<FantasyAnnihilationMenu> {

    /** Resolved against the ae2 namespace by {@code StyleManager}, hence the file lives under assets/ae2/screens/. */
    public static final String STYLE = "/screens/fantasy_annihilation.json";

    public FantasyAnnihilationScreen(FantasyAnnihilationMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected boolean shouldAddToolbar() {
        // Plain machine menu: no terminal toolbar, search bar or help sidebar.
        return false;
    }
}
