package cn.lyxc.fantasytechnology.integration.aelis;

import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AelisPatternBarrierTest {

    private static final String PREFIX = AelisPatternBarrier.UNKNOWN_PATTERN_TYPE_PREFIX;
    private static final String OWN_BARRIER = PREFIX + FantasyCraftingPattern.class.getName();

    @Test
    void matchesTheReasonAppliedEnhancementsDerivesFromAFantasyPattern() {
        // Applied Enhancements builds this as "unknown_pattern_type:" + details.getClass().getName().
        assertTrue(AelisPatternBarrier.matches(OWN_BARRIER, FantasyCraftingPattern.class));
    }

    @Test
    void matchesASubclassUsingItsOwnRuntimeName() {
        assertTrue(AelisPatternBarrier.matches(PREFIX + SubclassedFantasyPattern.class.getName(),
                SubclassedFantasyPattern.class));
    }

    @Test
    void ignoresBarrierReasonsThatAreNotTheFantasyOne() {
        assertFalse(AelisPatternBarrier.matches(null, FantasyCraftingPattern.class));
        assertFalse(AelisPatternBarrier.matches("missing_pattern_details", FantasyCraftingPattern.class));
        assertFalse(AelisPatternBarrier.matches("quantity_limited_pattern", FantasyCraftingPattern.class));
        assertFalse(AelisPatternBarrier.matches("unsupported_pattern_type:whatever", FantasyCraftingPattern.class));
        // AE always appends the runtime class name, so the bare prefix and near misses must not clear the barrier.
        assertFalse(AelisPatternBarrier.matches(PREFIX, FantasyCraftingPattern.class));
        assertFalse(AelisPatternBarrier.matches(OWN_BARRIER + " ", FantasyCraftingPattern.class));
        assertFalse(AelisPatternBarrier.matches(PREFIX + "cn.lyxc.fantasytechnology.crafting.SomethingElse",
                FantasyCraftingPattern.class));
    }

    @Test
    void ignoresClassesThatAreNotFantasyPatterns() {
        assertFalse(AelisPatternBarrier.matches(PREFIX + String.class.getName(), String.class));
        assertFalse(AelisPatternBarrier.matches(OWN_BARRIER, null));
        assertFalse(AelisPatternBarrier.matches(OWN_BARRIER, String.class));
    }

    /// Never instantiated - only its {@code Class} is used, to prove a fantasy pattern subclass is still recognised
    /// once the reason is matched against the runtime name. {@link FantasyCraftingPattern} has a single constructor
    /// that dereferences its arguments, so this declaration exists purely as a type.
    private static final class SubclassedFantasyPattern extends FantasyCraftingPattern {
        private SubclassedFantasyPattern() {
            super(null, null);
        }
    }
}
