package cn.lyxc.fantasytechnology.block;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import cn.lyxc.fantasytechnology.blockentity.FantasyDeviceAccessBlockEntity;
import cn.lyxc.fantasytechnology.menu.FantasyDeviceAccessMenu;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
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

/// The device access block ("设备接入方块"). See {@link FantasyDeviceAccessBlockEntity} for what its contents mean.
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FantasyDeviceAccessBlock extends Block implements EntityBlock {

    public FantasyDeviceAccessBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FantasyDeviceAccessBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Record the placing player as the node owner so the block stays connectable on networks protected by a
        // security terminal.
        if (placer instanceof Player player
                && level.getBlockEntity(pos) instanceof FantasyDeviceAccessBlockEntity device) {
            device.getMainNode().setOwningPlayer(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FantasyDeviceAccessBlockEntity device) {
            if (!level.isClientSide()) {
                // Opened through AE2's locator mechanism so the client can resolve the block entity back from the
                // menu's network packet.
                MenuOpener.open(FantasyDeviceAccessMenu.TYPE, player, MenuLocators.forBlockEntity(device));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof FantasyDeviceAccessBlockEntity device) {
            var inventory = device.getInternalInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                var stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
