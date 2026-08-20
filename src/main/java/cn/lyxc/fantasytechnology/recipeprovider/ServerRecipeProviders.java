package cn.lyxc.fantasytechnology.recipeprovider;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import com.google.gson.JsonElement;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.conditions.ICondition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// Registry and lookup facade for every trusted server recipe provider.
///
/// The two built-in providers are indexed: their catalogue rows and token lookups are built once per datapack reload
/// and reused, because a catalogue request happens whenever a player types in the browser and a token lookup happens
/// on every craft dispatch. Third-party providers are not indexed - they may answer per player - and are visited live.
public final class ServerRecipeProviders {

    /// Ceiling on how many rows one unindexed third-party provider may contribute to a single catalogue request, so a
    /// misbehaving addon cannot grow the match list without bound.
    private static final int MAX_DYNAMIC_ENTRIES = 20_000;

    private static final Map<ResourceLocation, ServerRecipeProvider> BY_ID = new ConcurrentHashMap<>();
    private static final List<ServerRecipeProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private static volatile DatapackRecipeSource datapackSource = DatapackRecipeSource.EMPTY;
    private static volatile Index datapackIndex = Index.EMPTY;

    private static final Object CRAFTING_INDEX_LOCK = new Object();
    private static volatile RecipeManager craftingIndexOwner;
    private static volatile Index craftingIndex = Index.EMPTY;

    static {
        register(CraftingServerRecipeProvider.INSTANCE);
        register(DatapackServerRecipeProvider.INSTANCE);
    }

    private ServerRecipeProviders() {
    }

    public static void register(ServerRecipeProvider provider) {
        if (BY_ID.putIfAbsent(provider.id(), provider) != null) {
            throw new IllegalStateException("A server recipe provider is already registered for " + provider.id());
        }
        PROVIDERS.add(provider);
    }

    public static List<ServerRecipe> recipes(ServerPlayer player) {
        List<ServerRecipe> recipes = new ArrayList<>();
        for (ServerRecipeProvider provider : PROVIDERS) {
            try {
                recipes.addAll(provider.recipes(player));
            } catch (RuntimeException e) {
                FantasyTechnology.LOGGER.error("Server recipe provider {} failed while building its catalogue",
                        provider.id(), e);
            }
        }
        return List.copyOf(recipes);
    }

    /// Visits the current server catalogue without materialising all providers into one list.
    public static boolean visit(ServerPlayer player, Predicate<ServerRecipe> visitor) {
        for (ServerRecipeProvider provider : PROVIDERS) {
            try {
                if (!provider.visit(player, visitor)) {
                    return false;
                }
            } catch (RuntimeException e) {
                FantasyTechnology.LOGGER.error("Server recipe provider {} failed while visiting its catalogue",
                        provider.id(), e);
            }
        }
        return true;
    }

    public static Optional<ServerRecipe> find(ServerPlayer player, ResourceLocation providerId,
            ResourceLocation recipeId) {
        ServerRecipeProvider provider = BY_ID.get(providerId);
        if (provider == null) {
            return Optional.empty();
        }
        try {
            return provider.find(player, recipeId);
        } catch (RuntimeException e) {
            FantasyTechnology.LOGGER.error("Server recipe provider {} failed while resolving {}", providerId,
                    recipeId, e);
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------------
    // Catalogue
    // ------------------------------------------------------------------------

    /// The catalogue rows matching {@code query}, ordered by display name.
    ///
    /// Only the lightweight rows are materialised; the caller resolves the page it is actually going to show. The
    /// display name used for ordering and matching is resolved on the logical server, so on a dedicated server a
    /// modded item that ships no server-side translation matches by its ids rather than by its localised name.
    public static List<CatalogueEntry> search(ServerPlayer player, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<CatalogueEntry> matches = new ArrayList<>();
        int contributors = 0;
        contributors += collect(datapackIndex, needle, matches) ? 1 : 0;
        contributors += collect(craftingIndex(player.level()), needle, matches) ? 1 : 0;

        for (ServerRecipeProvider provider : PROVIDERS) {
            if (isIndexed(provider)) {
                continue;
            }
            int before = matches.size();
            try {
                provider.visit(player, recipe -> {
                    CatalogueEntry entry = entryOf(recipe);
                    if (needle.isEmpty() || entry.searchKey().contains(needle)) {
                        matches.add(entry);
                    }
                    return matches.size() - before < MAX_DYNAMIC_ENTRIES;
                });
            } catch (RuntimeException e) {
                FantasyTechnology.LOGGER.error("Server recipe provider {} failed while building its catalogue",
                        provider.id(), e);
            }
            contributors += matches.size() > before ? 1 : 0;
        }

        // Each built-in index is already sorted, so with a single contributor there is nothing left to order, and
        // otherwise this is a merge of a few long runs rather than a full sort.
        if (contributors > 1) {
            matches.sort(Comparator.comparing(CatalogueEntry::searchKey));
        }
        return matches;
    }

    /// Appends the rows of {@code index} that match, in index order. Returns whether it contributed any.
    private static boolean collect(Index index, String needle, List<CatalogueEntry> into) {
        if (needle.isEmpty()) {
            into.addAll(index.entries());
            return !index.entries().isEmpty();
        }
        int before = into.size();
        for (CatalogueEntry entry : index.entries()) {
            if (entry.searchKey().contains(needle)) {
                into.add(entry);
            }
        }
        return into.size() > before;
    }

    // ------------------------------------------------------------------------
    // Token resolution
    // ------------------------------------------------------------------------

    public static Optional<ServerRecipe> findByToken(Level level, long token) {
        Index datapack = datapackIndex;
        if (datapack.ambiguous(token)) {
            return Optional.empty();
        }
        CatalogueEntry entry = datapack.byToken().get(token);
        if (entry != null) {
            Optional<ServerRecipe> recipe = datapack.resolve(token, () -> findDatapack(entry.recipeId()));
            if (recipe.isPresent()) {
                return recipe;
            }
        }

        Index crafting = craftingIndex(level);
        if (crafting.ambiguous(token)) {
            return Optional.empty();
        }
        CatalogueEntry craftingEntry = crafting.byToken().get(token);
        if (craftingEntry != null) {
            Optional<ServerRecipe> recipe = crafting.resolve(token,
                    () -> CraftingServerRecipeProvider.INSTANCE.find(level, craftingEntry.recipeId()));
            if (recipe.isPresent()) {
                return recipe;
            }
        }

        // Addons may expose player- or world-specific providers. They are not part of the built-in indexes, so retain
        // a bounded fallback for compatibility.
        for (ServerRecipeProvider provider : PROVIDERS) {
            if (isIndexed(provider)) {
                continue;
            }
            try {
                ServerRecipe[] match = new ServerRecipe[1];
                provider.visit(level, recipe -> {
                    if (recipe.token() == token) {
                        match[0] = recipe;
                        return false;
                    }
                    return true;
                });
                if (match[0] != null) {
                    return Optional.of(match[0]);
                }
            } catch (RuntimeException e) {
                FantasyTechnology.LOGGER.error("Server recipe provider {} failed while resolving token {}",
                        provider.id(), token, e);
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------------
    // Built-in provider state
    // ------------------------------------------------------------------------

    static void setDatapackSource(RegistryAccess registryAccess, ICondition.IContext conditionContext,
            Map<ResourceLocation, JsonElement> files) {
        DatapackRecipeSource source = DatapackRecipeSource.decode(registryAccess, conditionContext, files);
        datapackSource = source;
        datapackIndex = Index.of(source.all());
        // A datapack reload also rebuilds the recipe manager, but drop the crafting index explicitly rather than
        // relying on the manager identity changing.
        invalidateCraftingIndex();
        FantasyTechnology.LOGGER.info("Loaded {} trusted server recipe provider entry/entries", source.size());
    }

    /// Drops everything tied to a running server. Without this the resolved datapack recipes and the recipe manager
    /// behind the crafting index would stay reachable from these static fields after a single-player world is closed.
    public static void clearServerState() {
        datapackSource = DatapackRecipeSource.EMPTY;
        datapackIndex = Index.EMPTY;
        invalidateCraftingIndex();
    }

    static Collection<ServerRecipe> datapackRecipes() {
        return datapackSource.all();
    }

    static Optional<ServerRecipe> findDatapack(ResourceLocation recipeId) {
        return datapackSource.find(recipeId);
    }

    private static boolean isIndexed(ServerRecipeProvider provider) {
        return provider == CraftingServerRecipeProvider.INSTANCE || provider == DatapackServerRecipeProvider.INSTANCE;
    }

    /// The crafting index, built on first use after each reload.
    ///
    /// Building it resolves every crafting recipe once, which is the one noticeable cost in this class; everything
    /// afterwards - catalogue pages, token lookups - reads the finished index. It is keyed by recipe manager identity
    /// so a {@code /reload} naturally forces a rebuild, and it is only ever reached in trusted mode.
    private static Index craftingIndex(Level level) {
        RecipeManager manager = level.getRecipeManager();
        if (craftingIndexOwner == manager) {
            return craftingIndex;
        }
        synchronized (CRAFTING_INDEX_LOCK) {
            if (craftingIndexOwner == manager) {
                return craftingIndex;
            }
            Index.Builder builder = new Index.Builder();
            CraftingServerRecipeProvider.INSTANCE.visit(level, recipe -> {
                builder.add(recipe);
                return true;
            });
            Index index = builder.build();
            craftingIndex = index;
            craftingIndexOwner = manager;
            return index;
        }
    }

    private static void invalidateCraftingIndex() {
        synchronized (CRAFTING_INDEX_LOCK) {
            craftingIndexOwner = null;
            craftingIndex = Index.EMPTY;
        }
    }

    private static CatalogueEntry entryOf(ServerRecipe recipe) {
        String name = recipe.displayStack().what().getDisplayName().getString();
        String searchKey = (name + ' ' + recipe.recipeId() + ' ' + recipe.providerId())
                .toLowerCase(Locale.ROOT);
        return new CatalogueEntry(recipe.providerId(), recipe.recipeId(), recipe.token(), searchKey);
    }

    /// Catalogue rows of one built-in provider, sorted, plus its token lookup and a small cache of recipes that were
    /// recently re-resolved from it.
    ///
    /// The cache lives inside the index on purpose: a trusted pattern is re-resolved on every craft dispatch, and the
    /// answer may only change when the recipes are reloaded - which replaces this whole object. Tying the two together
    /// structurally means there is no invalidation to get wrong.
    ///
    /// Tokens are 64-bit truncations of a SHA-256, so a collision is not credible in practice - but if two recipes
    /// ever did share one, resolving that token to whichever came first would craft the wrong recipe. Colliding
    /// tokens are therefore recorded and refused instead.
    private record Index(List<CatalogueEntry> entries, Long2ObjectMap<CatalogueEntry> byToken, LongSet ambiguous,
            Map<Long, ServerRecipe> resolved) {

        /// Bound on the resolve cache. A pattern provider holds far fewer patterns than this, so in practice the whole
        /// working set stays resident while a pathological world still cannot grow it without limit.
        private static final int RESOLVE_CACHE_SIZE = 512;

        static final Index EMPTY = new Index(List.of(), Long2ObjectMaps.emptyMap(), LongSets.emptySet(),
                newResolveCache());

        static Index of(Collection<ServerRecipe> recipes) {
            Builder builder = new Builder();
            recipes.forEach(builder::add);
            return builder.build();
        }

        boolean ambiguous(long token) {
            return ambiguous.contains(token);
        }

        /// The recipe behind {@code token}, resolved through {@code loader} at most once per reload. The loader's
        /// answer is verified against the token before it is cached, so a recipe that changed shape without changing
        /// its id is refused rather than reused.
        Optional<ServerRecipe> resolve(long token, Supplier<Optional<ServerRecipe>> loader) {
            synchronized (resolved) {
                ServerRecipe cached = resolved.get(token);
                if (cached != null) {
                    return Optional.of(cached);
                }
            }
            Optional<ServerRecipe> loaded = loader.get().filter(recipe -> recipe.token() == token);
            loaded.ifPresent(recipe -> {
                synchronized (resolved) {
                    resolved.put(token, recipe);
                }
            });
            return loaded;
        }

        private static Map<Long, ServerRecipe> newResolveCache() {
            return new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, ServerRecipe> eldest) {
                    return size() > RESOLVE_CACHE_SIZE;
                }
            };
        }

        private static final class Builder {

            private final List<CatalogueEntry> entries = new ArrayList<>();
            private final Long2ObjectOpenHashMap<CatalogueEntry> byToken = new Long2ObjectOpenHashMap<>();
            private final LongOpenHashSet ambiguous = new LongOpenHashSet();

            void add(ServerRecipe recipe) {
                CatalogueEntry entry = entryOf(recipe);
                entries.add(entry);
                CatalogueEntry previous = byToken.putIfAbsent(entry.token(), entry);
                if (previous != null && !previous.recipeId().equals(entry.recipeId())) {
                    FantasyTechnology.LOGGER.warn("Trusted recipe token collision between {} and {}; neither can be"
                            + " used for a server-authenticated pattern", previous.recipeId(), entry.recipeId());
                    ambiguous.add(entry.token());
                }
            }

            Index build() {
                if (entries.isEmpty()) {
                    return EMPTY;
                }
                entries.sort(Comparator.comparing(CatalogueEntry::searchKey));
                return new Index(List.copyOf(entries), Long2ObjectMaps.unmodifiable(byToken),
                        ambiguous.isEmpty() ? LongSets.emptySet() : LongSets.unmodifiable(ambiguous),
                        newResolveCache());
            }
        }
    }
}
