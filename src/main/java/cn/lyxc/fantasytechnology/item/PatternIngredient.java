package cn.lyxc.fantasytechnology.item;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.integration.mekanism.MekanismCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// One ingredient of a fantasy pattern - an item, a fluid, or any other AE2 key type such as a MEK chemical.
///
/// When {@link #tag()} is present the ingredient is satisfied by anything in that tag, and {@link #stack()} is only the
/// representative shown in the UI; otherwise the exact key of {@code stack} is required. The tag is stored as a bare id
/// because each key type's tags live in a different registry - which one to look in is decided by the representative's
/// key type.
///
/// Tags come from the recipe a pattern was filled from, so encoding a bookshelf recipe stores {@code #minecraft:planks}
/// rather than the particular plank the recipe viewer happened to display.
public record PatternIngredient(GenericStack stack, Optional<ResourceLocation> tag) {

    public static final Codec<PatternIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GenericStack.CODEC.fieldOf("stack").forGetter(PatternIngredient::stack),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(PatternIngredient::tag))
            .apply(instance, PatternIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PatternIngredient> STREAM_CODEC = StreamCodec.composite(
            GenericStack.STREAM_CODEC,
            PatternIngredient::stack,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC.cast()),
            PatternIngredient::tag,
            PatternIngredient::new);

    /// An ingredient that requires this exact key.
    public static PatternIngredient of(GenericStack stack) {
        return new PatternIngredient(stack, Optional.empty());
    }

    /// An ingredient that accepts anything in {@code tag}, displayed as {@code representative}.
    public static PatternIngredient of(GenericStack representative, ResourceLocation tag) {
        return new PatternIngredient(representative, Optional.of(tag));
    }

    public AEKey what() {
        return stack.what();
    }

    public long amount() {
        return stack.amount();
    }

    public boolean isEmpty() {
        return stack.amount() <= 0;
    }

    /// Whether this ingredient is satisfied by the given key: the exact key for an untagged ingredient, anything in
    /// the tag otherwise.
    public boolean matches(AEKey key) {
        return tag.map(resourceLocation -> isInTag(key, resourceLocation)).orElseGet(() -> stack.what().equals(key));
    }

    /// A copy of this ingredient with a different amount, keeping its tag.
    public PatternIngredient withAmount(long amount) {
        return new PatternIngredient(new GenericStack(stack.what(), amount), tag);
    }

    /// Whether a key belongs to a tag, resolved in whichever registry matches the key's type. A bare tag id is
    /// ambiguous on its own - {@code c:tanks} could name both an item and a fluid tag - so the key decides.
    ///
    /// Other key types (MEK chemicals via applied-mekanistics, for instance) are handled without naming them: any one
    /// tag name their key type already reports identifies the registry such a tag would live in, which is all that is
    /// needed to build the {@link TagKey} and ask the key itself. Key types reporting no tags at all - the default
    /// for {@code AEKeyType} - therefore never match.
    public static boolean isInTag(AEKey key, ResourceLocation tagId) {
        if (key instanceof AEItemKey itemKey) {
            return itemKey.getItem().builtInRegistryHolder().is(TagKey.create(Registries.ITEM, tagId));
        }
        if (key instanceof AEFluidKey fluidKey) {
            return fluidKey.getFluid().builtInRegistryHolder().is(TagKey.create(Registries.FLUID, tagId));
        }
        // findAny() short-circuits on the first tag name, which keeps this constant time: it runs for every
        // candidate key the crafting planner considers.
        return key.getType().getTagNames().findAny()
                .map(known -> key.isTagged(TagKey.create(known.registry(), tagId)))
                .orElse(false);
    }

    /// Every key that satisfies this ingredient, the representative first so it stays the preferred choice when the
    /// network holds several of them.
    public List<AEKey> possibleKeys() {
        if (tag.isEmpty()) {
            return List.of(stack.what());
        }
        List<AEKey> keys = new ArrayList<>();
        keys.add(stack.what());
        for (AEKey key : keysInTag(stack.what(), tag.get())) {
            if (!key.equals(stack.what())) {
                keys.add(key);
            }
        }
        return keys;
    }

    /// Everything in the tag, for the registries this mod knows how to walk. Unlike {@link #isInTag}, enumerating a
    /// tag needs the registry object itself, which {@code AEKeyType} does not expose - so a key that is neither an
    /// item, a fluid nor a MEK chemical contributes no substitutes and the ingredient falls back to its
    /// representative alone.
    private static List<AEKey> keysInTag(AEKey representative, ResourceLocation tagId) {
        List<AEKey> keys = new ArrayList<>();
        if (representative instanceof AEItemKey) {
            for (Holder<Item> holder : BuiltInRegistries.ITEM
                    .getTagOrEmpty(TagKey.create(Registries.ITEM, tagId))) {
                AEItemKey key = AEItemKey.of(holder.value());
                if (key != null) {
                    keys.add(key);
                }
            }
        } else if (representative instanceof AEFluidKey) {
            for (Holder<Fluid> holder : BuiltInRegistries.FLUID
                    .getTagOrEmpty(TagKey.create(Registries.FLUID, tagId))) {
                AEFluidKey key = AEFluidKey.of(holder.value());
                if (key != null) {
                    keys.add(key);
                }
            }
        } else {
            // Chemicals live in mekanism's own registry, reachable only through the compat facade - which returns
            // nothing when the bridging mods are absent or the key is not a chemical.
            keys.addAll(MekanismCompat.chemicalKeysInTag(representative, tagId));
        }
        return keys;
    }

    /// How this ingredient reads in a tooltip: the tag id when tagged, the key's name otherwise.
    public Component displayName() {
        return tag.<Component>map(id -> Component.literal("#" + id))
                .orElseGet(() -> stack.what().getDisplayName());
    }
}
