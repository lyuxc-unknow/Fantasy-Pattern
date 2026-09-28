package cn.lyxc.fantasytechnology.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// Decides which of this mod's mixins may be applied, so that a mixin is never applied against a class that is not
/// there - whether because the optional mod it integrates with is missing, or because an AE2 update moved the internal
/// it targets.
///
/// Gates are keyed by mixin class name, not by target class. Two of the gated mixins target
/// {@code FantasyAnnihilationBlockEntity}, which is this mod's own class and is always present, so a target-keyed gate
/// would be meaningless for them - and would wrongly force any future ungated mixin on that same class to require
/// OmniSequence.
///
/// The mixin config is required, so a mixin whose target cannot be found would fail class loading rather than be
/// skipped. That is what the functional mixins want, but not the cosmetic client ones, which target AE2 internals that
/// an update may move; {@link #TOLERATE_ABSENT_TARGET} names those, and
/// {@code FantasyTechnologyMixinPluginTest} asserts that every mixin in the mixin config is gated here, listed as
/// target-tolerant here, or explicitly declared unconditional. Renaming a mixin class therefore cannot silently drop
/// its protection.
public final class FantasyTechnologyMixinPlugin implements IMixinConfigPlugin {

    static final String OMNI_MOD_ID = "molecularmanipulator";
    static final String ENHANCEMENTS_MOD_ID = "appliedenhancements";

    /// Mixin class name to the id of the mod that has to be loaded for that mixin to be applied.
    private static final Map<String, String> GATED_MIXINS = Map.of(
            "cn.lyxc.fantasytechnology.mixin.MolecularBatchCraftingProviderMixin", OMNI_MOD_ID,
            "cn.lyxc.fantasytechnology.mixin.OmniBatchDispatchMixin", OMNI_MOD_ID,
            "cn.lyxc.fantasytechnology.mixin.AelisPlannerMixin", ENHANCEMENTS_MOD_ID);

    /// Cosmetic client mixins whose target is an AE2 internal an update may move. They are dropped when their target
    /// is not on the class path, so a moved target costs the pattern-slot icons rather than the whole game. They are
    /// listed individually on purpose: the functional mixins must never be dropped this way, because silently losing
    /// them is worse than failing.
    private static final Set<String> TOLERATE_ABSENT_TARGET = Set.of(
            "cn.lyxc.fantasytechnology.mixin.GuiGraphicsHooksMixin",
            "cn.lyxc.fantasytechnology.mixin.PatternSlotMixin");

    /// How this plugin asks whether a mod is loaded. Injectable so both outcomes can be tested without FML.
    @FunctionalInterface
    interface ModPresence {
        boolean isLoaded(String modId);
    }

    private final ModPresence modPresence;
    private final Map<String, Boolean> presenceCache = new ConcurrentHashMap<>();

    public FantasyTechnologyMixinPlugin() {
        this(FantasyTechnologyMixinPlugin::isModLoaded);
    }

    /// Test seam: lets {@code shouldApplyMixin} be exercised for a mod being present and absent.
    FantasyTechnologyMixinPlugin(ModPresence modPresence) {
        this.modPresence = modPresence;
    }

    /// The gate table, exposed so a test can check it still covers every mixin in the config.
    static Map<String, String> gatedMixins() {
        return GATED_MIXINS;
    }

    /// The target-tolerant mixins, exposed so a test can check it still covers every mixin in the config.
    static Set<String> targetTolerantMixins() {
        return TOLERATE_ABSENT_TARGET;
    }

    private static boolean isModLoaded(String modId) {
        return LoadingModList.get().getModFileById(modId) != null;
    }

    /// Whether {@code className} is present on this mod's class path, looked up as a resource rather than resolved:
    /// asking the class loader for the class itself would define the very class Mixin is in the middle of preparing
    /// for this mixin.
    private static boolean isClassPresent(String className) {
        try {
            return FantasyTechnologyMixinPlugin.class.getClassLoader()
                    .getResource(className.replace('.', '/') + ".class") != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isPresent(String modId) {
        return presenceCache.computeIfAbsent(modId, id -> {
            try {
                return modPresence.isLoaded(id);
            } catch (LinkageError | RuntimeException ignored) {
                // No FML mod list (unit tests) or a half-initialised loader: treat the mod as absent, so the
                // optional mixins are skipped instead of failing class loading.
                return false;
            }
        });
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String requiredMod = GATED_MIXINS.get(mixinClassName);
        if (requiredMod != null && !isPresent(requiredMod)) {
            return false;
        }
        return !TOLERATE_ABSENT_TARGET.contains(mixinClassName) || isClassPresent(targetClassName);
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
