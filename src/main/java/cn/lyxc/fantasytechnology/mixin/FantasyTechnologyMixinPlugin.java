package cn.lyxc.fantasytechnology.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class FantasyTechnologyMixinPlugin implements IMixinConfigPlugin {

    private static final String OMNI_MOD_ID = "molecularmanipulator";
    private static final Set<String> OMNI_MIXINS = Set.of(
            "cn.lyxc.fantasytechnology.mixin.MolecularBatchCraftingProviderMixin",
            "cn.lyxc.fantasytechnology.mixin.OmniBatchDispatchMixin",
            "cn.lyxc.fantasytechnology.mixin.OmniMaxFastPlannerMixin");
    private static Boolean omniPresent;

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

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
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
