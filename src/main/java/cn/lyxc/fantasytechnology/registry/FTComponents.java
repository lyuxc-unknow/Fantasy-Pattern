package cn.lyxc.fantasytechnology.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;

public final class FTComponents {
    private FTComponents() {
    }

    public static final DeferredRegister<DataComponentType<?>> REGISTER = DeferredRegister
            .create(Registries.DATA_COMPONENT_TYPE, FantasyTechnology.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FantasyPatternData>> FANTASY_PATTERN_DATA = REGISTER
            .register("fantasy_pattern_data",
                    () -> DataComponentType.<FantasyPatternData>builder()
                            .persistent(FantasyPatternData.CODEC)
                            .networkSynchronized(FantasyPatternData.STREAM_CODEC)
                            .build());

    /** Forces class loading so the static registrations above run. */
    public static void init() {
    }
}
