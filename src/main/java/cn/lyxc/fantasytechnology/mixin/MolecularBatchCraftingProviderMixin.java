package cn.lyxc.fantasytechnology.mixin;

import appeng.api.crafting.IPatternDetails;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = FantasyAnnihilationBlockEntity.class, remap = false)
public abstract class MolecularBatchCraftingProviderMixin implements MolecularBatchCraftingProvider {

    @Override
    public boolean molecularmanipulator$supportsBatching(IPatternDetails pattern) {
        return FTConfig.BATCH_DISPATCH_ENABLED.get() && pattern instanceof FantasyCraftingPattern;
    }

    @Override
    public long molecularmanipulator$getBatchLimit(IPatternDetails pattern) {
        if (!molecularmanipulator$supportsBatching(pattern)) {
            return 0;
        }
        return fantasyTechnology$self().getMaxBatchCrafts((FantasyCraftingPattern) pattern, Long.MAX_VALUE);
    }

    @Override
    public boolean molecularmanipulator$supportsReusableBatching(IPatternDetails pattern) {
        return molecularmanipulator$supportsBatching(pattern);
    }

    @Unique
    private FantasyAnnihilationBlockEntity fantasyTechnology$self() {
        return (FantasyAnnihilationBlockEntity) (Object) this;
    }
}
