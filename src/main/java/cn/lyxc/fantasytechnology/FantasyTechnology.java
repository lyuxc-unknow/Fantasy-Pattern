package cn.lyxc.fantasytechnology;

import appeng.api.AECapabilities;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.parts.PartModels;
import appeng.blockentity.AEBaseBlockEntity;
import cn.lyxc.fantasytechnology.crafting.FantasyPatternDecoder;
import cn.lyxc.fantasytechnology.network.FTPackets;
import cn.lyxc.fantasytechnology.part.FantasyEncodingTerminalPart;
import cn.lyxc.fantasytechnology.registry.*;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(FantasyTechnology.MODID)
public class FantasyTechnology {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "fantasy_technology";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public FantasyTechnology(IEventBus modEventBus, ModContainer modContainer) {
        // Load registry classes so their static content gets registered.
        FTBlocks.init(modEventBus);
        FTItems.init(modEventBus);
        FTBlockEntities.init(modEventBus);
        FTMenus.init(modEventBus);
        FTComponents.init(modEventBus);
        FTCreativeTab.init(modEventBus);

        // AE2 bakes part models itself, so they have to be declared before it freezes the list.
        PartModels.registerModels(FantasyEncodingTerminalPart.getModels());

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
