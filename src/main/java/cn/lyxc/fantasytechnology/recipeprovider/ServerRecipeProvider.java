package cn.lyxc.fantasytechnology.recipeprovider;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/// Extensible server-side source for recipes shown by the fantasy encoding terminal.
///
/// Addons may register an implementation through {@link ServerRecipeProviders#register(ServerRecipeProvider)}.
/// Implementations must derive every returned input and output from server-owned state.
public interface ServerRecipeProvider {

    ResourceLocation id();

    Collection<ServerRecipe> recipes(ServerPlayer player);

    /// Visits recipes without requiring callers to materialise the provider's complete catalogue.
    default boolean visit(ServerPlayer player, Predicate<ServerRecipe> visitor) {
        for (ServerRecipe recipe : recipes(player)) {
            if (!visitor.test(recipe)) {
                return false;
            }
        }
        return true;
    }

    /// Providers that can resolve without player-specific state implement this for server-authenticated patterns.
    /// The default keeps existing extension providers source-compatible; such providers can still populate the browser
    /// through {@link #recipes(ServerPlayer)}, but their patterns cannot be re-resolved after the terminal is closed.
    default Collection<ServerRecipe> recipes(Level level) {
        return List.of();
    }

    /// Level-scoped counterpart used by token indexes and server-side pattern validation.
    default boolean visit(Level level, Predicate<ServerRecipe> visitor) {
        for (ServerRecipe recipe : recipes(level)) {
            if (!visitor.test(recipe)) {
                return false;
            }
        }
        return true;
    }

    default Optional<ServerRecipe> find(Level level, ResourceLocation recipeId) {
        return recipes(level).stream().filter(recipe -> recipe.recipeId().equals(recipeId)).findFirst();
    }

    default Optional<ServerRecipe> find(ServerPlayer player, ResourceLocation recipeId) {
        return recipes(player).stream().filter(recipe -> recipe.recipeId().equals(recipeId)).findFirst();
    }
}
