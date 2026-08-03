package cn.lyxc.fantasytechnology.registry;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import com.mojang.datafixers.DSL;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FTBlockEntities {
    private FTBlockEntities() {
    }

    private static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister
            .create(Registries.BLOCK_ENTITY_TYPE, FantasyTechnology.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FantasyAnnihilationBlockEntity>> FANTASY_ANNIHILATION = REGISTER
            .register("fantasy_annihilation",
                    () -> BlockEntityType.Builder
                            .of(FantasyAnnihilationBlockEntity::new, FTBlocks.FANTASY_ANNIHILATION.get())
                            .build(DSL.emptyPartType()));

    /// Forces class loading so the static registrations above run.
    public static void init(IEventBus bus) {
        REGISTER.register(bus);
    }
}
