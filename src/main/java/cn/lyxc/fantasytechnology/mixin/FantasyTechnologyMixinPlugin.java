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
/// - `AECraftingPatternInputMixin` is the same `getRemainingKey`/`isValid` cache OmniSequence ships, down to the
///   fields it adds.
/// - `KeyCounterMixin` is the same overflow saturation, and two cancelling HEAD handlers on one method just race to
///   write the same value.
///
/// `CraftingCpuLogicMixin` is deliberately *not* in that list, even though OmniSequence wraps the same extraction
/// and push. It is what dispatches several recipes per push to the fantasy annihilation block, and OmniSequence
/// knows nothing about {@link cn.lyxc.fantasytechnology.integration.ae2.IFantasyBatchCraftingProvider} - standing
/// aside would silently disable batch dispatch for anyone running both mods. The cost is that the two wrappers nest,
/// and whichever ends up on the outside sees an already-expanded holder:
///
/// - expanding it a second time is caught by `fantasyTechnology$alreadyScaled`, which inspects the holder rather
///   than trusting the call order;
/// - re-deriving the CPU's expectation from it is why a batch never rewrites `expectedContainerItems` and corrects
///   {@code job.waitingFor} afterwards instead. An OmniSequence Omni-Computation Core does exactly that re-derive.
///
/// The aggregated planner is left alone too: it triggers on this mod's own patterns, OmniSequence's triggers on
/// theirs, and their wrappers nest correctly - whichever aggregates first simply never calls through to the other.
public class FantasyTechnologyMixinPlugin implements IMixinConfigPlugin {

    private static final String OMNISEQUENCE_MOD_ID = "molecularmanipulator";
    private static final String OMNISEQUENCE_MAIN_CLASS = "com.atir.molecularmanipulator.MolecularManipulator";

    private static final Set<String> OMNISEQUENCE_OWNED_MIXINS = Set.of(
            "cn.lyxc.fantasytechnology.mixin.AECraftingPatternInputMixin",
            "cn.lyxc.fantasytechnology.mixin.KeyCounterMixin");

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
