package cn.lyxc.fantasytechnology.network;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.deviceaccess.DeviceRequirement;
import cn.lyxc.fantasytechnology.deviceaccess.DeviceRequirements;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/// Server -> client: the device-access rules, resent on login and after every {@code /reload}.
///
/// The recipe transfer button decides on the client, so the rules have to live there too. They are sent rather than
/// registered as a datapack registry because a datapack registry would not survive {@code /reload} in this version -
/// see {@link cn.lyxc.fantasytechnology.deviceaccess.DeviceRequirementLoader}.
@MethodsReturnNonnullByDefault
public record DeviceRequirementSync(List<DeviceRequirement> rules) implements CustomPacketPayload {

    public static final Type<DeviceRequirementSync> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID, "device_requirements"));

    /// A pack with more rules than this is a mistake rather than a configuration, and the cap keeps a hostile or
    /// broken server from making a client allocate without bound.
    private static final int MAX_RULES = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceRequirementSync> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.fromCodecWithRegistries(DeviceRequirement.CODEC)
                            .apply(ByteBufCodecs.list(MAX_RULES)),
                    DeviceRequirementSync::rules,
                    DeviceRequirementSync::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> DeviceRequirements.setRules(rules));
    }
}
