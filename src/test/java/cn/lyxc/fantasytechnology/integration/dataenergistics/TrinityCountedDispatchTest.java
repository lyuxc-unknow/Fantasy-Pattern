package cn.lyxc.fantasytechnology.integration.dataenergistics;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.lyxc.fantasytechnology.MinecraftTestBootstrap;
import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import cn.lyxc.fantasytechnology.integration.ae2.FantasyBatchDispatchContext;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingRoutingMode;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProvider;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern.TrinityBoundPatternDetails;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.config.IConfigSpec;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ParametersAreNonnullByDefault
class TrinityCountedDispatchTest {

    @BeforeEach
    void initialize() throws ReflectiveOperationException {
        MinecraftTestBootstrap.initialize();
        var config = CommentedConfig.inMemory();
        FTConfig.SPEC.correct(config);
        // Same in-memory config setup as FantasyAnnihilationAuthorizationTest; no server lifecycle is needed.
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructor(
                CommentedConfig.class, java.nio.file.Path.class, net.neoforged.fml.config.ModConfig.class);
        constructor.setAccessible(true);
        FTConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
        FTConfig.CONSUME_FUEL.set(false);
    }

    @AfterEach
    void restoreConfig() {
        FTConfig.SPEC.acceptConfig(null);
    }

    @Test
    void boundInputFallbackAdmitsOneCraftForRemainderPatterns() throws ReflectiveOperationException {
        var pattern = pattern(AEItemKey.of(Items.WATER_BUCKET));
        var provider = new TestProvider(pattern);
        var prototype = variantInputs(pattern);
        var inputKey = prototype[0].getFirstEntry().getKey();
        assertTrue(pattern.hasCraftingRemainders());
        assertTrue(provider.captureCapacityFast(pattern, prototype, 8).isEmpty());

        var admission = prepareBoundFallback(provider, pattern, prototype);

        assertEquals(1, admission.count());
        assertTrue(admission.commit(prototype));
        assertEquals(0, prototype[0].get(inputKey));
        assertFalse(provider.usedBatchContext);
        assertEquals(0, provider.limitQueries);
        assertNull(FantasyBatchDispatchContext.current());
    }

    @Test
    void boundInputFallbackAdmitsOneCraftWhenBatchingIsDisabled() throws ReflectiveOperationException {
        FTConfig.BATCH_DISPATCH_ENABLED.set(false);
        var pattern = pattern(AEItemKey.of(Items.STONE));
        var provider = new TestProvider(pattern);
        var prototype = variantInputs(pattern);
        var inputKey = prototype[0].getFirstEntry().getKey();
        assertTrue(provider.captureCapacityFast(pattern, prototype, 8).isEmpty());

        var admission = prepareBoundFallback(provider, pattern, prototype);

        assertEquals(1, admission.count());
        assertTrue(admission.commit(prototype));
        assertEquals(0, prototype[0].get(inputKey));
        assertFalse(provider.usedBatchContext);
        assertEquals(0, provider.limitQueries);
    }

    @Test
    void singleCraftStillRejectsMissingFuelWithoutTakingInputs() {
        FTConfig.CONSUME_FUEL.set(true);
        var pattern = pattern(AEItemKey.of(Items.WATER_BUCKET));
        var provider = new TestProvider(pattern);
        var prototype = variantInputs(pattern);

        var admission = provider.prepareBatch(pattern, prototype, 1);

        assertNotNull(admission);
        assertEquals(1, admission.count());
        assertFalse(admission.commit(prototype));
        assertEquals(1, prototype[0].getFirstEntry().getLongValue());
        assertFalse(provider.usedBatchContext);
    }

    @Test
    void singleCraftStillRejectsMissingGridWithoutTakingInputs() {
        var pattern = pattern(AEItemKey.of(Items.STONE));
        var provider = new TestProvider(pattern);
        provider.connected = false;
        var prototype = variantInputs(pattern);

        var admission = provider.prepareBatch(pattern, prototype, 1);

        assertNotNull(admission);
        assertFalse(admission.commit(prototype));
        assertEquals(1, prototype[0].getFirstEntry().getLongValue());
    }

    @Test
    void remainderPatternsStillRejectMultipleCrafts() {
        var pattern = pattern(AEItemKey.of(Items.WATER_BUCKET));
        var provider = new TestProvider(pattern);
        var prototype = variantInputs(pattern);

        assertNull(provider.prepareBatch(pattern, prototype, 2));
        assertEquals(1, prototype[0].getFirstEntry().getLongValue());
        assertEquals(0, provider.limitQueries);
    }

    @Test
    void disablingBatchingStillRejectsMultipleCrafts() {
        FTConfig.BATCH_DISPATCH_ENABLED.set(false);
        var pattern = pattern(AEItemKey.of(Items.STONE));
        var provider = new TestProvider(pattern);
        var prototype = variantInputs(pattern);

        assertNull(provider.prepareBatch(pattern, prototype, 2));
        assertEquals(1, prototype[0].getFirstEntry().getLongValue());
        assertEquals(0, provider.limitQueries);
    }

    @Test
    void ordinaryPatternsStillUseTheLiveBatchLimitAndCountedContext() {
        var pattern = pattern(AEItemKey.of(Items.STONE));
        var provider = new TestProvider(pattern);
        var prototype = variantInputs(pattern);
        var inputKey = prototype[0].getFirstEntry().getKey();
        var capacities = provider.captureCapacityFast(pattern, prototype, 4);
        assertEquals(1, capacities.size());
        assertEquals(CountedCraftingRoutingMode.AGGREGATE, capacities.getFirst().routingMode());
        assertEquals(4, capacities.getFirst().maximumSingleBatch().orElseThrow());

        var admission = provider.prepareBatchForTarget(pattern, prototype, 4, CountedCraftingTarget.provider());

        assertNotNull(admission);
        assertEquals(4, admission.count());
        assertEquals(2, provider.limitQueries);
        assertTrue(admission.commit(prototype));
        assertEquals(4, provider.dispatchedCrafts);
        assertTrue(provider.usedBatchContext);
        assertEquals(0, prototype[0].get(inputKey));
        assertNull(FantasyBatchDispatchContext.current());
    }

    @Test
    void nonPositiveRequestsAreNotAdmitted() {
        var pattern = pattern(AEItemKey.of(Items.STONE));
        var provider = new TestProvider(pattern);
        var prototype = variantInputs(pattern);

        assertNull(provider.prepareBatch(pattern, prototype, 0));
        assertNull(provider.prepareBatch(pattern, prototype, -1));
        assertEquals(1, prototype[0].getFirstEntry().getLongValue());
    }

    @Test
    void fallbackRouteCannotAdmitMultipleCraftsOrExternalTargets() {
        var pattern = pattern(AEItemKey.of(Items.STONE));
        var provider = new TestProvider(pattern);
        var prototype = variantInputs(pattern);
        var providerRoute = CountedCraftingTarget.provider().stableIdentity();

        assertNull(provider.prepareBatchForTarget(pattern, prototype, 2, CountedCraftingTarget.route(providerRoute)));
        assertNull(provider.prepareBatchForTarget(pattern, prototype, 1, CountedCraftingTarget.route("other")));
        assertNull(provider.prepareBatchForTarget(pattern, prototype, 1,
                CountedCraftingTarget.machine(providerRoute, "external-machine")));
        assertEquals(0, provider.limitQueries);
        assertEquals(1, prototype[0].getFirstEntry().getLongValue());
    }

    @Test
    void singleCraftDoesNotAdmitUnsupportedPatterns() {
        var pattern = pattern(AEItemKey.of(Items.STONE));
        var provider = new TestProvider(pattern);
        var prototype = variantInputs(pattern);
        IPatternDetails unsupported = new IPatternDetails() {
            @Override
            public AEItemKey getDefinition() { return pattern.getDefinition(); }

            @Override
            public IInput[] getInputs() { return pattern.getInputs(); }

            @Override
            public List<GenericStack> getOutputs() { return pattern.getOutputs(); }
        };

        assertNull(provider.prepareBatch(unsupported, prototype, 1));
        assertEquals(0, provider.limitQueries);
        assertEquals(1, prototype[0].getFirstEntry().getLongValue());
    }

    private static FantasyCraftingPattern pattern(AEItemKey input) {
        return new FantasyCraftingPattern(AEItemKey.of(Items.PAPER), new FantasyPatternData(
                List.of(PatternIngredient.of(new GenericStack(input, 1)).withIgnoreData(true)),
                List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1))));
    }

    private static KeyCounter[] variantInputs(FantasyCraftingPattern pattern) {
        var stack = ((AEItemKey) pattern.getInputs()[0].getPossibleInputs()[0].what()).toStack();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("bound component variant"));
        var key = AEItemKey.of(stack);
        assertTrue(pattern.getInputs()[0].isValid(key, null));
        var counter = new KeyCounter();
        counter.add(key, 1);
        return new KeyCounter[]{counter};
    }

    /// Trinity calls its counted adapter, not the native single-craft admission, when bound inputs differ from
    /// their planned template. Exercise that public adapter path with the same fallback snapshot the CPU creates.
    private static CountedCraftingAdmission prepareBoundFallback(TestProvider provider, FantasyCraftingPattern pattern,
            KeyCounter[] prototype) throws ReflectiveOperationException {
        var bindingClass = Class.forName(TrinityBoundPatternDetails.class.getName() + "$SlotBinding");
        var bindingConstructor = bindingClass.getDeclaredConstructor(
                IPatternDetails.IInput.class, GenericStack.class, List.class);
        bindingConstructor.setAccessible(true);
        var binding = bindingConstructor.newInstance(pattern.getInputs()[0],
                pattern.getInputs()[0].getPossibleInputs()[0],
                List.of(new GenericStack(prototype[0].getFirstEntry().getKey(), 1)));
        var boundConstructor = TrinityBoundPatternDetails.class.getDeclaredConstructor(IPatternDetails.class, List.class);
        boundConstructor.setAccessible(true);
        var bound = boundConstructor.newInstance(pattern, List.of(binding));
        assertFalse(bound.preservesNativeInputs(pattern));

        var snapshot = new ProviderCapacitySnapshot(new CraftingProviderId(1, 1),
                CraftingDispatchTarget.provider(), Optional.empty(), "test", 0, 0, 0,
                ProviderRoutingMode.UNKNOWN, DispatchCapacity.Unknown.INSTANCE, new DispatchCapacity.Known(1));
        var preparation = CountedCraftingProviderAdapters.prepare(
                provider, pattern, bound, prototype, 1, snapshot, target -> true);
        assertTrue(preparation.accepted(), () -> preparation.rejections().toString());
        assertNotNull(preparation.admission());
        return preparation.admission();
    }

    /// Mirrors the mixin's counted adapter while keeping the real block's single-craft validation and execution.
    private static final class TestProvider extends FantasyAnnihilationBlockEntity implements CountedCraftingProvider {
        private final FantasyCraftingPattern pattern;
        private boolean connected = true;
        private int limitQueries;
        private boolean usedBatchContext;
        private long dispatchedCrafts;

        TestProvider(FantasyCraftingPattern pattern) {
            super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            this.pattern = pattern;
        }

        @Override
        protected IManagedGridNode createMainNode() {
            var grid = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IGrid.class},
                    (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); });
            return (IManagedGridNode) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{IManagedGridNode.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getGrid")) { return connected ? grid : null; }
                        if (method.getReturnType() == IManagedGridNode.class) { return proxy; }
                        if (method.getReturnType() == boolean.class) { return false; }
                        return null;
                    });
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(pattern);
        }

        @Override
        public @NotNull ObjectList<CountedCraftingCapacity> captureCapacityFast(IPatternDetails details, KeyCounter[] prototype,
                                                                                long requestedCrafts) {
            return TrinityCountedDispatch.captureCapacity(this, details, prototype, requestedCrafts);
        }

        @Override
        public CountedCraftingAdmission prepareBatch(IPatternDetails details, KeyCounter[] prototype, long requestedCount) {
            return TrinityCountedDispatch.prepareBatch(this, details, prototype, requestedCount);
        }

        @Override
        public CountedCraftingAdmission prepareBatchForTarget(IPatternDetails details, KeyCounter[] prototype,
                long requestedCount, CountedCraftingTarget target) {
            return TrinityCountedDispatch.prepareBatchForTarget(this, details, prototype, requestedCount, target);
        }

        @Override
        public long getMaxBatchCrafts(FantasyCraftingPattern pattern, long requestedCrafts) {
            limitQueries++;
            return super.getMaxBatchCrafts(pattern, requestedCrafts);
        }

        @Override
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputHolder) {
            var context = FantasyBatchDispatchContext.current();
            usedBatchContext = context != null;
            dispatchedCrafts = context == null ? 1 : context.craftCount();
            return super.pushPattern(pattern, inputHolder);
        }

        @Override
        public void saveChanges() {
        }
    }
}
