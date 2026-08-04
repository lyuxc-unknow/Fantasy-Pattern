package cn.lyxc.fantasytechnology.registry;

import appeng.items.parts.PartItem;
import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.item.FantasyBlankPatternItem;
import cn.lyxc.fantasytechnology.item.FantasyPatternItem;
import cn.lyxc.fantasytechnology.part.FantasyEncodingTerminalPart;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FTItems {
    private FTItems() {
    }

    static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(FantasyTechnology.MODID);

    /// Blank fantasy patterns feed the fantasy encoding terminal; encoding consumes one and produces a
    /// {@link #FANTASY_PATTERN}.
    public static final DeferredItem<FantasyBlankPatternItem> FANTASY_BLANK_PATTERN = REGISTER
            .registerItem("fantasy_blank_pattern", FantasyBlankPatternItem::new, new Item.Properties());

    /// The encoded fantasy pattern ("幻梦重组样板"), carrying its recipe in the
    /// {@link FTComponents#FANTASY_PATTERN_DATA} component. Capped at one per stack like AE2's own encoded patterns,
    /// since every one of them holds a different recipe.
    public static final DeferredItem<FantasyPatternItem> FANTASY_PATTERN = REGISTER
            .registerItem("fantasy_pattern", FantasyPatternItem::new, new Item.Properties().stacksTo(1));

    /// The fantasy encoding terminal is an AE2 cable part, so it is registered as a part item rather than a block item:
    /// placing it attaches it to a cable, where it can reach the ME network.
    public static final DeferredItem<PartItem<FantasyEncodingTerminalPart>> FANTASY_ENCODING_TERMINAL = REGISTER
            .registerItem("fantasy_encoding_terminal",
                    properties -> new PartItem<>(properties, FantasyEncodingTerminalPart.class,
                            FantasyEncodingTerminalPart::new),
                    new Item.Properties().stacksTo(64));

    /// Forces class loading so the static registrations above run.
    public static void init(IEventBus bus) {
        REGISTER.register(bus);
    }
}
