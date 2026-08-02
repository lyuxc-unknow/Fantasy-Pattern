package cn.lyxc.fantasytechnology.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;

public final class FTBlockEntities {
    private FTBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister
            .create(Registries.BLOCK_ENTITY_TYPE, FantasyTechnology.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FantasyAnnihilationBlockEntity>> FANTASY_ANNIHILATION = REGISTER
            .register("fantasy_annihilation",
                    () -> BlockEntityType.Builder
                            .of(FantasyAnnihilationBlockEntity::new, FTBlocks.FANTASY_ANNIHILATION.get())
                            .build(null));

    /// Forces class loading so the static registrations above run.
    public static void init() {
    }
}
