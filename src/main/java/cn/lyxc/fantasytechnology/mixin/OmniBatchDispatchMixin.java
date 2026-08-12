package cn.lyxc.fantasytechnology.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.integration.ae2.FantasyBatchDispatchContext;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = FantasyAnnihilationBlockEntity.class, remap = false)
public abstract class OmniBatchDispatchMixin {

    @WrapMethod(method = "pushPattern")
    private boolean fantasyTechnology$bridgeOmniBatch(IPatternDetails pattern, KeyCounter[] inputs,
            Operation<Boolean> original) {
        var omniContext = MolecularBatchDispatchContext.current(pattern, inputs);
        if (omniContext == null) {
            return original.call(pattern, inputs);
        }

        KeyCounter remainders = omniContext.reusablePlan() == null
                ? null
                : omniContext.reusablePlan().expectedRemainders(omniContext.craftCount());
        try (var ignored = FantasyBatchDispatchContext.open(omniContext.craftCount(), remainders)) {
            return original.call(pattern, inputs);
        }
    }
}
