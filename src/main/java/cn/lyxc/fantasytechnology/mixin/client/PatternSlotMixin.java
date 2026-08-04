package cn.lyxc.fantasytechnology.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.client.gui.me.patternaccess.PatternSlot;
import appeng.menu.slot.AppEngSlot;

import cn.lyxc.fantasytechnology.item.FantasyPatternItem;

/// Makes the pattern access terminal show the first output of a fantasy recombination pattern in its pattern slots,
/// exactly as it already does for AE2's own encoded patterns.
///
/// Injected at the head rather than overwriting the method: AE2's own body still runs for everything that is not a
/// fantasy pattern, so its encoded-pattern branch keeps working, other mods touching the same method are not shut
/// out, and a change on AE2's side cannot be silently undone by a stale copy living here.
@Mixin(PatternSlot.class)
public abstract class PatternSlotMixin extends AppEngSlot {

    private PatternSlotMixin(InternalInventory inv, int index) {
        super(inv, index);
    }

    @Inject(method = "getDisplayStack", at = @At("HEAD"), cancellable = true, require = 0)
    private void fantasyTechnology$showPatternOutput(CallbackInfoReturnable<ItemStack> cir) {
        if (!isRemote()) {
            return;
        }
        ItemStack stack = super.getDisplayStack();
        if (stack.getItem() instanceof FantasyPatternItem) {
            ItemStack output = FantasyPatternItem.getOutput(stack);
            if (!output.isEmpty()) {
                cir.setReturnValue(output);
            }
        }
    }
}
