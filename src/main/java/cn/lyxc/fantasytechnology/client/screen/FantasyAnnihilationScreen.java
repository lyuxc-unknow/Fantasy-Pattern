package cn.lyxc.fantasytechnology.client.screen;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.menu.FantasyAnnihilationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/// Screen of the fantasy annihilation block, drawn by AE2's screen framework like any of its own machine menus: the
/// panel and slot positions come from the style document, the slots themselves are ordinary {@code AppEngSlot}s.
public class FantasyAnnihilationScreen extends AEBaseScreen<FantasyAnnihilationMenu> {

    /// Resolved against the ae2 namespace by {@code StyleManager}, hence the file lives under assets/ae2/screens/.
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

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        int color = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        guiGraphics.drawString(font,
                Component.translatable("gui.fantasy_technology.fantasy_annihilation.matter_ball"),
                8, 97, color, false);

        guiGraphics.drawString(font,
                Component.translatable("gui.fantasy_technology.fantasy_annihilation.matter_ball_charges",
                        menu.matterBallCharges),
                32, 109, color, false);
        guiGraphics.drawString(font, statusText(), 32, 120, color, false);
    }

    private Component statusText() {
        String suffix;
        if (menu.waitingForGrid) {
            suffix = "waiting_grid";
        } else if (FTConfig.CONSUME_FUEL.get() && menu.matterBallCharges <= 0) {
            suffix = "no_matter_ball";
        } else {
            suffix = "ready";
        }
        return Component.translatable("gui.fantasy_technology.fantasy_annihilation.status." + suffix);
    }

}
