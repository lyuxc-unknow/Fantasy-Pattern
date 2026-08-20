package cn.lyxc.fantasytechnology.network;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import cn.lyxc.fantasytechnology.config.FTConfig;
import cn.lyxc.fantasytechnology.deviceaccess.DeviceAccessCheck;
import cn.lyxc.fantasytechnology.menu.FantasyEncodingTermMenu;
import cn.lyxc.fantasytechnology.recipeprovider.ServerRecipeProviders;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// Client -> server: selects one trusted recipe. No ingredient or output data is accepted from the client.
@MethodsReturnNonnullByDefault
public record SelectServerRecipePayload(ResourceLocation providerId, ResourceLocation recipeId)
        implements CustomPacketPayload {

    public static final Type<SelectServerRecipePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FantasyTechnology.MODID, "select_server_recipe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectServerRecipePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC.cast(), SelectServerRecipePayload::providerId,
                    ResourceLocation.STREAM_CODEC.cast(), SelectServerRecipePayload::recipeId,
                    SelectServerRecipePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!FTConfig.TRUST_SERVER_RECIPE_PARSING.get()
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof FantasyEncodingTermMenu menu)) {
                return;
            }

            var recipe = ServerRecipeProviders.find(player, providerId, recipeId).orElse(null);
            if (recipe == null) {
                // The catalogue the client picked from is older than the server's; a reload can remove an entry
                // between the browser being filled and a row being clicked.
                menu.notifyPlayer(Component.translatable("gui.fantasy_technology.server_recipe_gone"));
                return;
            }
            DeviceAccessCheck.Result access = menu.checkDeviceAccess(recipe.recipeId(), recipe.categoryId(),
                    DeviceAccessCheck.outputItemIds(recipe.outputs()), recipe.catalysts(),
                    menu.deviceCatalystSnapshot());
            if (!access.allowed()) {
                menu.notifyPlayer(access.reason());
                return;
            }
            menu.setTrustedServerRecipe(recipe);
        });
    }
}
