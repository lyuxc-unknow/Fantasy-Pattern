package cn.lyxc.fantasytechnology.integration.aelis;

import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import org.jetbrains.annotations.Nullable;

/// Decides whether an Applied Enhancements pattern-barrier reason is the one its planner derives from a fantasy
/// pattern, which is what lets {@code AelisPlanner} treat that pattern as known instead of handing it to AE2-VM.
///
/// Applied Enhancements builds the reason as {@code "unknown_pattern_type:" + details.getClass().getName()}, so the
/// classification is pinned to the pattern's runtime class. Matching that same runtime name - rather than comparing
/// against {@link FantasyCraftingPattern} by identity - keeps a subclassed fantasy pattern working, and turns "what
/// string does AE actually produce" into an exact contract that can be asserted in a unit test.
public final class AelisPatternBarrier {

    /// The prefix Applied Enhancements uses for a pattern type its planner does not recognise natively. It is
    /// deliberately not part of AE's {@code requiresImmediateFallback} set, which holds
    /// {@code missing_pattern_details}, {@code missing_primary_output} and {@code unsupported_pattern_type:}.
    public static final String UNKNOWN_PATTERN_TYPE_PREFIX = "unknown_pattern_type:";

    private AelisPatternBarrier() {
    }

    /// Whether {@code barrierReason} is exactly the reason Applied Enhancements derives from {@code detailsClass}, and
    /// {@code detailsClass} is a fantasy pattern.
    ///
    /// The reason has to match the runtime class name character for character: a reason for a different pattern type,
    /// or the prefix on its own, must not clear the barrier.
    public static boolean matches(@Nullable String barrierReason, @Nullable Class<?> detailsClass) {
        return detailsClass != null
                && FantasyCraftingPattern.class.isAssignableFrom(detailsClass)
                && (UNKNOWN_PATTERN_TYPE_PREFIX + detailsClass.getName()).equals(barrierReason);
    }
}
