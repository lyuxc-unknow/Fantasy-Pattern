package cn.lyxc.fantasytechnology.deviceaccess;

import cn.lyxc.fantasytechnology.FantasyTechnology;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Loads the device-access rules from {@code data/<namespace>/device_access/<name>.json}.
///
/// A plain reload listener rather than a datapack registry, deliberately: datapack registries in this version are
/// built once when the world loads and {@code /reload} does not rebuild them, so a pack author editing a rule would
/// have to leave and re-enter the world to see it. This runs as part of {@code /reload} like recipes do, and
/// {@link DeviceRequirementLoader} pushes the result to everyone afterwards.
public class DeviceRequirementLoader extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "device_access";

    private static final Gson GSON = new Gson();

    private final RegistryAccess registryAccess;
    private final ICondition.IContext conditionContext;

    public DeviceRequirementLoader(RegistryAccess registryAccess, ICondition.IContext conditionContext) {
        super(GSON, DIRECTORY);
        this.registryAccess = registryAccess;
        this.conditionContext = conditionContext;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, @NotNull ResourceManager resourceManager,
                         @NotNull ProfilerFiller profiler) {
        var registryOps = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
        var ops = new ConditionalOps<>(registryOps, conditionContext);
        var codec = ConditionalOps.createConditionalCodec(DeviceRequirement.CODEC);
        List<DeviceRequirement> rules = new ArrayList<>(files.size());

        for (var file : files.entrySet()) {
            codec.parse(ops, file.getValue())
                    .resultOrPartial(error -> FantasyTechnology.LOGGER.error(
                            "Skipping device access rule {}: {}", file.getKey(), error))
                    .ifPresent(requirement -> requirement.ifPresent(rules::add));
        }

        DeviceRequirements.setRules(rules);
        FantasyTechnology.LOGGER.info("Loaded {} device access rule(s)", rules.size());
    }
}
