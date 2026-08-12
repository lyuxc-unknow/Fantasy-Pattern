package cn.lyxc.fantasytechnology.mixin;

import appeng.api.crafting.IPatternDetails;
import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import com.atir.molecularmanipulator.crafting.maxfast.OmniMaxFastPlanner;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = OmniMaxFastPlanner.class, remap = false)
public abstract class OmniMaxFastPlannerMixin {

    @ModifyReturnValue(method = "getPatternBarrierReason", at = @At("RETURN"))
    @Nullable
    private static String fantasyTechnology$allowFantasyPattern(
            @Nullable String original, IPatternDetails pattern) {
        return pattern instanceof FantasyCraftingPattern ? null : original;
    }
}
