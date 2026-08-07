package cn.lyxc.fantasytechnology.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/// Steps aside where OmniSequence-Transfinite already covers the same ground.
///
/// This mod's crafting acceleration is adapted from OmniSequence, so with both installed some mixins would overlap
/// in ways that are not merely redundant:
///
/// - `AECraftingPatternInputMixin` and `KeyCounterMixin` duplicate OmniSequence's cache and overflow handling.
/// - The MAX_FAST calculation and crafting-tree accessors are the local fallback copy of OmniSequence's planner.
/// - The crafting-job accessors only support this mod's fallback CPU dispatcher.
///
/// `CraftingCpuLogicMixin` is the local fallback for batching when OmniSequence is absent. Both implementations
/// wrap the same extraction and push call sites and maintain their own task/waiting-for accounting, so nesting them
/// is not composable: an outer wrapper can replace the expanded input holder and make the inner wrapper lose its
/// batch context. OmniSequence owns this integration whenever it is installed; this mod then opts into its public
/// provider SPI to receive batches without wrapping the CPU a second time.
///
/// The aggregated planner is left alone too: it triggers on this mod's own patterns, OmniSequence's triggers on
/// theirs, and their wrappers nest correctly - whichever aggregates first simply never calls through to the other.
public class FantasyTechnologyMixinPlugin implements IMixinConfigPlugin {

    private static final String OMNISEQUENCE_MOD_ID = "molecularmanipulator";
    private static final String OMNISEQUENCE_MAIN_CLASS = "com.atir.molecularmanipulator.MolecularManipulator";
    private static final String OMNISEQUENCE_BATCH_API_CLASS =
            "com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingProvider";

    private static final Set<String> OMNISEQUENCE_OWNED_MIXINS = Set.of(
            "cn.lyxc.fantasytechnology.mixin.AECraftingPatternInputMixin",
            "cn.lyxc.fantasytechnology.mixin.CraftingCpuLogicMixin",
            "cn.lyxc.fantasytechnology.mixin.CraftingTaskProgressAccessor",
            "cn.lyxc.fantasytechnology.mixin.ExecutingCraftingJobAccessor",
            "cn.lyxc.fantasytechnology.mixin.KeyCounterMixin",
            "cn.lyxc.fantasytechnology.mixin.OmniCraftingCalculationMixin",
            "cn.lyxc.fantasytechnology.mixin.OmniCraftingTreeNodeAccessor",
            "cn.lyxc.fantasytechnology.mixin.OmniCraftingTreeProcessAccessor");

    private static final String OMNISEQUENCE_API_MIXIN =
            "cn.lyxc.fantasytechnology.mixin.OmniBatchCraftingProviderMixin";

    private static Boolean omniSequencePresent;
    private static Boolean omniBatchApiPresent;

    /// Resolved on first use. This runs during mixin prepare, before any mod constructor, so it cannot depend on
    /// initialization order - the loading mod list is the one thing already populated at that point.
    private static boolean isOmniSequenceLoaded() {
        if (omniSequencePresent == null) {
            omniSequencePresent = detectOmniSequence();
        }
        return omniSequencePresent;
    }

    private static boolean detectOmniSequence() {
        try {
            return LoadingModList.get().getModFileById(OMNISEQUENCE_MOD_ID) != null;
        } catch (LinkageError | RuntimeException ignored) {
            // Fall through to a plain classpath probe if FML ever moves this API.
        }
        try {
            Class.forName(OMNISEQUENCE_MAIN_CLASS, false, FantasyTechnologyMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isOmniBatchApiAvailable() {
        if (omniBatchApiPresent == null) {
            try {
                Class.forName(OMNISEQUENCE_BATCH_API_CLASS, false,
                        FantasyTechnologyMixinPlugin.class.getClassLoader());
                omniBatchApiPresent = true;
            } catch (ClassNotFoundException | LinkageError ignored) {
                omniBatchApiPresent = false;
            }
        }
        return omniBatchApiPresent;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (OMNISEQUENCE_OWNED_MIXINS.contains(mixinClassName)) {
            return !isOmniSequenceLoaded();
        }
        if (OMNISEQUENCE_API_MIXIN.equals(mixinClassName)) {
            return isOmniBatchApiAvailable();
        }
        return true;
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
