package cn.lyxc.fantasytechnology.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FantasyTechnologyMixinPluginTest {

    private static final String MIXIN_PACKAGE = "cn.lyxc.fantasytechnology.mixin.";
    private static final String MIXIN_CONFIG = "fantasy_technology.mixins.json";

    private static final String AELIS_MIXIN = MIXIN_PACKAGE + "AelisPlannerMixin";
    private static final String OMNI_MIXIN = MIXIN_PACKAGE + "OmniBatchDispatchMixin";
    private static final String PATTERN_SLOT_MIXIN = MIXIN_PACKAGE + "PatternSlotMixin";
    private static final String GUI_GRAPHICS_HOOKS_MIXIN = MIXIN_PACKAGE + "GuiGraphicsHooksMixin";
    private static final String UNGATED_MIXIN = MIXIN_PACKAGE + "PatternProviderLogicMixin";

    private static final String AELIS_TARGET = "com.github.appliedenhancements.crafting.aelis.AelisPlanner";
    private static final String OMNI_TARGET = "cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity";
    private static final String PATTERN_PROVIDER_TARGET = "appeng.helpers.patternprovider.PatternProviderLogic";
    private static final String PATTERN_SLOT_TARGET = "appeng.client.gui.me.patternaccess.PatternSlot";
    private static final String GUI_GRAPHICS_HOOKS_TARGET = "appeng.hooks.GuiGraphicsHooks";

    /// Mixins that are deliberately unconditional, because the class they target is always present and nothing needs
    /// to be decided. Listing a mixin here is a decision, not a default.
    private static final Set<String> UNGATED_MIXINS = Set.of(
            MIXIN_PACKAGE + "PatternProviderLogicMixin");

    private static FantasyTechnologyMixinPlugin pluginSeeing(String... loadedMods) {
        Set<String> loaded = Set.of(loadedMods);
        return new FantasyTechnologyMixinPlugin(loaded::contains);
    }

    @Test
    void appliesAelisMixinWhenAppliedEnhancementsIsLoaded() {
        var plugin = pluginSeeing(FantasyTechnologyMixinPlugin.ENHANCEMENTS_MOD_ID);

        assertTrue(plugin.shouldApplyMixin(AELIS_TARGET, AELIS_MIXIN));
    }

    @Test
    void skipsAelisMixinWhenAppliedEnhancementsIsAbsent() {
        var plugin = pluginSeeing();

        assertFalse(plugin.shouldApplyMixin(AELIS_TARGET, AELIS_MIXIN));
    }

    @Test
    void gatesOmniMixinsOnOmniSequenceBeingLoaded() {
        assertFalse(pluginSeeing().shouldApplyMixin(OMNI_TARGET, OMNI_MIXIN));
        assertFalse(pluginSeeing(FantasyTechnologyMixinPlugin.ENHANCEMENTS_MOD_ID)
                .shouldApplyMixin(OMNI_TARGET, OMNI_MIXIN));
        assertTrue(pluginSeeing(FantasyTechnologyMixinPlugin.OMNI_MOD_ID)
                .shouldApplyMixin(OMNI_TARGET, OMNI_MIXIN));
    }

    @Test
    void appliesUngatedMixinsWhateverIsLoaded() {
        assertTrue(pluginSeeing().shouldApplyMixin(PATTERN_PROVIDER_TARGET, UNGATED_MIXIN));
        assertTrue(pluginSeeing(FantasyTechnologyMixinPlugin.OMNI_MOD_ID, FantasyTechnologyMixinPlugin.ENHANCEMENTS_MOD_ID)
                .shouldApplyMixin(PATTERN_PROVIDER_TARGET, UNGATED_MIXIN));
    }

    @Test
    void dropsTheCosmeticClientMixinsWhenTheirAe2TargetIsGone() {
        var plugin = pluginSeeing();

        assertTrue(plugin.shouldApplyMixin(PATTERN_SLOT_TARGET, PATTERN_SLOT_MIXIN));
        assertFalse(plugin.shouldApplyMixin(PATTERN_SLOT_TARGET + "Moved", PATTERN_SLOT_MIXIN));
        assertTrue(plugin.shouldApplyMixin(GUI_GRAPHICS_HOOKS_TARGET, GUI_GRAPHICS_HOOKS_MIXIN));
        assertFalse(plugin.shouldApplyMixin(GUI_GRAPHICS_HOOKS_TARGET + "Moved", GUI_GRAPHICS_HOOKS_MIXIN));
    }

    @Test
    void neverDropsAFunctionalMixinOverATargetLookup() {
        // Only the cosmetic client mixins are target-tolerant. If the functional ones followed, a lookup that went
        // wrong would silently stop them applying - worse than failing loudly.
        assertTrue(pluginSeeing(FantasyTechnologyMixinPlugin.ENHANCEMENTS_MOD_ID)
                .shouldApplyMixin("does.not.Exist", AELIS_MIXIN));
        assertTrue(pluginSeeing().shouldApplyMixin("does.not.Exist", UNGATED_MIXIN));
    }

    @Test
    void looksEachModUpOnlyOnce() {
        AtomicInteger lookups = new AtomicInteger();
        var plugin = new FantasyTechnologyMixinPlugin(modId -> {
            lookups.incrementAndGet();
            return false;
        });

        plugin.shouldApplyMixin(AELIS_TARGET, AELIS_MIXIN);
        plugin.shouldApplyMixin(AELIS_TARGET, AELIS_MIXIN);
        assertEquals(1, lookups.get(), "the Applied Enhancements gate should resolve its mod id once");

        plugin.shouldApplyMixin(OMNI_TARGET, OMNI_MIXIN);
        plugin.shouldApplyMixin(OMNI_TARGET, OMNI_MIXIN);
        assertEquals(2, lookups.get(), "the OmniSequence gate resolves its own mod id once, on top of the cached one");
    }

    @Test
    void treatsALookupThatFailsAsTheModBeingAbsent() {
        var unlinked = new FantasyTechnologyMixinPlugin(modId -> {
            throw new NoClassDefFoundError("net/neoforged/fml/loading/LoadingModList");
        });
        assertFalse(unlinked.shouldApplyMixin(AELIS_TARGET, AELIS_MIXIN));

        var notInitialised = new FantasyTechnologyMixinPlugin(modId -> {
            throw new IllegalStateException("mod list is not available yet");
        });
        assertFalse(notInitialised.shouldApplyMixin(OMNI_TARGET, OMNI_MIXIN));
    }

    @Test
    void readsTheRealModListAsAbsentRatherThanThrowing() {
        // Guards the production lookup path: a plain JVM has no FML mod list, and that has to degrade to "absent" so
        // optional mixins are skipped rather than failing class loading.
        var plugin = new FantasyTechnologyMixinPlugin();

        assertFalse(plugin.shouldApplyMixin(AELIS_TARGET, AELIS_MIXIN));
        assertTrue(plugin.shouldApplyMixin(PATTERN_PROVIDER_TARGET, UNGATED_MIXIN));
    }

    @Test
    void everyMixinInTheConfigIsAccountedForByThePlugin() throws IOException {
        Set<String> configured = mixinClassesIn(MIXIN_CONFIG);

        assertFalse(configured.isEmpty(), "no mixins found in " + MIXIN_CONFIG);
        for (String mixin : configured) {
            boolean accountedFor = FantasyTechnologyMixinPlugin.gatedMixins().containsKey(mixin)
                    || FantasyTechnologyMixinPlugin.targetTolerantMixins().contains(mixin)
                    || UNGATED_MIXINS.contains(mixin);
            assertTrue(accountedFor, mixin + " is neither gated nor target-tolerant in FantasyTechnologyMixinPlugin,"
                    + " and is not listed as deliberately unconditional in this test, so the protection it needs"
                    + " could be dropped silently. Gate it, make it target-tolerant, or add it to UNGATED_MIXINS to"
                    + " state that it needs no protection.");
        }
        for (String gated : FantasyTechnologyMixinPlugin.gatedMixins().keySet()) {
            assertTrue(configured.contains(gated),
                    gated + " is gated but does not appear in " + MIXIN_CONFIG);
        }
        for (String tolerant : FantasyTechnologyMixinPlugin.targetTolerantMixins()) {
            assertTrue(configured.contains(tolerant),
                    tolerant + " is target-tolerant but does not appear in " + MIXIN_CONFIG);
        }
    }

    /// The fully qualified mixin class names listed in a mixin config shipped in this mod's resources, taken from both
    /// the common and the client section.
    private static Set<String> mixinClassesIn(String configFile) throws IOException {
        InputStream in = FantasyTechnologyMixinPlugin.class.getClassLoader().getResourceAsStream(configFile);
        assertNotNull(in, configFile + " is not on the test classpath");
        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);

        Set<String> names = new LinkedHashSet<>();
        for (String section : List.of("mixins", "client")) {
            Matcher array = Pattern.compile("\"" + section + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
            assertTrue(array.find(), "no " + section + " array in " + configFile);
            Matcher entry = Pattern.compile("\"([^\"]+)\"").matcher(array.group(1));
            while (entry.find()) {
                names.add(MIXIN_PACKAGE + entry.group(1));
            }
        }
        return names;
    }
}
