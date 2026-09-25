package cn.lyxc.fantasytechnology.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class FantasyTechnologyMixinPlugin implements IMixinConfigPlugin {

    private static final String OMNI_MOD_ID = "molecularmanipulator";
    private static final String ENHANCEMENTS_MOD_ID = "appliedenhancements";
    private static final String ENHANCEMENTS_MIXIN =
            "cn.lyxc.fantasytechnology.mixin.AelisPlannerMixin";
    private static final Set<String> OMNI_MIXINS = Set.of(
            "cn.lyxc.fantasytechnology.mixin.MolecularBatchCraftingProviderMixin",
            "cn.lyxc.fantasytechnology.mixin.OmniBatchDispatchMixin");
    private static Boolean omniPresent;
    private static Boolean enhancementsPresent;

    private static boolean isOmniPresent() {
        if (omniPresent == null) {
            try {
                omniPresent = LoadingModList.get().getModFileById(OMNI_MOD_ID) != null;
            } catch (LinkageError | RuntimeException ignored) {
                omniPresent = false;
            }
        }
        return omniPresent;
    }

    private static boolean isEnhancementsPresent() {
        if (enhancementsPresent == null) {
            try {
                enhancementsPresent = LoadingModList.get().getModFileById(ENHANCEMENTS_MOD_ID) != null;
            } catch (LinkageError | RuntimeException ignored) {
                enhancementsPresent = false;
            }
        }
        return enhancementsPresent;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (ENHANCEMENTS_MIXIN.equals(mixinClassName)) {
            return isEnhancementsPresent();
        }
        return !OMNI_MIXINS.contains(mixinClassName) || isOmniPresent();
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
