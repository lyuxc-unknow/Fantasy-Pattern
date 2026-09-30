package cn.lyxc.fantasytechnology.blockentity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.lyxc.fantasytechnology.MinecraftTestBootstrap;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern;
import cn.lyxc.fantasytechnology.item.FantasyPatternData;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class FantasyAnnihilationAuthorizationTest {
    @BeforeEach
    void initialize() throws ReflectiveOperationException {
        MinecraftTestBootstrap.initialize();
        var config = CommentedConfig.inMemory();
        FTConfig.SPEC.correct(config);
        // NeoForge seals ILoadedConfig; construct its in-memory implementation without starting a server.
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
    void switchingToTrustedModeRejectsPreviouslyUsableOrdinaryPatternWithoutConsumingInputs() {
        var provider = new TestProvider();
        var pattern = pattern(Optional.empty());
        FTConfig.TRUST_SERVER_RECIPE_PARSING.set(false);
        var firstInputs = inputs();
        assertTrue(provider.pushPattern(pattern, firstInputs));
        assertEquals(0, firstInputs[0].get(AEItemKey.of(Items.STONE)));
        FTConfig.TRUST_SERVER_RECIPE_PARSING.set(true);
        var nextInputs = inputs();
        assertFalse(provider.pushPattern(pattern, nextInputs));
        assertEquals(1, nextInputs[0].get(AEItemKey.of(Items.STONE)));
    }

    @Test
    void ordinaryModeRejectsTrustedPatternBeforeResolvingOrConsumingIt() {
        FTConfig.TRUST_SERVER_RECIPE_PARSING.set(false);
        var inputs = inputs();
        assertFalse(new TestProvider().pushPattern(pattern(Optional.of(42L)), inputs));
        assertEquals(1, inputs[0].get(AEItemKey.of(Items.STONE)));
    }

    private static FantasyCraftingPattern pattern(Optional<Long> token) {
        return new FantasyCraftingPattern(AEItemKey.of(Items.PAPER), new FantasyPatternData(
                List.of(PatternIngredient.of(new GenericStack(AEItemKey.of(Items.STONE), 1))),
                List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)), List.of(false), token));
    }

    private static KeyCounter[] inputs() {
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.STONE), 1);
        return new KeyCounter[] { inputs };
    }

    /// Supply a connected grid without running a world. Output delivery is deliberately deferred by the provider.
    private static class TestProvider extends FantasyAnnihilationBlockEntity {
        TestProvider() { super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }

        @Override
        protected IManagedGridNode createMainNode() {
            var grid = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { IGrid.class },
                    (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); });
            return (IManagedGridNode) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { IManagedGridNode.class }, (proxy, method, args) -> {
                        if (method.getName().equals("getGrid")) { return grid; }
                        if (method.getReturnType() == IManagedGridNode.class) { return proxy; }
                        if (method.getReturnType() == boolean.class) { return false; }
                        return null;
                    });
        }

        @Override
        public void saveChanges() { }
    }
}
