package cn.lyxc.fantasytechnology.mixin;

import appeng.api.crafting.IPatternDetails;
import cn.lyxc.fantasytechnology.integration.aelis.AelisPatternBarrier;
import com.github.appliedenhancements.crafting.aelis.AelisPlanner;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// Clears Aelis's unknown-pattern barrier for a fantasy pattern so its planner can apply the attempt.
///
/// Applied Enhancements' {@code AelisPlanner.getPatternBarrierReason} returns {@code null} for the AE2 pattern types it
/// recognises and {@code "unknown_pattern_type:" + details.getClass().getName()} for everything else. A non-null reason
/// makes {@code isKnownDeterministicPattern} false, so the candidate attempt ends as {@code FALLBACK} - what the confirm
/// screen shows as the gold AE2 result - and the request is handed to AE2-VM. Returning {@code null} keeps the pattern on
/// Aelis's known-pattern path, which is the pink result.
///
/// Because {@code unknown_pattern_type:} is not part of AE's {@code requiresImmediateFallback} set, what this clears is
/// the planner's "not deterministic" classification, not a hard hand-off. That classification is consulted from seven
/// call sites in {@code AelisPlanner}, including its batch-validity checks, so a single-craft smoke test does not cover
/// the blast radius of this override.
///
/// Applied Enhancements is an optional dependency and {@code getPatternBarrierReason} is a private method, so the
/// injector is declared optional ({@code require = 0}): if a future release moves or renames it, this fix stops applying
/// and Aelis keeps its default behaviour rather than failing mixin application and crashing startup. The presence of
/// Applied Enhancements is checked by {@link FantasyTechnologyMixinPlugin}, which stops this mixin from being applied at
/// all when the mod is missing.
@Mixin(value = AelisPlanner.class, remap = false)
public abstract class AelisPlannerMixin {

    @ModifyReturnValue(method = "getPatternBarrierReason", at = @At("RETURN"), require = 0)
    @Nullable
    private static String fantasyTechnology$allowFantasyPattern(
            @Nullable String original, IPatternDetails details) {
        return AelisPatternBarrier.matches(original, details == null ? null : details.getClass())
                ? null
                : original;
    }
}
