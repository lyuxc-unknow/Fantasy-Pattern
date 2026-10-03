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
/// there - whether because the optional mod it integrates with is missing, or because an update moved an internal the
/// mixin depends on.
///
/// Gates are keyed by mixin class name, not by target class. Three of the gated mixins target
/// {@code FantasyAnnihilationBlockEntity}, which is this mod's own class and is always present, so a target-keyed gate
/// would be meaningless for them - and would wrongly force any future ungated mixin on that same class to require
/// OmniSequence.
///
/// The main mixin config is required, so a mixin whose target cannot be found would fail class loading rather than be
/// skipped. That is what the functional mixins want, but not the cosmetic client ones, which target AE2 internals that
/// an update may move; {@link #TOLERATE_ABSENT_TARGET} names those. Integrating with another mod's internals needs a
/// fourth answer, because those mixins implement an interface of that mod rather than targeting one: a mixin whose
/// required type is gone would otherwise fail startup instead of dropping the integration. {@link #TYPE_GATED_MIXINS}
/// names that type per mixin, and {@link #UNCONDITIONAL_MIXINS} declares the mixins that need no protection at all, so
/// that {@code FantasyTechnologyMixinPluginTest} can assert every mixin in either mixin config is declared by at least
/// one of these four lists. Renaming a mixin class therefore cannot silently drop its protection, and adding a mixin to
/// a config without deciding how it is protected fails the build.
public final class FantasyTechnologyMixinPlugin implements IMixinConfigPlugin {

    static final String OMNI_MOD_ID = "molecularmanipulator";
    static final String ENHANCEMENTS_MOD_ID = "appliedenhancements";
    static final String DATA_ENERGISTICS_MOD_ID = "data_energistics";

    /// Mixin class name to the id of the mod that has to be loaded for that mixin to be applied.
    private static final Map<String, String> GATED_MIXINS = Map.of(
            "cn.lyxc.fantasytechnology.mixin.MolecularBatchCraftingProviderMixin", OMNI_MOD_ID,
            "cn.lyxc.fantasytechnology.mixin.OmniBatchDispatchMixin", OMNI_MOD_ID,
            "cn.lyxc.fantasytechnology.mixin.TrinityCountedCraftingProviderMixin", DATA_ENERGISTICS_MOD_ID,
            "cn.lyxc.fantasytechnology.mixin.AelisPlannerMixin", ENHANCEMENTS_MOD_ID);

    /// Mixin class name to a type of the mod gated above that has to be on the class path for that mixin. These mixins
    /// implement an interface the optional mod owns, so a renamed or moved interface has to drop the integration
    /// instead of failing to apply against a type that no longer exists. Only integrations that name such a type appear
    /// here: a mixin that targets an optional mod's class rather than implementing one of its types is covered by its
    /// mod gate alone.
    private static final Map<String, String> TYPE_GATED_MIXINS = Map.of(
            "cn.lyxc.fantasytechnology.mixin.TrinityCountedCraftingProviderMixin",
            "com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProvider");

    /// Cosmetic client mixins whose target is an AE2 internal an update may move. They are dropped when their target
    /// is not on the class path, so a moved target costs the pattern-slot icons rather than the whole game. They are
    /// listed individually on purpose: the functional mixins must never be dropped this way, because silently losing
    /// them is worse than failing.
    ///
    /// They live in the client mixin config, which declares no plugin and is not required, so Mixin already tolerates a
    /// missing target there; listing them here is what makes that config's decision explicit, and what lets
    /// {@code FantasyTechnologyMixinPluginTest} cover both configs with the same four declarations.
    private static final Set<String> TOLERATE_ABSENT_TARGET = Set.of(
            "cn.lyxc.fantasytechnology.mixin.client.GuiGraphicsHooksMixin",
            "cn.lyxc.fantasytechnology.mixin.client.PatternSlotMixin");

    /// Mixins that are applied whenever their target exists. They target AE2 itself, which is a required dependency, so
    /// there is nothing to gate them on - declaring them here keeps that a decision rather than an omission.
    private static final Set<String> UNCONDITIONAL_MIXINS = Set.of(
            "cn.lyxc.fantasytechnology.mixin.CreativeCellInventoryMixin",
            "cn.lyxc.fantasytechnology.mixin.PatternProviderLogicMixin");

    /// How this plugin asks whether a mod is loaded. Injectable so both outcomes can be tested without FML.
    @FunctionalInterface
    interface ModPresence {
        boolean isLoaded(String modId);
    }

    /// How this plugin asks whether a type is on the class path. Injectable for the same reason as
    /// {@link ModPresence}: a test has to exercise the type gate without the optional mod's jar being on the test
    /// runtime class path.
    @FunctionalInterface
    interface TypePresence {
        boolean isPresent(String className);
    }

    private final ModPresence modPresence;
    private final TypePresence typePresence;
    private final Map<String, Boolean> presenceCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> typePresenceCache = new ConcurrentHashMap<>();

    public FantasyTechnologyMixinPlugin() {
        this(FantasyTechnologyMixinPlugin::isModLoaded, FantasyTechnologyMixinPlugin::isClassPresent);
    }

    /// Test seam: lets {@code shouldApplyMixin} be exercised for a mod being present and absent.
    FantasyTechnologyMixinPlugin(ModPresence modPresence) {
        this(modPresence, FantasyTechnologyMixinPlugin::isClassPresent);
    }

    /// Test seam: adds control over whether a required type exists.
    FantasyTechnologyMixinPlugin(ModPresence modPresence, TypePresence typePresence) {
        this.modPresence = modPresence;
        this.typePresence = typePresence;
    }

    /// The gate table, exposed so a test can check it still covers every mixin in the config.
    static Map<String, String> gatedMixins() {
        return GATED_MIXINS;
    }

    /// The type gate table, exposed so a test can check it still covers every mixin in the config.
    static Map<String, String> typeGatedMixins() {
        return TYPE_GATED_MIXINS;
    }

    /// The target-tolerant mixins, exposed so a test can check it still covers every mixin in the config.
    static Set<String> targetTolerantMixins() {
        return TOLERATE_ABSENT_TARGET;
    }

    /// The mixins that need no gate, exposed so a test can check it still covers every mixin in the config.
    static Set<String> unconditionalMixins() {
        return UNCONDITIONAL_MIXINS;
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

    private boolean isTypePresent(String className) {
        try {
            return typePresenceCache.computeIfAbsent(className, name -> {
                try {
                    return typePresence.isPresent(name);
                } catch (LinkageError | RuntimeException ignored) {
                    return false;
                }
            });
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String requiredMod = GATED_MIXINS.get(mixinClassName);
        if (requiredMod != null && !isPresent(requiredMod)) {
            return false;
        }
        String requiredType = TYPE_GATED_MIXINS.get(mixinClassName);
        if (requiredType != null && !isTypePresent(requiredType)) {
            return false;
        }
        return !TOLERATE_ABSENT_TARGET.contains(mixinClassName) || isTypePresent(targetClassName);
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
