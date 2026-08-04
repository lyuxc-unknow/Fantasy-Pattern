package cn.lyxc.fantasytechnology.registry;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class FTCreativeTab {
    public FTCreativeTab() {
    }

    private static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, FantasyTechnology.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FT_MAIN = REGISTER.register(
            "fantasy_technology",
            () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.fantasy_technology"))
                .icon(() -> FTItems.FANTASY_PATTERN.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    output.accept(FTItems.FANTASY_BLANK_PATTERN.get());
                    output.accept(FTItems.FANTASY_PATTERN.get());
                    output.accept(FTItems.FANTASY_ENCODING_TERMINAL.get());
                    output.accept(FTBlocks.FANTASY_ANNIHILATION.get());
                })
                .build()
    );

    /// Forces class loading so the static registrations above run.
    public static void init(IEventBus bus) {
        REGISTER.register(bus);
    }
}
