package cn.lyxc.fantasytechnology.registry;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

import appeng.items.parts.PartItem;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.item.FantasyPatternItem;
import cn.lyxc.fantasytechnology.part.FantasyEncodingTerminalPart;

public final class FTItems {
    private FTItems() {
    }

    public static final DeferredItem<FantasyPatternItem> FANTASY_PATTERN = FantasyTechnology.ITEMS
            .registerItem("fantasy_pattern", FantasyPatternItem::new, new Item.Properties());

    /**
     * The fantasy encoding terminal is an AE2 cable part, so it is registered as a part item rather than a block item:
     * placing it attaches it to a cable, where it can reach the ME network.
     */
    public static final DeferredItem<PartItem<FantasyEncodingTerminalPart>> FANTASY_ENCODING_TERMINAL = FantasyTechnology.ITEMS
            .registerItem("fantasy_encoding_terminal",
                    properties -> new PartItem<>(properties, FantasyEncodingTerminalPart.class,
                            FantasyEncodingTerminalPart::new),
                    new Item.Properties().stacksTo(64));

    /** Forces class loading so the static registrations above run. */
    public static void init() {
    }
}
