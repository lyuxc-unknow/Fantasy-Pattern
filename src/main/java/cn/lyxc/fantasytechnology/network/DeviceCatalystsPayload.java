package cn.lyxc.fantasytechnology.network;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.deviceaccess.DeviceCatalystSnapshot;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/// Server -> client: what the device access blocks on the open terminal's network are holding.
///
/// The recipe transfer button has to answer "can this recipe be encoded here?" on the client, where JEI runs and
/// where the device blocks' contents are otherwise invisible. Sending a summary rather than answering per recipe
/// keeps the check instant and lets the button grey out as the player fills the blocks, instead of only telling
/// them something is wrong after a click that does nothing.
///
/// Counts are per item and ignore data components, matching {@code FantasyDeviceAccessBlockEntity#collectCatalysts}.
@MethodsReturnNonnullByDefault
public record DeviceCatalystsPayload(Map<Item, Integer> catalysts) implements CustomPacketPayload {

    public static final Type<DeviceCatalystsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID, "device_catalysts"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceCatalystsPayload> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.registry(Registries.ITEM), ByteBufCodecs.VAR_INT),
                    DeviceCatalystsPayload::catalysts,
                    DeviceCatalystsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> DeviceCatalystSnapshot.setClientSnapshot(catalysts));
    }
}
