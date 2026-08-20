package cn.lyxc.fantasytechnology.network;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/// Client -> server: asks for one page of the trusted recipe catalogue while the encoding terminal is open.
///
/// Paging is decided by the server rather than by shipping the whole catalogue: a modpack has far more recipes than
/// fit in one packet, and the client has no way to judge device access anyway.
@MethodsReturnNonnullByDefault
public record RequestServerRecipesPayload(String query, int page, int pageSize) implements CustomPacketPayload {

    public static final int MAX_QUERY_LENGTH = 128;
    /// Upper bound on how many rows one page may ask for. The terminal shows at most six, so this only has to stop a
    /// modified client from asking for a page that would be expensive to build.
    public static final int MAX_PAGE_SIZE = 32;

    public static final Type<RequestServerRecipesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID, "request_server_recipes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestServerRecipesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_QUERY_LENGTH), RequestServerRecipesPayload::query,
                    ByteBufCodecs.VAR_INT, RequestServerRecipesPayload::page,
                    ByteBufCodecs.VAR_INT, RequestServerRecipesPayload::pageSize,
                    RequestServerRecipesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof FantasyEncodingTermMenu menu)) {
                return;
            }
            if (!FTConfig.TRUST_SERVER_RECIPE_PARSING.get()) {
                PacketDistributor.sendToPlayer(player, new ServerRecipeCatalogPayload(List.of(), 0, 1, 0));
                return;
            }
            // Queued rather than answered inline: a player typing in the search box produces one of these per
            // keystroke, and the menu coalesces a burst into a single catalogue build.
            menu.queueServerRecipeCatalog(query, Math.max(0, page),
                    Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE)));
        });
    }
}
