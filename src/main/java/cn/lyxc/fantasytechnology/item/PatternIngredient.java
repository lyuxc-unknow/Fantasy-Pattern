package cn.lyxc.fantasytechnology.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * One ingredient of a fantasy pattern, for items and fluids alike.
 *
 * When {@link #tag()} is present the ingredient is satisfied by anything in that tag, and {@link #stack()} is only the
 * representative shown in the UI; otherwise the exact key of {@code stack} is required. The tag is stored as a bare id
 * because item and fluid tags live in different registries - which one to look in is decided by the representative's
 * key type.
 *
 * Tags come from the recipe a pattern was filled from, so encoding a bookshelf recipe stores {@code #minecraft:planks}
 * rather than the particular plank the recipe viewer happened to display.
 */
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

    /** An ingredient that requires this exact key. */
    public static PatternIngredient of(GenericStack stack) {
        return new PatternIngredient(stack, Optional.empty());
    }

    /** An ingredient that accepts anything in {@code tag}, displayed as {@code representative}. */
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

    /**
     * Whether a key belongs to a tag, resolved in whichever registry matches the key's type. A bare tag id is
     * ambiguous on its own - {@code c:tanks} could name both an item and a fluid tag - so the key decides.
     */
    public static boolean isInTag(AEKey key, ResourceLocation tagId) {
        if (key instanceof AEItemKey itemKey) {
            return itemKey.getItem().builtInRegistryHolder().is(TagKey.create(Registries.ITEM, tagId));
        }
        if (key instanceof AEFluidKey fluidKey) {
            return fluidKey.getFluid().builtInRegistryHolder().is(TagKey.create(Registries.FLUID, tagId));
        }
        return false;
    }

    /**
     * Every key that satisfies this ingredient, the representative first so it stays the preferred choice when the
     * network holds several of them.
     */
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

    private static List<AEKey> keysInTag(AEKey representative, ResourceLocation tagId) {
        List<AEKey> keys = new ArrayList<>();
        if (representative instanceof AEItemKey) {
            for (Holder<net.minecraft.world.item.Item> holder : BuiltInRegistries.ITEM
                    .getTagOrEmpty(TagKey.create(Registries.ITEM, tagId))) {
                AEItemKey key = AEItemKey.of(holder.value());
                if (key != null) {
                    keys.add(key);
                }
            }
        } else if (representative instanceof AEFluidKey) {
            for (Holder<net.minecraft.world.level.material.Fluid> holder : BuiltInRegistries.FLUID
                    .getTagOrEmpty(TagKey.create(Registries.FLUID, tagId))) {
                AEFluidKey key = AEFluidKey.of(holder.value());
                if (key != null) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    /** How this ingredient reads in a tooltip: the tag id when tagged, the key's name otherwise. */
    public Component displayName() {
        return tag.<Component>map(id -> Component.literal("#" + id))
                .orElseGet(() -> stack.what().getDisplayName());
    }
}
