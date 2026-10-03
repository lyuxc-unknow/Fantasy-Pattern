package cn.lyxc.fantasytechnology.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Guards the two decisions {@link FantasyTechnologyMixinPlugin} makes: how each configured mixin is protected, and
/// whether an optional integration is applied.
///
/// The config-coverage test is why the plugin keeps {@link FantasyTechnologyMixinPlugin#unconditionalMixins()}: the main
/// mixin config is {@code required}, so a mixin that nobody decided how to protect would fail class loading rather than
/// be skipped, and this test makes that decision explicit instead of leaving it to whichever file was edited last. It
/// reads both of this mod's mixin configs, because the client mixins are protected by
/// {@link FantasyTechnologyMixinPlugin#targetTolerantMixins()} in name even though their own config - which declares no
/// plugin - tolerates an absent target on its own.
class FantasyTechnologyMixinPluginTest {

    private static final String TRINITY_MIXIN = "cn.lyxc.fantasytechnology.mixin.TrinityCountedCraftingProviderMixin";
    private static final String ANNIHILATION_TARGET =
            "cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity";

    @Test
    void everyConfiguredMixinDeclaresHowItIsProtected() {
        Set<String> declared = new HashSet<>();
        declared.addAll(FantasyTechnologyMixinPlugin.gatedMixins().keySet());
        declared.addAll(FantasyTechnologyMixinPlugin.typeGatedMixins().keySet());
        declared.addAll(FantasyTechnologyMixinPlugin.targetTolerantMixins());
        declared.addAll(FantasyTechnologyMixinPlugin.unconditionalMixins());

        Set<String> configured = configuredMixins();
        assertFalse(configured.isEmpty(), "the mixin configs should list at least one mixin");
        for (String mixin : configured) {
            assertTrue(declared.contains(mixin),
                    mixin + " is in a mixin config but no gate, type gate, target tolerance or unconditional"
                            + " declaration covers it");
        }
    }

    @Test
    void dataEnergisticsIntegrationNeedsBothTheModAndItsCountedDispatchInterface() {
        assertTrue(shouldApply(TRINITY_MIXIN, true, true), "both present: the integration applies");
        assertFalse(shouldApply(TRINITY_MIXIN, false, true), "mod absent: the integration is dropped");
        assertFalse(shouldApply(TRINITY_MIXIN, true, false),
                "the interface moved or was renamed: the integration is dropped instead of failing to apply");
    }

    @Test
    void targetTolerantMixinsFollowTheirTarget() {
        String slots = "cn.lyxc.fantasytechnology.mixin.client.PatternSlotMixin";
        assertFalse(shouldApply(slots, false, false), "an absent target drops a target-tolerant mixin");
        assertTrue(shouldApply(slots, false, true), "a present target keeps it, even with no optional mod loaded");
    }

    @Test
    void unconditionalMixinsApplyWithoutAnyOptionalMod() {
        assertTrue(shouldApply("cn.lyxc.fantasytechnology.mixin.PatternProviderLogicMixin", false, false));
    }

    private static boolean shouldApply(String mixinClass, boolean modLoaded, boolean typePresent) {
        FantasyTechnologyMixinPlugin plugin = new FantasyTechnologyMixinPlugin(modId -> modLoaded,
                className -> typePresent);
        return plugin.shouldApplyMixin(ANNIHILATION_TARGET, mixinClass);
    }

    /// Every mixin this mod configures, as a fully qualified name, using each config's own package.
    private static Set<String> configuredMixins() {
        Set<String> mixins = new HashSet<>();
        for (String config : List.of("fantasy_technology.mixins.json", "fantasy_technology.client.mixins.json")) {
            mixins.addAll(mixinsIn(config));
        }
        return mixins;
    }

    private static Set<String> mixinsIn(String resource) {
        try (InputStream stream = FantasyTechnologyMixinPluginTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " has to be on the test class path");
            JsonObject config = JsonParser
                    .parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            String packageName = config.get("package").getAsString();
            Set<String> mixins = new HashSet<>();
            for (String section : List.of("mixins", "client")) {
                JsonArray declared = config.getAsJsonArray(section);
                if (declared == null) {
                    continue;
                }
                for (JsonElement element : declared) {
                    mixins.add(packageName + "." + element.getAsString());
                }
            }
            return mixins;
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + resource, exception);
        }
    }
}
