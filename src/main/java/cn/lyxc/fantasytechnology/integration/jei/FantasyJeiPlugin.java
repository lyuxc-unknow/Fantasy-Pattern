package cn.lyxc.fantasytechnology.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;

import cn.lyxc.fantasytechnology.FantasyTechnology;

/// JEI integration: enables the recipe transfer ("+") button on the fantasy encoding terminal.
///
/// The handler is registered universally rather than for a single recipe type, because a fantasy pattern is just a set
/// of ingredients and results - smelting, stonecutting and modded categories transfer as sensibly as crafting does.
@JeiPlugin
@MethodsReturnNonnullByDefault
public class FantasyJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID,
            "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(
                new FantasyEncodingTransferHandler(registration.getTransferHelper()));
    }
}
