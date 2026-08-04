package cn.lyxc.fantasytechnology.item;

import net.minecraft.world.item.Item;

import javax.annotation.ParametersAreNonnullByDefault;

/// The blank fantasy pattern ("空白幻梦样板"). A plain item with no recipe data; it is consumed by the fantasy
/// encoding terminal, which writes a processing recipe onto it and turns it into a {@link FantasyPatternItem}
/// ("幻梦重组样板").
@ParametersAreNonnullByDefault
public class FantasyBlankPatternItem extends Item {

    public FantasyBlankPatternItem(Properties properties) {
        super(properties);
    }
}
