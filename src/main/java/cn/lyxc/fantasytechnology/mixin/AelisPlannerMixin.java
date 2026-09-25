package cn.lyxc.fantasytechnology.mixin;

import appeng.api.crafting.IPatternDetails;
import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import com.github.appliedenhancements.crafting.aelis.AelisPlanner;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AelisPlanner.class, remap = false)
public abstract class AelisPlannerMixin {

    @Unique
    private static final String FANTASY_PATTERN_BARRIER =
            "unknown_pattern_type:" + FantasyCraftingPattern.class.getName();

    @ModifyReturnValue(method = "getPatternBarrierReason", at = @At("RETURN"))
    @Nullable
    private static String fantasyTechnology$allowFantasyPattern(
            @Nullable String original, IPatternDetails details) {
        return details != null && details.getClass() == FantasyCraftingPattern.class
                && FANTASY_PATTERN_BARRIER.equals(original) ? null : original;
    }
}
