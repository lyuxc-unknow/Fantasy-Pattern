package cn.lyxc.fantasytechnology.block;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.menu.FantasyAnnihilationMenu;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/// The fantasy annihilation block ("幻梦寂灭"). See {@link FantasyAnnihilationBlockEntity} for the crafting logic.
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FantasyAnnihilationBlock extends Block implements EntityBlock {

    public FantasyAnnihilationBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FantasyAnnihilationBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Record the placing player as the node owner so the machine is allowed to extract/insert
        // on networks protected by a security terminal.
        if (placer instanceof Player player
                && level.getBlockEntity(pos) instanceof FantasyAnnihilationBlockEntity annihilation) {
            annihilation.getMainNode().setOwningPlayer(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FantasyAnnihilationBlockEntity annihilation) {
            if (!level.isClientSide()) {
                // Opened through AE2's locator mechanism so the client can resolve the block entity back from the
                // menu's network packet.
                MenuOpener.open(FantasyAnnihilationMenu.TYPE, player, MenuLocators.forBlockEntity(annihilation));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof FantasyAnnihilationBlockEntity annihilation) {
            for (int i = 0; i < annihilation.getInternalInventory().size(); i++) {
                var stack = annihilation.getInternalInventory().getStackInSlot(i);
                if (!stack.isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
