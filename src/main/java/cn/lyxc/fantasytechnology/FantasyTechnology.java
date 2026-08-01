package cn.lyxc.fantasytechnology;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import appeng.api.AECapabilities;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.parts.PartModels;
import appeng.blockentity.AEBaseBlockEntity;

import cn.lyxc.fantasytechnology.crafting.FantasyPatternDecoder;
import cn.lyxc.fantasytechnology.network.FTPackets;
import cn.lyxc.fantasytechnology.part.FantasyEncodingTerminalPart;
import cn.lyxc.fantasytechnology.registry.FTBlockEntities;
import cn.lyxc.fantasytechnology.registry.FTBlocks;
import cn.lyxc.fantasytechnology.registry.FTComponents;
import cn.lyxc.fantasytechnology.registry.FTItems;
import cn.lyxc.fantasytechnology.registry.FTMenus;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(FantasyTechnology.MODID)
public class FantasyTechnology {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "fantasy_technology";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // Deferred Registers for this mod's content, registered under the "fantasy_technology" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, MODID);

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public FantasyTechnology(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        FTBlockEntities.REGISTER.register(modEventBus);
        FTMenus.REGISTER.register(modEventBus);
        FTComponents.REGISTER.register(modEventBus);

        // Load registry classes so their static content gets registered.
        FTItems.init();
        FTBlocks.init();
        FTBlockEntities.init();
        FTMenus.init();
        FTComponents.init();

        // AE2 bakes part models itself, so they have to be declared before it freezes the list.
        PartModels.registerModels(FantasyEncodingTerminalPart.getModels());

        CREATIVE_MODE_TABS.register("fantasy_technology", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.fantasy_technology"))
                .icon(() -> FTItems.FANTASY_PATTERN.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    output.accept(FTItems.FANTASY_PATTERN.get());
                    output.accept(FTItems.FANTASY_ENCODING_TERMINAL.get());
                    output.accept(FTBlocks.FANTASY_ANNIHILATION.get());
                })
                .build());

        modEventBus.addListener(FTPackets::onRegisterPayloadHandlers);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::commonSetup);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Cables connect to the annihilation block's grid node through this capability.
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                FTBlockEntities.FANTASY_ANNIHILATION.get(),
                (blockEntity, context) -> blockEntity);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Used by AE2 for the network visual representation of the annihilation block.
            AEBaseBlockEntity.registerBlockEntityItem(
                    FTBlockEntities.FANTASY_ANNIHILATION.get(),
                    FTBlocks.FANTASY_ANNIHILATION.asItem());
            // Lets crafting CPUs decode fantasy patterns (required for job persistence across reloads).
            PatternDetailsHelper.registerDecoder(FantasyPatternDecoder.INSTANCE);
        });
    }
}
