package cn.lyxc.fantasytechnology.recipeprovider;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.lyxc.fantasytechnology.item.PatternIngredient;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/// Opaque numeric identity for a server-owned recipe.
///
/// This is a fingerprint of the complete resolved recipe rather than a client-readable id, so a datapack reload or a
/// recipe change invalidates old server-authenticated patterns instead of silently executing the previous inputs.
///
/// It deliberately is not a secret, and does not need to be one. A client that invents a token either matches no
/// recipe - in which case the pattern is refused outright - or matches a real one, in which case
/// {@link cn.lyxc.fantasytechnology.crafting.FantasyCraftingPattern#decode} replaces the pattern's inputs and outputs
/// with that recipe's own. Forging a token therefore buys nothing beyond selecting a recipe the player could have
/// selected in the terminal anyway. The 64-bit truncation only bounds accidental collisions, which are detected and
/// logged where {@link ServerRecipeProviders} builds its token indexes.
public final class ServerRecipeToken {

    private ServerRecipeToken() {
    }

    /// Compatibility entry point for providers using only built-in registries. World-dependent components need
    /// the overload taking the world's registry lookup. Serialization errors are never silently discarded.
    public static long of(ResourceLocation providerId, ResourceLocation recipeId,
            @Nullable ResourceLocation categoryId, List<PatternIngredient> inputs, List<GenericStack> outputs,
            List<Boolean> outputsIgnore) {
        return of(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), providerId, recipeId,
                categoryId, inputs, outputs, outputsIgnore);
    }

    public static long of(HolderLookup.Provider registries, ResourceLocation providerId, ResourceLocation recipeId,
            @Nullable ResourceLocation categoryId, List<PatternIngredient> inputs, List<GenericStack> outputs,
            List<Boolean> outputsIgnore) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var stream = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                // Changing the fingerprint format intentionally invalidates tokens produced by older versions.
                writeString(stream, "fantasy_recipe_v2");
                writeString(stream, providerId.toString());
                writeString(stream, recipeId.toString());
                writeString(stream, categoryId == null ? "" : categoryId.toString());
                stream.writeInt(inputs.size());
                for (PatternIngredient input : inputs) {
                    writeKey(stream, registries, input.what());
                    stream.writeLong(input.amount());
                    writeString(stream, input.tag().map(Object::toString).orElse(""));
                    stream.writeBoolean(input.ignoreData());
                }
                stream.writeInt(outputs.size());
                for (GenericStack output : outputs) {
                    writeKey(stream, registries, output.what());
                    stream.writeLong(output.amount());
                }
                stream.writeInt(outputsIgnore.size());
                for (Boolean ignored : outputsIgnore) {
                    stream.writeBoolean(ignored);
                }
            }
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new IllegalStateException("Unable to fingerprint server recipe", exception);
        }
    }

    private static void writeKey(DataOutputStream stream, HolderLookup.Provider registries, AEKey key)
            throws IOException {
        // The codec includes the key type, registry id and complete component patch. getOrThrow prevents a partial
        // encoding (e.g. a missing dynamic registry) from minting an identity that omits part of the recipe.
        Tag tag = AEKey.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), key).getOrThrow();
        writeCanonicalTag(stream, tag);
    }

    private static void writeCanonicalTag(DataOutputStream stream, Tag tag) throws IOException {
        stream.writeByte(tag.getId());
        if (tag instanceof CompoundTag compound) {
            var names = compound.getAllKeys().stream().sorted().toList();
            stream.writeInt(names.size());
            for (String name : names) {
                writeString(stream, name);
                writeCanonicalTag(stream, compound.get(name));
            }
        } else if (tag instanceof ListTag list) {
            stream.writeByte(list.getElementType());
            stream.writeInt(list.size());
            for (Tag entry : list) {
                writeCanonicalTag(stream, entry);
            }
        } else {
            NbtIo.writeAnyTag(tag, stream);
        }
    }

    private static void writeString(DataOutputStream stream, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        stream.writeInt(bytes.length);
        stream.write(bytes);
    }
}
