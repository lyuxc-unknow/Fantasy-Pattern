package cn.lyxc.fantasytechnology.recipeprovider;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/// Loads trusted terminal recipes from {@code data/<namespace>/recipe_provider/<path>.json}.
public final class DatapackRecipeProviderLoader extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "recipe_provider";
    private static final Gson GSON = new Gson();

    private final RegistryAccess registryAccess;
    private final ICondition.IContext conditionContext;

    public DatapackRecipeProviderLoader(RegistryAccess registryAccess, ICondition.IContext conditionContext) {
        super(GSON, DIRECTORY);
        this.registryAccess = registryAccess;
        this.conditionContext = conditionContext;
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> files, @NotNull ResourceManager resourceManager,
                         @NotNull ProfilerFiller profiler) {
        ServerRecipeProviders.setDatapackSource(registryAccess, conditionContext, files);
    }
}
