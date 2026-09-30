package cn.lyxc.fantasytechnology.recipeprovider;

import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.config.FTConfig;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/// Server-thread lifecycle for AE2's copied provider registrations. Sleeping grids need explicit updates too.
public final class PatternProviderRefresh {
    private static final Set<FantasyAnnihilationBlockEntity> LOADED =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean refreshPending;
    private static Boolean lastTrustMode;

    private PatternProviderRefresh() {
    }

    public static void register(FantasyAnnihilationBlockEntity provider) {
        LOADED.add(provider);
        provider.refreshPatterns();
    }

    public static void unregister(FantasyAnnihilationBlockEntity provider) {
        LOADED.remove(provider);
    }

    /// Called after a successful reload, once the recipe manager and tags have been installed.
    public static void afterReload() {
        refreshPending = true;
    }

    public static void beforeServerTick(ServerTickEvent.Pre event) {
        boolean trusted = FTConfig.TRUST_SERVER_RECIPE_PARSING.get();
        if (refreshPending || lastTrustMode == null || lastTrustMode != trusted) {
            refreshPending = false;
            lastTrustMode = trusted;
            ServerRecipeProviders.invalidateCraftingIndex();
            for (var provider : Set.copyOf(LOADED)) {
                if (!provider.isRemoved() && provider.getLevel() != null
                        && provider.getLevel().getServer() == event.getServer()) {
                    provider.refreshPatterns();
                }
            }
        }
    }

    public static void clear() {
        LOADED.clear();
        refreshPending = false;
        lastTrustMode = null;
    }
}
