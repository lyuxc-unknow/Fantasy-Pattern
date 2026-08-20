package cn.lyxc.fantasytechnology.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class FTPackets {
    private FTPackets() {
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3");
        // Encoding and clearing are driven by AE2's client actions, so transferring a recipe from a recipe viewer is
        // the only thing that still needs a packet of our own.
        registrar.playToServer(TransferRecipePayload.TYPE, TransferRecipePayload.STREAM_CODEC,
                TransferRecipePayload::handle);
        registrar.playToServer(RequestServerRecipesPayload.TYPE, RequestServerRecipesPayload.STREAM_CODEC,
                RequestServerRecipesPayload::handle);
        registrar.playToServer(SelectServerRecipePayload.TYPE, SelectServerRecipePayload.STREAM_CODEC,
                SelectServerRecipePayload::handle);
        // The other direction: the recipe transfer button decides on the client whether the network owns the
        // machines a recipe is made on, which it can only know if the server tells it - both what the device
        // access blocks hold, and the datapack rules that judge them.
        registrar.playToClient(DeviceCatalystsPayload.TYPE, DeviceCatalystsPayload.STREAM_CODEC,
                DeviceCatalystsPayload::handle);
        registrar.playToClient(DeviceRequirementSync.TYPE, DeviceRequirementSync.STREAM_CODEC,
                DeviceRequirementSync::handle);
        registrar.playToClient(ServerRecipeCatalogPayload.TYPE, ServerRecipeCatalogPayload.STREAM_CODEC,
                ServerRecipeCatalogPayload::handle);
    }
}
