package cn.lyxc.fantasytechnology.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class FTPackets {
    private FTPackets() {
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Encoding and clearing are driven by AE2's client actions, so transferring a recipe from a recipe viewer is
        // the only thing that still needs a packet of our own.
        registrar.playToServer(TransferRecipePayload.TYPE, TransferRecipePayload.STREAM_CODEC,
                TransferRecipePayload::handle);
    }
}
