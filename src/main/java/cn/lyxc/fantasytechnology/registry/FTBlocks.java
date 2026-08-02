package cn.lyxc.fantasytechnology.registry;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.block.FantasyAnnihilationBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.function.Supplier;

public final class FTBlocks {
    private FTBlocks() {
    }

    // The fantasy encoding terminal is no longer a block: it is an AE2 cable part, registered in FTItems.

    public static final DeferredBlock<FantasyAnnihilationBlock> FANTASY_ANNIHILATION = registerBlockWithItem(
            "fantasy_annihilation",
            () -> new FantasyAnnihilationBlock(machineProperties()));

    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .sound(SoundType.METAL)
                .strength(3.5F, 6.0F);
    }

    private static <T extends Block> DeferredBlock<T> registerBlockWithItem(String name, Supplier<T> block) {
        DeferredBlock<T> holder = FantasyTechnology.BLOCKS.register(name, block);
        FantasyTechnology.ITEMS.register(name, () -> new BlockItem(holder.get(), new Item.Properties()));
        return holder;
    }

    /// Forces class loading so the static registrations above run.
    public static void init() {
    }
}
