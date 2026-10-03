package cn.lyxc.fantasytechnology.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.integration.dataenergistics.TrinityCountedDispatch;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProvider;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/// Makes the fantasy annihilation block a counted crafting provider for Data Energistics' Trinity Data Core CPU.
///
/// That CPU has its own crafting logic and only ever hands a provider a whole batch if the provider implements its
/// counted dispatch interface - the CPU asks for capacity, offers up to that many crafts in one physical dispatch, and
/// accounts the count the provider admits. Without it the block is an ordinary provider that is offered exactly one
/// craft per push.
///
/// Implementing the interface rather than registering an adapter keeps the integration free of lifecycle state: the
/// CPU discovers this block through its own publication of AE2's crafting providers, and the capability is an
/// `instanceof` check that no registry lookup can miss. The interface lives in the other mod's internal dispatch
/// package, so {@link FantasyTechnologyMixinPlugin} drops this mixin both when Data Energistics is absent and when that
/// interface is not on the class path, rather than failing to apply against a missing type.
@Mixin(value = FantasyAnnihilationBlockEntity.class, remap = false)
public abstract class TrinityCountedCraftingProviderMixin implements CountedCraftingProvider {

    @Override
    public ObjectList<CountedCraftingCapacity> captureCapacityFast(IPatternDetails patternDetails,
            KeyCounter[] prototype, long requestedCrafts) {
        return TrinityCountedDispatch.captureCapacity(fantasyTechnology$self(), patternDetails, prototype,
                requestedCrafts);
    }

    @Nullable
    @Override
    public CountedCraftingAdmission prepareBatch(IPatternDetails patternDetails, KeyCounter[] prototype,
            long requestedCount) {
        return TrinityCountedDispatch.prepareBatch(fantasyTechnology$self(), patternDetails, prototype,
                requestedCount);
    }

    @Unique
    private FantasyAnnihilationBlockEntity fantasyTechnology$self() {
        return (FantasyAnnihilationBlockEntity) (Object) this;
    }
}
