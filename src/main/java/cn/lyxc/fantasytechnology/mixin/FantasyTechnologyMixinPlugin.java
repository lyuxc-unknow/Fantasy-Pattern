package cn.lyxc.fantasytechnology.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/// Steps aside where OmniSequence-Transfinite already covers the same ground.
///
/// This mod's crafting acceleration is adapted from OmniSequence, so with both installed two mixins would overlap in
/// ways that are not merely redundant:
///
/// - `CraftingCpuLogicMixin` wraps AE2's pattern extraction. Chained with OmniSequence's, whichever runs on the
///   outside would take the other's already-expanded N-craft holder for a single craft and expand it again.
/// - `AECraftingPatternInputMixin` is the same cache OmniSequence ships, down to the fields it adds.
///
/// The aggregated planner is left alone: it triggers on this mod's own block, OmniSequence's triggers on theirs, and
/// their wrappers nest correctly - whichever aggregates first simply never calls through to the other.
public class FantasyTechnologyMixinPlugin implements IMixinConfigPlugin {

    private static final String OMNISEQUENCE_MOD_ID = "molecularmanipulator";
    private static final String OMNISEQUENCE_MAIN_CLASS = "com.atir.molecularmanipulator.MolecularManipulator";

    private static final Set<String> OMNISEQUENCE_OWNED_MIXINS = Set.of(
            "cn.lyxc.fantasytechnology.mixin.CraftingCpuLogicMixin",
            "cn.lyxc.fantasytechnology.mixin.AECraftingPatternInputMixin");

    private static Boolean omniSequencePresent;

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

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !OMNISEQUENCE_OWNED_MIXINS.contains(mixinClassName) || !isOmniSequenceLoaded();
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
