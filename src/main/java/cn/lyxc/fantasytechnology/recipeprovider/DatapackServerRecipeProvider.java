package cn.lyxc.fantasytechnology.recipeprovider;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

/// Exposes the datapack DSL entries loaded by {@link DatapackRecipeProviderLoader} as an ordinary provider.
///
/// Every method delegates to the one immutable snapshot held by {@link ServerRecipeProviders}, so the player- and
/// level-scoped halves of the interface answer identically - a datapack entry does not depend on who is asking.
final class DatapackServerRecipeProvider implements ServerRecipeProvider {

    static final DatapackServerRecipeProvider INSTANCE = new DatapackServerRecipeProvider();

    private DatapackServerRecipeProvider() {
    }

    @Override
    public ResourceLocation id() {
        return DatapackServerRecipe.PROVIDER_ID;
    }

    @Override
    public Collection<ServerRecipe> recipes(ServerPlayer player) {
        return ServerRecipeProviders.datapackRecipes();
    }

    @Override
    public Optional<ServerRecipe> find(ServerPlayer player, ResourceLocation recipeId) {
        return ServerRecipeProviders.findDatapack(recipeId);
    }

    @Override
    public boolean visit(ServerPlayer player, Predicate<ServerRecipe> visitor) {
        return visit((Level) null, visitor);
    }

    @Override
    public Collection<ServerRecipe> recipes(Level level) {
        return ServerRecipeProviders.datapackRecipes();
    }

    @Override
    public Optional<ServerRecipe> find(Level level, ResourceLocation recipeId) {
        return ServerRecipeProviders.findDatapack(recipeId);
    }

    @Override
    public boolean visit(Level level, Predicate<ServerRecipe> visitor) {
        for (ServerRecipe recipe : ServerRecipeProviders.datapackRecipes()) {
            if (!visitor.test(recipe)) {
                return false;
            }
        }
        return true;
    }
}
