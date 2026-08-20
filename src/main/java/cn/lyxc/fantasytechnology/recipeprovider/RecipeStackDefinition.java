package cn.lyxc.fantasytechnology.recipeprovider;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.integration.mekanism.MekanismCompat;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// One typed stack in the recipe-provider datapack DSL.
///
/// Built-in types are {@code minecraft:item}, {@code minecraft:fluid}, and (when Mekanism plus Applied Mekanistics
/// are loaded) {@code mekanism:chemical}. The resolver registry deliberately stays public so another AE key bridge
/// can add a type without changing the JSON shape, network protocol, or terminal UI.
public record RecipeStackDefinition(ResourceLocation type, ResourceLocation id, long amount,
        Optional<ResourceLocation> tag, boolean ignoreData) {

    public static final ResourceLocation ITEM = ResourceLocation.withDefaultNamespace("item");
    public static final ResourceLocation FLUID = ResourceLocation.withDefaultNamespace("fluid");
    public static final ResourceLocation CHEMICAL = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical");

    private static final Codec<Long> POSITIVE_LONG = Codec.LONG.validate(value -> value > 0
            && value <= Integer.MAX_VALUE
            ? DataResult.success(value)
            : DataResult.error(() -> "amount must be between 1 and " + Integer.MAX_VALUE));

    public static final Codec<RecipeStackDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("type", ITEM).forGetter(RecipeStackDefinition::type),
            ResourceLocation.CODEC.fieldOf("id").forGetter(RecipeStackDefinition::id),
            POSITIVE_LONG.optionalFieldOf("amount", 1L).forGetter(RecipeStackDefinition::amount),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(RecipeStackDefinition::tag),
            Codec.BOOL.optionalFieldOf("ignore_data", false).forGetter(RecipeStackDefinition::ignoreData))
            .apply(instance, RecipeStackDefinition::new));

    @FunctionalInterface
    public interface Resolver {
        @Nullable AEKey resolve(ResourceLocation id);
    }

    private static final Map<ResourceLocation, Resolver> RESOLVERS = new ConcurrentHashMap<>();

    static {
        registerResolver(ITEM, id -> BuiltInRegistries.ITEM.getOptional(id).map(AEItemKey::of).orElse(null));
        registerResolver(FLUID, id -> BuiltInRegistries.FLUID.getOptional(id).map(AEFluidKey::of).orElse(null));
    }

    public static void registerResolver(ResourceLocation type, Resolver resolver) {
        Resolver previous = RESOLVERS.putIfAbsent(type, resolver);
        if (previous != null && previous != resolver) {
            throw new IllegalStateException("A recipe stack resolver is already registered for " + type);
        }
    }

    /// Registers the optional chemical type after mod loading has established whether both bridge mods are present.
    /// Idempotent: a method reference is a fresh object every time, so registering through the strict path above
    /// would reject a second call rather than ignore it.
    public static void registerOptionalResolvers() {
        if (MekanismCompat.isPresent()) {
            RESOLVERS.computeIfAbsent(CHEMICAL, type -> MekanismCompat::chemicalKey);
        }
    }

    public GenericStack resolveStack() {
        Resolver resolver = RESOLVERS.get(type);
        if (resolver == null) {
            throw new IllegalArgumentException("Unknown recipe stack type " + type);
        }
        AEKey key = resolver.resolve(id);
        if (key == null) {
            throw new IllegalArgumentException("Unknown " + type + " id " + id);
        }
        return new GenericStack(key, amount);
    }

    public PatternIngredient resolveIngredient() {
        GenericStack stack = resolveStack();
        return new PatternIngredient(stack, tag, ignoreData);
    }
}
