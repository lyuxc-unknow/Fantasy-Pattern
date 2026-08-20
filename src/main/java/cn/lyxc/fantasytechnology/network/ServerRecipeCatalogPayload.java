package cn.lyxc.fantasytechnology.network;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import cn.lyxc.fantasytechnology.recipeprovider.ServerRecipeSummary;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/// Server -> client: one page of display summaries for the encoding terminal's built-in recipe provider.
///
/// Bounded by {@link RequestServerRecipesPayload#MAX_PAGE_SIZE} rather than by the size of the catalogue, so the
/// packet stays a few kilobytes no matter how many recipes the pack has.
@MethodsReturnNonnullByDefault
public record ServerRecipeCatalogPayload(List<ServerRecipeSummary> recipes, int page, int totalPages,
        int totalMatches) implements CustomPacketPayload {

    public static final Type<ServerRecipeCatalogPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID, "server_recipe_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerRecipeCatalogPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ServerRecipeSummary.STREAM_CODEC.apply(
                            ByteBufCodecs.list(RequestServerRecipesPayload.MAX_PAGE_SIZE)),
                    ServerRecipeCatalogPayload::recipes,
                    ByteBufCodecs.VAR_INT, ServerRecipeCatalogPayload::page,
                    ByteBufCodecs.VAR_INT, ServerRecipeCatalogPayload::totalPages,
                    ByteBufCodecs.VAR_INT, ServerRecipeCatalogPayload::totalMatches,
                    ServerRecipeCatalogPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FantasyEncodingTermMenu menu) {
                menu.setServerRecipeCatalog(recipes, page, totalPages, totalMatches);
            }
        });
    }
}
