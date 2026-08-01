package cn.lyxc.fantasytechnology.ui;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;

/**
 * Small shared building blocks for this mod's LDLib UIs.
 */
final class UIElements {

    /** Dark gray, shadowless text, matching the vanilla container title color. */
    static final int LABEL_COLOR = 0xFF404040;

    private UIElements() {
    }

    /** A plain, vanilla-looking single text line. */
    static TextElement label(Component text) {
        TextElement element = new TextElement();
        element.setText(text);
        element.textStyle(style -> style
                .textColor(LABEL_COLOR)
                .textShadow(false)
                .adaptiveWidth(true)
                .textAlignVertical(Vertical.CENTER));
        element.getLayout().height(9);
        return element;
    }
}
