package cn.lyxc.fantasytechnology.client.screen;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.menu.FantasyAnnihilationMenu;
import cn.lyxc.fantasytechnology.util.CompactCount;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/// Screen of the fantasy annihilation block, drawn by AE2's screen framework like any of its own machine menus: the
/// panel and slot positions come from the style document, the slots themselves are ordinary {@code AppEngSlot}s.
public class FantasyAnnihilationScreen extends AEBaseScreen<FantasyAnnihilationMenu> {

    /// Resolved against the ae2 namespace by {@code StyleManager}, hence the file lives under assets/ae2/screens/.
    public static final String STYLE = "/screens/fantasy_annihilation.json";

    /// Vanilla draws a stack count ending 17px from the slot origin, one pixel past the 16px icon.
    private static final int COUNT_RIGHT = 17;
    private static final int COUNT_TOP = 9;
    /// Widest count that still sits inside the icon. Wider numbers, such as 9999, are scaled down to this width.
    private static final int COUNT_MAX_WIDTH = 16;

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
        // Three catalyst slots occupy x=8..62. Status uses the free row above them; the label and count sit to the right.
        guiGraphics.drawString(font, statusText(), 8, 97, color, false);
        guiGraphics.drawString(font,
                Component.translatable("gui.fantasy_technology.fantasy_annihilation.matter_ball"),
                66, 109, color, false);
        guiGraphics.drawString(font,
                Component.translatable("gui.fantasy_technology.fantasy_annihilation.matter_ball_charges",
                        CompactCount.format(menu.matterBallCharges)),
                66, 118, color, false);
        drawCatalystCounts(guiGraphics);
    }

    /// Stack counts stay inside the catalyst slot. Two-digit counts keep vanilla's size and position; wider counts
    /// shrink toward the slot's bottom-right corner instead of spilling into the neighbouring slot.
    private void drawCatalystCounts(GuiGraphics graphics) {
        for (Slot slot : menu.slots) {
            if (!menu.isCatalystSlot(slot)) {
                continue;
            }
            ItemStack stack = slot.getItem();
            int count = stack.getCount();
            if (count <= 1) {
                continue;
            }
            String text = Integer.toString(count);
            int textWidth = font.width(text);
            float scale = textWidth <= COUNT_MAX_WIDTH ? 1.0F : (float) COUNT_MAX_WIDTH / textWidth;
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(0.0F, 0.0F, 200.0F);
            pose.translate(slot.x + COUNT_RIGHT, slot.y + COUNT_TOP + font.lineHeight, 0.0F);
            pose.scale(scale, scale, 1.0F);
            graphics.drawString(font, text, -textWidth, -font.lineHeight, 0xFFFFFF, true);
            pose.popPose();
        }
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
