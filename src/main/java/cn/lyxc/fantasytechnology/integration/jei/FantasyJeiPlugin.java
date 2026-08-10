package cn.lyxc.fantasytechnology.integration.jei;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.deviceaccess.DeviceCatalystSnapshot;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;

/// JEI integration: enables the recipe transfer ("+") button on the fantasy encoding terminal.
///
/// The handler is registered universally rather than for a single recipe type, because a fantasy pattern is just a set
/// of ingredients and results - smelting, stonecutting and modded categories transfer as sensibly as crafting does.
///
/// The runtime is captured because a universal handler is never told which category a recipe came from, and the
/// transfer is gated on that category's catalysts; see {@link JeiRecipeCategoryIndex}.
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

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiRecipeCategoryIndex.setRuntime(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiRecipeCategoryIndex.setRuntime(null);
        DeviceCatalystSnapshot.clearClientSnapshot();
    }
}
