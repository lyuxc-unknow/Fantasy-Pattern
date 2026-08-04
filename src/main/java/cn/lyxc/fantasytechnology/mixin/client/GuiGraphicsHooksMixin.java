package cn.lyxc.fantasytechnology.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.hooks.GuiGraphicsHooks;

import cn.lyxc.fantasytechnology.item.FantasyPatternItem;

/// Extends AE2's own rendering hook ({@link GuiGraphicsHooks}, which is what makes AE2's encoded patterns render as
/// their first output in inventories while shift is held) to fantasy recombination patterns as well.
@Mixin(GuiGraphicsHooks.class)
public abstract class GuiGraphicsHooksMixin {

    @Shadow
    @Final
    private static ThreadLocal<ItemStack> OVERRIDING_FOR;

    @Shadow
    private static void renderInstead(GuiGraphics guiGraphics, LivingEntity entity, Level level, ItemStack stack,
            int x, int y, int seed, int slot) {
    }

    @Inject(method = "onRenderGuiItem", at = @At("HEAD"), cancellable = true, require = 0)
    private static void fantasyTechnology$renderPatternOutputOnShift(GuiGraphics guiGraphics, LivingEntity entity,
            Level level, ItemStack stack, int x, int y, int seed, int slot,
            CallbackInfoReturnable<Boolean> cir) {
        if (!(stack.getItem() instanceof FantasyPatternItem)) {
            return;
        }
        // Mirror the conditions of AE2's own branch: don't re-enter while rendering the substitute, and only when
        // the player holds shift.
        if (OVERRIDING_FOR.get() == stack || !Screen.hasShiftDown() || level == null) {
            return;
        }
        ItemStack output = FantasyPatternItem.getOutput(stack);
        if (output.isEmpty()) {
            return;
        }
        renderInstead(guiGraphics, entity, level, output, x, y, seed, slot);
        cir.setReturnValue(true);
    }
}
